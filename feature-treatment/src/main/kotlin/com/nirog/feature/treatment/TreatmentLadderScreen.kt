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
import androidx.compose.ui.res.stringResource
import com.nirog.ui.MicButton
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton
import com.nirog.ui.R as UiR

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
            Text(stringResource(UiR.string.ladder_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
            Text(
                stringResource(UiR.string.ladder_sub, pestNameHi, severityPct.toInt()),
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
                                stringResource(UiR.string.ladder_no_product_title),
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                            )
                            Text(
                                stringResource(UiR.string.ladder_no_product_body),
                                fontSize = 16.sp, color = Palette.TextSecondary, lineHeight = 24.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    RejectionsSection(result.rejections, products)
                }
                else -> Text(
                    stringResource(UiR.string.ladder_no_treatment),
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
                    stringResource(UiR.string.ladder_locked_suffix, nameHi),
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
                    stringResource(UiR.string.ladder_chemical_band),
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
                hi = stringResource(UiR.string.ladder_dose_button),
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
                        stringResource(UiR.string.ladder_bio_tag),
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
        stringResource(UiR.string.ladder_rejections_header),
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

@Composable
private fun reasonHi(reason: RejectionReason): String = when (reason) {
    is RejectionReason.NoLabelClaim -> stringResource(UiR.string.reject_no_label_claim)
    is RejectionReason.ClaimNotEffective -> stringResource(UiR.string.reject_claim_not_effective)
    is RejectionReason.Banned -> stringResource(UiR.string.reject_banned, reason.notificationRef)
    is RejectionReason.PhiTooLong ->
        stringResource(UiR.string.reject_phi, reason.daysToHarvest, reason.phiDays)
    is RejectionReason.ResistanceRotation ->
        stringResource(UiR.string.reject_rotation, reason.groupCode, reason.group)
    is RejectionReason.OrganicCertification -> stringResource(UiR.string.reject_organic)
}
