package com.nirog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirog.data.PlotEntity
import com.nirog.engine.AreaConverter
import com.nirog.model.AreaUnit
import com.nirog.ui.MicButton
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton
import com.nirog.ui.R as UiR
import java.time.LocalDate
import java.util.UUID

// Crops are picked from pictures, not a dropdown (artboard 12). Emoji stand in
// for the canvas's photo slots until real crop photography exists.
private data class CropChoice(val id: String, val hi: String, val en: String, val emoji: String)

private val CROPS = listOf(
    CropChoice("wheat", "गेहूं", "WHEAT", "🌾"),
    CropChoice("cotton", "कपास", "COTTON", "☁️"),
    CropChoice("chilli", "मिर्च", "CHILLI", "🌶"),
    CropChoice("mustard", "सरसों", "MUSTARD", "🌼"),
)

// VERIFY WITH AGRONOMIST: sowing→harvest defaults (days) used only to seed
// plannedHarvestDate; the farmer's real date should replace these in plot
// settings. Shorter = stricter PHI gate (I3), so err short.
private val CROP_DURATION_DAYS = mapOf(
    "wheat" to 130,
    "cotton" to 160,
    "chilli" to 130,
    "mustard" to 110,
)

/** Sowing date as memory-shaped chips, including "don't remember" (artboard 12). */
private data class SownChoice(val labelRes: Int, val daysAgo: Long)

private val SOWN_CHOICES = listOf(
    SownChoice(UiR.string.sown_this_week, 4),
    SownChoice(UiR.string.sown_last_month, 30),
    // ponytail: "don't remember" = 45-day estimate; only feeds context priors,
    // never a safety rule. Replace with crop-stage estimation if it matters.
    SownChoice(UiR.string.sown_dont_remember, 45),
)

@Composable
fun OnboardingScreen(onAdd: (PlotEntity) -> Unit) {
    var cropId by remember { mutableStateOf("wheat") }
    var areaText by remember { mutableStateOf("2.5") }
    var unit by remember { mutableStateOf(AreaUnit.ACRE) }
    var stateName by remember { mutableStateOf<String?>(null) }
    var sownIndex by remember { mutableStateOf(0) }

    val area = areaText.toDoubleOrNull()
    val valid = area != null && area > 0 && (unit != AreaUnit.BIGHA || stateName != null)

    PaperScreen {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                stringResource(UiR.string.onboarding_kicker),
                fontSize = 14.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Palette.TextSecondary,
            )
            Text(
                stringResource(UiR.string.onboarding_title),
                fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, lineHeight = 40.sp,
            )

            SectionLabel(UiR.string.onboarding_which_crop)
            // 2-column photo-card grid; selected card wears the thick green frame
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CROPS.chunked(2).forEach { rowCrops ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowCrops.forEach { crop ->
                            val selected = crop.id == cropId
                            Column(
                                Modifier
                                    .weight(1f)
                                    .height(108.dp)
                                    .background(Palette.Card)
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) Palette.Green else Palette.Ink,
                                    )
                                    .clickable { cropId = crop.id },
                            ) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(crop.emoji, fontSize = 34.sp)
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(if (selected) Palette.Green else Palette.Ink.copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${crop.hi} ${crop.en}",
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (selected) Text("✓", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            SectionLabel(UiR.string.onboarding_how_much_land)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = areaText,
                    onValueChange = { areaText = it },
                    modifier = Modifier.width(110.dp).border(1.5.dp, Palette.Ink),
                    textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Palette.Card,
                        unfocusedContainerColor = Palette.Card,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Row(Modifier.weight(1f).heightIn(min = 56.dp).border(1.5.dp, Palette.Ink)) {
                    UnitOption(UiR.string.unit_acre, unit == AreaUnit.ACRE) { unit = AreaUnit.ACRE }
                    UnitOption(UiR.string.unit_bigha, unit == AreaUnit.BIGHA) { unit = AreaUnit.BIGHA }
                    UnitOption(UiR.string.unit_guntha, unit == AreaUnit.GUNTHA) { unit = AreaUnit.GUNTHA }
                }
            }

            if (unit == AreaUnit.BIGHA) {
                // Bigha size is a per-state lookup (I10); refuse to guess a state.
                SectionLabel(UiR.string.onboarding_which_state)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AreaConverter.bighaHaByState.keys.sorted().chunked(2).forEach { rowStates ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowStates.forEach { s ->
                                val selected = s == stateName
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = 56.dp)
                                        .background(if (selected) Palette.Green else Palette.Card)
                                        .border(1.5.dp, if (selected) Palette.GreenPressed else Palette.Ink)
                                        .clickable { stateName = s },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        s.replace('_', ' '),
                                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else Palette.Ink,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionLabel(UiR.string.onboarding_when_sown)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SOWN_CHOICES.forEachIndexed { i, choice ->
                    val selected = i == sownIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .background(if (selected) Palette.Green else Palette.Card)
                            .border(1.5.dp, if (selected) Palette.GreenPressed else Palette.Ink)
                            .clickable { sownIndex = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(choice.labelRes),
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) Color.White else Palette.Ink,
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                stringResource(UiR.string.onboarding_add),
                Modifier.weight(1f).height(70.dp),
                enabled = valid,
            ) {
                val sowing = LocalDate.now().minusDays(SOWN_CHOICES[sownIndex].daysAgo)
                val duration = CROP_DURATION_DAYS.getValue(cropId)
                onAdd(
                    PlotEntity(
                        id = UUID.randomUUID().toString(),
                        farmerId = "demo-farmer",
                        label = "मेरा खेत",
                        cropId = cropId,
                        variety = null,
                        areaValue = area!!,
                        areaUnit = unit.name,
                        sowingDate = sowing.toEpochDay(),
                        // Location is optional: without it the app degrades to
                        // image-only context (no weather, no outbreak radar).
                        lat = null, lon = null, pincode = null,
                        plannedHarvestDate = sowing.plusDays(duration.toLong()).toEpochDay(),
                        organicStatus = "NONE",
                        state = stateName,
                    ),
                )
            }
            MicButton()
        }
    }
}

@Composable
private fun SectionLabel(res: Int) {
    Text(
        stringResource(res),
        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.UnitOption(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .background(if (selected) Palette.Green else Palette.Card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(labelRes),
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Color.White else Palette.Ink,
        )
    }
}
