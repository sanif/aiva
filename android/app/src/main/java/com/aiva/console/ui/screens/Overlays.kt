package com.aiva.console.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.data.model.TaskItem
import com.aiva.console.ui.components.HudButton
import com.aiva.console.ui.components.Orb
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.components.sansStyle
import com.aiva.console.ui.theme.Aiva
import kotlinx.coroutines.delay

/* AmbientOverlay now lives in AmbientClocks.kt — five swipeable always-on styles. */

/* ============================ FOCUS MODE ============================ */

@Composable
fun FocusOverlay(
    focusTaskTitle: String?,
    endAt: Long,
    onMinimize: () -> Unit,
    onEnd: () -> Unit,
) {
    val c = Aiva.c
    val total = 45 * 60
    var sec by remember { mutableIntStateOf(((endAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)) }
    LaunchedEffect(endAt) {
        while (true) {
            sec = ((endAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            if (sec <= 0) break
            delay(1000)
        }
    }
    val mm = (sec / 60).toString().padStart(2, '0')
    val ss = (sec % 60).toString().padStart(2, '0')
    val pct = sec.toFloat() / total

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF0D0A25), Color(0xFF050509)),
                    radius = 900f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // minimize — keep the timer running, return to the dashboard (strip shows countdown)
        Text(
            "MINIMIZE",
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onMinimize),
            style = monoStyle(9.sp, FontWeight.SemiBold, 1.2.sp, c.inkFaint),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val sw = 3.dp.toPx()
                            val inset = sw / 2 + 4.dp.toPx()
                            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                            drawArc(Color.White.copy(alpha = 0.07f), 0f, 360f, false, tl, arcSize, style = Stroke(sw))
                            drawArc(
                                c.mint.copy(alpha = 0.35f), -90f, pct * 360f, false, tl, arcSize,
                                style = Stroke(sw * 2.4f, cap = StrokeCap.Round),
                            )
                            drawArc(
                                c.mint, -90f, pct * 360f, false, tl, arcSize,
                                style = Stroke(sw, cap = StrokeCap.Round),
                            )
                        },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Orb(40.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("$mm:$ss", style = monoStyle(44.sp, color = c.ink), maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Text("FOCUS MODE", style = monoStyle(9.sp, tracking = 2.16.sp, color = c.mint), maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Notifications muted${focusTaskTitle?.let { " · $it" } ?: ""}",
                style = sansStyle(11.sp, color = c.inkDim),
                maxLines = 1,
            )
            Spacer(Modifier.height(26.dp))
            HudButton("End session", Modifier.width(160.dp), onClick = onEnd)
        }
    }
}
