package com.nirog.feature.scan

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nirog.engine.QualityGate
import com.nirog.engine.QualityVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Three-shot guided flow: whole plant → leaf top → leaf underside (design artboard 02). */
enum class CaptureStep(val hi: String, val en: String) {
    WHOLE_PLANT("पूरा पौधा", "Whole plant"),
    LEAF_TOP("पत्ती ऊपर से", "Leaf top"),
    LEAF_UNDERSIDE("पत्ती नीचे से", "Leaf underside"),
}

// Farmer-facing text for gate verdicts; proper string extraction is Phase 8.
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("कैमरे की अनुमति चाहिए · Camera permission needed", fontSize = 16.sp)
        }
        return
    }

    val sessionId = remember { store.newSessionId() }
    var stepIndex by remember { mutableStateOf(0) }
    var verdict by remember { mutableStateOf<QualityVerdict>(QualityVerdict.TooBlurry(0.0, 0.0)) }
    var capturing by remember { mutableStateOf(false) }
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

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val step = CaptureStep.entries[stepIndex]
        Text(
            "चरण ${stepIndex + 1}/3 — ${step.hi} · ${step.en}",
            fontSize = 18.sp,
            style = MaterialTheme.typography.titleMedium,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(verdictText(verdict), fontSize = 16.sp)
            Button(
                enabled = verdict is QualityVerdict.Pass && !capturing,
                onClick = {
                    capturing = true
                    scope.launch {
                        val file = store.imageFile(sessionId, step)
                        try {
                            takePicture(imageCapture, file)
                            captured += file
                            if (stepIndex == CaptureStep.entries.lastIndex) {
                                withContext(Dispatchers.IO) {
                                    store.completeSession(sessionId, plotId, captured)
                                }
                                onComplete(sessionId)
                            } else {
                                stepIndex++
                            }
                        } finally {
                            capturing = false
                        }
                    }
                },
                // 56dp minimum touch target rule
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("फ़ोटो लें · Capture", fontSize = 18.sp)
            }
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
