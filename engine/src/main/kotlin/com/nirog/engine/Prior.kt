package com.nirog.engine

import com.nirog.model.DiseaseCandidate

enum class PriorMetric { RAIN_MM_14D, AVG_RH_14D, AVG_TMAX_14D, DISTRICT_PRESSURE }
enum class RuleOp { GTE, LTE }

/**
 * One agronomist-provided rule: "if metric op value, multiply pestId's prior by
 * multiplier". Rules live in ml/src/main/assets/config/prior_rules.csv —
 * headers-only until an agronomist supplies them, same policy as label data.
 */
data class PriorRule(
    val pestId: String,
    val metric: PriorMetric,
    val op: RuleOp,
    val value: Double,
    val multiplier: Double,
    val source: String,
)

/** Audit record: exactly what was adjusted, by how much, and on what evidence. */
data class PriorAdjustment(
    val pestId: String,
    val metric: PriorMetric,
    val actualValue: Double,
    val ruleValue: Double,
    val multiplier: Double,
    val source: String,
)

data class AdjustedPriors(
    val candidates: List<DiseaseCandidate>,
    val adjustments: List<PriorAdjustment>,
)

object PriorAdjuster {

    /** Missing metrics skip their rules — absent evidence never adjusts anything. */
    fun adjust(
        candidates: List<DiseaseCandidate>,
        metrics: Map<PriorMetric, Double>,
        rules: List<PriorRule>,
    ): AdjustedPriors {
        if (rules.isEmpty() || candidates.isEmpty()) return AdjustedPriors(candidates, emptyList())
        val adjustments = mutableListOf<PriorAdjustment>()
        val multipliers = candidates.associate { it.pestId to 1.0 }.toMutableMap()
        for (rule in rules) {
            if (rule.pestId !in multipliers) continue
            val actual = metrics[rule.metric] ?: continue
            val holds = when (rule.op) {
                RuleOp.GTE -> actual >= rule.value
                RuleOp.LTE -> actual <= rule.value
            }
            if (!holds) continue
            multipliers[rule.pestId] = multipliers.getValue(rule.pestId) * rule.multiplier
            adjustments += PriorAdjustment(rule.pestId, rule.metric, actual, rule.value, rule.multiplier, rule.source)
        }
        if (adjustments.isEmpty()) return AdjustedPriors(candidates, emptyList())
        val weighted = candidates.map { it.copy(probability = it.probability * multipliers.getValue(it.pestId)) }
        val total = weighted.sumOf { it.probability }
        return AdjustedPriors(
            weighted.map { it.copy(probability = it.probability / total) },
            adjustments,
        )
    }

    /** CSV: pestId,metric,op,value,multiplier,source. Fails loudly on bad rows. */
    fun parseRules(csv: String): List<PriorRule> =
        csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .drop(1)
            .map { line ->
                val c = line.split(',').map { it.trim() }
                require(c.size == 6) { "prior_rules.csv expects 6 columns: $line" }
                PriorRule(
                    pestId = c[0],
                    metric = PriorMetric.valueOf(c[1].uppercase()),
                    op = RuleOp.valueOf(c[2].uppercase()),
                    value = c[3].toDouble(),
                    multiplier = c[4].toDouble(),
                    source = c[5],
                )
            }
            .toList()
}
