package com.nirog.feature.treatment

import com.nirog.data.DiagnosisEntity
import com.nirog.data.NirogDb
import com.nirog.data.toDomain
import com.nirog.engine.Catalog
import com.nirog.engine.RecommendationEngine
import com.nirog.engine.RecommendationResult
import com.nirog.model.ContextSnapshot
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
}
