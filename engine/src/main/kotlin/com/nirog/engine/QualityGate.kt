package com.nirog.engine

/**
 * Pure image-quality gate run on live preview frames so bad photos are rejected
 * BEFORE capture, not after diagnosis. Input is the Y (luminance) plane of a
 * YUV_420_888 frame — unsigned bytes, row-major with optional row padding.
 */
sealed interface QualityVerdict {
    val messageKey: String

    data object Pass : QualityVerdict {
        override val messageKey = "quality_pass"
    }

    data class TooBlurry(val laplacianVariance: Double, val required: Double) : QualityVerdict {
        override val messageKey = "quality_too_blurry"
    }

    data class TooDark(val meanLuma: Double) : QualityVerdict {
        override val messageKey = "quality_too_dark"
    }

    data class TooBright(val meanLuma: Double) : QualityVerdict {
        override val messageKey = "quality_too_bright"
    }
}

/**
 * Calibration knobs. Defaults are starting points from common practice, NOT
 * field-validated — tune on pilot images from real farmer phones (cheap sensors
 * differ a lot). Provenance of each default:
 * - minLaplacianVariance 100: widely used cutoff for the 3x3 Laplacian on
 *   0-255 luma (OpenCV blur-detection folklore); scale depends on kernel, so
 *   this number only means anything with THIS kernel.
 * - darkMeanMax 50 / brightMeanMin 200: ~20%/80% of the 0-255 range.
 * - crushedShadowFrac 0.6 / blownHighlightFrac 0.4: majority-of-frame clipping
 *   means the histogram, not the mean, tells the story (backlit leaf, sky shot).
 */
data class QualityConfig(
    val minLaplacianVariance: Double = 100.0,
    val darkMeanMax: Double = 50.0,
    val brightMeanMin: Double = 200.0,
    val crushedShadowFrac: Double = 0.6,
    val blownHighlightFrac: Double = 0.4,
)

object QualityGate {

    fun evaluate(
        luma: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int = width,
        config: QualityConfig = QualityConfig(),
    ): QualityVerdict {
        require(width > 2 && height > 2) { "frame too small: ${width}x$height" }
        require(luma.size >= rowStride * height) { "luma buffer smaller than stride*height" }

        fun px(x: Int, y: Int): Int = luma[y * rowStride + x].toInt() and 0xFF

        // Exposure first: blur measured on a crushed or blown frame is meaningless.
        var sum = 0L
        var crushed = 0
        var blown = 0
        val n = width * height
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = px(x, y)
                sum += v
                if (v <= 15) crushed++
                if (v >= 240) blown++
            }
        }
        val mean = sum.toDouble() / n
        if (mean < config.darkMeanMax || crushed.toDouble() / n > config.crushedShadowFrac) {
            return QualityVerdict.TooDark(mean)
        }
        if (mean > config.brightMeanMin || blown.toDouble() / n > config.blownHighlightFrac) {
            return QualityVerdict.TooBright(mean)
        }

        // 4-neighbour Laplacian variance over interior pixels.
        var lapSum = 0.0
        var lapSqSum = 0.0
        val m = (width - 2) * (height - 2)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val lap = (4 * px(x, y) - px(x - 1, y) - px(x + 1, y) - px(x, y - 1) - px(x, y + 1)).toDouble()
                lapSum += lap
                lapSqSum += lap * lap
            }
        }
        val lapMean = lapSum / m
        val variance = lapSqSum / m - lapMean * lapMean
        if (variance < config.minLaplacianVariance) {
            return QualityVerdict.TooBlurry(variance, config.minLaplacianVariance)
        }
        return QualityVerdict.Pass
    }
}
