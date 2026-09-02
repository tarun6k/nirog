package com.nirog.engine

import kotlin.test.Test
import kotlin.test.assertIs

// Synthetic 64x64 luma planes. Values are unsigned bytes 0..255.
private const val W = 64
private const val H = 64

private fun flat(value: Int) = ByteArray(W * H) { value.toByte() }

/** High-frequency detail with no clipped pixels: 80/180 checkerboard. */
private fun checkerboard() = ByteArray(W * H) { i ->
    val x = i % W
    val y = i / W
    (if ((x + y) % 2 == 0) 80 else 180).toByte()
}

class QualityGateTest {

    @Test
    fun `sharp well-exposed image passes`() {
        assertIs<QualityVerdict.Pass>(QualityGate.evaluate(checkerboard(), W, H))
    }

    @Test
    fun `flat image is too blurry`() {
        val v = assertIs<QualityVerdict.TooBlurry>(QualityGate.evaluate(flat(128), W, H))
        kotlin.test.assertTrue(v.laplacianVariance < v.required)
    }

    @Test
    fun `dark image is rejected as too dark not blurry`() {
        // flat AND dark: exposure verdict must win, blur on a dark frame is meaningless
        assertIs<QualityVerdict.TooDark>(QualityGate.evaluate(flat(20), W, H))
    }

    @Test
    fun `bright image is rejected as too bright`() {
        assertIs<QualityVerdict.TooBright>(QualityGate.evaluate(flat(245), W, H))
    }

    @Test
    fun `mostly clipped highlights reject even with mid mean`() {
        // half the frame blown out at 255, half at 100: mean ~177 but unusable
        val luma = ByteArray(W * H) { i -> (if (i < W * H / 2) 255 else 100).toByte() }
        assertIs<QualityVerdict.TooBright>(QualityGate.evaluate(luma, W, H))
    }

    @Test
    fun `mostly crushed shadows reject even with mid mean`() {
        val luma = ByteArray(W * H) { i -> (if (i < W * H * 2 / 3) 5 else 220).toByte() }
        assertIs<QualityVerdict.TooDark>(QualityGate.evaluate(luma, W, H))
    }

    @Test
    fun `row stride padding is ignored`() {
        // same checkerboard but each row carries 16 bytes of zero padding;
        // if padding leaked into the histogram the dark fraction would spike
        val stride = W + 16
        val padded = ByteArray(stride * H)
        val src = checkerboard()
        for (y in 0 until H) src.copyInto(padded, y * stride, y * W, (y + 1) * W)
        assertIs<QualityVerdict.Pass>(QualityGate.evaluate(padded, W, H, rowStride = stride))
    }

    @Test
    fun `every verdict carries a farmer-readable message key`() {
        val verdicts = listOf(
            QualityGate.evaluate(checkerboard(), W, H),
            QualityGate.evaluate(flat(128), W, H),
            QualityGate.evaluate(flat(20), W, H),
            QualityGate.evaluate(flat(245), W, H),
        )
        kotlin.test.assertTrue(verdicts.all { it.messageKey.startsWith("quality_") })
    }
}
