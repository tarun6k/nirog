package com.nirog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CsvSeedTest {

    @Test
    fun `parses a product row with empty optional cells`() {
        val csv = """
            id,tradeNames,activeIngredient,concentration,formulation,fracGroup,iracGroup,toxicityTriangle,isBiopesticide,npopPermitted,pricePerUnitInr
            P1,Alpha;Beta,testazole,10% EC,EC,11,,YELLOW,false,false,0.5
        """.trimIndent()
        val p = CsvSeed.parseProducts(csv).single()
        assertEquals("P1", p.id)
        assertEquals("Alpha;Beta", p.tradeNames)
        assertEquals("11", p.fracGroup)
        assertNull(p.iracGroup)
        assertEquals(0.5, p.pricePerUnitInr)
    }

    @Test
    fun `label claim without a gazette ref is rejected`() {
        val csv = """
            productId,cropId,pestId,doseValue,doseUnit,dilutionLPerHa,phiDays,sourceNotificationRef,effectiveFrom,effectiveTo
            P1,wheat,yellow_rust,2.0,ML_PER_L,500,7,,2025-01-01,
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> { CsvSeed.parseLabelClaims(csv) }
    }

    @Test
    fun `label claim dates parse to epoch days and empty effectiveTo is null`() {
        val csv = """
            productId,cropId,pestId,doseValue,doseUnit,dilutionLPerHa,phiDays,sourceNotificationRef,effectiveFrom,effectiveTo
            P1,wheat,yellow_rust,2.0,ML_PER_L,500,7,SO 1(E),2025-01-01,
        """.trimIndent()
        val c = CsvSeed.parseLabelClaims(csv).single()
        assertEquals(java.time.LocalDate.of(2025, 1, 1).toEpochDay(), c.effectiveFrom)
        assertNull(c.effectiveTo)
    }

    @Test
    fun `headers-only files parse to empty lists`() {
        assertEquals(emptyList(), CsvSeed.parseBannedActives("activeIngredient,notificationRef,bannedFrom,scopeCropId"))
        assertEquals(emptyList(), CsvSeed.parseEtlThresholds("cropId,pestId,metric,thresholdValue,unit,source"))
    }

    @Test
    fun `wrong column count fails loudly instead of shifting fields`() {
        val csv = """
            activeIngredient,notificationRef,bannedFrom,scopeCropId
            monocrotophos,SO 686(E),2021-01-01
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> { CsvSeed.parseBannedActives(csv) }
    }
}
