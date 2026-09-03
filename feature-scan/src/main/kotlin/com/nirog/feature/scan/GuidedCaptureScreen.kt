package com.nirog.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nirog.engine.QualityGate
import com.nirog.engine.QualityVerdict
import com.nirog.ui.MicButton
import com.nirog.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Three-shot guided flow: whole plant → leaf top → leaf underside (artboard 02). */
enum class CaptureStep(val hi: String, val en: String) {
    WHOLE_PLANT("पूरा पौधा दूर से", "WHOLE PLANT"),
    LEAF_TOP("पत्ती ऊपर से", "LEAF TOP"),
    LEAF_UNDERSIDE("अब पत्ती को पलटिए", "TURN THE LEAF OVER"),
}

private fun verdictText(v: QualityVerdict): String = when (v) {
    is QualityVerdict.Pass -> "फ़ोटो साफ़ है — खींचें"
    is QualityVerdict.TooBlurry -> "फ़ोन स्थिर रखें — धुंधला है"
    is QualityVerdict.TooDark -> "रोशनी कम है — धूप में जाएं"
    is QualityVerdict.TooBright -> "बहुत तेज़ रोशनी — छाया करें"
}

@Composable
fun GuidedCaptureScreen(
    plotId: String,
    store: ScanStore,
    onComplete: (sessionId: String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Palette.Paper), contentAlignment = Alignment.Center) {
            Text("कैमरे की अनुमति चाहिए · Camera permission needed", fontSize = 16.sp, color = Palette.Ink)
        }
        return
    }

    val sessionId = remember { store.newSessionId() }
    var stepIndex by remember { mutableStateOf(0) }
    var verdict by remember { mutableStateOf<QualityVerdict>(QualityVerdict.TooBlurry(0.0, 0.0)) }
    var capturing by remember { mutableStateOf(false) }
    var retakeVerdict by remember { mutableStateOf<QualityVerdict?>(null) }
    val captured = remember { mutableListOf<File>() }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { a ->
                a.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                    verdict = proxy.use { evaluateFrame(it) }
                }
            }
    }

    val step = CaptureStep.entries[stepIndex]

    fun advance(file: File) {
        captured += file
        if (stepIndex == CaptureStep.entries.lastIndex) {
            scope.launch {
                withContext(Dispatchers.IO) { store.completeSession(sessionId, plotId, captured) }
                onComplete(sessionId)
            }
        } else {
            stepIndex++
        }
    }

    fun capture() {
        capturing = true
        scope.launch {
            val file = store.imageFile(sessionId, step)
            try {
                takePicture(imageCapture, file)
                // Full-res shot re-checked: preview passing doesn't guarantee the capture did.
                val captureVerdict = withContext(Dispatchers.Default) { evaluateJpeg(file) }
                if (captureVerdict is QualityVerdict.Pass) advance(file) else retakeVerdict = captureVerdict
            } finally {
                capturing = false
            }
        }
    }

    retakeVerdict?.let { rv ->
        RetakeScreen(
            verdict = rv,
            othersOk = captured.size,
            onRetake = { retakeVerdict = null },
            onSendAnyway = {
                retakeVerdict = null
                advance(store.imageFile(sessionId, step))
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Palette.CameraDark)) {
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build()
                                .also { it.surfaceProvider = previewView.surfaceProvider }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                preview, analysis, imageCapture,
                            )
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
            )
            // Instruction bar: one instruction at a time, spoken-size type (artboard 02)
            Row(
                Modifier.fillMaxWidth().background(Palette.CameraDark.copy(alpha = 0.88f)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(step.hi, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(
                        step.en,
                        fontSize = 15.sp, letterSpacing = 1.5.sp,
                        color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "चरण ${stepIndex + 1}/3",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.border(1.5.dp, Color.White.copy(alpha = 0.8f)).padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            // Live gate message pinned above the shutter area
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                Text(
                    verdictText(verdict),
                    fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Palette.CameraDark.copy(alpha = 0.8f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        // Step checklist strip
        Column(
            Modifier.fillMaxWidth().background(Palette.Paper).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CaptureStep.entries.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .background(if (i < stepIndex) Palette.Green else Palette.Card)
                            .border(
                                if (i == stepIndex) 2.5.dp else 1.5.dp,
                                if (i <= stepIndex) Palette.Green else Palette.Disabled,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i < stepIndex) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        else Text("${i + 1}", color = if (i == stepIndex) Palette.Green else Palette.Stone, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text(
                        s.hi,
                        fontSize = if (i == stepIndex) 17.sp else 16.sp,
                        fontWeight = if (i == stepIndex) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (i < stepIndex) Palette.TextSecondary else if (i == stepIndex) Palette.Ink else Palette.Stone,
                        textDecoration = if (i < stepIndex) TextDecoration.LineThrough else null,
                    )
                    if (i == stepIndex) {
                        Text(
                            "अभी",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.background(Palette.Green).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        // Shutter row: oversized shutter, mic beside it
        Row(
            Modifier.fillMaxWidth().background(Palette.Paper).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(72.dp, 56.dp))
            Spacer(Modifier.weight(1f))
            val enabled = verdict is QualityVerdict.Pass && !capturing
            Box(
                Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(5.dp, if (enabled) Palette.Green else Palette.Disabled, CircleShape)
                    .clickable(enabled = enabled) { capture() },
            )
            Spacer(Modifier.weight(1f))
            MicButton()
        }
    }
}

/** Artboard 03: helpful, not punishing — names the problem, explains the fix, reassures. */
@Composable
fun RetakeScreen(
    verdict: QualityVerdict,
    othersOk: Int,
    onRetake: () -> Unit,
    onSendAnyway: () -> Unit,
) {
    val (title, en, fix) = when (verdict) {
        is QualityVerdict.TooBlurry -> Triple(
            "फोटो धुंधली है", "TOO BLURRY — HOLD STEADY",
            "कोई बात नहीं। फोन को दोनों हाथों से पकड़ें, पत्ती से एक बालिश्त दूर, और सांस रोक कर फोटो लें।",
        )
        is QualityVerdict.TooDark -> Triple(
            "बहुत अंधेरा है", "TOO DARK — MOVE INTO THE LIGHT",
            "कोई बात नहीं — छांव में ऐसा अक्सर होता है। पत्ती को छाया से निकालकर खुली रोशनी में रखें।",
        )
        is QualityVerdict.TooBright -> Triple(
            "बहुत तेज़ रोशनी", "TOO BRIGHT — MAKE SOME SHADE",
            "धूप सीधी पड़ रही है। अपने शरीर की छाया से पत्ती को ढकें और दोबारा लें।",
        )
        is QualityVerdict.Pass -> Triple("", "", "")
    }
    Column(Modifier.fillMaxSize().background(Palette.Paper)) {
        Box(Modifier.fillMaxWidth().height(240.dp).background(Color(0xFF2A2820)), contentAlignment = Alignment.Center) {
            Text("आपकी ली हुई फोटो", fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).padding(20.dp)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
            Text(en, fontSize = 15.sp, letterSpacing = 1.5.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold)
            Text(fix, fontSize = 18.sp, lineHeight = 30.sp, color = Palette.Ink, modifier = Modifier.padding(top = 14.dp))
            if (othersOk > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp).background(Palette.Card).border(1.dp, Palette.Hairline).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🕐", fontSize = 18.sp)
                    Text(
                        "पहली $othersOk फोटो अच्छी हैं — बस यही एक फिर से",
                        fontSize = 16.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.nirog.ui.PrimaryButton("फिर से फोटो लें", Modifier.fillMaxWidth().height(76.dp), en = "RETAKE", onClick = onRetake)
            com.nirog.ui.SecondaryButton("ऐसे ही भेज दें", Modifier.fillMaxWidth(), onClick = onSendAnyway)
        }
    }
}

private fun evaluateFrame(proxy: ImageProxy): QualityVerdict {
    val plane = proxy.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return QualityGate.evaluate(bytes, proxy.width, proxy.height, plane.rowStride)
}

/** Decode a captured JPEG at reduced size and run the same pure gate on its luma. */
private fun evaluateJpeg(file: File): QualityVerdict {
    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return QualityVerdict.TooDark(0.0)
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    val luma = ByteArray(w * h) { i ->
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        ((r * 299 + g * 587 + b * 114) / 1000).toByte()
    }
    return QualityGate.evaluate(luma, w, h)
}

private suspend fun takePicture(imageCapture: ImageCapture, file: File): File =
    suspendCancellableCoroutine { cont ->
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            Runnable::run,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) = cont.resume(file)
                override fun onError(e: ImageCaptureException) = cont.resumeWithException(e)
            },
        )
    }
