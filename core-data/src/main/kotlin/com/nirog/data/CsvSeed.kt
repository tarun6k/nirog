package com.nirog.data

// Parses the seed CSVs in assets/seed/ into entities. Format is our own:
// comma-separated, no quoting, ';' separates values inside a list field,
// empty cell = null, dates are ISO (yyyy-MM-dd) converted to epochDay.
// Pure string functions so they unit-test on the JVM without Android.

import java.time.LocalDate

object CsvSeed {

    private fun rows(csv: String): List<List<String>> =
        csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .drop(1) // header
            .map { line -> line.split(',').map { it.trim() } }
            .toList()

    private fun String.orNull(): String? = ifEmpty { null }
    private fun String.epochDay(): Long = LocalDate.parse(this).toEpochDay()

    fun parseProducts(csv: String): List<ProductEntity> = rows(csv).map { c ->
        require(c.size == 11) { "products.csv expects 11 columns, got ${c.size}: $c" }
        ProductEntity(
            id = c[0], tradeNames = c[1], activeIngredient = c[2], concentration = c[3],
            formulation = c[4], fracGroup = c[5].orNull(), iracGroup = c[6].orNull(),
            toxicityTriangle = c[7], isBiopesticide = c[8].toBooleanStrict(),
            npopPermitted = c[9].toBooleanStrict(), pricePerUnitInr = c[10].toDouble(),
        )
    }

    fun parseLabelClaims(csv: String): List<LabelClaimEntity> = rows(csv).map { c ->
        require(c.size == 10) { "label_claims.csv expects 10 columns, got ${c.size}: $c" }
        require(c[7].isNotEmpty()) { "label claim without a gazette notification ref: $c" }
        LabelClaimEntity(
            productId = c[0], cropId = c[1], pestId = c[2], doseValue = c[3].toDouble(),
            doseUnit = c[4], dilutionLPerHa = c[5].toDouble(), phiDays = c[6].toInt(),
            sourceNotificationRef = c[7], effectiveFrom = c[8].epochDay(),
            effectiveTo = c[9].orNull()?.epochDay(),
        )
    }

    fun parseBannedActives(csv: String): List<BannedActiveEntity> = rows(csv).map { c ->
        require(c.size == 4) { "banned_actives.csv expects 4 columns, got ${c.size}: $c" }
        BannedActiveEntity(
            activeIngredient = c[0], notificationRef = c[1],
            bannedFrom = c[2].epochDay(), scopeCropId = c[3].orNull(),
        )
    }

    fun parseEtlThresholds(csv: String): List<EtlThresholdEntity> = rows(csv).map { c ->
        require(c.size == 6) { "etl_thresholds.csv expects 6 columns, got ${c.size}: $c" }
        EtlThresholdEntity(
            cropId = c[0], pestId = c[1], metric = c[2],
            thresholdValue = c[3].toDouble(), unit = c[4], source = c[5],
        )
    }
}
