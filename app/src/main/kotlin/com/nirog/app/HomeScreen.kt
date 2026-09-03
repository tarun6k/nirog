package com.nirog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirog.data.PlotEntity
import com.nirog.ui.MicGlyph
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton
import com.nirog.ui.blueprintCorners
import java.time.LocalDate

/** Artboard 01: three things only — alert, scan, ask. */
@Composable
fun HomeScreen(
    plot: PlotEntity?,
    outbreakCount: Int,
    onScan: () -> Unit,
) {
    PaperScreen {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("निरोग", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Green)
            Text(
                "NIROG",
                fontSize = 15.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
                color = Palette.Ink, modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (plot != null) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("नमस्ते", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                val days = LocalDate.now().toEpochDay() - plot.sowingDate
                Text(
                    "${plot.label} · ${plot.cropId.uppercase()} · ${plot.areaValue} ${areaUnitHi(plot.areaUnit)} · बुआई का ${days}वां दिन",
                    fontSize = 16.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp,
                )
            }
        }
        if (outbreakCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 18.dp)
                    .background(Palette.TintClay)
                    .border(1.5.dp, Palette.Clay)
                    .blueprintCorners(Palette.Clay)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("🍂", fontSize = 26.sp)
                Column {
                    Text(
                        "चेतावनी · OUTBREAK ALERT",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Palette.Clay,
                    )
                    Text(
                        "आस-पास रोग पुष्ट हुआ — अपनी फसल जांचें",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, lineHeight = 26.sp,
                    )
                }
            }
        }
        Box(Modifier.padding(16.dp).padding(top = 4.dp)) {
            PrimaryButton(
                hi = "फसल की जांच करें",
                en = "SCAN MY CROP · 3 फोटो · 1 मिनट",
                modifier = Modifier.fillMaxWidth().height(216.dp),
                onClick = onScan,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(76.dp)
                .background(Palette.Card)
                .border(1.5.dp, Palette.Ink)
                .clickable { /* voice Q&A lands in Phase 8 */ },
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicGlyph(size = 28.dp)
            Text("सवाल पूछें", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            Text(
                "ASK A QUESTION",
                fontSize = 14.sp, letterSpacing = 1.5.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = Palette.Hairline)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📶", fontSize = 16.sp, color = Palette.Green)
            Text(
                "बिना सिग्नल भी काम करता है · डायरी और दवा की मात्रा",
                fontSize = 14.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun areaUnitHi(unit: String): String = when (unit) {
    "ACRE" -> "एकड़"
    "BIGHA" -> "बीघा"
    "GUNTHA" -> "गुंठा"
    "HECTARE" -> "हेक्टेयर"
    else -> unit
}
