package com.aiva.console.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.data.model.TaskItem
import com.aiva.console.ui.AivaIcons
import com.aiva.console.ui.Screen
import com.aiva.console.ui.components.Eyebrow
import com.aiva.console.ui.components.Gauge
import com.aiva.console.ui.components.HeaderLink
import com.aiva.console.ui.components.HudCard
import com.aiva.console.ui.components.Orb
import com.aiva.console.ui.components.PriBar
import com.aiva.console.ui.components.SectionHeader
import com.aiva.console.ui.components.TickBox
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.components.sansStyle
import com.aiva.console.ui.theme.Aiva
import com.aiva.console.ui.theme.Sans
import java.util.Calendar

data class ClockParts(val h: String, val m: String, val ap: String, val date: String, val greeting: String)

fun clockParts(now: Long): ClockParts {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h24 = cal.get(Calendar.HOUR_OF_DAY)
    val h = (h24 % 12).let { if (it == 0) 12 else it }
    val m = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    val ap = if (h24 < 12) "AM" else "PM"
    val days = listOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
    val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    val date = "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]}, ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"
    val greeting = when {
        h24 < 12 -> "Good morning,"
        h24 < 18 -> "Good afternoon,"
        else -> "Good evening,"
    }
    return ClockParts(h.toString(), m, ap, date, greeting)
}

@Composable
fun HomeScreen(
    snapshot: DashboardSnapshot,
    now: Long,
    aiState: String,
    onToggleTask: (TaskItem) -> Unit,
    onNav: (Screen) -> Unit,
    onFire: (String) -> Unit,
) {
    val c = Aiva.c
    val t = clockParts(now)
    val m = snapshot.metrics
    val alertCount = snapshot.alerts.size

    Column {
        /* hero — greeting + Aiva orb */
        HudCard(glow = true, showBracket = true, padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.greeting, style = sansStyle(19.sp, FontWeight.SemiBold, c.ink), maxLines = 1)
                    Text(snapshot.greetingName, style = sansStyle(19.sp, FontWeight.SemiBold, c.violet2), maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (aiState == "alert") "$alertCount ITEMS NEED ATTENTION" else "ALL SYSTEMS NOMINAL",
                        style = monoStyle(10.5.sp, FontWeight.Medium, 0.84.sp, c.inkFaint),
                        maxLines = 1,
                    )
                }
                Column(
                    // tapping Aiva herself opens the conversation
                    Modifier.clickable(
                        remember { MutableInteractionSource() },
                        indication = null,
                    ) { onNav(Screen.Chat) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Orb(58.dp, aiState)
                    Spacer(Modifier.height(8.dp))
                    Text("AIVA", style = monoStyle(8.sp, tracking = 1.28.sp, color = c.violet2))
                    Spacer(Modifier.height(5.dp))
                    Text(
                        when (aiState) {
                            "thinking" -> "THINKING…"
                            "alert" -> "$alertCount ALERTS"
                            else -> "ONLINE · LOCAL"
                        },
                        style = monoStyle(7.5.sp, tracking = 0.9.sp, color = c.inkFaint),
                    )
                }
            }
        }

        Spacer(Modifier.height(9.dp))

        /* big clock */
        HudCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${t.h}:${t.m}", style = monoStyle(30.sp, FontWeight.SemiBold, color = c.ink), maxLines = 1)
                        Text(" ${t.ap}", style = monoStyle(15.sp, FontWeight.SemiBold, color = c.inkDim), maxLines = 1)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(t.date, style = monoStyle(9.5.sp, FontWeight.Medium, 1.33.sp, c.inkFaint), maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("72°", style = monoStyle(13.sp, color = c.mint)) // placeholder weather (design mock)
                    Spacer(Modifier.height(5.dp))
                    Text("CLEAR", style = monoStyle(7.5.sp, tracking = 0.9.sp, color = c.inkFaint))
                }
            }
        }

        /* system health */
        SectionHeader("System health", trailing = { HeaderLink("Details") { onNav(Screen.Monitor) } })
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HealthGauge("CPU", m.cpuPct, c.violet, Modifier.weight(1f))
            HealthGauge("RAM", m.ramPct, c.mint, Modifier.weight(1f))
            HealthGauge("DISK", m.diskPct, c.blue, Modifier.weight(1f))
        }

        /* top tasks */
        val top = snapshot.topTasks.take(3)
        SectionHeader("Today · top ${top.size}", trailing = { HeaderLink("All") { onNav(Screen.Tasks) } })
        HudCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            top.forEachIndexed { i, task ->
                TaskRow(task, divider = i < top.lastIndex, onToggle = { onToggleTask(task) })
            }
            if (top.isEmpty()) {
                Text(
                    "NO TASKS TODAY",
                    Modifier.padding(vertical = 14.dp).align(Alignment.CenterHorizontally),
                    style = monoStyle(11.sp, FontWeight.Medium, color = c.inkFaint),
                )
            }
        }

        /* agenda preview */
        if (snapshot.agenda.isNotEmpty()) {
            SectionHeader("Agenda")
            HudCard {
                val preview = snapshot.agenda.take(3)
                preview.forEachIndexed { i, a ->
                    Row(Modifier.padding(vertical = 7.dp)) {
                        Text(a.time, Modifier.width(46.dp), style = monoStyle(10.sp, tracking = 0.4.sp, color = c.inkDim))
                        Column(Modifier.width(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .padding(top = 3.dp)
                                    .size(8.dp)
                                    .drawBehind {
                                        if (a.now) {
                                            drawCircle(c.violet.copy(alpha = 0.5f), radius = size.minDimension)
                                            drawCircle(c.violet)
                                        } else {
                                            drawCircle(c.violet, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                                        }
                                    },
                            )
                            if (i < preview.lastIndex) {
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .width(1.5.dp)
                                        .height(20.dp)
                                        .drawBehind { drawRect(c.line) },
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(a.title, style = sansStyle(12.sp, color = c.ink), maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            Text(a.meta, style = monoStyle(9.5.sp, FontWeight.Medium, 0.48.sp, c.inkFaint), maxLines = 1)
                        }
                    }
                }
            }
        }

        /* quick actions */
        SectionHeader("Quick actions", trailing = { HeaderLink("More") { onNav(Screen.Actions) } })
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickAction(AivaIcons.Focus, "Focus", Modifier.weight(1f)) { onFire("focus") }
            QuickAction(AivaIcons.Note, "Note", Modifier.weight(1f)) { onFire("note") }
            QuickAction(AivaIcons.Chat, "Chat", Modifier.weight(1f)) { onNav(Screen.Chat) }
            QuickAction(AivaIcons.Pulse, "Monitor", Modifier.weight(1f)) { onNav(Screen.Monitor) }
        }
    }
}

@Composable
private fun HealthGauge(label: String, value: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val c = Aiva.c
    HudCard(modifier, padding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Gauge(value, 50.dp, color)
                Text("${value.toInt()}", style = monoStyle(13.sp, color = c.ink))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = monoStyle(8.sp, tracking = 1.28.sp, color = c.inkFaint))
        }
    }
}

@Composable
fun TaskRow(task: TaskItem, divider: Boolean, onToggle: () -> Unit) {
    val c = Aiva.c
    val done = task.status == "done"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
            .drawBehind {
                if (divider) {
                    drawLine(c.lineSoft, Offset(0f, size.height + 9.dp.toPx()), Offset(size.width, size.height + 9.dp.toPx()), 1.dp.toPx())
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PriBar(task.priority, 19.dp)
        TickBox(done, onToggle)
        Text(
            task.title,
            Modifier.weight(1f),
            style = sansStyle(12.5.sp, color = if (done) c.inkFaint else c.ink).copy(
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 1,
        )
        Text(task.due ?: "", style = monoStyle(9.5.sp, color = c.inkFaint), maxLines = 1)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Aiva.c
    HudCard(
        modifier
            .aspectRatio(1f)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        padding = PaddingValues(0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = c.inkDim)
            Spacer(Modifier.height(7.dp))
            Text(label.uppercase(), style = monoStyle(8.sp, tracking = 0.8.sp, color = c.inkDim), maxLines = 1)
        }
    }
}
