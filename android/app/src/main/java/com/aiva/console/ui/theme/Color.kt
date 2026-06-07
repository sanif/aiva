package com.aiva.console.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Aiva design tokens — mirrors design/prototype/aiva.css :root palette.
 */
object Base {
    val bg = Color(0xFF06060C)
    val bg2 = Color(0xFF0A0A14)
    val ink = Color(0xFFEEF0FF)
    val inkDim = Color(0xFF9A9CC0)
    val inkFaint = Color(0xFF5D5F86)

    // rgba(154,156,192,.16) / .09
    val line = Color(0x299A9CC0)
    val lineSoft = Color(0x179A9CC0)

    // rgba(14,15,28,.72) card surface
    val panel2 = Color(0xB80E0F1C)

    val warn = Color(0xFFFFCE5C)
    val err = Color(0xFFFF5D6C)
    val blue = Color(0xFF7CB3FF) // "work" tag / disk gauge accent
}

/** Accent pair — the 4 swappable HUD color pairs from Settings. */
data class Accent(
    val violet: Color,
    val violet2: Color,
    val mint: Color,
    val mint2: Color,
)

val ACCENTS = listOf(
    Accent(Color(0xFF7C4DFF), Color(0xFF9D7BFF), Color(0xFF4DFFC4), Color(0xFF37E0A8)),
    Accent(Color(0xFF4D7CFF), Color(0xFF7BA0FF), Color(0xFF4DD2FF), Color(0xFF37BCE0)),
    Accent(Color(0xFFB14DFF), Color(0xFFC97BFF), Color(0xFFFF4DD2), Color(0xFFE037B8)),
    Accent(Color(0xFF4DFFC4), Color(0xFF7BFFD6), Color(0xFF7C4DFF), Color(0xFF9D7BFF)),
)
