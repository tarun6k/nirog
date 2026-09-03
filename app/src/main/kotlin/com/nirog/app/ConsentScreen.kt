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
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton

/** DPDP Rules 2025: itemised consent in the farmer's language before any capture. */
const val CONSENT_VERSION = 1

private val ITEMS = listOf(
    "📷" to "पौधों की फोटो — रोग पहचानने के लिए, फोन में ही जांची जाती हैं",
    "📍" to "खेत का मोटा इलाका (~5 किमी) — आस-पास की चेतावनी के लिए। सटीक जगह कभी नहीं भेजी जाती",
    "🌦" to "मौसम की जानकारी — सिर्फ आपके इलाके के निर्देशांक से",
    "👤" to "फोटो फसल डॉक्टर को तभी जाती है जब आप हर बार खुद हां कहें",
)

@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    PaperScreen {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("आपकी जानकारी, आपकी मर्ज़ी", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, lineHeight = 40.sp)
            Text(
                "YOUR DATA, YOUR CHOICE",
                fontSize = 14.sp, letterSpacing = 1.5.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "निरोग यह इस्तेमाल करता है, और कुछ नहीं:",
                fontSize = 18.sp, color = Palette.Ink, modifier = Modifier.padding(top = 16.dp),
            )
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ITEMS.forEach { (icon, text) ->
                    Row(
                        Modifier.fillMaxWidth().background(Palette.Card).border(1.dp, Palette.Hairline).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(icon, fontSize = 22.sp)
                        Text(text, fontSize = 16.sp, lineHeight = 25.sp, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                "कोई विज्ञापन नहीं · कोई ट्रैकिंग नहीं · \"मेरा सब कुछ मिटाओ\" कभी भी",
                fontSize = 15.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp), lineHeight = 24.sp,
            )
        }
        PrimaryButton(
            "मंज़ूर है — शुरू करें",
            Modifier.fillMaxWidth().padding(16.dp).height(72.dp),
            en = "I AGREE",
            onClick = onAccept,
        )
    }
}
