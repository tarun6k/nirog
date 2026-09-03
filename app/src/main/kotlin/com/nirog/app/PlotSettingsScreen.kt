package com.nirog.app

import android.app.DatePickerDialog
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirog.data.PlotEntity
import com.nirog.model.AreaUnit
import com.nirog.model.OrganicStatus
import com.nirog.ui.MicButton
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton
import com.nirog.ui.R as UiR
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Edit the plot facts the safety rules depend on: harvest date (I3 PHI gate),
 * organic status (I5), area/unit/state (I10 dose), sowing date, location.
 * Plus the DPDP "delete everything about me" action.
 */
@Composable
fun PlotSettingsScreen(
    plot: PlotEntity,
    onSave: (PlotEntity) -> Unit,
    onDeleteEverything: () -> Unit,
) {
    val context = LocalContext.current
    var areaText by remember { mutableStateOf(plot.areaValue.toString()) }
    var unit by remember { mutableStateOf(AreaUnit.valueOf(plot.areaUnit)) }
    var stateName by remember { mutableStateOf(plot.state) }
    var sowing by remember { mutableStateOf(LocalDate.ofEpochDay(plot.sowingDate)) }
    var harvest by remember { mutableStateOf(LocalDate.ofEpochDay(plot.plannedHarvestDate)) }
    var organic by remember { mutableStateOf(OrganicStatus.valueOf(plot.organicStatus)) }
    var location by remember { mutableStateOf<Location?>(null) }
    var locationFailed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val requestLocation = rememberLocationRequester { loc ->
        location = loc
        locationFailed = loc == null
    }

    // Native platform picker over any picker library.
    fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        ).show()
    }

    val area = areaText.toDoubleOrNull()
    val valid = area != null && area > 0 &&
        (unit != AreaUnit.BIGHA || stateName != null) &&
        harvest.isAfter(sowing)

    PaperScreen {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                stringResource(UiR.string.settings_title),
                fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, lineHeight = 40.sp,
            )
            Text(
                "${plot.label} · ${plot.cropId.uppercase()}",
                fontSize = 16.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
            )

            SectionLabel(UiR.string.onboarding_how_much_land)
            AreaEditor(
                areaText = areaText, unit = unit, stateName = stateName,
                onAreaText = { areaText = it }, onUnit = { unit = it }, onState = { stateName = it },
            )

            SectionLabel(UiR.string.settings_sowing)
            DateRow(sowing) { pickDate(sowing) { sowing = it } }

            SectionLabel(UiR.string.settings_harvest)
            DateRow(harvest) { pickDate(harvest) { harvest = it } }
            Text(
                stringResource(UiR.string.settings_harvest_why),
                fontSize = 14.sp, lineHeight = 21.sp, color = Palette.TextSecondary,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
            )

            SectionLabel(UiR.string.settings_organic)
            val organicOptions = listOf(
                OrganicStatus.NONE to UiR.string.organic_none,
                OrganicStatus.NPOP_CERTIFIED to UiR.string.organic_npop,
                OrganicStatus.PGS_REGISTERED to UiR.string.organic_pgs,
                OrganicStatus.IN_CONVERSION to UiR.string.organic_conversion,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                organicOptions.chunked(2).forEach { rowOpts ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowOpts.forEach { (status, labelRes) ->
                            SelectChip(
                                label = stringResource(labelRes),
                                selected = organic == status,
                                modifier = Modifier.weight(1f),
                            ) { organic = status }
                        }
                    }
                }
            }
            if (organic != OrganicStatus.NONE) {
                // I5 is certification-destroying territory: say what the choice does.
                Text(
                    stringResource(UiR.string.settings_organic_note),
                    fontSize = 14.sp, lineHeight = 21.sp, color = Palette.GreenPressed,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionLabel(UiR.string.onboarding_where)
            LocationField(
                captured = location != null,
                failed = locationFailed,
                labelRes = if (plot.lat != null) UiR.string.location_update else UiR.string.location_add,
                onRequest = requestLocation,
            )

            // DPDP: the delete path, behind one explicit confirmation.
            SectionLabel(UiR.string.settings_danger)
            if (!confirmDelete) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Palette.TintClay)
                        .border(1.5.dp, Palette.Clay)
                        .clickable { confirmDelete = true }
                        .padding(14.dp),
                ) {
                    Text(
                        stringResource(UiR.string.settings_danger),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Clay,
                    )
                    Text(
                        stringResource(UiR.string.settings_danger_sub),
                        fontSize = 14.sp, color = Palette.Ink, fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().background(Palette.TintClay).border(1.5.dp, Palette.Clay).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(UiR.string.settings_danger_confirm),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Clay,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectChip(
                            label = stringResource(UiR.string.settings_danger_no),
                            selected = false,
                            modifier = Modifier.weight(1f),
                        ) { confirmDelete = false }
                        PrimaryButton(
                            stringResource(UiR.string.settings_danger_yes),
                            Modifier.weight(1f).heightIn(min = 56.dp),
                            color = Palette.Clay,
                            pressedColor = Palette.Ink,
                            onClick = onDeleteEverything,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                stringResource(UiR.string.settings_save),
                Modifier.weight(1f).height(70.dp),
                enabled = valid,
            ) {
                onSave(
                    plot.copy(
                        areaValue = area!!,
                        areaUnit = unit.name,
                        state = stateName,
                        sowingDate = sowing.toEpochDay(),
                        plannedHarvestDate = harvest.toEpochDay(),
                        organicStatus = organic.name,
                        lat = location?.latitude ?: plot.lat,
                        lon = location?.longitude ?: plot.lon,
                    ),
                )
            }
            MicButton()
        }
    }
}

@Composable
private fun DateRow(date: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(Palette.Card)
            .border(1.5.dp, Palette.Ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📅", fontSize = 20.sp)
        Spacer(Modifier.weight(1f))
        Text(
            date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink,
        )
    }
}
