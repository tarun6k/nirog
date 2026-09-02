package com.nirog.feature.scan

import android.content.Context
import android.os.Build
import com.nirog.data.ScanDao
import com.nirog.data.ScanSessionEntity
import com.nirog.model.SyncState
import java.io.File
import java.util.UUID

/** Owns where scan images live (app-private storage) and session persistence. */
class ScanStore(private val context: Context, private val scanDao: ScanDao) {

    fun newSessionId(): String = UUID.randomUUID().toString()

    fun imageFile(sessionId: String, step: CaptureStep): File =
        File(File(context.filesDir, "scans/$sessionId").apply { mkdirs() }, "${step.name.lowercase()}.jpg")

    suspend fun completeSession(sessionId: String, plotId: String, images: List<File>) {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        scanDao.insertSession(
            ScanSessionEntity(
                id = sessionId,
                plotId = plotId,
                createdAt = System.currentTimeMillis(),
                imagePaths = images.joinToString(";") { it.absolutePath },
                // ponytail: 1.0 = passed the live gate; store real gate metrics
                // per image when the diagnosis pipeline starts consuming them
                qualityScores = images.joinToString(";") { "1.0" },
                deviceModel = Build.MODEL,
                appVersion = version,
                syncState = SyncState.PENDING.name,
            ),
        )
    }
}
