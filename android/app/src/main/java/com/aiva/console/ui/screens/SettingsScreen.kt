package com.aiva.console.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.data.model.ConnState
import com.aiva.console.data.settings.AivaSettings
import com.aiva.console.ui.AivaIcons
import com.aiva.console.ui.components.HudButton
import com.aiva.console.ui.components.HudCard
import com.aiva.console.ui.components.HudChip
import com.aiva.console.ui.components.HudToggle
import com.aiva.console.ui.components.SectionHeader
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.components.sansStyle
import com.aiva.console.ui.theme.ACCENTS
import com.aiva.console.ui.theme.Aiva

sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data class Ok(val ms: Long) : TestState
    data class Fail(val reason: String) : TestState
}

@Composable
fun SettingsScreen(
    settings: AivaSettings,
    conn: ConnState,
    testState: TestState,
    onUpdate: (AivaSettings) -> Unit,
    onTest: () -> Unit,
    onCalibrate: () -> Unit = {},
) {
    val c = Aiva.c
    var url by remember(settings.backendUrl) { mutableStateOf(settings.backendUrl) }
    var token by remember(settings.token) { mutableStateOf(settings.token) }

    Column {
        /* connection */
        SectionHeader(
            "Connection", first = true,
            trailing = {
                when {
                    settings.mockMode -> HudChip("MOCK DATA", dotColor = c.ai)
                    conn == ConnState.CONNECTED -> HudChip("WS LINKED", dotColor = c.ok)
                    conn == ConnState.CONNECTING -> HudChip("LINKING…", dotColor = c.warn)
                    else -> HudChip("OFFLINE", dotColor = c.err)
                }
            },
        )
        HudCard {
            FieldLabel("BACKEND SERVER URL")
            SettingsField(url, { url = it }, onCommit = { onUpdate(settings.copy(backendUrl = url.trim())) })
            Spacer(Modifier.height(11.dp))
            FieldLabel("AUTH TOKEN")
            SettingsField(token, { token = it }, onCommit = { onUpdate(settings.copy(token = token.trim())) })
            Spacer(Modifier.height(11.dp))

            HudButton(
                text = when (testState) {
                    is TestState.Testing -> "TESTING…"
                    is TestState.Ok -> "CONNECTED · ${testState.ms}ms"
                    is TestState.Fail -> "FAILED — RETRY"
                    else -> "Test connection"
                },
                primary = true,
                leading = when (testState) {
                    is TestState.Testing -> ({ Spinner() })
                    is TestState.Ok -> ({ Icon(AivaIcons.Check, null, Modifier.size(14.dp), tint = Color.White) })
                    else -> ({ Icon(AivaIcons.Wifi, null, Modifier.size(15.dp), tint = Color.White) })
                },
                onClick = {
                    onUpdate(settings.copy(backendUrl = url.trim(), token = token.trim()))
                    onTest()
                },
            )

            Spacer(Modifier.height(11.dp))
            SetRow("Mock data mode", "DEMO WITHOUT A BACKEND", last = true) {
                HudToggle(settings.mockMode) { onUpdate(settings.copy(mockMode = !settings.mockMode)) }
            }
        }

        /* display & theme */
        SectionHeader("Display & theme")
        HudCard(padding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
            SetRow("Accent", "HUD COLOR PAIR") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ACCENTS.forEachIndexed { i, a ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(
                                    Brush.linearGradient(listOf(a.violet, a.mint)),
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    2.dp,
                                    if (settings.accentIndex == i) Color.White else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable(remember { MutableInteractionSource() }, indication = null) {
                                    onUpdate(settings.copy(accentIndex = i))
                                },
                        )
                    }
                }
            }
            SetRow("Kiosk / always-on", "KEEP SCREEN AWAKE WHILE CHARGING") {
                HudToggle(settings.kiosk) { onUpdate(settings.copy(kiosk = !settings.kiosk)) }
            }
            SetRow("Lens style", "CAMERA CLUSTER TREATMENT") {
                ChipChoices(
                    options = listOf("eyes" to "EYES", "gauges" to "GAUGE", "radar" to "RADAR", "hangar" to "BAY"),
                    selected = settings.lensStyle,
                ) { onUpdate(settings.copy(lensStyle = it)) }
            }
            SetRow("Dock design", if (settings.dockDesign == "rail") "RAIL + SWIPE STRIP" else "WAVE BAND + TABS") {
                ChipChoices(
                    options = listOf("wave" to "WAVE", "rail" to "RAIL"),
                    selected = settings.dockDesign,
                ) { onUpdate(settings.copy(dockDesign = it)) }
            }
            SetRow(
                "Lens calibration",
                if (settings.lensCal.isBlank()) "ALIGN RINGS TO THE CAMERAS" else "CALIBRATED · TAP TO ADJUST",
            ) {
                Box(
                    Modifier
                        .background(c.panel2, RoundedCornerShape(8.dp))
                        .border(1.dp, c.violet.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCalibrate)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("OPEN", style = monoStyle(9.sp, tracking = 0.9.sp, color = c.violet2))
                }
            }
            SetRow("Refresh interval", "LIVE PUSH EVERY ${settings.refreshSec}s", last = true) {
                Slider(
                    value = settings.refreshSec.toFloat(),
                    onValueChange = { onUpdate(settings.copy(refreshSec = it.toInt().coerceIn(1, 10))) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.width(120.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = c.violet,
                        activeTrackColor = c.violet,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                    ),
                )
            }
        }

        /* security — informational, mirrors backend posture */
        SectionHeader("Security")
        HudCard(padding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
            SetRow("Local network only", "TOKEN-GATED LAN API") { HudToggle(true) {} }
            SetRow("Action allowlist", "${QUICK_ACTIONS.size} SAFE COMMANDS") { HudToggle(true) {} }
            SetRow("Hide sensitive logs", "REDACT BY DEFAULT", last = true) { HudToggle(true) {} }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "AIVA · v0.3.0 · OUTER DISPLAY",
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            style = monoStyle(8.sp, tracking = 1.6.sp, color = c.inkFaint),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Compact chip selector used by the style/layout/shape rows. */
@Composable
private fun ChipChoices(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = Aiva.c
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        options.forEach { (id, label) ->
            val on = selected == id
            Box(
                Modifier
                    .background(if (on) c.violet.copy(alpha = 0.16f) else c.panel2, RoundedCornerShape(7.dp))
                    .border(1.dp, if (on) c.violet else c.line, RoundedCornerShape(7.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(id) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Text(label, style = monoStyle(8.sp, tracking = 0.3.sp, color = if (on) c.ink else c.inkFaint), maxLines = 1)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = monoStyle(9.sp, tracking = 0.45.sp, color = Aiva.c.inkFaint))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SettingsField(value: String, onChange: (String) -> Unit, onCommit: () -> Unit) {
    val c = Aiva.c
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(c.panel2, RoundedCornerShape(8.dp))
            .border(1.dp, if (focused) c.violet.copy(alpha = 0.5f) else c.line, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = monoStyle(11.sp, color = c.ink),
            cursorBrush = SolidColor(c.violet),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { st ->
                    if (focused && !st.isFocused) onCommit()
                    focused = st.isFocused
                },
        )
    }
}

@Composable
private fun SetRow(
    title: String,
    sub: String,
    last: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    val c = Aiva.c
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .drawBehind {
                if (!last) {
                    drawLine(c.lineSoft, Offset(0f, size.height + 12.dp.toPx()), Offset(size.width, size.height + 12.dp.toPx()), 1.dp.toPx())
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = sansStyle(12.sp, color = c.ink))
            Spacer(Modifier.height(4.dp))
            Text(sub.uppercase(), style = monoStyle(9.sp, tracking = 0.45.sp, color = c.inkFaint), maxLines = 1)
        }
        trailing()
    }
}

@Composable
private fun Spinner() {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "angle")
    Box(
        Modifier
            .size(13.dp)
            .rotate(angle)
            .drawBehind {
                drawArc(
                    Color.White.copy(alpha = 0.3f), 0f, 360f, false,
                    style = Stroke(2.dp.toPx()),
                )
                drawArc(
                    Color.White, -90f, 90f, false,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            },
    )
}
