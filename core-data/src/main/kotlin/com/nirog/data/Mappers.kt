package com.nirog.data

import com.nirog.model.AreaUnit
import com.nirog.model.BannedActive
import com.nirog.model.Diagnosis
import com.nirog.model.DiagnosisSource
import com.nirog.model.DiseaseCandidate
import com.nirog.model.DoseUnit
import com.nirog.model.OrganicStatus
import com.nirog.model.Plot
import com.nirog.model.Verdict
import com.nirog.model.EtlThreshold
import com.nirog.model.LabelClaim
import com.nirog.model.Product
import com.nirog.model.SprayLog
import com.nirog.model.ToxicityTriangle
import java.time.LocalDate

// Entity -> domain, for feeding the engine's Catalog. Domain -> entity mappers
// get added when write paths need them.

fun ProductEntity.toDomain() = Product(
    id = id,
    tradeNames = tradeNames.split(';').filter { it.isNotEmpty() },
    activeIngredient = activeIngredient,
    concentration = concentration,
    formulation = formulation,
    fracGroup = fracGroup,
    iracGroup = iracGroup,
    toxicityTriangle = ToxicityTriangle.valueOf(toxicityTriangle),
    isBiopesticide = isBiopesticide,
    npopPermitted = npopPermitted,
    pricePerUnitInr = pricePerUnitInr,
)

fun LabelClaimEntity.toDomain() = LabelClaim(
    productId = productId,
    cropId = cropId,
    pestId = pestId,
    doseValue = doseValue,
    doseUnit = DoseUnit.valueOf(doseUnit),
    dilutionLPerHa = dilutionLPerHa,
    phiDays = phiDays,
    sourceNotificationRef = sourceNotificationRef,
    effectiveFrom = LocalDate.ofEpochDay(effectiveFrom),
    effectiveTo = effectiveTo?.let { LocalDate.ofEpochDay(it) },
)

fun BannedActiveEntity.toDomain() = BannedActive(
    activeIngredient = activeIngredient,
    notificationRef = notificationRef,
    bannedFrom = LocalDate.ofEpochDay(bannedFrom),
    scopeCropId = scopeCropId,
)

fun EtlThresholdEntity.toDomain() = EtlThreshold(
    cropId = cropId, pestId = pestId, metric = metric,
    thresholdValue = thresholdValue, unit = unit, source = source,
)

fun PlotEntity.toDomain() = Plot(
    id = id, farmerId = farmerId, label = label, cropId = cropId, variety = variety,
    areaValue = areaValue, areaUnit = AreaUnit.valueOf(areaUnit),
    sowingDate = LocalDate.ofEpochDay(sowingDate), lat = lat, lon = lon, pincode = pincode,
    plannedHarvestDate = LocalDate.ofEpochDay(plannedHarvestDate),
    organicStatus = OrganicStatus.valueOf(organicStatus), state = state,
)

fun DiagnosisEntity.toDomain() = Diagnosis(
    id = id, scanId = scanId,
    candidates = candidates.split(';').filter { it.isNotEmpty() }.map {
        val (pest, prob) = it.split(':', limit = 2)
        DiseaseCandidate(pest, prob.toDouble())
    },
    calibratedConfidence = calibratedConfidence, oodScore = oodScore, severityPct = severityPct,
    verdict = Verdict.valueOf(verdict), modelVersion = modelVersion,
    source = DiagnosisSource.valueOf(source),
)

fun SprayLogEntity.toDomain() = SprayLog(
    id = id, plotId = plotId, date = LocalDate.ofEpochDay(date), productId = productId,
    activeIngredient = activeIngredient, doseActual = doseActual, tanks = tanks,
    costInr = costInr, phiExpiryDate = phiExpiryDate?.let { LocalDate.ofEpochDay(it) },
    recommendationId = recommendationId, farmerConfirmed = farmerConfirmed,
)
