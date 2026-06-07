package com.aiva.console.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Full resolved palette: base tokens + the active accent pair. */
data class AivaPalette(
    val bg: Color = Base.bg,
    val bg2: Color = Base.bg2,
    val ink: Color = Base.ink,
    val inkDim: Color = Base.inkDim,
    val inkFaint: Color = Base.inkFaint,
    val line: Color = Base.line,
    val lineSoft: Color = Base.lineSoft,
    val panel2: Color = Base.panel2,
    val warn: Color = Base.warn,
    val err: Color = Base.err,
    val blue: Color = Base.blue,
    val violet: Color = ACCENTS[0].violet,
    val violet2: Color = ACCENTS[0].violet2,
    val mint: Color = ACCENTS[0].mint,
    val mint2: Color = ACCENTS[0].mint2,
) {
    val ok: Color get() = mint
    val ai: Color get() = violet2
}

fun paletteFor(accentIndex: Int): AivaPalette {
    val a = ACCENTS.getOrElse(accentIndex) { ACCENTS[0] }
    return AivaPalette(violet = a.violet, violet2 = a.violet2, mint = a.mint, mint2 = a.mint2)
}

val LocalAiva = staticCompositionLocalOf { AivaPalette() }

/** Shorthand accessor: `Aiva.violet`, `Aiva.ink`, … */
object Aiva {
    val c: AivaPalette
        @Composable get() = LocalAiva.current
}

@Composable
fun AivaTheme(accentIndex: Int = 0, content: @Composable () -> Unit) {
    val palette = remember(accentIndex) { paletteFor(accentIndex) }
    CompositionLocalProvider(LocalAiva provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.violet,
                secondary = palette.mint,
                background = palette.bg,
                surface = palette.bg2,
                onBackground = palette.ink,
                onSurface = palette.ink,
                error = palette.err,
            ),
            content = content,
        )
    }
}
