package com.nirog.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.java.TfLite
import com.nirog.engine.Calibration
import com.nirog.engine.InferenceThresholds
import com.nirog.engine.VerdictMapper
import com.nirog.model.DiseaseCandidate
import com.nirog.model.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Two-stage LiteRT pipeline via Play Services runtime: crop router verifies the
 * declared crop, then the crop-conditional disease head classifies. GPU delegate
 * when available, XNNPACK CPU otherwise (never NNAPI — deprecated in Android 15).
 * Models come from [ModelStore]: OTA-updated files beat bundled assets.
 */
class LiteRtInferenceEngine(
    private val context: Context,
    private val store: ModelStore = ModelStore(context),
) : InferenceEngine {

    private val thresholds: InferenceThresholds by lazy {
        InferenceThresholds.fromProperties(store.inferenceConfigText())
    }
    private val inputSize: Int by lazy {
        store.inferenceConfigText().lineSequence()
            .firstOrNull { it.trim().startsWith("inputSize=") }
            ?.substringAfter('=')?.trim()?.toIntOrNull()
            ?: error("inference config missing inputSize")
    }

    private var initialized = false

    private suspend fun ensureInitialized() {
        if (initialized) return
        // GPU-enabled init first; plain init (XNNPACK CPU) as fallback.
        val gpuOk = runCatching {
            initTask(TfLiteInitializationOptions.builder().setEnableGpuDelegateSupport(true).build())
        }.isSuccess
        if (!gpuOk) initTask(null)
        gpuAvailable = gpuOk
        initialized = true
    }

    private var gpuAvailable = false

    private suspend fun initTask(options: TfLiteInitializationOptions?): Unit =
        suspendCancellableCoroutine { cont ->
            val task = if (options != null) TfLite.initialize(context, options) else TfLite.initialize(context)
            task.addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun interpreter(model: File): InterpreterApi {
        val opts = InterpreterApi.Options().setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
        if (gpuAvailable) opts.addDelegateFactory(GpuDelegateFactory())
        return InterpreterApi.create(model, opts)
    }

    override suspend fun diagnose(images: List<File>, cropId: String): InferenceOutput =
        withContext(Dispatchers.Default) {
            ensureInitialized()

            // Stage 1: crop router on the whole-plant shot.
            val routerModel = store.resolve("crop_router.tflite")
            val routerLabels = store.labels("crop_router.labels")
            val routerLogits = interpreter(routerModel).use { run(it, images.first(), routerLabels.size) }
            val routerProbs = Calibration.softmax(routerLogits, thresholds.temperature)
            val routedIdx = routerProbs.indices.maxBy { routerProbs[it] }
            if (routerLabels[routedIdx] != cropId && routerProbs[routedIdx] >= thresholds.routerDisagreeProb) {
                return@withContext InferenceOutput(
                    candidates = emptyList(),
                    calibratedConfidence = 0.0,
                    oodScore = Calibration.energyOod(routerLogits, thresholds.temperature),
                    severityPct = 0.0,
                    verdict = Verdict.ABSTAIN,
                    modelVersion = version(routerModel),
                )
            }

            // Stage 2: crop-conditional disease head, logits averaged over all shots.
            val headModel = store.resolve("disease_$cropId.tflite")
            val headLabels = store.labels("disease_$cropId.labels")
            val logits = interpreter(headModel).use { itp ->
                images
                    .map { img -> run(itp, img, headLabels.size) }
                    .reduce { acc, l -> DoubleArray(acc.size) { acc[it] + l[it] } }
                    .map { it / images.size }.toDoubleArray()
            }
            val probs = Calibration.softmax(logits, thresholds.temperature)
            val ood = Calibration.energyOod(logits, thresholds.temperature)
            val candidates = probs.indices
                .sortedByDescending { probs[it] }
                .take(3)
                .map { DiseaseCandidate(headLabels[it], probs[it]) }
            InferenceOutput(
                candidates = candidates,
                calibratedConfidence = candidates.first().probability,
                oodScore = ood,
                // No severity model yet: 0% keeps the chemical rung ETL-locked (I7),
                // which is the conservative failure mode. Severity head is planned work.
                severityPct = 0.0,
                verdict = VerdictMapper.map(probs, ood, thresholds),
                modelVersion = version(headModel),
            )
        }

    private fun run(interpreter: InterpreterApi, image: File, numClasses: Int): DoubleArray {
        val input = preprocess(image)
        val output = Array(1) { FloatArray(numClasses) }
        interpreter.run(input, output)
        return DoubleArray(numClasses) { output[0][it].toDouble() }
    }

    /** Decode → center-square → inputSize² RGB float32 in [0,1]. Must match training. */
    private fun preprocess(image: File): ByteBuffer {
        val raw = BitmapFactory.decodeFile(image.absolutePath)
            ?: error("cannot decode ${image.absolutePath}")
        val side = minOf(raw.width, raw.height)
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            buffer.putFloat(((p shr 16) and 0xFF) / 255f)
            buffer.putFloat(((p shr 8) and 0xFF) / 255f)
            buffer.putFloat((p and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    private fun version(model: File) = "${model.name}@${model.length()}"

    private inline fun <T> InterpreterApi.use(block: (InterpreterApi) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
