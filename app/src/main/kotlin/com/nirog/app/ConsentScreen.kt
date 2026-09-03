package com.nirog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton
import com.nirog.ui.R as UiR

/** DPDP Rules 2025: itemised consent in the farmer's language before any capture. */
const val CONSENT_VERSION = 1

private val ITEMS = listOf(
    "📷" to UiR.string.consent_item_photos,
    "📍" to UiR.string.consent_item_location,
    "🌦" to UiR.string.consent_item_weather,
    "👤" to UiR.string.consent_item_escalation,
)

@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    PaperScreen {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(UiR.string.consent_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, lineHeight = 40.sp)
            Text(
                stringResource(UiR.string.consent_sub),
                fontSize = 14.sp, letterSpacing = 1.5.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(UiR.string.consent_intro),
                fontSize = 18.sp, color = Palette.Ink, modifier = Modifier.padding(top = 16.dp),
            )
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ITEMS.forEach { (icon, textRes) ->
                    Row(
                        Modifier.fillMaxWidth().background(Palette.Card).border(1.dp, Palette.Hairline).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(icon, fontSize = 22.sp)
                        Text(stringResource(textRes), fontSize = 16.sp, lineHeight = 25.sp, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                stringResource(UiR.string.consent_footer),
                fontSize = 15.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp), lineHeight = 24.sp,
            )
        }
        PrimaryButton(
            stringResource(UiR.string.consent_accept),
            Modifier.fillMaxWidth().padding(16.dp).height(72.dp),
            en = stringResource(UiR.string.consent_accept_sub),
            onClick = onAccept,
        )
    }
}
