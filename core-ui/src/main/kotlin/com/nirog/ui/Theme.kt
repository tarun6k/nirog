package com.nirog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Field palette from the Nirog design canvas (artboard 15). Single light theme:
 * high-contrast warm paper tuned for outdoor sunlight; no dark variant by design
 * (the only dark surfaces are the camera and voice screens, painted explicitly).
 * Colour never travels alone — every colored element pairs with an icon + label.
 */
object Palette {
    val Green = Color(0xFF1D5B38) // action
    val GreenPressed = Color(0xFF123B24)
    val TintGreen = Color(0xFFDCEAD7)
    val Ochre = Color(0xFF8A5305) // caution / chemical
    val OchreDark = Color(0xFF6B4004)
    val OchreDeep = Color(0xFF4A2C02)
    val TintOchre = Color(0xFFF3E2C0)
    val Clay = Color(0xFFA03024) // alert
    val TintClay = Color(0xFFF6DDD4)
    val Ink = Color(0xFF201D17)
    val Paper = Color(0xFFF8F4EA)
    val Card = Color(0xFFFFFDF6)
    val Stone = Color(0xFF5A5548)
    val TextSecondary = Color(0xFF3F3B31)
    val Hairline = Color(0xFFCFC8B6)
    val Disabled = Color(0xFF8F8A7C)
    val DisabledBg = Color(0xFFEFE9DA)
    val CameraDark = Color(0xFF14130F)
}

@Composable
fun NirogTheme(content: @Composable () -> Unit) {
    // Deliberately no dark variant: sunlight legibility beats theming.
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Green,
            onPrimary = Color.White,
            background = Palette.Paper,
            onBackground = Palette.Ink,
            surface = Palette.Card,
            onSurface = Palette.Ink,
            error = Palette.Clay,
        ),
        content = content,
    )
}
