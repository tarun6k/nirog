package com.nirog.engine

import com.nirog.model.Verdict
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val THRESHOLDS = InferenceThresholds(
    temperature = 1.5,
    minTopProbConfident = 0.80,
    minMarginConfident = 0.30,
    minTopProbAmbiguous = 0.45,
    maxOodEnergy = -2.0,
    routerDisagreeProb = 0.5,
)

class CalibrationTest {

    @Test
    fun `softmax sums to one and preserves order`() {
        val p = Calibration.softmax(doubleArrayOf(2.0, 1.0, 0.5), temperature = 1.0)
        assertTrue(abs(p.sum() - 1.0) < 1e-9)
        assertTrue(p[0] > p[1] && p[1] > p[2])
    }

    @Test
    fun `higher temperature flattens the distribution`() {
        val logits = doubleArrayOf(4.0, 1.0, 0.0)
        val sharp = Calibration.softmax(logits, 1.0)
        val flat = Calibration.softmax(logits, 3.0)
        assertTrue(flat[0] < sharp[0])
        assertTrue(flat[2] > sharp[2])
    }

    @Test
    fun `energy is lower for confident in-distribution logits than for uniform ones`() {
        val confident = Calibration.energyOod(doubleArrayOf(12.0, 1.0, 0.0), 1.0)
        val uniform = Calibration.energyOod(doubleArrayOf(0.3, 0.3, 0.3), 1.0)
        assertTrue(confident < uniform)
    }

    // ── verdict mapping ────────────────────────────────────────────────────

    @Test
    fun `high top prob with margin and low ood is confident`() {
        val v = VerdictMapper.map(doubleArrayOf(0.9, 0.05, 0.05), ood = -10.0, t = THRESHOLDS)
        assertEquals(Verdict.CONFIDENT, v)
    }

    @Test
    fun `high ood energy abstains regardless of probabilities`() {
        val v = VerdictMapper.map(doubleArrayOf(0.95, 0.03, 0.02), ood = -1.0, t = THRESHOLDS)
        assertEquals(Verdict.ABSTAIN, v)
    }

    @Test
    fun `two close candidates are ambiguous`() {
        val v = VerdictMapper.map(doubleArrayOf(0.48, 0.44, 0.08), ood = -10.0, t = THRESHOLDS)
        assertEquals(Verdict.AMBIGUOUS, v)
    }

    @Test
    fun `weak top probability abstains`() {
        val v = VerdictMapper.map(doubleArrayOf(0.3, 0.3, 0.4), ood = -10.0, t = THRESHOLDS)
        assertEquals(Verdict.ABSTAIN, v)
    }

    @Test
    fun `boundary values count as confident`() {
        val v = VerdictMapper.map(doubleArrayOf(0.80, 0.50), ood = THRESHOLDS.maxOodEnergy, t = THRESHOLDS)
        // margin 0.30 exactly, top 0.80 exactly, ood exactly at limit
        assertEquals(Verdict.CONFIDENT, v)
    }

    // ── config parsing ─────────────────────────────────────────────────────

    @Test
    fun `thresholds parse from properties text`() {
        val t = InferenceThresholds.fromProperties(
            """
            # comment
            temperature=1.5
            minTopProbConfident=0.8
            minMarginConfident=0.3
            minTopProbAmbiguous=0.45
            maxOodEnergy=-2.0
            routerDisagreeProb=0.5
            """.trimIndent(),
        )
        assertEquals(1.5, t.temperature)
        assertEquals(-2.0, t.maxOodEnergy)
    }

    @Test
    fun `missing threshold key fails loudly`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceThresholds.fromProperties("temperature=1.5")
        }
    }
}
