package com.nirog.engine

import com.nirog.model.BannedActive
import com.nirog.model.ContextSnapshot
import com.nirog.model.Diagnosis
import com.nirog.model.DiseaseCandidate
import com.nirog.model.EtlThreshold
import com.nirog.model.LabelClaim
import com.nirog.model.OrganicStatus
import com.nirog.model.Plot
import com.nirog.model.Product
import com.nirog.model.SprayLog
import com.nirog.model.TreatmentRung
import com.nirog.model.TreatmentTier
import com.nirog.model.Verdict
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The regulatory reference data the engine filters against. Loaded from Room by callers. */
data class Catalog(
    val products: List<Product>,
    val claims: List<LabelClaim>,
    val banned: List<BannedActive>,
    val etlThresholds: List<EtlThreshold>,
)

/** Why a product was excluded. Typed so the UI can render it in the farmer's language (I6). */
sealed interface RejectionReason {
    val messageKey: String

    data object NoLabelClaim : RejectionReason {
        override val messageKey = "reject_no_label_claim"
    }

    data class ClaimNotEffective(val from: LocalDate, val to: LocalDate?) : RejectionReason {
        override val messageKey = "reject_claim_not_effective"
    }

    data class Banned(val notificationRef: String) : RejectionReason {
        override val messageKey = "reject_banned"
    }

    data class PhiTooLong(val phiDays: Int, val daysToHarvest: Long) : RejectionReason {
        override val messageKey = "reject_phi_too_long"
    }

    data class ResistanceRotation(val groupCode: String, val group: String) : RejectionReason {
        override val messageKey = "reject_resistance_rotation"
    }

    data object OrganicCertification : RejectionReason {
        override val messageKey = "reject_organic_certification"
    }
}

data class Rejection(val productId: String, val reason: RejectionReason)

sealed interface RecommendationResult {
    data class Ladder(val rungs: List<TreatmentRung>, val rejections: List<Rejection>) : RecommendationResult

    /** I9: nothing approved survives. Never falls back to a closest match. */
    data class NoApprovedProductFound(val rejections: List<Rejection>) : RecommendationResult

    /** I8: an ABSTAIN verdict produces an escalation, never advice. */
    data class Escalate(val scanId: String) : RecommendationResult

    /** Ambiguous diagnosis: show candidates, offer escalation, recommend nothing. */
    data class Ambiguous(val candidates: List<DiseaseCandidate>) : RecommendationResult
}

object RecommendationEngine {

    fun recommend(
        diagnosis: Diagnosis,
        plot: Plot,
        context: ContextSnapshot,
        sprayHistory: List<SprayLog>,
        catalog: Catalog,
        today: LocalDate,
        tankCapacityL: Double = DoseCalculator.DEFAULT_TANK_CAPACITY_L,
    ): RecommendationResult {
        if (diagnosis.verdict == Verdict.ABSTAIN) return RecommendationResult.Escalate(diagnosis.scanId)
        if (diagnosis.verdict == Verdict.AMBIGUOUS) return RecommendationResult.Ambiguous(diagnosis.candidates)

        val pestId = diagnosis.candidates.maxByOrNull { it.probability }?.pestId
            ?: return RecommendationResult.Escalate(diagnosis.scanId)

        // I4: groups of the last two sprays on this plot, resolved via the catalog.
        // Manually-logged products unknown to the catalog can't clash; acceptable gap.
        val recentProducts = sprayHistory
            .sortedByDescending { it.date }
            .take(2)
            .mapNotNull { log -> catalog.products.find { it.id == log.productId } }

        val rejections = mutableListOf<Rejection>()
        val survivors = mutableListOf<Pair<Product, LabelClaim>>()

        for (product in catalog.products) {
            // I1: exact (product, crop, pest) claim, effective today. No fuzzy matching.
            val claims = catalog.claims.filter {
                it.productId == product.id && it.cropId == plot.cropId && it.pestId == pestId
            }
            if (claims.isEmpty()) {
                rejections += Rejection(product.id, RejectionReason.NoLabelClaim)
                continue
            }
            val claim = claims.firstOrNull {
                !today.isBefore(it.effectiveFrom) && (it.effectiveTo == null || !today.isAfter(it.effectiveTo))
            }
            if (claim == null) {
                val c = claims.first()
                rejections += Rejection(product.id, RejectionReason.ClaimNotEffective(c.effectiveFrom, c.effectiveTo))
                continue
            }

            // I2: banned actives. No override path exists anywhere in this module.
            val ban = catalog.banned.firstOrNull {
                it.activeIngredient == product.activeIngredient &&
                    !today.isBefore(it.bannedFrom) &&
                    (it.scopeCropId == null || it.scopeCropId == plot.cropId)
            }
            if (ban != null) {
                rejections += Rejection(product.id, RejectionReason.Banned(ban.notificationRef))
                continue
            }

            // I5: organic certification. Any non-NONE status excludes non-bio / non-NPOP.
            if (plot.organicStatus != OrganicStatus.NONE && (!product.isBiopesticide || !product.npopPermitted)) {
                rejections += Rejection(product.id, RejectionReason.OrganicCertification)
                continue
            }

            // I3: PHI must fit inside the remaining days to planned harvest.
            val daysToHarvest = ChronoUnit.DAYS.between(today, plot.plannedHarvestDate)
            if (claim.phiDays > daysToHarvest) {
                rejections += Rejection(product.id, RejectionReason.PhiTooLong(claim.phiDays, daysToHarvest))
                continue
            }

            // I4: no repeat of either of the last two sprays' FRAC/IRAC groups.
            val fracClash = product.fracGroup != null && recentProducts.any { it.fracGroup == product.fracGroup }
            if (fracClash) {
                rejections += Rejection(product.id, RejectionReason.ResistanceRotation("FRAC", product.fracGroup!!))
                continue
            }
            val iracClash = product.iracGroup != null && recentProducts.any { it.iracGroup == product.iracGroup }
            if (iracClash) {
                rejections += Rejection(product.id, RejectionReason.ResistanceRotation("IRAC", product.iracGroup!!))
                continue
            }

            survivors += product to claim
        }

        if (survivors.isEmpty()) return RecommendationResult.NoApprovedProductFound(rejections)

        val etl = catalog.etlThresholds.firstOrNull { it.cropId == plot.cropId && it.pestId == pestId }
        val rungs = survivors
            .map { (product, claim) ->
                // ponytail: tier derived from isBiopesticide only; CULTURAL/MECHANICAL/BOTANICAL
                // rungs need an advisory-practices dataset that doesn't exist yet.
                val tier = if (product.isBiopesticide) TreatmentTier.BIOLOGICAL else TreatmentTier.CHEMICAL
                // I7: chemical rungs stay locked below the ETL — or when no ETL data exists.
                val (locked, lockReason) = when {
                    tier != TreatmentTier.CHEMICAL -> false to null
                    etl == null -> true to "No ETL threshold on file for ${plot.cropId}/$pestId — chemical use not unlocked without it"
                    diagnosis.severityPct < etl.thresholdValue ->
                        true to "Severity ${diagnosis.severityPct}${etl.unit} is below the economic threshold ${etl.thresholdValue}${etl.unit} (${etl.source})"
                    else -> false to null
                }
                val cost = (DoseCalculator.plan(claim, product, plot, today, tankCapacityL) as? DoseResult.Plan)?.totalCostInr
                Triple(tier, product, TreatmentRung(0, tier, product.id, cost, locked, lockReason))
            }
            .sortedWith(compareBy({ it.first.ordinal }, { it.third.costInr ?: Double.MAX_VALUE }))
            .mapIndexed { i, (_, _, rung) -> rung.copy(order = i) }

        return RecommendationResult.Ladder(rungs, rejections)
    }
}
