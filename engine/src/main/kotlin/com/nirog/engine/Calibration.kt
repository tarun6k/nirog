package com.nirog.engine

import com.nirog.model.Verdict
import kotlin.math.exp
import kotlin.math.ln

object Calibration {

    /** Temperature-scaled softmax. T is fit on a held-out calibration set (see inference.properties). */
    fun softmax(logits: DoubleArray, temperature: Double): DoubleArray {
        require(temperature > 0) { "temperature must be positive" }
        val max = logits.max()
        val exps = DoubleArray(logits.size) { exp((logits[it] - max) / temperature) }
        val sum = exps.sum()
        return DoubleArray(logits.size) { exps[it] / sum }
    }

    /**
     * Energy-based OOD score: -T * logsumexp(logits/T). Large confident logits
     * give very negative energy; out-of-distribution inputs drift toward zero.
     * Higher energy = more likely OOD.
     */
    fun energyOod(logits: DoubleArray, temperature: Double): Double {
        require(temperature > 0) { "temperature must be positive" }
        val max = logits.max()
        val sum = logits.sumOf { exp((it - max) / temperature) }
        return -temperature * (max / temperature + ln(sum))
    }
}

/**
 * Verdict thresholds. NEVER hardcode these at call sites — they are read from
 * ml/src/main/assets/config/inference.properties, where each value's provenance
 * is documented. Parsing fails loudly on a missing key.
 */
data class InferenceThresholds(
    val temperature: Double,
    val minTopProbConfident: Double,
    val minMarginConfident: Double,
    val minTopProbAmbiguous: Double,
    val maxOodEnergy: Double,
    /** If the crop router contradicts the declared crop with at least this probability, abstain. */
    val routerDisagreeProb: Double,
) {
    companion object {
        fun fromProperties(text: String): InferenceThresholds {
            val map = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { line ->
                    val (k, v) = line.split('=', limit = 2).also {
                        require(it.size == 2) { "bad config line: $line" }
                    }
                    k.trim() to v.trim()
                }

            fun need(key: String): Double =
                requireNotNull(map[key]?.toDoubleOrNull()) { "inference config missing or non-numeric: $key" }

            return InferenceThresholds(
                temperature = need("temperature"),
                minTopProbConfident = need("minTopProbConfident"),
                minMarginConfident = need("minMarginConfident"),
                minTopProbAmbiguous = need("minTopProbAmbiguous"),
                maxOodEnergy = need("maxOodEnergy"),
                routerDisagreeProb = need("routerDisagreeProb"),
            )
        }
    }
}

object VerdictMapper {

    /** probs: calibrated class probabilities, any order. ood: energy score from [Calibration.energyOod]. */
    fun map(probs: DoubleArray, ood: Double, t: InferenceThresholds): Verdict {
        if (ood > t.maxOodEnergy) return Verdict.ABSTAIN
        val sorted = probs.sortedDescending()
        val top = sorted.getOrElse(0) { 0.0 }
        val margin = top - sorted.getOrElse(1) { 0.0 }
        return when {
            top >= t.minTopProbConfident && margin >= t.minMarginConfident -> Verdict.CONFIDENT
            top >= t.minTopProbAmbiguous -> Verdict.AMBIGUOUS
            else -> Verdict.ABSTAIN
        }
    }
}
