package com.nirog.engine

import com.nirog.model.AreaUnit
import com.nirog.model.DoseUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.math.abs

private fun assertNear(expected: Double, actual: Double, eps: Double = 1e-6) {
    kotlin.test.assertTrue(abs(expected - actual) < eps, "expected $expected, got $actual")
}

class DoseTest {

    // ── Area conversions ───────────────────────────────────────────────────

    @Test
    fun `one acre is 0_4047 hectares`() {
        val r = assertIs<AreaResult.Hectares>(AreaConverter.toHectares(1.0, AreaUnit.ACRE, null))
        assertNear(0.4047, r.value)
    }

    @Test
    fun `one guntha is a fortieth of an acre`() {
        val r = assertIs<AreaResult.Hectares>(AreaConverter.toHectares(40.0, AreaUnit.GUNTHA, null))
        assertNear(0.4047, r.value)
    }

    @Test
    fun `hectares pass through`() {
        val r = assertIs<AreaResult.Hectares>(AreaConverter.toHectares(2.5, AreaUnit.HECTARE, null))
        assertNear(2.5, r.value)
    }

    @Test
    fun `bigha uses the state lookup`() {
        val up = assertIs<AreaResult.Hectares>(AreaConverter.toHectares(1.0, AreaUnit.BIGHA, "UTTAR_PRADESH"))
        val wb = assertIs<AreaResult.Hectares>(AreaConverter.toHectares(1.0, AreaUnit.BIGHA, "WEST_BENGAL"))
        assertNear(0.2529, up.value)
        assertNear(0.1338, wb.value)
        kotlin.test.assertTrue(up.value != wb.value, "bigha must vary by state")
    }

    @Test
    fun `bigha with unknown or missing state is an explicit error never a default`() {
        assertIs<AreaResult.UnknownBighaState>(AreaConverter.toHectares(1.0, AreaUnit.BIGHA, "NARNIA"))
        assertIs<AreaResult.UnknownBighaState>(AreaConverter.toHectares(1.0, AreaUnit.BIGHA, null))
    }

    // ── I10: per-tank dose plan ────────────────────────────────────────────
    // Fixture claim: 2 ml/L, 500 L/ha dilution, 7-day PHI, on a 1-acre plot.
    // 1 acre = 0.4047 ha -> spray volume 202.35 L -> 14 tanks of 15 L.

    @Test
    fun `I10 ml-per-litre dose on one acre with 15L knapsack`() {
        val plan = assertIs<DoseResult.Plan>(
            DoseCalculator.plan(claim(), product(priceInr = 0.5), plot(), TODAY),
        )
        assertNear(202.35, plan.totalSprayVolumeL, 1e-9)
        assertEquals(14, plan.tanksRequired)
        assertNear(30.0, plan.productPerTank)          // 2 ml/L * 15 L tank
        assertEquals("ml", plan.productUnit)
        assertNear(404.7, plan.totalProduct, 1e-9)     // 2 ml/L * 202.35 L
        assertNear(202.35, plan.totalCostInr, 1e-9)    // 404.7 * 0.5 ₹/ml
        assertEquals(TODAY.plusDays(7), plan.phiExpiry)
    }

    @Test
    fun `I10 per-hectare dose splits total across tanks`() {
        val c = claim(dose = 1000.0, doseUnit = DoseUnit.ML_PER_HA, dilution = 300.0)
        // 1 ha plot: 300 L -> 20 tanks, total 1000 ml -> 50 ml/tank
        val plan = assertIs<DoseResult.Plan>(
            DoseCalculator.plan(c, product(), plot(areaValue = 1.0, areaUnit = AreaUnit.HECTARE), TODAY),
        )
        assertEquals(20, plan.tanksRequired)
        assertNear(1000.0, plan.totalProduct)
        assertNear(50.0, plan.productPerTank)
    }

    @Test
    fun `I10 gram doses report grams`() {
        val c = claim(dose = 3.0, doseUnit = DoseUnit.G_PER_L)
        val plan = assertIs<DoseResult.Plan>(DoseCalculator.plan(c, product(), plot(), TODAY))
        assertEquals("g", plan.productUnit)
        assertNear(45.0, plan.productPerTank) // 3 g/L * 15 L
    }

    @Test
    fun `I10 tank count rounds up and never drops to zero`() {
        // tiny plot: 1 guntha -> 500 * 0.0101 ha = ~5.06 L -> still 1 tank
        val plan = assertIs<DoseResult.Plan>(
            DoseCalculator.plan(claim(), product(), plot(areaValue = 1.0, areaUnit = AreaUnit.GUNTHA), TODAY),
        )
        assertEquals(1, plan.tanksRequired)
    }

    @Test
    fun `I10 custom sprayer capacity changes the per-tank dose`() {
        val plan = assertIs<DoseResult.Plan>(
            DoseCalculator.plan(claim(), product(), plot(), TODAY, tankCapacityL = 20.0),
        )
        assertNear(40.0, plan.productPerTank) // 2 ml/L * 20 L
        assertEquals(11, plan.tanksRequired)  // ceil(202.35 / 20)
    }

    @Test
    fun `I10 bigha plot in unknown state cannot compute and says why`() {
        val r = DoseCalculator.plan(
            claim(), product(),
            plot(areaValue = 2.0, areaUnit = AreaUnit.BIGHA, state = null), TODAY,
        )
        val err = assertIs<DoseResult.CannotCompute>(r)
        assertEquals("dose_unknown_bigha_state", err.messageKey)
    }
}
