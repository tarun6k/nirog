package com.nirog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared Nirog components in the design canvas's blueprint idiom: square corners,
// hairline borders, hard offset shadows, registration corner marks.

/** Blueprint registration marks: small crosses just outside each corner. */
fun Modifier.blueprintCorners(color: Color): Modifier = drawBehind {
    val arm = 5.dp.toPx()
    val w = 1.dp.toPx()
    listOf(
        Offset(0f, 0f), Offset(size.width, 0f),
        Offset(0f, size.height), Offset(size.width, size.height),
    ).forEach { c ->
        drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), w)
        drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), w)
    }
}

/** Raised solid-green primary action: hard 4dp offset shadow, sinks when disabled. */
@Composable
fun PrimaryButton(
    hi: String,
    modifier: Modifier = Modifier,
    en: String? = null,
    enabled: Boolean = true,
    color: Color = Palette.Green,
    pressedColor: Color = Palette.GreenPressed,
    onClick: () -> Unit,
) {
    Box(modifier = modifier.heightIn(min = 60.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 4.dp)
                .background(if (enabled) pressedColor else Palette.Disabled),
        )
        Column(
            Modifier
                .matchParentSize()
                .padding(bottom = 4.dp)
                .background(if (enabled) color else Palette.DisabledBg)
                .border(1.dp, if (enabled) pressedColor else Palette.Disabled)
                .blueprintCorners(Color.White.copy(alpha = 0.7f))
                .clickable(enabled = enabled, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                hi,
                color = if (enabled) Color.White else Palette.Disabled,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (en != null) {
                Text(
                    en,
                    color = if (enabled) Color.White.copy(alpha = 0.85f) else Palette.Disabled,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
            }
        }
    }
}

/** Flat bordered secondary action on card paper. */
@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 56.dp)
            .background(Palette.Card)
            .border(1.5.dp, Palette.Ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
    }
}

/** Hand-drawn mic glyph, stroke style matching the canvas icons. */
@Composable
fun MicGlyph(color: Color = Palette.Green, size: androidx.compose.ui.unit.Dp = 24.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(width = 1.5f * s)
        drawRoundRect(
            color, Offset(9f * s, 2f * s), Size(6f * s, 12f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s), style = stroke,
        )
        drawArc(color, 0f, 180f, false, Offset(5f * s, 3f * s), Size(14f * s, 14f * s), style = stroke)
        drawLine(color, Offset(12f * s, 17f * s), Offset(12f * s, 22f * s), 1.5f * s)
        drawLine(color, Offset(8f * s, 22f * s), Offset(16f * s, 22f * s), 1.5f * s)
    }
}

/**
 * The mic affordance every screen carries (72x56dp bordered block).
 * Wired to Bhashini in Phase 8; until then announces it's coming.
 */
@Composable
fun MicButton(modifier: Modifier = Modifier, dark: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .width(72.dp)
            .heightIn(min = 56.dp)
            .background(if (dark) Palette.Ink else Palette.Card)
            .border(1.5.dp, if (dark) Palette.Card else Palette.Ink)
            .clickable { onClick?.invoke() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicGlyph(color = if (dark) Palette.TintGreen else Palette.Green, size = 22.dp)
        Text(
            androidx.compose.ui.res.stringResource(R.string.speak),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (dark) Palette.Card else Palette.Ink,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

enum class ConfidenceKind { CONFIDENT, AMBIGUOUS, ESCALATED }

/** The three confidence chips — shape+icon+label, never color alone. */
@Composable
fun ConfidenceChip(kind: ConfidenceKind, modifier: Modifier = Modifier) {
    val (bg, border, fg, glyph, labelRes) = when (kind) {
        ConfidenceKind.CONFIDENT ->
            ChipSpec(Palette.TintGreen, Palette.Green, Palette.GreenPressed, "✓", R.string.chip_confident)
        ConfidenceKind.AMBIGUOUS ->
            ChipSpec(Palette.TintOchre, Palette.Ochre, Palette.OchreDark, "▲", R.string.chip_ambiguous)
        ConfidenceKind.ESCALATED ->
            ChipSpec(Color(0xFFE7E2D3), Palette.Stone, Palette.TextSecondary, "👤", R.string.chip_escalated)
    }
    val label = androidx.compose.ui.res.stringResource(labelRes)
    Row(
        modifier.background(bg).border(1.5.dp, border).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

private data class ChipSpec(
    val bg: Color,
    val border: Color,
    val fg: Color,
    val glyph: String,
    val labelRes: Int,
)

/** Full-screen paper background column. */
@Composable
fun PaperScreen(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().background(Palette.Paper), content = content)
}
