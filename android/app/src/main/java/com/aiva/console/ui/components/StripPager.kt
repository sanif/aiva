package com.aiva.console.ui.components

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.ui.Screen
import com.aiva.console.ui.theme.Aiva
import java.util.Calendar

/**
 * The rail-mode bottom strip: a horizontally swipeable pager of glanceable
 * modules — ① Next up ② Telemetry ③ Quick chips ④ Aiva. A running focus
 * timer takes over the whole strip until it ends or is reopened.
 */
@Composable
fun StripPager(
    snapshot: DashboardSnapshot,
    streaming: Boolean,
    aiSnippet: String?,
    focusEndAt: Long?,
    now: Long,
    onNav: (Screen) -> Unit,
    onAction: (String) -> Unit,
    onAmbient: () -> Unit,
    onOpenFocus: () -> Unit,
    onFocusExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Aiva.c
    val remain = focusEndAt?.let { it - now }

    if (focusEndAt != null && (remain == null || remain <= 0)) {
        LaunchedEffect(focusEndAt) { onFocusExpired() }
    }

    if (remain != null && remain > 0) {
        FocusModule(remain, onOpenFocus, modifier)
        return
    }

    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    Column(modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> NextUpModule(snapshot, now, Modifier.fillMaxSize().tap { onNav(Screen.Tasks) })
                1 -> TickerModule(snapshot, Modifier.fillMaxSize().tap { onNav(Screen.Monitor) })
                2 -> ChipsModule(onAction, onAmbient, Modifier.fillMaxSize())
                3 -> AivaModule(snapshot.aiStatus, streaming, aiSnippet, Modifier.fillMaxSize().tap { onNav(Screen.Chat) })
            }
        }
        // page dots
        Row(
            Modifier.fillMaxWidth().padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (pagerState.currentPage == i) 5.dp else 4.dp)
                        .background(
                            if (pagerState.currentPage == i) c.violet else Color(0xFF3A3B58),
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)

/* ---- ① next up ---- */

internal fun minutesUntil(hhmm: String?, now: Long): Long? = runCatching {
    val parts = (hhmm ?: return null).split(":")
    if (parts.size != 2) return null
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        set(Calendar.MINUTE, parts[1].toInt())
        set(Calendar.SECOND, 0)
    }
    (cal.timeInMillis - now) / 60_000
}.getOrNull()?.takeIf { it in 0..720 }

@Composable
private fun NextUpModule(snapshot: DashboardSnapshot, now: Long, modifier: Modifier = Modifier) {
    val c = Aiva.c
    val agendaNext = snapshot.agenda.firstOrNull { it.now } ?: snapshot.agenda.firstOrNull()
    val task = snapshot.topTasks.firstOrNull { it.status == "today" }
    val title = agendaNext?.title ?: task?.title ?: "All clear"
    val meta = agendaNext?.meta ?: task?.let { "${it.category} · ${it.priority}".uppercase() }
    val time = agendaNext?.time ?: task?.due
    val inMin = minutesUntil(time, now)

    Column(
        modifier.padding(start = 14.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.size(9.dp).drawBehind {
                    drawCircle(c.violet, style = Stroke(2.dp.toPx()))
                },
            )
            Text(
                buildString {
                    append("NEXT")
                    if (time != null) append(" · $time")
                    if (inMin != null) append(" · IN ${inMin}M")
                },
                style = monoStyle(9.sp, tracking = 0.9.sp, color = c.inkFaint),
                maxLines = 1,
            )
        }
        Text(
            title,
            style = sansStyle(13.sp, FontWeight.Medium, c.ink),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta != null) {
            Text(meta, style = monoStyle(9.sp, FontWeight.Medium, 0.5.sp, c.inkFaint), maxLines = 1)
        }
    }
}

/* ---- ② telemetry ticker ---- */

@Composable
private fun TickerModule(snapshot: DashboardSnapshot, modifier: Modifier = Modifier) {
    val c = Aiva.c
    val m = snapshot.metrics
    Row(
        modifier.padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CPU ${m.cpuPct.toInt()}%", style = monoStyle(11.sp, color = c.violet2), maxLines = 1)
                Text("RAM ${m.ramPct.toInt()}%", style = monoStyle(11.sp, color = c.mint), maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("↓ %.1f Mb/s".format(m.netDownMbps), style = monoStyle(11.sp, color = c.blue), maxLines = 1)
                if (snapshot.alerts.isNotEmpty()) {
                    Text("${snapshot.alerts.size} ALERTS", style = monoStyle(11.sp, color = c.warn), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Sparkline(m.cpuHistory, c.violet2, modifier = Modifier.size(width = 90.dp, height = 36.dp))
    }
}

/* ---- ③ quick chips ---- */

@Composable
private fun ChipsModule(onAction: (String) -> Unit, onAmbient: () -> Unit, modifier: Modifier = Modifier) {
    val c = Aiva.c
    Row(
        modifier.padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StripChip("FOCUS", c.violet2) { onAction("focus") }
        StripChip("NOTE", c.mint) { onAction("note") }
        StripChip("DIM", c.inkDim) { onAmbient() }
    }
}

@Composable
private fun StripChip(text: String, tone: Color, onClick: () -> Unit) {
    val c = Aiva.c
    Box(
        Modifier
            .background(tone.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    tone.copy(alpha = 0.45f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(text, style = monoStyle(9.sp, tracking = 0.9.sp, color = tone), maxLines = 1)
    }
}

/* ---- ④ aiva ---- */

@Composable
private fun AivaModule(aiState: String, streaming: Boolean, snippet: String?, modifier: Modifier = Modifier) {
    val c = Aiva.c
    Row(
        modifier.padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Orb(28.dp, if (streaming) "thinking" else aiState)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                if (streaming) "AIVA · THINKING…" else "AIVA · ONLINE",
                style = monoStyle(9.5.sp, tracking = 1.0.sp, color = c.violet2),
                maxLines = 1,
            )
            Text(
                snippet ?: "Tap to chat",
                style = monoStyle(9.sp, FontWeight.Medium, color = c.inkFaint),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (streaming) Waveform(bars = 8, color = c.mint, height = 12.dp)
    }
}

/* ---- focus takeover ---- */

@Composable
private fun FocusModule(remainMs: Long, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = Aiva.c
    val totalSec = (remainMs / 1000).coerceAtLeast(0)
    val mm = (totalSec / 60).toString().padStart(2, '0')
    val ss = (totalSec % 60).toString().padStart(2, '0')
    Row(
        modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(20.dp).drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f), style = Stroke(2.5.dp.toPx()))
                drawArc(
                    c.mint, -90f, (totalSec / (45f * 60f)).coerceIn(0f, 1f) * 360f, false,
                    style = Stroke(2.5.dp.toPx()),
                )
            },
        )
        Text("$mm:$ss", style = monoStyle(16.sp, color = c.ink), maxLines = 1)
        Column {
            Text("FOCUS MODE", style = monoStyle(8.5.sp, tracking = 1.3.sp, color = c.mint), maxLines = 1)
            Text("TAP TO OPEN", style = monoStyle(7.5.sp, tracking = 0.9.sp, color = c.inkFaint), maxLines = 1)
        }
    }
}
