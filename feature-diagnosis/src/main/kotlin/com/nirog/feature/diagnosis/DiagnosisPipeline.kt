package com.nirog.feature.diagnosis

import com.nirog.data.ContextBuilder
import com.nirog.data.DiagnosisEntity
import com.nirog.data.NirogDb
import com.nirog.data.ScanSessionEntity
import com.nirog.engine.InferenceThresholds
import com.nirog.engine.PriorAdjuster
import com.nirog.engine.PriorRule
import com.nirog.engine.VerdictMapper
import com.nirog.ml.InferenceEngine
import com.nirog.ml.ModelNotAvailableException
import com.nirog.model.DiagnosisSource
import com.nirog.model.Verdict
import java.io.File
import java.util.UUID

/**
 * Scan session -> persisted Diagnosis: inference, then context-fusion prior
 * adjustment (audited), then verdict. Offline or model-missing paths degrade to
 * a visible note / ABSTAIN — never a crash, never a silent downgrade.
 */
class DiagnosisPipeline(
    private val inference: InferenceEngine,
    private val contextBuilder: ContextBuilder,
    private val db: NirogDb,
    private val thresholds: InferenceThresholds,
    private val priorRules: List<PriorRule>,
) {
    data class Result(val diagnosis: DiagnosisEntity, val noteKeys: List<String>)

    suspend fun run(session: ScanSessionEntity): Result {
        val plot = requireNotNull(db.plotDao().plot(session.plotId)) { "unknown plot ${session.plotId}" }
        val ctx = contextBuilder.build(plot, session.id)
        val notes = mutableListOf<String>()
        ctx.noteKey?.let { notes += it }

        val images = session.imagePaths.split(';').filter { it.isNotEmpty() }.map(::File)
        val out = try {
            inference.diagnose(images, plot.cropId)
        } catch (_: ModelNotAvailableException) {
            notes += "diagnosis_model_unavailable"
            null
        }

        val (candidates, adjustments, verdict) = when {
            out == null -> Triple(emptyList(), emptyList(), Verdict.ABSTAIN)
            // I8: an abstaining model is never "rescued" by context fusion.
            out.verdict == Verdict.ABSTAIN || priorRules.isEmpty() ->
                Triple(out.candidates, emptyList(), out.verdict)
            else -> {
                val adj = PriorAdjuster.adjust(out.candidates, ctx.metrics, priorRules)
                val v = VerdictMapper.map(
                    adj.candidates.map { it.probability }.toDoubleArray(),
                    out.oodScore,
                    thresholds,
                )
                Triple(adj.candidates, adj.adjustments, v)
            }
        }

        val entity = DiagnosisEntity(
            id = UUID.randomUUID().toString(),
            scanId = session.id,
            candidates = candidates.joinToString(";") { "${it.pestId}:${it.probability}" },
            calibratedConfidence = candidates.maxOfOrNull { it.probability } ?: 0.0,
            oodScore = out?.oodScore ?: 0.0,
            severityPct = out?.severityPct ?: 0.0,
            verdict = verdict.name,
            modelVersion = out?.modelVersion ?: "none",
            source = DiagnosisSource.ON_DEVICE.name,
            priorAdjustments = adjustments.takeIf { it.isNotEmpty() }?.joinToString(";") {
                "${it.pestId}|${it.metric}|${it.actualValue}|${it.ruleValue}|${it.multiplier}|${it.source}"
            },
        )
        db.scanDao().insertDiagnosis(entity)
        db.scanDao().insertContext(ctx.entity)

        if (verdict == Verdict.ABSTAIN) {
            // I8: abstain produces an escalation. Images upload only after per-scan consent.
            db.escalationDao().insert(
                com.nirog.data.EscalationTicketEntity(
                    id = UUID.randomUUID().toString(), scanId = session.id, status = "PENDING",
                    expertAnswer = null, answeredAt = null, uploadConsent = false, synced = false,
                ),
            )
        }
        if (verdict == Verdict.CONFIDENT && plot.lat != null && plot.lon != null) {
            // Community signal at geohash-5 (~5 km) only — never exact coordinates.
            db.outbreakDao().insert(
                com.nirog.data.OutbreakReportEntity(
                    geohash5 = com.nirog.engine.Geohash.encode(plot.lat!!, plot.lon!!, 5),
                    cropId = plot.cropId,
                    diseaseId = candidates.first().pestId,
                    confirmedBy = DiagnosisSource.ON_DEVICE.name,
                    createdAt = System.currentTimeMillis(),
                    synced = false,
                ),
            )
        }
        return Result(entity, notes)
    }
}
