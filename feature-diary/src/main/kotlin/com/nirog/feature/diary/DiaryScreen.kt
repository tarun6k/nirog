package com.nirog.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nirog.data.SprayLogEntity
import com.nirog.ui.MicButton
import com.nirog.ui.R as UiR
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.blueprintCorners
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Artboard 10: a season ledger, not a dashboard — entries, one big total, the
 * PHI countdown, and the residue record framed as worth showing a buyer.
 */
@Composable
fun DiaryScreen(
    plotLine: String,
    logs: List<SprayLogEntity>,
    productNames: Map<String, String>,
) {
    val today = LocalDate.now()
    val totalCost = logs.sumOf { it.costInr ?: 0.0 }
    val phiActive = logs.mapNotNull { it.phiExpiryDate }.maxOrNull()
        ?.let { LocalDate.ofEpochDay(it) }?.takeIf { it.isAfter(today) }

    PaperScreen {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(UiR.string.diary_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
            Text(plotLine, fontSize = 16.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold)

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(Palette.Card)
                    .border(1.5.dp, Palette.Ink)
                    .blueprintCorners(Palette.Stone),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(UiR.string.diary_season_cost), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                    Spacer(Modifier.weight(1f))
                    Text("₹${totalCost.toInt()}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                }
                HorizontalDivider(color = Palette.Hairline)
                if (logs.isEmpty()) {
                    Text(
                        stringResource(UiR.string.diary_empty),
                        fontSize = 16.sp, color = Palette.Stone, lineHeight = 24.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                logs.forEach { log ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            LocalDate.ofEpochDay(log.date).format(DateTimeFormatter.ofPattern("dd MMM")),
                            fontSize = 15.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            productNames[log.productId] ?: log.activeIngredient,
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            log.costInr?.let { "₹${it.toInt()}" } ?: "—",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                        )
                    }
                    HorizontalDivider(color = Palette.DisabledBg)
                }
            }

            if (phiActive != null) {
                // PHI countdown as a "safe to harvest after" line, ochre while active
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .background(Palette.TintOchre)
                        .border(1.5.dp, Palette.Ochre)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("⏳", fontSize = 20.sp)
                    Text(
                        stringResource(UiR.string.diary_phi_line, phiActive.format(DateTimeFormatter.ofPattern("d MMMM"))),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.OchreDeep,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(Palette.TintGreen)
                    .border(1.5.dp, Palette.Green)
                    .blueprintCorners(Palette.Green)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("🛡", fontSize = 20.sp)
                Text(
                    stringResource(UiR.string.diary_residue),
                    fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = Palette.GreenPressed,
                )
            }
        }
        Row(Modifier.padding(16.dp)) {
            Spacer(Modifier.weight(1f))
            MicButton()
        }
    }
}
