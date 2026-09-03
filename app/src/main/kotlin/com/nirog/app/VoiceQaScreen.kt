package com.nirog.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nirog.ui.MicGlyph
import com.nirog.ui.Palette
import com.nirog.ui.blueprintCorners
import com.nirog.ui.R as UiR
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Everything the offline Q&A can answer from, loaded by the caller. */
data class QaFacts(
    val spendInr: Int,
    val phiSafeAfter: LocalDate?,
    val nearbyReports: Int,
)

/** Artboard 14: the one dark screen — a held-up ear, not a page. */
@Composable
fun VoiceQaScreen(
    voice: VoiceProvider,
    facts: QaFacts,
    onTopicAction: (QaTopic) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var transcript by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf<QaTopic?>(null) }
    var failed by remember { mutableStateOf(false) }
    val unavailable = remember { !voice.canListen() }

    fun startListening() {
        transcript = ""
        topic = null
        failed = false
        listening = true
        voice.startListening(
            onPartial = { transcript = it },
            onFinal = { final ->
                listening = false
                val heard = final ?: transcript
                if (heard.isBlank()) {
                    failed = true
                } else {
                    transcript = heard
                    val t = routeQuestion(heard)
                    topic = t
                }
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startListening() else failed = true }

    fun ensureListening() {
        if (unavailable) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) { ensureListening() }

    // Speak the answer once when a topic lands.
    val answerText = topic?.let { answerText(it, facts) }
    LaunchedEffect(topic) { answerText?.let { voice.speak(it) } }

    Column(
        Modifier.fillMaxSize().background(Palette.Ink),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))
        Text(
            stringResource(UiR.string.qa_brand),
            fontSize = 16.sp, letterSpacing = 2.sp, color = Palette.Hairline, fontWeight = FontWeight.SemiBold,
        )
        Text(
            when {
                unavailable -> stringResource(UiR.string.qa_unavailable)
                failed -> stringResource(UiR.string.qa_heard_nothing)
                listening -> stringResource(UiR.string.qa_listening)
                else -> ""
            },
            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
            modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp), lineHeight = 40.sp,
        )

        if (listening) Waveform()

        // Live transcript / final question in a blueprint frame
        if (transcript.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 26.dp)
                    .border(1.dp, Palette.Paper.copy(alpha = 0.45f))
                    .blueprintCorners(Palette.Paper.copy(alpha = 0.6f))
                    .padding(18.dp),
            ) {
                Text(
                    "\"$transcript\"",
                    fontSize = 22.sp, lineHeight = 36.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // The answer, from the app's own data
        answerText?.let { text ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 18.dp)
                    .background(Palette.TintGreen)
                    .border(1.5.dp, Palette.Green)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text,
                    fontSize = 20.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold,
                    color = Palette.GreenPressed,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .background(Palette.Green)
                        .clickable { onTopicAction(topic!!) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(
                            when (topic!!) {
                                QaTopic.SPEND, QaTopic.HARVEST -> UiR.string.qa_open_diary
                                QaTopic.NEARBY -> UiR.string.qa_open_radar
                                QaTopic.SCAN -> UiR.string.qa_start_scan
                            },
                        ),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
            }
        }

        if (topic == null && !listening) {
            Text(
                stringResource(UiR.string.qa_hint),
                fontSize = 16.sp, color = Palette.Hairline, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        // 112dp mic target you can hit with a wet thumb
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(if (listening) Palette.Green else Palette.GreenPressed)
                .border(3.dp, Palette.TintGreen, CircleShape)
                .clickable(enabled = !unavailable) { if (!listening) ensureListening() },
            contentAlignment = Alignment.Center,
        ) {
            MicGlyph(color = Color.White, size = 46.dp)
        }
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(58.dp)
                    .border(1.5.dp, Palette.Paper.copy(alpha = 0.6f))
                    .clickable {
                        voice.cancelListening()
                        onClose()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(UiR.string.qa_cancel),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Paper,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(58.dp)
                    .background(Palette.Paper)
                    .clickable {
                        if (listening) voice.stopListening() else ensureListening()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (listening) UiR.string.qa_done else UiR.string.qa_ask_again),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                )
            }
        }
    }
}

@Composable
private fun answerText(topic: QaTopic, facts: QaFacts): String = when (topic) {
    QaTopic.SPEND -> stringResource(UiR.string.qa_answer_spend, facts.spendInr)
    QaTopic.HARVEST ->
        facts.phiSafeAfter
            ?.let { stringResource(UiR.string.qa_answer_harvest, it.format(DateTimeFormatter.ofPattern("d MMMM"))) }
            ?: stringResource(UiR.string.qa_answer_harvest_clear)
    QaTopic.NEARBY -> stringResource(UiR.string.qa_answer_nearby, facts.nearbyReports)
    QaTopic.SCAN -> stringResource(UiR.string.qa_answer_scan)
}

/** Decorative listening waveform — seven green bars breathing out of phase. */
@Composable
private fun Waveform() {
    val transition = rememberInfiniteTransition(label = "wave")
    val heights = listOf(44, 56, 64, 50, 60, 40, 52)
    val colors = listOf(0xFF7FB08F, 0xFFA8C9B2, 0xFFDCEAD7, 0xFFA8C9B2, 0xFF7FB08F, 0xFFDCEAD7, 0xFFA8C9B2)
    Row(
        Modifier.height(64.dp).padding(top = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { i, h ->
            val scale by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(500, delayMillis = i * 120, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(6.dp)
                    .height(h.dp)
                    .scale(scaleX = 1f, scaleY = scale)
                    .background(Color(colors[i])),
            )
        }
    }
}
