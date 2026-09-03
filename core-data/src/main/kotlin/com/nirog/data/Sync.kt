package com.nirog.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration

/**
 * Upload queue: consented escalation images and geohash-5 outbreak reports.
 * WorkManager gives retry-with-backoff and survives process death. Payloads
 * carry NO farmer identifier and no coordinates finer than geohash-5.
 */
object Sync {
    const val WORK_NAME = "nirog-sync"

    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
                .build(),
        )
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val base = BuildConfig.NIROG_API_BASE
        // No backend configured yet: keep everything queued locally, no retry storm.
        if (base.isEmpty()) return Result.success()

        val db = NirogDb.create(applicationContext)
        var allOk = true

        for (ticket in db.escalationDao().pendingUploads()) {
            val session = db.scanDao().session(ticket.scanId) ?: continue
            val ok = runCatching { uploadEscalation(base, ticket, session) }.getOrDefault(false)
            if (ok) db.escalationDao().markSynced(ticket.id) else allOk = false
        }

        val pending = db.outbreakDao().pending()
        if (pending.isNotEmpty()) {
            val ok = runCatching { uploadOutbreaks(base, pending) }.getOrDefault(false)
            if (ok) pending.forEach { db.outbreakDao().markSynced(it.geohash5, it.createdAt) } else allOk = false
        }

        return if (allOk) Result.success() else Result.retry()
    }

    private fun uploadEscalation(base: String, ticket: EscalationTicketEntity, session: ScanSessionEntity): Boolean {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("ticketId", ticket.id)
            .addFormDataPart("scanId", ticket.scanId)
            .addFormDataPart("deviceModel", session.deviceModel)
            .addFormDataPart("appVersion", session.appVersion)
        session.imagePaths.split(';').filter { it.isNotEmpty() }.map(::File).filter { it.exists() }
            .forEachIndexed { i, f ->
                body.addFormDataPart("image$i", f.name, f.asRequestBody("image/jpeg".toMediaType()))
            }
        return client.newCall(
            Request.Builder().url("$base/v1/escalations").post(body.build()).build(),
        ).execute().use { it.isSuccessful }
    }

    private fun uploadOutbreaks(base: String, reports: List<OutbreakReportEntity>): Boolean {
        val payload = JSONArray(
            reports.map {
                JSONObject()
                    .put("geohash5", it.geohash5)
                    .put("cropId", it.cropId)
                    .put("diseaseId", it.diseaseId)
                    .put("confirmedBy", it.confirmedBy)
                    .put("createdAt", it.createdAt)
            },
        ).toString()
        return client.newCall(
            Request.Builder().url("$base/v1/outbreaks")
                .post(payload.toRequestBody("application/json".toMediaType())).build(),
        ).execute().use { it.isSuccessful }
    }
}
