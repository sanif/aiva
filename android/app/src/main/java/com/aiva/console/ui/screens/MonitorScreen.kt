package com.aiva.console.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.data.model.Alert
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.ui.AivaIcons
import com.aiva.console.ui.components.Eyebrow
import com.aiva.console.ui.components.Gauge
import com.aiva.console.ui.components.HudCard
import com.aiva.console.ui.components.HudChip
import com.aiva.console.ui.components.SectionHeader
import com.aiva.console.ui.components.Sparkline
import com.aiva.console.ui.components.StatusDot
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.components.sansStyle
import com.aiva.console.ui.components.statusColor
import com.aiva.console.ui.theme.Aiva

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonitorScreen(snapshot: DashboardSnapshot) {
    val c = Aiva.c
    val m = snapshot.metrics

    Column {
        SectionHeader("System monitor", first = true, trailing = { HudChip("LIVE", dotColor = c.ok) })

        /* gauges 2x2 */
        val temp = m.tempC
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GaugeCard("CPU", "${m.cpuPct.toInt()}", "%", m.cpuPct, c.violet, AivaIcons.Cpu, Modifier.weight(1f))
            GaugeCard("MEMORY", "${m.ramPct.toInt()}", "%", m.ramPct, c.mint, AivaIcons.Ram, Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GaugeCard("DISK", "${m.diskPct.toInt()}", "%", m.diskPct, c.blue, AivaIcons.Disk, Modifier.weight(1f))
            GaugeCard(
                "TEMP",
                temp?.toInt()?.toString() ?: "—", "°C",
                (temp ?: 0f) / 85f * 100f,
                if ((temp ?: 0f) > 62f) c.warn else c.mint,
                AivaIcons.Thermo,
                Modifier.weight(1f),
            )
        }

        /* network */
        SectionHeader("Network")
        HudCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("↓ DOWN", style = monoStyle(8.5.sp, tracking = 1.36.sp, color = c.inkFaint))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.1f".format(m.netDownMbps), style = monoStyle(24.sp, color = c.mint), maxLines = 1)
                        Text(" Mb/s", style = monoStyle(11.sp, FontWeight.Medium, color = c.inkDim))
                    }
                }
                Spacer(Modifier.weight(1f))
                Sparkline(m.netHistory, c.mint, modifier = Modifier.size(width = 110.dp, height = 40.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("↑ UP", style = monoStyle(8.5.sp, tracking = 1.36.sp, color = c.inkFaint))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.1f".format(m.netUpMbps), style = monoStyle(24.sp, color = c.violet2), maxLines = 1)
                        Text(" Mb/s", style = monoStyle(11.sp, FontWeight.Medium, color = c.inkDim))
                    }
                }
            }
        }

        /* cpu history */
        Spacer(Modifier.height(9.dp))
        HudCard {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow("CPU · 60s history", color = c.inkDim)
                Text("${m.cpuPct.toInt()}%", style = monoStyle(11.sp, color = c.violet2))
            }
            Sparkline(m.cpuHistory, c.violet2, modifier = Modifier.fillMaxWidth().height(46.dp))
        }

        /* services */
        val upCount = snapshot.services.count { it.status == "up" }
        SectionHeader(
            "Local services",
            trailing = {
                Text("$upCount/${snapshot.services.size} UP", style = monoStyle(9.sp, tracking = 0.72.sp, color = c.inkFaint))
            },
        )
        HudCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            snapshot.services.forEachIndexed { i, s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .drawBehind {
                            if (i < snapshot.services.lastIndex) {
                                drawLine(c.lineSoft, Offset(0f, size.height + 8.dp.toPx()), Offset(size.width, size.height + 8.dp.toPx()), 1.dp.toPx())
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    StatusDot(statusColor(s.status), 7.dp)
                    Text(s.name, Modifier.weight(1f), style = sansStyle(11.5.sp, color = c.ink), maxLines = 1)
                    Text(s.target, style = monoStyle(9.5.sp, FontWeight.Medium, color = c.inkFaint), maxLines = 1)
                    StatusBadge(s.status)
                }
            }
            if (snapshot.services.isEmpty()) {
                Text(
                    "NO SERVICES CONFIGURED",
                    Modifier.padding(vertical = 14.dp).align(Alignment.CenterHorizontally),
                    style = monoStyle(10.sp, FontWeight.Medium, color = c.inkFaint),
                )
            }
        }

        /* docker */
        SectionHeader(
            "Docker",
            trailing = {
                Text(
                    if (snapshot.docker.available) "${snapshot.docker.running}/${snapshot.docker.total} RUNNING" else "UNAVAILABLE",
                    style = monoStyle(9.sp, tracking = 0.72.sp, color = c.inkFaint),
                )
            },
        )
        HudCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                snapshot.docker.containers.forEach { d ->
                    HudChip(d.name, dotColor = if (d.status == "up") c.ok else c.err)
                }
                if (snapshot.docker.containers.isEmpty()) {
                    Text(
                        if (snapshot.docker.available) "NO CONTAINERS" else "DOCKER NOT DETECTED",
                        style = monoStyle(10.sp, FontWeight.Medium, color = c.inkFaint),
                    )
                }
            }
        }

        /* alerts */
        SectionHeader(
            "Alerts",
            tickColor = c.warn,
            trailing = {
                Text("${snapshot.alerts.size} ACTIVE", style = monoStyle(9.sp, color = c.inkFaint))
            },
        )
        snapshot.alerts.forEach { AlertRow(it); Spacer(Modifier.height(7.dp)) }
        if (snapshot.alerts.isEmpty()) {
            HudCard {
                Text(
                    "NO ACTIVE ALERTS",
                    Modifier.padding(vertical = 6.dp).align(Alignment.CenterHorizontally),
                    style = monoStyle(10.sp, FontWeight.Medium, color = c.inkFaint),
                )
            }
        }
    }
}

@Composable
private fun GaugeCard(
    label: String,
    valueText: String,
    unit: String,
    gaugeValue: Float,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val c = Aiva.c
    HudCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Gauge(gaugeValue, 50.dp, color)
                Icon(icon, null, Modifier.size(15.dp), tint = c.inkDim)
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(valueText, style = monoStyle(19.sp, color = c.ink), maxLines = 1)
                    Text(" $unit", style = monoStyle(11.sp, FontWeight.Medium, color = c.inkDim))
                }
                Spacer(Modifier.height(5.dp))
                Text(label, style = monoStyle(8.5.sp, tracking = 1.36.sp, color = c.inkFaint), maxLines = 1)
            }
        }
    }
}

/** UP / WARN / DOWN status pill. */
@Composable
private fun StatusBadge(status: String) {
    val tone = statusColor(status)
    Box(
        Modifier
            .background(tone.copy(alpha = 0.11f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            when (status) { "up" -> "UP"; "warn" -> "WARN"; else -> "DOWN" },
            style = monoStyle(8.5.sp, tracking = 0.85.sp, color = tone),
            maxLines = 1,
        )
    }
}

@Composable
fun AlertRow(alert: Alert) {
    val c = Aiva.c
    val err = alert.level == "err"
    val tone = if (err) c.err else c.warn
    Row(
        Modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .border(1.dp, tone.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(if (err) AivaIcons.Bolt else AivaIcons.Bell, null, Modifier.size(16.dp), tint = tone)
        Column {
            Text(alert.title, style = sansStyle(11.sp, color = c.ink))
            Spacer(Modifier.height(4.dp))
            Text(alert.meta, style = monoStyle(9.sp, FontWeight.Medium, 0.45.sp, c.inkFaint), maxLines = 1)
        }
    }
}
