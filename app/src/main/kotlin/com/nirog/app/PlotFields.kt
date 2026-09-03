package com.nirog.app

// Field components shared by onboarding and plot settings, plus the coarse
// location plumbing. One implementation, two screens.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nirog.engine.AreaConverter
import com.nirog.model.AreaUnit
import com.nirog.ui.Palette
import com.nirog.ui.R as UiR

@Composable
fun SectionLabel(res: Int) {
    Text(
        stringResource(res),
        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
    )
}

/** Big numeric area field + acre/bigha/guntha segmented row + per-state bigha picker. */
@Composable
fun AreaEditor(
    areaText: String,
    unit: AreaUnit,
    stateName: String?,
    onAreaText: (String) -> Unit,
    onUnit: (AreaUnit) -> Unit,
    onState: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextField(
            value = areaText,
            onValueChange = onAreaText,
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
            UnitOption(UiR.string.unit_acre, unit == AreaUnit.ACRE) { onUnit(AreaUnit.ACRE) }
            UnitOption(UiR.string.unit_bigha, unit == AreaUnit.BIGHA) { onUnit(AreaUnit.BIGHA) }
            UnitOption(UiR.string.unit_guntha, unit == AreaUnit.GUNTHA) { onUnit(AreaUnit.GUNTHA) }
        }
    }
    if (unit == AreaUnit.BIGHA) {
        // Bigha size is a per-state lookup (I10); refuse to guess a state.
        SectionLabel(UiR.string.onboarding_which_state)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AreaConverter.bighaHaByState.keys.sorted().chunked(2).forEach { rowStates ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowStates.forEach { s ->
                        SelectChip(
                            label = s.replace('_', ' '),
                            selected = s == stateName,
                            modifier = Modifier.weight(1f),
                        ) { onState(s) }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 56.dp)
            .background(if (selected) Palette.Green else Palette.Card)
            .border(1.5.dp, if (selected) Palette.GreenPressed else Palette.Ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Color.White else Palette.Ink,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun RowScope.UnitOption(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
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

/** Capture button + privacy/failure note. Parent owns the location state. */
@Composable
fun LocationField(captured: Boolean, failed: Boolean, labelRes: Int, onRequest: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (captured) Palette.TintGreen else Palette.Card)
            .border(1.5.dp, if (captured) Palette.Green else Palette.Ink)
            .clickable(enabled = !captured, onClick = onRequest),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (captured) "📌" else "📍", fontSize = 20.sp)
            Text(
                stringResource(if (captured) UiR.string.location_added else labelRes),
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = if (captured) Palette.GreenPressed else Palette.Ink,
            )
        }
    }
    Text(
        stringResource(if (failed) UiR.string.location_failed else UiR.string.location_privacy),
        fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold,
        color = if (failed) Palette.OchreDark else Palette.TextSecondary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Handles the coarse permission ask, then fetches. Returns the trigger. */
@Composable
fun rememberLocationRequester(onResult: (Location?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) fetchCoarseLocation(context, onResult) else onResult(null) }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            fetchCoarseLocation(context, onResult)
        } else {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

/**
 * Coarse platform location, no Play Services dependency: freshest last-known
 * fix from the coarse providers, else one network-provider update.
 * Only ever consumed as a geohash-5 cell.
 */
private fun fetchCoarseLocation(context: Context, onResult: (Location?) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    runCatching {
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { it in lm.allProviders }
            .mapNotNull { lm.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
        if (last != null) {
            onResult(last)
        } else if (LocationManager.NETWORK_PROVIDER in lm.allProviders) {
            // ponytail: requestSingleUpdate is deprecated (API 30 has
            // getCurrentLocation); swap when minSdk allows.
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, { onResult(it) }, Looper.getMainLooper())
        } else {
            onResult(null)
        }
    }.onFailure { onResult(null) }
}
