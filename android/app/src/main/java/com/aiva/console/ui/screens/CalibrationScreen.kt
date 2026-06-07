package com.aiva.console.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.ui.components.LensCircle
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.components.rememberLensCutouts
import com.aiva.console.ui.theme.Aiva

/**
 * Debug screen: drag two circles to match the physical lens outlines.
 * Shows real-time px + fractional values (share these to hardcode them).
 * SAVE persists the calibration — the dock applies it immediately.
 */
@Composable
fun CalibrationScreen(
    initial: List<LensCircle>,
    onSave: (List<LensCircle>) -> Unit,
    onClose: () -> Unit,
) {
    val c = Aiva.c
    val view = LocalView.current
    val w = view.width.toFloat().coerceAtLeast(1f)
    val h = view.height.toFloat().coerceAtLeast(1f)
    val cutouts = rememberLensCutouts()

    // Working state in window-fraction space: [flash, lens1, lens2].
    val circles = remember {
        mutableStateListOf<LensCircle>().apply {
            when {
                initial.size >= 3 -> addAll(initial.take(3))
                initial.size == 2 -> {
                    add(LensCircle(0.580f, 0.935f, 0.016f)) // flash default
                    addAll(initial)
                }
                else -> {
                    add(LensCircle(0.580f, 0.935f, 0.016f)) // flash hole
                    add(LensCircle(0.720f, 0.930f, 0.050f)) // lens 1
                    add(LensCircle(0.890f, 0.930f, 0.050f)) // lens 2
                }
            }
        }
    }
    var sel by remember { mutableIntStateOf(1) }

    fun nudge(dxPx: Float, dyPx: Float) {
        val cur = circles[sel]
        circles[sel] = cur.copy(
            cx = (cur.cx + dxPx / w).coerceIn(0f, 1f),
            cy = (cur.cy + dyPx / h).coerceIn(0f, 1f),
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF050509))
            // drag anywhere moves the SELECTED circle
            .pointerInput(sel) {
                detectDragGestures { change, amt ->
                    change.consume()
                    nudge(amt.x, amt.y)
                }
            }
            .drawBehind {
                // faint alignment grid
                val step = size.width / 10f
                var x = step
                while (x < size.width) {
                    drawLine(Color.White.copy(alpha = 0.04f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
                // what the cutout API reports (faint white outlines) — for diagnosis
                cutouts.forEach { r ->
                    drawRect(
                        Color.White.copy(alpha = 0.25f),
                        topLeft = Offset(r.left, r.top),
                        size = androidx.compose.ui.geometry.Size(r.width, r.height),
                        style = Stroke(1.5f),
                    )
                }
                // the adjustable circles: flash (amber), lens 1 (violet), lens 2 (mint)
                circles.forEachIndexed { i, lc ->
                    val accent = when (i) { 0 -> c.warn; 1 -> c.violet; else -> c.mint }
                    val center = Offset(lc.cx * size.width, lc.cy * size.height)
                    val radius = lc.r * size.width
                    drawCircle(accent.copy(alpha = if (i == sel) 1f else 0.45f), radius, center, style = Stroke(if (i == sel) 3f else 2f))
                    // crosshair
                    drawLine(accent.copy(alpha = 0.7f), center - Offset(radius * 0.4f, 0f), center + Offset(radius * 0.4f, 0f), 1.5f)
                    drawLine(accent.copy(alpha = 0.7f), center - Offset(0f, radius * 0.4f), center + Offset(0f, radius * 0.4f), 1.5f)
                }
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            /* selector + actions */
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                CalChip("FLASH", c.warn, sel == 0) { sel = 0 }
                CalChip("L1", c.violet, sel == 1) { sel = 1 }
                CalChip("L2", c.mint, sel == 2) { sel = 2 }
                Spacer(Modifier.weight(1f))
                CalChip("SAVE", c.ok, false) { onSave(circles.toList()) }
                CalChip("CLOSE", c.err, false) { onClose() }
            }

            Spacer(Modifier.height(8.dp))

            /* live values — share these */
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xE60E0F1C), RoundedCornerShape(8.dp))
                    .border(1.dp, c.line, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                circles.forEachIndexed { i, lc ->
                    val tag = when (i) { 0 -> "FL"; 1 -> "L1"; else -> "L2" }
                    Text(
                        "$tag  cx=${(lc.cx * w).toInt()}px (%.4f)  cy=${(lc.cy * h).toInt()}px (%.4f)  r=${(lc.r * w).toInt()}px (%.4f)"
                            .format(lc.cx, lc.cy, lc.r),
                        style = monoStyle(9.sp, FontWeight.Medium, color = if (i == sel) c.ink else c.inkDim),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    "WIN ${w.toInt()}x${h.toInt()}px · CUTOUT API: " +
                        if (cutouts.isEmpty()) "nothing reported" else cutouts.joinToString { r ->
                            "[${r.left.toInt()},${r.top.toInt()} ${r.width.toInt()}x${r.height.toInt()}]"
                        },
                    style = monoStyle(8.sp, FontWeight.Medium, color = c.inkFaint),
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(8.dp))

            /* radius slider for the selected circle */
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("R", style = monoStyle(9.sp, color = c.inkDim))
                Slider(
                    value = circles[sel].r,
                    onValueChange = { circles[sel] = circles[sel].copy(r = it) },
                    valueRange = 0.005f..0.15f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = if (sel == 0) c.violet else c.mint,
                        activeTrackColor = if (sel == 0) c.violet else c.mint,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                    ),
                )
            }

            /* 1px nudge buttons */
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CalChip("◀", c.inkDim, false) { nudge(-1f, 0f) }
                CalChip("▶", c.inkDim, false) { nudge(1f, 0f) }
                CalChip("▲", c.inkDim, false) { nudge(0f, -1f) }
                CalChip("▼", c.inkDim, false) { nudge(0f, 1f) }
                Spacer(Modifier.weight(1f))
                Text(
                    "DRAG TO MOVE · SLIDER = SIZE",
                    style = monoStyle(7.5.sp, tracking = 0.75.sp, color = c.inkFaint),
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
private fun CalChip(text: String, tone: Color, active: Boolean, onClick: () -> Unit) {
    val c = Aiva.c
    Box(
        Modifier
            .background(if (active) tone.copy(alpha = 0.18f) else Color(0xE60E0F1C), RoundedCornerShape(7.dp))
            .border(1.dp, if (active) tone else c.line, RoundedCornerShape(7.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(text, style = monoStyle(9.sp, tracking = 0.9.sp, color = if (active) tone else c.inkDim), maxLines = 1)
    }
}
