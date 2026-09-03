package com.nirog.feature.treatment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirog.engine.DoseResult
import com.nirog.model.Product
import com.nirog.ui.MicButton
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton
import java.time.format.DateTimeFormatter

/** Artboard 09: doses in tank-fuls, not per-hectare maths. Big tabular figures. */
@Composable
fun DoseScreen(
    product: Product,
    pestNameHi: String,
    plotLine: String,
    plan: DoseResult,
    onSave: () -> Unit,
) {
    PaperScreen {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Text(
                "रासायनिक · CHEMICAL",
                fontSize = 14.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Palette.OchreDark,
            )
            Text(
                product.tradeNames.firstOrNull() ?: product.activeIngredient,
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "$pestNameHi के लिए · $plotLine",
                fontSize = 16.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
            )
            when (plan) {
                is DoseResult.Plan -> Column(
                    Modifier.fillMaxWidth().padding(top = 14.dp).background(Palette.Card).border(1.5.dp, Palette.Ink),
                ) {
                    DoseRow("प्रति टंकी (15L नैपसैक)", "${plan.productPerTank.toInt()} ${plan.productUnit}", big = true)
                    DoseRow("कुल टंकियां", "${plan.tanksRequired}")
                    DoseRow("कुल दवा", "${plan.totalProduct.toInt()} ${plan.productUnit}", big = true, accent = true)
                    DoseRow("कीमत", "₹${plan.totalCostInr.toInt()}", big = true)
                    DoseRow(
                        "कटाई सुरक्षित",
                        plan.phiExpiry.format(DateTimeFormatter.ofPattern("d MMM")) + " के बाद",
                        last = true,
                    )
                }
                is DoseResult.CannotCompute -> Column(
                    Modifier.fillMaxWidth().padding(top = 14.dp).background(Palette.TintOchre).border(1.5.dp, Palette.Ochre).padding(14.dp),
                ) {
                    Text(
                        "मात्रा नहीं निकल पाई",
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Palette.OchreDeep,
                    )
                    Text(
                        when (plan.messageKey) {
                            "dose_unknown_bigha_state" -> "आपके राज्य का बीघा माप दर्ज नहीं है — खेत की जानकारी में राज्य चुनें"
                            else -> plan.detail
                        },
                        fontSize = 16.sp, lineHeight = 26.sp, color = Palette.OchreDeep,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                "डायरी में सहेजें",
                Modifier.weight(1f).height(72.dp),
                enabled = plan is DoseResult.Plan,
                onClick = onSave,
            )
            MicButton()
        }
    }
}

@Composable
private fun DoseRow(label: String, value: String, big: Boolean = false, accent: Boolean = false, last: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = if (big) 24.sp else 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (accent) Palette.Green else Palette.Ink,
        )
    }
    if (!last) HorizontalDivider(color = Palette.Hairline)
}
