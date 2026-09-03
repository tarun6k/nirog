package com.nirog.feature.diagnosis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirog.model.DiseaseCandidate
import com.nirog.ui.ConfidenceChip
import com.nirog.ui.ConfidenceKind
import com.nirog.ui.MicButton
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton
import com.nirog.ui.SecondaryButton

// Display names for supported pests. UI labels only — regulatory data stays in Room.
fun pestNameHi(pestId: String): String = mapOf(
    "yellow_rust" to "पीला रतुआ",
    "brown_rust" to "भूरा रतुआ",
)[pestId] ?: pestId

fun pestNameEn(pestId: String): String =
    pestId.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

enum class AnalysisStep { PHOTOS, WEATHER, NEARBY }

/** Artboard 04: no spinner — a readable three-step ledger of the actual work. */
@Composable
fun AnalysingScreen(currentStep: AnalysisStep) {
    PaperScreen {
        Column(Modifier.padding(24.dp)) {
            Spacer(Modifier.height(32.dp))
            Text("आपकी फसल की जांच हो रही है", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
            Text(
                "CHECKING · ~20 SECONDS",
                fontSize = 15.sp, letterSpacing = 1.5.sp, color = Palette.TextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(30.dp))
            LedgerStep("आपकी तीनों फोटो देख लीं", "पत्ती के लक्षण पहचाने जा रहे हैं", currentStep.ordinal > 0, currentStep == AnalysisStep.PHOTOS)
            LedgerStep("मौसम जांच रहे हैं", "पिछले 14 दिन की नमी और बारिश", currentStep.ordinal > 1, currentStep == AnalysisStep.WEATHER)
            LedgerStep("आस-पास की रिपोर्ट मिला रहे हैं", "5 किमी के अंदर के खेत", false, currentStep == AnalysisStep.NEARBY)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.padding(16.dp).fillMaxWidth().background(Palette.Card).border(1.dp, Palette.Hairline).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🌿", fontSize = 20.sp)
            Text(
                "सिर्फ फोटो नहीं — मौसम और पड़ोस की रिपोर्ट भी देखी जाती है",
                fontSize = 16.sp, color = Palette.TextSecondary, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LedgerStep(title: String, sub: String, done: Boolean, active: Boolean) {
    Row(Modifier.padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(30.dp)
                .background(if (done) Palette.Green else Palette.Card)
                .border(if (active) 2.5.dp else 1.5.dp, if (done || active) Palette.Green else Palette.Disabled),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
            else if (active) Box(Modifier.size(12.dp).background(Palette.Green))
        }
        Column {
            Text(
                title,
                fontSize = if (active) 19.sp else 18.sp,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (done || active) Palette.Ink else Palette.Stone,
            )
            Text(sub, fontSize = 15.sp, color = Palette.Stone, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Artboard 05: diagnosis as the headline, severity pinned to evidence, one action. */
@Composable
fun ResultConfidentScreen(
    pestId: String,
    severityPct: Double,
    noteKeys: List<String>,
    onTreatment: () -> Unit,
) {
    PaperScreen {
        Column(Modifier.weight(1f).padding(20.dp)) {
            ConfidenceChip(ConfidenceKind.CONFIDENT)
            Text(
                pestNameHi(pestId),
                fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink,
                modifier = Modifier.padding(top = 12.dp), lineHeight = 54.sp,
            )
            Text(
                pestNameEn(pestId).uppercase(),
                fontSize = 22.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic, letterSpacing = 1.sp,
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Palette.Hairline)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat("${severityPct.toInt()}%", "फैलाव अभी")
            }
            ContextNotes(noteKeys)
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("अब क्या करूं?", Modifier.weight(1f).height(76.dp), onClick = onTreatment)
            MicButton()
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
        Text(label, fontSize = 15.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold)
    }
}

/** Artboard 06: ranked candidates + the rub-the-leaf differentiation card. */
@Composable
fun ResultAmbiguousScreen(
    candidates: List<DiseaseCandidate>,
    noteKeys: List<String>,
    onPick: (pestId: String) -> Unit,
    onEscalate: () -> Unit,
) {
    PaperScreen {
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfidenceChip(ConfidenceKind.AMBIGUOUS)
            candidates.take(2).forEachIndexed { i, c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Palette.Card)
                        .border(if (i == 0) 1.5.dp else 1.dp, if (i == 0) Palette.Ink else Palette.Disabled)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${(c.probability * 100).toInt()}%",
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (i == 0) Palette.Green else Palette.Stone,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(pestNameHi(c.pestId), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                        Text(
                            pestNameEn(c.pestId).uppercase(),
                            fontSize = 15.sp, color = Palette.TextSecondary, letterSpacing = 1.sp,
                        )
                    }
                    if (i == 0) {
                        Text(
                            "अधिक संभावना",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.GreenPressed,
                            modifier = Modifier.background(Palette.TintGreen).border(1.dp, Palette.Green).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (candidates.size >= 2) {
                Column(
                    Modifier.fillMaxWidth().background(Palette.TintOchre).border(1.5.dp, Palette.Ochre).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "10 सेकंड की जांच — खुद पता करें",
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.OchreDeep,
                    )
                    Text(
                        "पत्ती की धारी पर उंगली रगड़ें। उंगली पर पीला पाउडर लगे तो पीला रतुआ है; कुछ न लगे तो भूरा रतुआ।",
                        fontSize = 18.sp, lineHeight = 30.sp, color = Palette.Ink, fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("पीला पाउडर लगा", Modifier.weight(1f).height(60.dp)) { onPick(candidates[0].pestId) }
                        SecondaryButton("कुछ नहीं लगा", Modifier.weight(1f).height(60.dp)) { onPick(candidates[1].pestId) }
                    }
                }
            }
            ContextNotes(noteKeys)
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("पक्का नहीं — डॉक्टर को भेजें", Modifier.weight(1f).height(60.dp), onClick = onEscalate)
            MicButton()
        }
    }
}

/** Artboard 07: honesty as a feature — abstain means a human, a deadline, and what was sent. */
@Composable
fun EscalatedScreen(
    noteKeys: List<String>,
    onSendPhotos: (() -> Unit)? = null,
    onHome: () -> Unit,
) {
    PaperScreen {
        Column(Modifier.weight(1f).padding(20.dp)) {
            Spacer(Modifier.height(16.dp))
            ConfidenceChip(ConfidenceKind.ESCALATED)
            Text(
                "हमें पूरा भरोसा नहीं हुआ",
                fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink,
                modifier = Modifier.padding(top = 14.dp), lineHeight = 42.sp,
            )
            Text(
                "यह तस्वीर मुश्किल है, और गलत दवा बताना ठीक नहीं। इसलिए आपकी फोटो फसल डॉक्टर देखेंगे।",
                fontSize = 18.sp, lineHeight = 30.sp, color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .background(Palette.TintGreen)
                    .border(1.5.dp, Palette.Green)
                    .padding(16.dp),
            ) {
                Text("जवाब सिग्नल आने पर भेजा जाएगा", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.GreenPressed)
                Text(
                    "फोन पर घंटी बजेगी — फोटो कतार में सुरक्षित है",
                    fontSize = 15.sp, color = Palette.Green, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp,
                )
            }
            Text(
                "जो भेजा गया · WHAT WAS SENT",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                color = Palette.TextSecondary, modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "3 फोटो · फसल और इलाका · मौसम रिकॉर्ड\nसटीक जगह कभी नहीं भेजी जाती",
                fontSize = 15.sp, lineHeight = 24.sp, color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            ContextNotes(noteKeys)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // DPDP: images travel only on this explicit per-scan yes.
            if (onSendPhotos != null) {
                PrimaryButton(
                    "हां — फोटो डॉक्टर को भेजें",
                    Modifier.fillMaxWidth().height(72.dp),
                    en = "SEND MY PHOTOS",
                    onClick = onSendPhotos,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("घर वापस", Modifier.weight(1f).height(64.dp), onClick = onHome)
                MicButton()
            }
        }
    }
}

/** Degradation notes (I-brief phase 3): visible, never silent. */
@Composable
private fun ContextNotes(noteKeys: List<String>) {
    noteKeys.forEach { key ->
        val text = when (key) {
            "context_no_weather" -> "मौसम जांच नहीं हो पाई — सिर्फ फोटो से नतीजा"
            "diagnosis_model_unavailable" -> "जांच का मॉडल उपलब्ध नहीं — फसल डॉक्टर को भेजा गया"
            else -> key
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).background(Palette.Card).border(1.dp, Palette.Hairline).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ℹ", color = Palette.Stone, fontSize = 16.sp)
            Text(text, fontSize = 15.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold)
        }
    }
}
