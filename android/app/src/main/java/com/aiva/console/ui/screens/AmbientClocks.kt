package com.aiva.console.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.ui.components.LensCircle
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.components.sansStyle
import com.aiva.console.ui.theme.Aiva
import com.aiva.console.ui.theme.Mono
import com.aiva.console.ui.theme.Sans
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/* ============================================================
 * AMBIENT — always-on clock suite. Battery-first:
 *  · pure-black background (AMOLED: unlit = no power)
 *  · thin strokes, minimal lit pixels, no infinite animations
 *  · one redraw per minute, ±px drift against burn-in
 *  · deep-dim 00:00–06:00
 * Swipe ⟷ to switch style (persisted) · tap to wake.
 * ============================================================ */

private data class LensPx(val center: Offset, val r: Float)

/** Calibrated lens geometry with a Razr-60-Ultra-shaped fallback. */
private fun lensGeometry(cal: List<LensCircle>, w: Float, h: Float): List<LensPx> =
    if (cal.isNotEmpty()) {
        cal.map { LensPx(Offset(it.cx * w, it.cy * h), it.r * w) }
    } else {
        listOf(
            LensPx(Offset(0.5030f * w, 0.8550f * h), 0.0370f * w), // flash (estimated)
            LensPx(Offset(0.6660f * w, 0.8550f * h), 0.1011f * w), // lens 1
            LensPx(Offset(0.8739f * w, 0.8551f * h), 0.1016f * w), // lens 2
        )
    }

private fun List<LensPx>.flash(): LensPx? = if (size >= 3) first() else null
private fun List<LensPx>.lensA(): LensPx = if (size >= 3) this[1] else first()
private fun List<LensPx>.lensB(): LensPx = last()

/** Accent palette for the ambient clocks (selected via in-clock settings). */
val AMBIENT_COLORS = listOf(
    "VIOLET" to Color(0xFF9D7BFF),
    "MINT" to Color(0xFF4DFFC4),
    "WHITE" to Color(0xFFEEF0FF),
    "AMBER" to Color(0xFFFFCE5C),
    "RED" to Color(0xFFFF5D6C),
    "BLUE" to Color(0xFF7CB3FF),
)

@Composable
fun AmbientOverlay(
    snapshot: DashboardSnapshot,
    batteryPct: Int?,
    lensCal: List<LensCircle>,
    styleIndex: Int,
    colorIndex: Int,
    onStyleChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
    onExit: () -> Unit,
) {
    val c = Aiva.c
    val view = LocalView.current

    // one tick per minute — the whole overlay redraws only then
    val minuteNow by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - (value % 60_000) + 50)
        }
    }
    val cal = remember(minuteNow) { Calendar.getInstance().apply { timeInMillis = minuteNow } }
    val t = clockParts(minuteNow)
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)

    // burn-in drift + deep-night dim
    val tick = minuteNow / 60_000
    val dx = ((tick % 3).toInt() - 1) * 2
    val dy = ((tick % 5).toInt() - 2) * 2
    val dim = if (hour24 < 6) 0.55f else 1f

    val lenses = lensGeometry(lensCal, view.width.toFloat(), view.height.toFloat())
    val accent = AMBIENT_COLORS[colorIndex.coerceIn(0, AMBIENT_COLORS.lastIndex)].second
    val pagerState = rememberPagerState(initialPage = styleIndex.coerceIn(0, 5)) { 6 }
    LaunchedEffect(pagerState.currentPage) { onStyleChange(pagerState.currentPage) }

    // view mode = pure fullscreen clock; single tap toggles the settings
    // chrome (style dots + color swatches); DOUBLE tap exits.
    var chrome by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(dim)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { chrome = !chrome },
                    onDoubleTap = { onExit() },
                )
            },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> OrbitClock(t, hour24, minute, lenses, accent, dx, dy)
                1 -> LensAnalog(t, hour24, minute, lenses, accent, dx, dy)
                2 -> TerminalZero(t, snapshot, batteryPct, lenses, accent, dx, dy)
                3 -> EclipseClock(t, hour24, batteryPct, lenses, accent, dx, dy)
                4 -> IphoneClock(t, lenses, accent, dx, dy)
                else -> HorizonClock(t, accent, dx, dy)
            }
        }

        if (chrome) {
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // style dots
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                    repeat(6) { i ->
                        Box(
                            Modifier
                                .size(if (pagerState.currentPage == i) 5.dp else 4.dp)
                                .background(
                                    if (pagerState.currentPage == i) accent
                                    else Color.White.copy(alpha = 0.15f),
                                    CircleShape,
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // color swatches
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp)) {
                    AMBIENT_COLORS.forEachIndexed { i, (_, color) ->
                        Box(
                            Modifier
                                .size(18.dp)
                                .background(color.copy(alpha = 0.9f), CircleShape)
                                .border(
                                    if (colorIndex == i) 2.dp else 1.dp,
                                    if (colorIndex == i) Color.White else Color.White.copy(alpha = 0.2f),
                                    CircleShape,
                                )
                                .clickable(remember { MutableInteractionSource() }, indication = null) {
                                    onColorChange(i)
                                },
                        )
                    }
                }
            }
            Text(
                "SWIPE · STYLE   TAP · HIDE   DOUBLE-TAP · WAKE",
                Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp),
                style = monoStyle(8.sp, FontWeight.Medium, 1.2.sp, Color.White.copy(alpha = 0.30f)),
            )
        }
    }
}

/* ---------- shared draw helpers ---------- */

private fun DrawScope.ring(l: LensPx, color: Color, width: Float = 2f) {
    drawCircle(color, l.r, l.center, style = Stroke(width))
}

private fun DrawScope.arcOn(l: LensPx, color: Color, start: Float, sweep: Float, width: Float, cap: StrokeCap = StrokeCap.Round) {
    drawArc(
        color, start, sweep, false,
        topLeft = Offset(l.center.x - l.r, l.center.y - l.r),
        size = Size(l.r * 2, l.r * 2),
        style = Stroke(width, cap = cap),
    )
}

private fun DrawScope.labelUnder(tm: TextMeasurer, text: String, l: LensPx, style: TextStyle, gap: Float) {
    val layout = tm.measure(AnnotatedString(text), style)
    drawText(layout, topLeft = Offset(l.center.x - layout.size.width / 2f, l.center.y + l.r + gap))
}

/* ============ 0 · ORBIT TIME — lens rings are the dials ============ */

@Composable
private fun OrbitClock(t: ClockParts, hour24: Int, minute: Int, lenses: List<LensPx>, accent: Color, dx: Int, dy: Int) {
    val c = Aiva.c
    val tm = rememberTextMeasurer()
    val small = TextStyle(fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = c.inkFaint)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.offset(x = (24 + dx).dp, y = (52 + dy).dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${t.h}:${t.m}", style = monoStyle(62.sp, color = accent.copy(alpha = 0.92f)), maxLines = 1)
                Text(" ${t.ap}", style = monoStyle(22.sp, color = c.inkFaint), maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Text(t.date, style = monoStyle(11.sp, FontWeight.Medium, 3.2.sp, c.inkFaint), maxLines = 1)
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val hrs = lenses.lensA(); val mins = lenses.lensB(); val fl = lenses.flash()
                // tracks
                ring(hrs, accent.copy(alpha = 0.18f)); ring(mins, c.mint.copy(alpha = 0.15f))
                // dials: violet = hour progress, mint = minute hand
                val hourSweep = ((hour24 % 12) + minute / 60f) / 12f * 360f
                arcOn(hrs, accent.copy(alpha = 0.85f), -90f, hourSweep, 2.5.dp.toPx())
                arcOn(mins, c.mint.copy(alpha = 0.8f), -90f, minute / 60f * 360f, 2.5.dp.toPx())
                if (fl != null) {
                    ring(fl, c.warn.copy(alpha = 0.22f), 1.5.dp.toPx())
                    // hour-top marker dot
                    drawCircle(c.warn.copy(alpha = 0.7f), 2.dp.toPx(), Offset(fl.center.x, fl.center.y - fl.r - 5.dp.toPx()))
                }
                labelUnder(tm, "HRS", hrs, small, 7.dp.toPx())
                labelUnder(tm, "MIN", mins, small, 7.dp.toPx())
            },
        )
    }
}

/* ============ 1 · LENS ANALOG — the camera is the clock hub ============ */

@Composable
private fun LensAnalog(t: ClockParts, hour24: Int, minute: Int, lenses: List<LensPx>, accent: Color, dx: Int, dy: Int) {
    val c = Aiva.c
    val tm = rememberTextMeasurer()

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.offset(x = (24 + dx).dp, y = (36 + dy).dp), verticalAlignment = Alignment.Bottom) {
            Text("${t.h}:${t.m}", style = monoStyle(26.sp, color = c.inkDim), maxLines = 1)
            Text(" ${t.ap}", style = monoStyle(12.sp, color = c.inkFaint), maxLines = 1)
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val hub = lenses.lensA(); val date = lenses.lensB(); val fl = lenses.flash()
                // hub ring + quadrant ticks
                ring(hub, accent.copy(alpha = 0.5f), 1.5.dp.toPx())
                for (q in 0..3) {
                    val a = (q * 90f - 90f) * (Math.PI / 180f).toFloat()
                    val dir = Offset(cos(a), sin(a))
                    drawLine(
                        c.inkFaint.copy(alpha = 0.6f),
                        hub.center + dir * (hub.r + 4.dp.toPx()),
                        hub.center + dir * (hub.r + 10.dp.toPx()),
                        1.2.dp.toPx(),
                    )
                }
                // hands radiate FROM the physical camera (hidden at center, emerge past the rim)
                val hourA = (((hour24 % 12) + minute / 60f) / 12f * 360f - 90f) * (Math.PI / 180f).toFloat()
                val minA = (minute / 60f * 360f - 90f) * (Math.PI / 180f).toFloat()
                drawLine(
                    c.ink.copy(alpha = 0.9f), hub.center,
                    hub.center + Offset(cos(hourA), sin(hourA)) * (hub.r * 1.18f),
                    2.5.dp.toPx(), cap = StrokeCap.Round,
                )
                drawLine(
                    accent.copy(alpha = 0.9f), hub.center,
                    hub.center + Offset(cos(minA), sin(minA)) * (hub.r * 1.55f),
                    2.dp.toPx(), cap = StrokeCap.Round,
                )
                // date complication above the second lens
                ring(date, c.mint.copy(alpha = 0.35f), 1.5.dp.toPx())
                val day = AnnotatedString("%02d".format(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)))
                val dayL = tm.measure(day, TextStyle(fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.mint))
                drawText(dayL, topLeft = Offset(date.center.x - dayL.size.width / 2f, date.center.y - date.r - dayL.size.height - 14.dp.toPx()))
                if (fl != null) ring(fl, c.warn.copy(alpha = 0.2f), 1.2.dp.toPx())
            },
        )
    }
}

/* ============ 2 · TERMINAL ZERO — outline digits, ~1% lit ============ */

@Composable
private fun TerminalZero(
    t: ClockParts,
    snapshot: DashboardSnapshot,
    batteryPct: Int?,
    lenses: List<LensPx>,
    accent: Color,
    dx: Int,
    dy: Int,
) {
    val c = Aiva.c
    val tm = rememberTextMeasurer()
    val small = TextStyle(fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = c.inkFaint)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().offset(x = dx.dp, y = (64 + dy).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${t.h}:${t.m}",
                style = TextStyle(
                    fontFamily = Mono, fontSize = 84.sp, fontWeight = FontWeight.SemiBold,
                    color = accent.copy(alpha = 0.8f), drawStyle = Stroke(width = 2.5f),
                ),
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            Text(t.date, style = monoStyle(11.sp, FontWeight.Medium, 4.sp, c.inkFaint), maxLines = 1)
            Spacer(Modifier.height(14.dp))
            Text(
                "CPU ${snapshot.metrics.cpuPct.toInt()}% · ${snapshot.alerts.size} ALERTS · ${t.ap}",
                style = monoStyle(9.sp, FontWeight.Medium, 1.8.sp, c.inkFaint.copy(alpha = 0.65f)),
                maxLines = 1,
            )
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                lenses.forEach { ring(it, Color(0xFF15161F), 1.5.dp.toPx()) }
                if (batteryPct != null) {
                    val a = lenses.lensA()
                    arcOn(a, c.mint.copy(alpha = 0.7f), -90f, batteryPct / 100f * 360f, 2.dp.toPx())
                    labelUnder(tm, "BAT $batteryPct", a, small, 7.dp.toPx())
                }
            },
        )
    }
}

/* ============ 3 · ECLIPSE — lenses as crescent-lit moons ============ */

private val STARS = listOf(
    0.12f to 0.10f, 0.78f to 0.07f, 0.30f to 0.30f, 0.90f to 0.26f, 0.18f to 0.46f,
    0.55f to 0.16f, 0.70f to 0.40f, 0.42f to 0.55f, 0.08f to 0.66f, 0.86f to 0.55f,
)

@Composable
private fun EclipseClock(t: ClockParts, hour24: Int, batteryPct: Int?, lenses: List<LensPx>, accent: Color, dx: Int, dy: Int) {
    val c = Aiva.c
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.offset(x = (24 + dx).dp, y = (64 + dy).dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${t.h}:${t.m}", style = monoStyle(56.sp, color = accent.copy(alpha = 0.9f)), maxLines = 1)
                Text(" ${t.ap}", style = monoStyle(20.sp, color = c.inkFaint), maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append(t.date)
                    if (batteryPct != null) append(" · $batteryPct%")
                },
                style = monoStyle(10.sp, FontWeight.Medium, 3.sp, c.inkFaint),
                maxLines = 1,
            )
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                STARS.forEachIndexed { i, (fx, fy) ->
                    drawCircle(
                        if (i == 6) c.violet2.copy(alpha = 0.7f) else c.inkFaint.copy(alpha = 0.5f),
                        if (i == 6) 1.8.dp.toPx() else 1.2.dp.toPx(),
                        Offset(fx * size.width, fy * size.height),
                    )
                }
                // crescents rotate slowly with the hour — three moons catching light
                val base = -150f + hour24 * 15f
                lenses.forEachIndexed { i, l ->
                    val isFlash = lenses.size >= 3 && i == 0
                    ring(l, Color(0xFF0D0D18), 1.dp.toPx())
                    arcOn(
                        l,
                        when {
                            isFlash -> c.warn.copy(alpha = 0.65f)
                            i == lenses.lastIndex -> c.mint.copy(alpha = 0.65f)
                            else -> c.violet2.copy(alpha = 0.75f)
                        },
                        base, if (isFlash) 50f else 58f,
                        if (isFlash) 1.6.dp.toPx() else 2.5.dp.toPx(),
                    )
                }
            },
        )
    }
}

/* ============ 4 · IPHONE — huge stacked digits ============ */

@Composable
private fun IphoneClock(t: ClockParts, lenses: List<LensPx>, accent: Color, dx: Int, dy: Int) {
    val c = Aiva.c
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().offset(x = dx.dp, y = (26 + dy).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(t.date, style = sansStyle(13.sp, FontWeight.Medium, c.inkDim), maxLines = 1)
            Text(
                t.h.padStart(2, '0'),
                style = TextStyle(
                    fontFamily = Sans, fontSize = 118.sp, fontWeight = FontWeight.Bold,
                    color = c.ink.copy(alpha = 0.88f), lineHeight = 112.sp, textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(
                t.m,
                style = TextStyle(
                    fontFamily = Sans, fontSize = 118.sp, fontWeight = FontWeight.Bold,
                    color = accent.copy(alpha = 0.88f), lineHeight = 112.sp, textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(t.ap, style = monoStyle(12.sp, tracking = 3.sp, color = c.inkFaint), maxLines = 1)
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                lenses.forEach { ring(it, Color.White.copy(alpha = 0.10f), 1.2.dp.toPx()) }
            },
        )
    }
}

/* ============ 5 · HORIZON — landscape one-liner, reads sideways ============ */

@Composable
private fun HorizonClock(t: ClockParts, accent: Color, dx: Int, dy: Int) {
    val c = Aiva.c
    // Rotated 90° so the time reads upright when the closed phone lies on
    // its side — a bedside/desk clock. requiredWidth(maxHeight) lets the
    // line use the tall axis as its width before rotation.
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .requiredWidth(maxHeight)
                .offset(x = dx.dp, y = dy.dp)
                .graphicsLayer { rotationZ = 90f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t.date,
                style = monoStyle(12.sp, FontWeight.Medium, 3.sp, c.inkFaint),
                maxLines = 1,
            )
            Text(
                "${t.h}:${t.m}",
                style = TextStyle(
                    fontFamily = Sans,
                    fontSize = 150.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent.copy(alpha = 0.9f),
                    lineHeight = 150.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(t.ap, style = monoStyle(14.sp, tracking = 4.sp, color = c.inkFaint), maxLines = 1)
        }
    }
}
