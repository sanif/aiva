package com.aiva.console.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiva.console.ui.theme.Aiva
import kotlin.math.abs
import kotlin.math.sin

/* ============================ ORB ============================
 * The Aiva presence — idle | thinking | alert.
 * Breathing scale + expanding fading ring; alert recolors to red. */
@Composable
fun Orb(size: Dp, state: String = "idle", modifier: Modifier = Modifier) {
    val c = Aiva.c
    val alert = state == "alert"
    val period = if (state == "thinking") 1100 else 3400

    val hi = if (alert) Color(0xFFFFB6C0) else Color(0xFFC9B6FF)
    val mid = if (alert) c.err else c.violet
    val lo = if (alert) Color(0xFF5B1320) else Color(0xFF2A1A6B)
    val glow = mid

    val t = rememberInfiniteTransition(label = "orb")
    val breathe by t.animateFloat(
        1f, 1.06f,
        infiniteRepeatable(tween(period / 2, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val ringPhase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // outer glow halo
        drawCircle(
            brush = Brush.radialGradient(
                listOf(glow.copy(alpha = 0.55f), Color.Transparent),
                center = center, radius = r * 1.65f,
            ),
            radius = r * 1.65f, center = center,
        )
        // expanding fading ring
        val ringR = r * (1.12f + 0.22f * ringPhase)
        drawCircle(
            color = glow.copy(alpha = 0.45f * (1f - ringPhase)),
            radius = ringR, center = center, style = Stroke(width = 1.dp.toPx()),
        )
        // body (breathing)
        val br = r * breathe * 0.92f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(hi, mid, lo),
                center = Offset(center.x - br * 0.24f, center.y - br * 0.36f),
                radius = br * 2.0f,
            ),
            radius = br, center = center,
        )
        // specular highlight
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(center.x - br * 0.2f, center.y - br * 0.3f),
                radius = br * 0.65f,
            ),
            radius = br * 0.65f,
            center = Offset(center.x - br * 0.2f, center.y - br * 0.3f),
        )
    }
}

/* ============================ GAUGE ============================ */
@Composable
fun Gauge(
    value: Float,
    size: Dp = 50.dp,
    color: Color = Aiva.c.violet,
    stroke: Dp = 5.dp,
    track: Color = Color.White.copy(alpha = 0.08f),
    modifier: Modifier = Modifier,
) {
    val v by animateFloatAsState(value.coerceIn(0f, 100f), tween(800), label = "gauge")
    Canvas(modifier.size(size)) {
        val sw = stroke.toPx()
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
        val tl = Offset(sw / 2f, sw / 2f)
        drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(sw))
        // soft glow pass then crisp arc
        drawArc(
            color.copy(alpha = 0.30f), -90f, v / 100f * 360f, false, tl, arcSize,
            style = Stroke(sw * 1.9f, cap = StrokeCap.Round),
        )
        drawArc(
            color, -90f, v / 100f * 360f, false, tl, arcSize,
            style = Stroke(sw, cap = StrokeCap.Round),
        )
    }
}

/* ============================ SPARKLINE ============================ */
@Composable
fun Sparkline(
    data: List<Float>,
    color: Color = Aiva.c.violet2,
    fill: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (data.size < 2) { Box(modifier); return }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val max = (data.max()) * 1.1f
        val min = (data.min()) * 0.9f
        val span = (max - min).takeIf { it != 0f } ?: 1f
        val pts = data.mapIndexed { i, v ->
            Offset(i / (data.size - 1f) * w, h - (v - min) / span * h)
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (p in pts.drop(1)) lineTo(p.x, p.y)
        }
        if (fill) {
            val area = Path().apply { addPath(line); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(
                area,
                Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent), endY = h),
            )
        }
        // glow pass + crisp line
        drawPath(line, color.copy(alpha = 0.35f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        drawPath(line, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

/* ============================ BAR METER ============================ */
@Composable
fun BarMeter(value: Float, color: Color? = null, modifier: Modifier = Modifier) {
    val c = Aiva.c
    val barColor = color ?: when {
        value > 85f -> c.err
        value > 70f -> c.warn
        else -> c.mint
    }
    val v by animateFloatAsState(value.coerceIn(0f, 100f) / 100f, tween(600), label = "bar")
    Canvas(modifier.height(6.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.06f), cornerRadius = r)
        if (v > 0f) {
            drawRoundRect(
                barColor.copy(alpha = 0.4f),
                size = androidx.compose.ui.geometry.Size(size.width * v, size.height),
                cornerRadius = r,
            )
            drawRoundRect(
                barColor,
                size = androidx.compose.ui.geometry.Size(size.width * v, size.height),
                cornerRadius = r,
            )
        }
    }
}

/* ============================ WAVEFORM ============================ */
@Composable
fun Waveform(
    bars: Int = 22,
    color: Color = Aiva.c.mint,
    active: Boolean = true,
    height: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "phase",
    )
    Row(modifier.height(height), verticalAlignment = Alignment.CenterVertically) {
        repeat(bars) { i ->
            val base = 0.2f + abs(sin(i * 0.9f)) * 0.7f
            val scale = if (active) 0.35f + 0.65f * (0.5f + 0.5f * sin(i * 0.9f + phase * 2f)) else 0.14f
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight(if (active) base else 0.14f)
                    .graphicsLayer { scaleY = if (active) scale else 1f }
                    .background(color.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(1.dp)),
            )
            if (i < bars - 1) Spacer(Modifier.width(2.dp))
        }
    }
}
