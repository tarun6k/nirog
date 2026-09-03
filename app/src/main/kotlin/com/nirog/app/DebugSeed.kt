package com.nirog.app

import com.nirog.data.BannedActiveEntity
import com.nirog.data.EtlThresholdEntity
import com.nirog.data.LabelClaimEntity
import com.nirog.data.NirogDb
import com.nirog.data.ProductEntity
import java.time.LocalDate

/**
 * DEBUG BUILDS ONLY. Synthetic catalog rows so the treatment ladder, dose
 * calculator and rejection list can be exercised before real gazette data is
 * entered. Every id, name and ref screams TEST — none of this is agronomy, and
 * none of it ships in release (guarded by BuildConfig.DEBUG at the call site).
 *
 * With the stub diagnosis (wheat / yellow_rust / 12% severity) this yields:
 *  - TEST-NEEM      biological rung, unlocked, cheapest
 *  - TEST-CHEM-A    chemical rung, unlocked (12% >= 10% ETL), dose flow works
 *  - TEST-BANNED    in the "not shown" list — banned active
 *  - TEST-PHI-LONG  in the "not shown" list — 200-day PHI can't fit the season
 *  - after saving TEST-CHEM-A to the diary, the next scan rejects it (FRAC rotation)
 */
suspend fun seedDebugCatalog(db: NirogDb) {
    if (db.catalogDao().products().isNotEmpty()) return

    val from = LocalDate.now().minusYears(1).toEpochDay()
    fun claim(productId: String, dose: Double, phi: Int) = LabelClaimEntity(
        productId = productId, cropId = "wheat", pestId = "yellow_rust",
        doseValue = dose, doseUnit = "ML_PER_L", dilutionLPerHa = 500.0, phiDays = phi,
        sourceNotificationRef = "TEST/DEBUG/0", effectiveFrom = from, effectiveTo = null,
    )

    db.catalogDao().insertProducts(
        listOf(
            ProductEntity(
                id = "TEST-NEEM", tradeNames = "TEST नीम स्प्रे", activeIngredient = "test-neem-oil",
                concentration = "1500 ppm", formulation = "EC", fracGroup = null, iracGroup = null,
                toxicityTriangle = "GREEN", isBiopesticide = true, npopPermitted = true,
                pricePerUnitInr = 0.1,
            ),
            ProductEntity(
                id = "TEST-CHEM-A", tradeNames = "TEST रसायन A", activeIngredient = "test-azole-a",
                concentration = "25% EC", formulation = "EC", fracGroup = "T1", iracGroup = null,
                toxicityTriangle = "YELLOW", isBiopesticide = false, npopPermitted = false,
                pricePerUnitInr = 1.0,
            ),
            ProductEntity(
                id = "TEST-BANNED", tradeNames = "TEST प्रतिबंधित", activeIngredient = "test-banned-active",
                concentration = "40% EC", formulation = "EC", fracGroup = "T3", iracGroup = null,
                toxicityTriangle = "RED", isBiopesticide = false, npopPermitted = false,
                pricePerUnitInr = 0.8,
            ),
            ProductEntity(
                id = "TEST-PHI-LONG", tradeNames = "TEST लंबा PHI", activeIngredient = "test-slow-active",
                concentration = "10% SC", formulation = "SC", fracGroup = "T4", iracGroup = null,
                toxicityTriangle = "BLUE", isBiopesticide = false, npopPermitted = false,
                pricePerUnitInr = 0.6,
            ),
        ),
    )
    db.catalogDao().insertLabelClaims(
        listOf(
            claim("TEST-NEEM", dose = 5.0, phi = 3),
            claim("TEST-CHEM-A", dose = 1.0, phi = 21),
            claim("TEST-BANNED", dose = 1.0, phi = 14),
            claim("TEST-PHI-LONG", dose = 1.0, phi = 200),
        ),
    )
    db.catalogDao().insertBannedActives(
        listOf(
            BannedActiveEntity(
                activeIngredient = "test-banned-active", notificationRef = "TEST/DEBUG/BAN",
                bannedFrom = from, scopeCropId = null,
            ),
        ),
    )
    db.catalogDao().insertEtlThresholds(
        listOf(
            EtlThresholdEntity(
                cropId = "wheat", pestId = "yellow_rust", metric = "severityPct",
                thresholdValue = 10.0, unit = "%", source = "TEST/DEBUG",
            ),
        ),
    )
}
