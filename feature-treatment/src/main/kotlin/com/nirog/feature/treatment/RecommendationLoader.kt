package com.nirog.feature.treatment

import com.nirog.data.DiagnosisEntity
import com.nirog.data.NirogDb
import com.nirog.data.toDomain
import com.nirog.engine.Catalog
import com.nirog.engine.RecommendationEngine
import com.nirog.engine.RecommendationResult
import com.nirog.engine.DoseCalculator
import com.nirog.engine.DoseResult
import com.nirog.model.ContextSnapshot
import com.nirog.model.LabelClaim
import com.nirog.model.Product
import java.time.Instant
import java.time.LocalDate

/** Loads the catalog + history from Room and runs the pure engine. */
class RecommendationLoader(private val db: NirogDb) {

    suspend fun recommend(diagnosisEntity: DiagnosisEntity, plotId: String): RecommendationResult {
        val plot = requireNotNull(db.plotDao().plot(plotId)) { "unknown plot $plotId" }.toDomain()
        val catalog = Catalog(
            products = db.catalogDao().products().map { it.toDomain() },
            claims = db.catalogDao().labelClaims().map { it.toDomain() },
            banned = db.catalogDao().bannedActives().map { it.toDomain() },
            etlThresholds = db.catalogDao().etlThresholds().map { it.toDomain() },
        )
        val history = db.diaryDao().lastTwoSprays(plotId).map { it.toDomain() }
        // The engine only reads spray-history groups from ContextSnapshot indirectly;
        // a minimal snapshot is enough here.
        val context = ContextSnapshot(
            scanId = diagnosisEntity.scanId, daysSinceSowing = 0, growthStage = null,
            weather14d = null, districtPressureScore = null,
            lastSprayFracGroup = null, lastSprayIracGroup = null, capturedAt = Instant.now(),
        )
        return RecommendationEngine.recommend(
            diagnosis = diagnosisEntity.toDomain(),
            plot = plot,
            context = context,
            sprayHistory = history,
            catalog = catalog,
            today = LocalDate.now(),
        )
    }

    data class DosePrep(val product: Product, val claim: LabelClaim, val plan: DoseResult)

    /** Resolve the rung's product + its I1 claim and compute the I10 tank plan. */
    suspend fun prepareDose(productId: String, pestId: String, plotId: String): DosePrep? {
        val plot = db.plotDao().plot(plotId)?.toDomain() ?: return null
        val product = db.catalogDao().product(productId)?.toDomain() ?: return null
        val today = LocalDate.now()
        val claim = db.catalogDao().labelClaims().map { it.toDomain() }.firstOrNull {
            it.productId == productId && it.cropId == plot.cropId && it.pestId == pestId &&
                !today.isBefore(it.effectiveFrom) && (it.effectiveTo == null || !today.isAfter(it.effectiveTo))
        } ?: return null
        return DosePrep(product, claim, DoseCalculator.plan(claim, product, plot, today))
    }
}
