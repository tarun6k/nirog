package com.nirog.engine

import com.nirog.model.*
import java.time.Instant
import java.time.LocalDate

// All values here are synthetic test fixtures, never shipped data.
val TODAY: LocalDate = LocalDate.of(2026, 3, 1)

fun product(
    id: String = "P1",
    active: String = "testazole",
    frac: String? = null,
    irac: String? = null,
    bio: Boolean = false,
    npop: Boolean = false,
    priceInr: Double = 1.0,
) = Product(
    id = id, tradeNames = listOf("Test $id"), activeIngredient = active,
    concentration = "10% EC", formulation = "EC", fracGroup = frac, iracGroup = irac,
    toxicityTriangle = ToxicityTriangle.YELLOW, isBiopesticide = bio,
    npopPermitted = npop, pricePerUnitInr = priceInr,
)

fun claim(
    productId: String = "P1",
    cropId: String = "wheat",
    pestId: String = "yellow_rust",
    dose: Double = 2.0,
    doseUnit: DoseUnit = DoseUnit.ML_PER_L,
    dilution: Double = 500.0,
    phi: Int = 7,
    from: LocalDate = TODAY.minusYears(1),
    to: LocalDate? = null,
) = LabelClaim(
    productId = productId, cropId = cropId, pestId = pestId, doseValue = dose,
    doseUnit = doseUnit, dilutionLPerHa = dilution, phiDays = phi,
    sourceNotificationRef = "TEST/GAZETTE/1", effectiveFrom = from, effectiveTo = to,
)

fun plot(
    cropId: String = "wheat",
    organic: OrganicStatus = OrganicStatus.NONE,
    areaValue: Double = 1.0,
    areaUnit: AreaUnit = AreaUnit.ACRE,
    state: String? = "UTTAR_PRADESH",
    harvest: LocalDate = TODAY.plusDays(60),
) = Plot(
    id = "plot1", farmerId = "f1", label = "Back field", cropId = cropId, variety = null,
    areaValue = areaValue, areaUnit = areaUnit, sowingDate = TODAY.minusDays(90),
    lat = null, lon = null, pincode = "226001", plannedHarvestDate = harvest,
    organicStatus = organic, state = state,
)

fun diagnosis(
    verdict: Verdict = Verdict.CONFIDENT,
    pestId: String = "yellow_rust",
    severity: Double = 50.0,
) = Diagnosis(
    id = "d1", scanId = "scan1",
    candidates = listOf(DiseaseCandidate(pestId, 0.92)),
    calibratedConfidence = 0.92, oodScore = 0.1, severityPct = severity,
    verdict = verdict, modelVersion = "test", source = DiagnosisSource.ON_DEVICE,
)

fun sprayLog(productId: String, daysAgo: Long) = SprayLog(
    id = "s-$productId-$daysAgo", plotId = "plot1", date = TODAY.minusDays(daysAgo),
    productId = productId, activeIngredient = "past-active", doseActual = null,
    tanks = null, costInr = null, phiExpiryDate = null, recommendationId = null,
    farmerConfirmed = true,
)

fun context() = ContextSnapshot(
    scanId = "scan1", daysSinceSowing = 90, growthStage = null, weather14d = null,
    districtPressureScore = null, lastSprayFracGroup = null, lastSprayIracGroup = null,
    capturedAt = Instant.parse("2026-03-01T09:00:00Z"),
)

fun catalog(
    products: List<Product>,
    claims: List<LabelClaim> = emptyList(),
    banned: List<BannedActive> = emptyList(),
    etl: List<EtlThreshold> = listOf(
        EtlThreshold("wheat", "yellow_rust", "severityPct", 10.0, "%", "TEST/SOURCE"),
    ),
) = Catalog(products, claims, banned, etl)

fun recommend(
    diagnosis: Diagnosis = diagnosis(),
    plot: Plot = plot(),
    history: List<SprayLog> = emptyList(),
    catalog: Catalog,
) = RecommendationEngine.recommend(diagnosis, plot, context(), history, catalog, TODAY)
