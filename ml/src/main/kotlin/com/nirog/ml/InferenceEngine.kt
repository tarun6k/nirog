package com.nirog.ml

import com.nirog.model.DiseaseCandidate
import com.nirog.model.Verdict
import java.io.File

/** Raw inference result; callers attach scan/plot identity to build a Diagnosis. */
data class InferenceOutput(
    val candidates: List<DiseaseCandidate>,
    val calibratedConfidence: Double,
    val oodScore: Double,
    val severityPct: Double,
    val verdict: Verdict,
    val modelVersion: String,
)

class ModelNotAvailableException(name: String) :
    IllegalStateException("model '$name' not present in updated-models dir or assets")

interface InferenceEngine {
    /**
     * images: the three captured shots (whole plant, leaf top, leaf underside).
     * cropId: the plot's declared crop — the router model verifies it rather
     * than trusting it blindly.
     */
    suspend fun diagnose(images: List<File>, cropId: String): InferenceOutput
}

/**
 * Fixture engine so the whole app is buildable and testable before any real
 * model exists. Default in debug builds via DI.
 */
class StubInferenceEngine(
    private val fixture: InferenceOutput = InferenceOutput(
        candidates = listOf(
            DiseaseCandidate("yellow_rust", 0.92),
            DiseaseCandidate("brown_rust", 0.05),
        ),
        calibratedConfidence = 0.92,
        oodScore = -8.0,
        severityPct = 12.0,
        verdict = Verdict.CONFIDENT,
        modelVersion = "stub-1",
    ),
) : InferenceEngine {
    override suspend fun diagnose(images: List<File>, cropId: String): InferenceOutput = fixture
}
