package com.nirog.model

import java.time.Instant
import java.time.LocalDate

enum class AreaUnit { ACRE, BIGHA, GUNTHA, HECTARE }
enum class OrganicStatus { NONE, NPOP_CERTIFIED, PGS_REGISTERED, IN_CONVERSION }
enum class Verdict { CONFIDENT, AMBIGUOUS, ABSTAIN }
enum class DiagnosisSource { ON_DEVICE, CLOUD, HUMAN }
enum class ToxicityTriangle { GREEN, BLUE, YELLOW, RED }
enum class TreatmentTier { CULTURAL, MECHANICAL, BIOLOGICAL, BOTANICAL, CHEMICAL }
enum class SyncState { PENDING, SYNCED, FAILED }

/** How a label-claim dose is expressed. Per litre of spray water, or per hectare of land. */
enum class DoseUnit { ML_PER_L, G_PER_L, ML_PER_HA, G_PER_HA }

data class Farmer(
    val id: String,
    val phoneHash: String,
    val preferredLanguage: String,
    val consentVersion: Int,
    val consentGrantedAt: Instant,
)

data class Plot(
    val id: String,
    val farmerId: String,
    val label: String,
    val cropId: String,
    val variety: String?,
    val areaValue: Double,
    val areaUnit: AreaUnit,
    val sowingDate: LocalDate,
    val lat: Double?,
    val lon: Double?,
    val pincode: String?,
    val plannedHarvestDate: LocalDate,
    val organicStatus: OrganicStatus,
    // Not in the original spec: bigha size is a per-state lookup, so the plot must know
    // its state. Deriving it from pincode is a data problem deferred to onboarding.
    val state: String?,
)

data class ScanSession(
    val id: String,
    val plotId: String,
    val createdAt: Instant,
    val imagePaths: List<String>,
    val qualityScores: List<Double>,
    val deviceModel: String,
    val appVersion: String,
    val syncState: SyncState,
)

data class ContextSnapshot(
    val scanId: String,
    val daysSinceSowing: Int,
    val growthStage: String?,
    val weather14d: String?,
    val districtPressureScore: Double?,
    val lastSprayFracGroup: String?,
    val lastSprayIracGroup: String?,
    val capturedAt: Instant,
)

data class DiseaseCandidate(val pestId: String, val probability: Double)

data class Diagnosis(
    val id: String,
    val scanId: String,
    val candidates: List<DiseaseCandidate>,
    val calibratedConfidence: Double,
    val oodScore: Double,
    val severityPct: Double,
    val verdict: Verdict,
    val modelVersion: String,
    val source: DiagnosisSource,
)

data class Product(
    val id: String,
    val tradeNames: List<String>,
    val activeIngredient: String,
    val concentration: String,
    val formulation: String,
    val fracGroup: String?,
    val iracGroup: String?,
    val toxicityTriangle: ToxicityTriangle,
    val isBiopesticide: Boolean,
    val npopPermitted: Boolean,
    /** ₹ per ml (liquid) or per g (powder) of formulated product. */
    val pricePerUnitInr: Double,
)

data class LabelClaim(
    val productId: String,
    val cropId: String,
    val pestId: String,
    val doseValue: Double,
    val doseUnit: DoseUnit,
    val dilutionLPerHa: Double,
    val phiDays: Int,
    val sourceNotificationRef: String,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate?,
)

data class BannedActive(
    val activeIngredient: String,
    val notificationRef: String,
    val bannedFrom: LocalDate,
    /** null = banned for all uses; otherwise banned for this crop only. */
    val scopeCropId: String?,
)

data class EtlThreshold(
    val cropId: String,
    val pestId: String,
    val metric: String,
    val thresholdValue: Double,
    val unit: String,
    val source: String,
)

data class TreatmentRung(
    val order: Int,
    val tier: TreatmentTier,
    val productId: String?,
    val costInr: Double?,
    val locked: Boolean,
    val lockReason: String?,
)

data class SprayLog(
    val id: String,
    val plotId: String,
    val date: LocalDate,
    val productId: String,
    val activeIngredient: String,
    val doseActual: String?,
    val tanks: Int?,
    val costInr: Double?,
    val phiExpiryDate: LocalDate?,
    val recommendationId: String?,
    val farmerConfirmed: Boolean,
)

data class EscalationTicket(
    val id: String,
    val scanId: String,
    val status: String,
    val expertAnswer: String?,
    val answeredAt: Instant?,
)

/** 14-day weather aggregate used for context fusion; serialized into ContextSnapshot.weather14d. */
data class Weather14d(
    val rainMmTotal: Double,
    val avgRhPct: Double,
    val avgTMaxC: Double,
    val avgTMinC: Double,
) {
    fun serialize() = "rain=$rainMmTotal;rh=$avgRhPct;tmax=$avgTMaxC;tmin=$avgTMinC"
}

data class OutbreakReport(
    val geohash5: String,
    val cropId: String,
    val diseaseId: String,
    val confirmedBy: DiagnosisSource,
    val createdAt: Instant,
)
