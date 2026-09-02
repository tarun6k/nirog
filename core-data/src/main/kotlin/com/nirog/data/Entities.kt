package com.nirog.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Storage conventions: LocalDate -> epochDay Long, Instant -> epochMilli Long,
// List<String> -> ';'-joined String, enums -> name String. Keeps Room free of
// TypeConverters; mapping to :core-model domain types lives in Mappers.kt.

@Entity(tableName = "farmer")
data class FarmerEntity(
    @PrimaryKey val id: String,
    val phoneHash: String,
    val preferredLanguage: String,
    val consentVersion: Int,
    val consentGrantedAt: Long,
)

@Entity(tableName = "plot")
data class PlotEntity(
    @PrimaryKey val id: String,
    val farmerId: String,
    val label: String,
    val cropId: String,
    val variety: String?,
    val areaValue: Double,
    val areaUnit: String,
    val sowingDate: Long,
    val lat: Double?,
    val lon: Double?,
    val pincode: String?,
    val plannedHarvestDate: Long,
    val organicStatus: String,
    val state: String?,
)

@Entity(tableName = "scan_session")
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val plotId: String,
    val createdAt: Long,
    val imagePaths: String,
    val qualityScores: String,
    val deviceModel: String,
    val appVersion: String,
    val syncState: String,
)

@Entity(tableName = "context_snapshot", primaryKeys = ["scanId"])
data class ContextSnapshotEntity(
    val scanId: String,
    val daysSinceSowing: Int,
    val growthStage: String?,
    val weather14d: String?,
    val districtPressureScore: Double?,
    val lastSprayFracGroup: String?,
    val lastSprayIracGroup: String?,
    val capturedAt: Long,
)

@Entity(tableName = "diagnosis")
data class DiagnosisEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    /** "pestId:probability;pestId:probability" ordered by probability desc. */
    val candidates: String,
    val calibratedConfidence: Double,
    val oodScore: Double,
    val severityPct: Double,
    val verdict: String,
    val modelVersion: String,
    val source: String,
    /** Audit trail of context-fusion prior adjustments: "pest|metric|actual|rule|mult|source;…", null = none. */
    val priorAdjustments: String?,
)

@Entity(tableName = "product")
data class ProductEntity(
    @PrimaryKey val id: String,
    val tradeNames: String,
    val activeIngredient: String,
    val concentration: String,
    val formulation: String,
    val fracGroup: String?,
    val iracGroup: String?,
    val toxicityTriangle: String,
    val isBiopesticide: Boolean,
    val npopPermitted: Boolean,
    val pricePerUnitInr: Double,
)

@Entity(tableName = "label_claim", primaryKeys = ["productId", "cropId", "pestId", "effectiveFrom"])
data class LabelClaimEntity(
    val productId: String,
    val cropId: String,
    val pestId: String,
    val doseValue: Double,
    val doseUnit: String,
    val dilutionLPerHa: Double,
    val phiDays: Int,
    val sourceNotificationRef: String,
    val effectiveFrom: Long,
    val effectiveTo: Long?,
)

@Entity(tableName = "banned_active", primaryKeys = ["activeIngredient", "notificationRef"])
data class BannedActiveEntity(
    val activeIngredient: String,
    val notificationRef: String,
    val bannedFrom: Long,
    val scopeCropId: String?,
)

@Entity(tableName = "etl_threshold", primaryKeys = ["cropId", "pestId"])
data class EtlThresholdEntity(
    val cropId: String,
    val pestId: String,
    val metric: String,
    val thresholdValue: Double,
    val unit: String,
    val source: String,
)

@Entity(tableName = "spray_log")
data class SprayLogEntity(
    @PrimaryKey val id: String,
    val plotId: String,
    val date: Long,
    val productId: String,
    val activeIngredient: String,
    val doseActual: String?,
    val tanks: Int?,
    val costInr: Double?,
    val phiExpiryDate: Long?,
    val recommendationId: String?,
    val farmerConfirmed: Boolean,
)

@Entity(tableName = "escalation_ticket")
data class EscalationTicketEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    val status: String,
    val expertAnswer: String?,
    val answeredAt: Long?,
)

@Entity(tableName = "outbreak_report", primaryKeys = ["geohash5", "cropId", "diseaseId", "createdAt"])
data class OutbreakReportEntity(
    val geohash5: String,
    val cropId: String,
    val diseaseId: String,
    val confirmedBy: String,
    val createdAt: Long,
)
