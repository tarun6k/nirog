package com.nirog.feature.treatment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirog.engine.Rejection
import com.nirog.engine.RejectionReason
import com.nirog.engine.RecommendationResult
import com.nirog.model.Product
import com.nirog.model.TreatmentRung
import com.nirog.model.TreatmentTier
import com.nirog.ui.MicButton
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton
import androidx.compose.foundation.BorderStroke

/** Design artboard 08: free-to-chemical ladder, cost on every rung, locked rungs say why. */
@Composable
fun TreatmentLadderScreen(
    result: RecommendationResult,
    pestNameHi: String,
    severityPct: Double,
    products: Map<String, Product>,
    onDose: (TreatmentRung) -> Unit,
) {
    PaperScreen {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("इलाज की सीढ़ी", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
            Text(
                "$pestNameHi · फैलाव ${severityPct.toInt()}% · सस्ते से शुरू करें",
                fontSize = 16.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
            )
            when (result) {
                is RecommendationResult.Ladder -> {
                    result.rungs.forEach { rung ->
                        RungCard(rung, products[rung.productId], onDose)
                    }
                    RejectionsSection(result.rejections, products)
                }
                is RecommendationResult.NoApprovedProductFound -> {
                    // I9: the honest empty state — never a closest match.
                    Box(
                        Modifier.fillMaxWidth().background(Palette.Card).border(1.5.dp, Palette.Ink).padding(16.dp),
                    ) {
                        Column {
                            Text(
                                "इस रोग के लिए कोई स्वीकृत दवा नहीं मिली",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                            )
                            Text(
                                "NO APPROVED PRODUCT — बिना पंजीकृत दवा हम कभी नहीं बताते। फसल डॉक्टर से पूछें।",
                                fontSize = 16.sp, color = Palette.TextSecondary, lineHeight = 24.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    RejectionsSection(result.rejections, products)
                }
                else -> Text(
                    "इस नतीजे पर इलाज नहीं बनता — पहले पहचान पक्की करें",
                    fontSize = 16.sp, color = Palette.TextSecondary,
                )
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.End) {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            MicButton()
        }
    }
}

@Composable
private fun RungCard(rung: TreatmentRung, product: Product?, onDose: (TreatmentRung) -> Unit) {
    val nameHi = product?.tradeNames?.firstOrNull() ?: rung.productId ?: "—"
    val chemical = rung.tier == TreatmentTier.CHEMICAL
    when {
        rung.locked -> Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.DisabledBg)
                .dashedBorder()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔒", fontSize = 18.sp)
                Text(
                    "$nameHi — बंद",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Stone,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                CostLabel(rung.costInr, dim = true)
            }
            // I7/I6: the written reason, never grey-out alone
            Text(
                rung.lockReason ?: "",
                fontSize = 14.sp, color = Palette.Stone, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        chemical -> Column(Modifier.fillMaxWidth().background(Palette.Card).border(1.5.dp, Palette.Ochre)) {
            Row(
                Modifier.fillMaxWidth().background(Palette.TintOchre).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "⚠ रासायनिक — दस्ताने पहनें, हवा के रुख में न छिड़कें",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.OchreDark,
                )
            }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nameHi, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    product?.activeIngredient?.let {
                        Text(it.uppercase(), fontSize = 13.sp, color = Palette.TextSecondary, letterSpacing = 1.sp)
                    }
                }
                CostLabel(rung.costInr)
            }
            PrimaryButton(
                hi = "मात्रा निकालें →",
                color = Palette.Ochre,
                pressedColor = Palette.OchreDark,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) { onDose(rung) }
        }
        else -> Row(
            Modifier.fillMaxWidth().background(Palette.Card).border(1.dp, Palette.Ink).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(nameHi, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                if (product?.isBiopesticide == true) {
                    Text(
                        "✓ जैविक-सुरक्षित",
                        fontSize = 14.sp, color = Palette.GreenPressed, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            CostLabel(rung.costInr)
        }
    }
}

@Composable
private fun CostLabel(costInr: Double?, dim: Boolean = false) {
    Text(
        costInr?.let { "₹${it.toInt()}" } ?: "—",
        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
        color = if (dim) Palette.Disabled else Palette.Ink,
    )
}

/** I6: rejections are shown to the farmer, never silently filtered. */
@Composable
private fun RejectionsSection(rejections: List<Rejection>, products: Map<String, Product>) {
    if (rejections.isEmpty()) return
    Text(
        "जो नहीं दिखाए गए, और क्यों · NOT SHOWN",
        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.TextSecondary,
        letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 10.dp),
    )
    rejections.forEach { r ->
        val name = products[r.productId]?.tradeNames?.firstOrNull() ?: r.productId
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.DisabledBg)
                .dashedBorder()
                .padding(10.dp),
        ) {
            Column {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Stone)
                Text(reasonHi(r.reason), fontSize = 14.sp, color = Palette.Stone, lineHeight = 20.sp)
            }
        }
    }
}

/** Locked/omitted things wear a dashed frame (design system 07). */
private fun Modifier.dashedBorder(): Modifier = this.drawBehind {
    drawRect(
        Palette.Disabled,
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        ),
    )
}

private fun reasonHi(reason: RejectionReason): String = when (reason) {
    is RejectionReason.NoLabelClaim -> "इस फसल-रोग के लिए पंजीकृत नहीं है"
    is RejectionReason.ClaimNotEffective -> "पंजीकरण की अवधि लागू नहीं है"
    is RejectionReason.Banned -> "भारत में प्रतिबंधित (${reason.notificationRef})"
    is RejectionReason.PhiTooLong ->
        "कटाई तक ${reason.daysToHarvest} दिन बचे हैं, इस दवा को ${reason.phiDays} दिन चाहिए"
    is RejectionReason.ResistanceRotation ->
        "पिछले छिड़काव जैसा ही ${reason.groupCode} समूह (${reason.group}) — बदल-बदल कर दवा दें"
    is RejectionReason.OrganicCertification -> "आपके जैविक प्रमाणपत्र के लिए सुरक्षित नहीं"
}
