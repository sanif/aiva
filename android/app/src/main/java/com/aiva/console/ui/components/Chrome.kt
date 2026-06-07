package com.aiva.console.ui.components

import android.os.Build
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.ui.AivaIcons
import com.aiva.console.ui.Screen
import com.aiva.console.ui.theme.Aiva
import com.aiva.console.ui.theme.Mono
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* ============================ TOP STATUS STRIP ============================ */

@Composable
fun TopBar(
    title: String,
    batteryPct: Int?,
    onActions: () -> Unit,
    onSettings: () -> Unit,
    onAmbient: () -> Unit,
) {
    val c = Aiva.c
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .drawBehind {
                drawLine(c.lineSoft, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(c.ok)
        Text(title.uppercase(), style = monoStyle(10.sp, tracking = 1.2.sp, color = c.inkDim), maxLines = 1)
        Spacer(Modifier.weight(1f))

        Icon(AivaIcons.Wifi, null, Modifier.size(13.dp), tint = c.inkDim)
        Icon(AivaIcons.Battery, null, Modifier.size(15.dp), tint = c.inkDim)
        if (batteryPct != null) {
            Text("$batteryPct%", style = monoStyle(10.sp, tracking = 1.2.sp, color = c.mint), maxLines = 1)
        }
        TopIcon(AivaIcons.Grid, onActions)
        TopIcon(AivaIcons.Gear, onSettings)
        TopIcon(AivaIcons.Moon, onAmbient)
    }
}

@Composable
private fun TopIcon(icon: ImageVector, onClick: () -> Unit) {
    val c = Aiva.c
    Box(
        Modifier
            .size(30.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = c.inkDim)
    }
}

/* ============================ LENS CALIBRATION DATA ============================ */

/**
 * A lens circle in window-relative fractions: cx/cy as fractions of window
 * width/height, r as a fraction of window width. Resolution-independent.
 * Convention: [flash, lens1, lens2].
 */
data class LensCircle(val cx: Float, val cy: Float, val r: Float)

/** Default calibration measured on a real Razr 60 Ultra (1080x1272 window).
 *  L1/L2 come from on-device calibration; the flash is estimated. A user
 *  calibration saved in Settings always wins over these. */
val HARDCODED_LENSES: List<LensCircle>? = listOf(
    LensCircle(0.5030f, 0.8550f, 0.0370f), // flash (estimated)
    LensCircle(0.6660f, 0.8550f, 0.1011f), // lens 1
    LensCircle(0.8739f, 0.8551f, 0.1016f), // lens 2
)

fun encodeLensCal(circles: List<LensCircle>): String =
    circles.joinToString(";") { "%.4f,%.4f,%.4f".format(it.cx, it.cy, it.r) }

fun parseLensCal(s: String): List<LensCircle> =
    s.split(";").mapNotNull { part ->
        val n = part.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (n.size == 3) LensCircle(n[0], n[1], n[2]) else null
    }

/**
 * Reads the physical camera cutout rects (window px) from the system.
 * Empty list -> caller falls back to calibration / design proportions.
 */
@Composable
fun rememberLensCutouts(): List<Rect> {
    val view = LocalView.current
    var rects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    DisposableEffect(view) {
        fun read() {
            rects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
                    .map { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
                    .filter { it.center.y > view.height * 0.5f }
                    .sortedBy { it.left }
            } else emptyList()
        }
        read()
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> read() }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    return rects
}

/* ============================ SYSTEM DOCK — CONTINUOUS WAVE ============================
 * One band: flat top edge under the nav/strip, then a single Bézier sweep up
 * and over the lens cluster. No separate island shape. Tab/strip width is
 * clamped left of the calibrated flash so content NEVER overlaps the optics. */

val NAV_ITEMS = listOf(
    Triple(Screen.Home, "Home", AivaIcons.Home),
    Triple(Screen.Monitor, "Monitor", AivaIcons.Monitor),
    Triple(Screen.Tasks, "Tasks", AivaIcons.Tasks),
    Triple(Screen.Chat, "Chat", AivaIcons.Chat),
)

/** Live data the lens styles render. */
data class BayData(
    val aiState: String = "idle",
    val cpu: Float = 0f,
    val ram: Float = 0f,
    val disk: Float = 0f,
    val serviceStates: List<String> = emptyList(),
    val alertCount: Int = 0,
)

@Composable
fun SystemDock(
    current: Screen,
    onSelect: (Screen) -> Unit,
    lensCal: List<LensCircle> = emptyList(),
    lensStyle: String = "eyes",
    bay: BayData = BayData(),
    flareAt: Long = 0L,
    design: String = "wave", // wave | rail
    strip: (@Composable () -> Unit)? = null, // rail-mode strip content (pager)
    modifier: Modifier = Modifier,
) {
    val c = Aiva.c
    val view = LocalView.current
    val density = LocalDensity.current
    val cutouts = rememberLensCutouts()

    // Lens targets in WINDOW px — priority: user calibration > hardcoded > cutout API.
    val calibration = lensCal.ifEmpty { HARDCODED_LENSES ?: emptyList() }
    val targets: List<Pair<Offset, Float>> =
        if (calibration.isNotEmpty() && view.width > 0) {
            calibration.map {
                Offset(it.cx * view.width, it.cy * view.height) to (it.r * view.width)
            }
        } else {
            cutouts.map { r ->
                r.center to (maxOf(r.width, r.height) / 2f + 2.5f * density.density)
            }
        }

    val t = rememberInfiniteTransition(label = "lens")
    val pulse by t.animateFloat(
        0.55f, 0.85f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )
    val spinSlow by t.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "spinSlow")
    val spinFast by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "spinFast")

    val flare = remember { Animatable(0f) }
    LaunchedEffect(flareAt) {
        if (flareAt > 0L) {
            flare.snapTo(1f)
            flare.animateTo(0f, tween(900))
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.7.sp, color = c.inkFaint,
    )

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val vw = view.width.toFloat().coerceAtLeast(1f)
        val vh = view.height.toFloat().coerceAtLeast(1f)
        // window-px geometry from calibration (fallback: design proportions)
        val lensTopW = if (targets.isNotEmpty()) targets.minOf { it.first.y - it.second } else vh - with(density) { 64.dp.toPx() }
        val lensLeftW = if (targets.isNotEmpty()) targets.minOf { it.first.x - it.second } else vw * 0.62f

        val dockH: Dp = with(density) { (vh - lensTopW).toDp() + 24.dp }.coerceIn(84.dp, 200.dp)
        // wave: band stays LOW over the tabs; the pager floats above the wave line
        val tabsH = 72.dp

        var dockOrigin by remember { mutableStateOf(Offset.Zero) }

        Box(
            Modifier
                .fillMaxWidth()
                .height(dockH)
                .onGloballyPositioned { dockOrigin = it.positionInWindow() },
        ) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val flatY = size.height - tabsH.toPx()
                    val lensLeftL = lensLeftW - dockOrigin.x
                    val sweepStart = (lensLeftL - 44.dp.toPx()).coerceAtLeast(20.dp.toPx())
                    val sweepEnd = (lensLeftL - 4.dp.toPx()).coerceAtLeast(sweepStart + 8.dp.toPx())
                    val midX = sweepStart + (sweepEnd - sweepStart) * 0.5f

                    // rail: one straight top edge; wave: subtle Bézier step over the lenses
                    val band = Path().apply {
                        if (design == "rail") {
                            moveTo(0f, 0f); lineTo(size.width, 0f)
                        } else {
                            moveTo(0f, flatY)
                            lineTo(sweepStart, flatY)
                            cubicTo(midX, flatY, midX, 0f, sweepEnd, 0f)
                            lineTo(size.width, 0f)
                        }
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(band, Color(0xFF0B0B16))
                    drawPath(
                        band,
                        Brush.verticalGradient(
                            listOf(c.violet.copy(alpha = 0.06f), Color(0x59000000)),
                            startY = 0f, endY = size.height,
                        ),
                    )

                    val edge = Path().apply {
                        if (design == "rail") {
                            moveTo(0f, 0f); lineTo(size.width, 0f)
                        } else {
                            moveTo(0f, flatY)
                            lineTo(sweepStart, flatY)
                            cubicTo(midX, flatY, midX, 0f, sweepEnd, 0f)
                            lineTo(size.width, 0f)
                        }
                    }
                    drawPath(edge, c.violet.copy(alpha = 0.16f), style = Stroke(6.dp.toPx()))
                    drawPath(edge, c.violet.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
                    if (flare.value > 0.01f) {
                        drawPath(edge, c.violet.copy(alpha = 0.6f * flare.value), style = Stroke(3.dp.toPx()))
                    }

                    /* ---- per-element lens treatment ---- */
                    val local = targets.map { (ctr, r) -> Offset(ctr.x - dockOrigin.x, ctr.y - dockOrigin.y) to r }
                    local.forEachIndexed { i, (center, radius) ->
                        val isFlash = local.size >= 3 && i == 0
                        val isLast = i == local.lastIndex
                        val arcTL = Offset(center.x - radius, center.y - radius)
                        val arcSz = Size(radius * 2, radius * 2)
                        val baseAccent = when {
                            isFlash -> c.warn
                            isLast -> c.mint
                            else -> c.violet
                        }
                        drawCircle(
                            Brush.radialGradient(
                                listOf(baseAccent.copy(alpha = if (isFlash) 0.08f else 0.13f), Color.Transparent),
                                center = center, radius = radius * 2.0f,
                            ),
                            radius = radius * 2.0f, center = center,
                        )

                        when (lensStyle) {
                            "gauges" -> {
                                val (value, color) = when {
                                    isFlash -> bay.disk to c.blue
                                    isLast -> bay.ram to c.mint
                                    else -> bay.cpu to when {
                                        bay.cpu > 85f -> c.err
                                        bay.cpu > 70f -> c.warn
                                        else -> c.violet
                                    }
                                }
                                val sw = if (isFlash) 2.dp.toPx() else 3.dp.toPx()
                                drawCircle(Color.White.copy(alpha = 0.08f), radius, center, style = Stroke(sw))
                                val sweep = (value.coerceIn(0f, 100f) / 100f) * 360f
                                drawArc(color.copy(alpha = 0.3f), -90f, sweep, false, arcTL, arcSz, style = Stroke(sw * 1.8f, cap = StrokeCap.Round))
                                drawArc(color, -90f, sweep, false, arcTL, arcSz, style = Stroke(sw, cap = StrokeCap.Round))
                            }

                            "radar" -> when {
                                isFlash -> drawCircle(c.warn.copy(alpha = 0.5f), radius, center, style = Stroke(1.5.dp.toPx()))
                                isLast -> {
                                    drawCircle(c.mint.copy(alpha = 0.35f), radius, center, style = Stroke(1.5.dp.toPx()))
                                    for (k in 0..5) {
                                        drawArc(
                                            c.mint.copy(alpha = 0.55f * (1f - k / 6f)),
                                            spinFast - k * 9f, 9f, false, arcTL, arcSz,
                                            style = Stroke(2.5.dp.toPx()),
                                        )
                                    }
                                    repeat(bay.alertCount.coerceAtMost(5)) { b ->
                                        val ang = ((b * 137f + 40f) * PI / 180f).toFloat()
                                        val rr = radius * (0.40f + 0.15f * (b % 3))
                                        drawCircle(c.err.copy(alpha = pulse), 2.5.dp.toPx(), center + Offset(cos(ang) * rr, sin(ang) * rr))
                                    }
                                }
                                else -> {
                                    drawCircle(c.violet.copy(alpha = 0.35f), radius, center, style = Stroke(1.5.dp.toPx()))
                                    val n = bay.serviceStates.size.coerceAtLeast(1)
                                    bay.serviceStates.forEachIndexed { si, st ->
                                        val ang = ((-90f + si * 360f / n) * PI / 180f).toFloat()
                                        val col = when (st) { "up" -> c.ok; "warn" -> c.warn; else -> c.err }
                                        drawCircle(col, 2.2.dp.toPx(), center + Offset(cos(ang) * radius, sin(ang) * radius))
                                    }
                                }
                            }

                            "hangar" -> {
                                val alpha = if (i % 2 == 0) pulse else 1.40f - pulse
                                drawCircle(
                                    baseAccent.copy(alpha = alpha), radius, center,
                                    style = Stroke(if (isFlash) 1.5.dp.toPx() else 2.dp.toPx()),
                                )
                            }

                            else -> { // "eyes"
                                if (isFlash) {
                                    val led = when (bay.aiState) {
                                        "thinking" -> 0.35f + 0.6f * ((spinFast % 90f) / 90f)
                                        "alert" -> pulse
                                        else -> 0.18f
                                    }
                                    drawCircle(c.warn.copy(alpha = led), radius, center, style = Stroke(1.5.dp.toPx()))
                                } else {
                                    val alert = bay.aiState == "alert"
                                    val accent = if (alert) c.err else baseAccent
                                    drawCircle(accent.copy(alpha = if (alert) pulse else 0.40f), radius, center, style = Stroke(2.dp.toPx()))
                                    val gaze = if (bay.aiState == "thinking") spinFast else spinSlow
                                    drawArc(accent.copy(alpha = 0.25f), gaze - 8f, 86f, false, arcTL, arcSz, style = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round))
                                    drawArc(accent, gaze, 70f, false, arcTL, arcSz, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                                }
                            }
                        }
                    }

                    /* ---- denoting labels above the lens cluster ---- */
                    if (local.isNotEmpty()) {
                        val labelY = (local.minOf { (ctr, r) -> ctr.y - r } - 16.dp.toPx()).coerceAtLeast(3.dp.toPx())
                        fun label(text: String, centerX: Float) {
                            val l = textMeasurer.measure(AnnotatedString(text), labelStyle)
                            drawText(l, topLeft = Offset(centerX - l.size.width / 2f, labelY))
                        }
                        when (lensStyle) {
                            "gauges" -> local.forEachIndexed { i, (center, _) ->
                                val isFlash = local.size >= 3 && i == 0
                                label(if (isFlash) "DSK" else if (i == local.lastIndex) "RAM" else "CPU", center.x)
                            }
                            "radar" -> local.forEachIndexed { i, (center, _) ->
                                val isFlash = local.size >= 3 && i == 0
                                label(if (isFlash) "LED" else if (i == local.lastIndex) "SCAN" else "SVC", center.x)
                            }
                            "hangar" -> label("◍ OPTICS", local.first().first.x)
                            else -> {
                                val state = when (bay.aiState) {
                                    "thinking" -> "AIVA · THINKING"
                                    "alert" -> "AIVA · ALERT"
                                    else -> "AIVA"
                                }
                                // center over the lens pair (skip flash)
                                val lensesOnly = if (local.size >= 3) local.drop(1) else local
                                label(state, lensesOnly.map { it.first.x }.average().toFloat())
                            }
                        }
                    }
                },
            )

            // interactive flat section — width clamped LEFT of the sweep, never over optics
            val lensLeftDp: Dp = with(density) { (lensLeftW - dockOrigin.x).coerceAtLeast(0f).toDp() }
            val safeW: Dp = (lensLeftDp - 48.dp).coerceAtLeast(120.dp)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .width(safeW)
                    .fillMaxHeight(),
            ) {
                if (design == "wave") {
                    // pager floats ABOVE the wave line; tabs stay inside the band
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) { strip?.invoke() }
                        Row(Modifier.height(tabsH).fillMaxWidth()) {
                            NAV_ITEMS.forEach { (screen, label, icon) ->
                                NavTab(
                                    label = label,
                                    icon = icon,
                                    selected = current == screen,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onClick = { onSelect(screen) },
                                )
                            }
                        }
                    }
                } else {
                    strip?.invoke()
                }
            }
        }
    }
}

@Composable
private fun NavTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Aiva.c
    Column(
        modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .drawBehind {
                if (selected) {
                    val w = 26.dp.toPx()
                    val x = (size.width - w) / 2f
                    drawLine(c.violet.copy(alpha = 0.5f), Offset(x - 3, 1.dp.toPx()), Offset(x + w + 3, 1.dp.toPx()), 5.dp.toPx())
                    drawLine(c.violet, Offset(x, 1.dp.toPx()), Offset(x + w, 1.dp.toPx()), 2.dp.toPx())
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(30.dp)
                        .drawBehind {
                            drawCircle(Brush.radialGradient(listOf(c.violet.copy(alpha = 0.45f), Color.Transparent)))
                        },
                )
            }
            Icon(icon, null, Modifier.size(21.dp), tint = if (selected) c.violet2 else c.inkFaint)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(),
            style = monoStyle(8.sp, tracking = 0.5.sp, color = if (selected) c.ink else c.inkFaint),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/* ============================ NAV RAIL (collapsible) ============================ */

@Composable
fun NavRail(
    current: Screen,
    onSelect: (Screen) -> Unit,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Aiva.c
    val w by animateDpAsState(if (collapsed) 16.dp else 46.dp, label = "railW")
    Column(
        modifier
            .fillMaxHeight()
            .width(w)
            .drawBehind {
                drawRect(Color(0xCC08080F))
                drawLine(c.lineSoft, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (collapsed) {
            // thin handle — tap anywhere to expand
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggleCollapse),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AivaIcons.ChevR, null, Modifier.size(12.dp), tint = c.inkFaint)
            }
        } else {
            Spacer(Modifier.height(48.dp))
            NAV_ITEMS.forEach { (screen, _, icon) ->
                val sel = current == screen
                Box(
                    Modifier
                        .size(38.dp)
                        .drawBehind {
                            if (sel) {
                                drawCircle(Brush.radialGradient(listOf(c.violet.copy(alpha = 0.40f), Color.Transparent)))
                                drawLine(c.violet, Offset(1.dp.toPx(), size.height * 0.25f), Offset(1.dp.toPx(), size.height * 0.75f), 2.dp.toPx())
                            }
                        }
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(screen) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(20.dp), tint = if (sel) c.violet2 else c.inkFaint)
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.weight(1f))
            // collapse chevron
            Box(
                Modifier
                    .size(38.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggleCollapse),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AivaIcons.ChevR, null, Modifier.size(14.dp).rotate(180f), tint = c.inkFaint)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/* ============================ SCANLINES ============================ */

@Composable
fun Scanlines(modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            var y = 0f
            val step = 3.dp.toPx()
            val lineH = 1.dp.toPx()
            while (y < size.height) {
                drawRect(
                    Color.White.copy(alpha = 0.02f),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, lineH),
                )
                y += step
            }
        },
    )
}

/* ============================ TOAST ============================ */

@Composable
fun HudToast(message: String?, modifier: Modifier = Modifier) {
    val c = Aiva.c
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 3 },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 3 },
    ) {
        var last by remember { mutableStateOf(message ?: "") }
        if (message != null) last = message
        Row(
            Modifier
                .background(Color(0xF50E0F1C), RoundedCornerShape(10.dp))
                .drawBehind {
                    drawRoundRect(
                        c.violet.copy(alpha = 0.4f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Orb(16.dp)
            Text(last, style = sansStyle(11.sp, color = c.ink), maxLines = 2)
        }
    }
}
