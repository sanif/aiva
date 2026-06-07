package com.aiva.console.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiva.console.ui.components.BayData
import com.aiva.console.ui.components.HudToast
import com.aiva.console.ui.components.NavRail
import com.aiva.console.ui.components.Scanlines
import com.aiva.console.ui.components.StripPager
import com.aiva.console.ui.components.SystemDock
import com.aiva.console.ui.components.TopBar
import com.aiva.console.ui.components.encodeLensCal
import com.aiva.console.ui.components.parseLensCal
import com.aiva.console.ui.screens.ActionsScreen
import com.aiva.console.ui.screens.AmbientOverlay
import com.aiva.console.ui.screens.CalibrationScreen
import com.aiva.console.ui.screens.ChatScreen
import com.aiva.console.ui.screens.FocusOverlay
import com.aiva.console.ui.screens.HomeScreen
import com.aiva.console.ui.screens.MonitorScreen
import com.aiva.console.ui.screens.NotesScreen
import com.aiva.console.ui.screens.SettingsScreen
import com.aiva.console.ui.screens.TasksScreen
import com.aiva.console.ui.theme.Aiva
import com.aiva.console.viewmodel.AppViewModel
import kotlinx.coroutines.delay

enum class Screen { Home, Monitor, Tasks, Chat, Actions, Settings, Notes }

private val TITLES = mapOf(
    Screen.Home to "AIVA",
    Screen.Monitor to "MONITOR",
    Screen.Tasks to "TASKS",
    Screen.Chat to "ASSISTANT",
    Screen.Actions to "ACTIONS",
    Screen.Settings to "SETTINGS",
    Screen.Notes to "NOTES",
)

@Composable
fun AivaApp(vm: AppViewModel) {
    val c = Aiva.c
    val screen by vm.screen.collectAsState()
    val snapshot by vm.snapshot.collectAsState()
    val settings by vm.settings.collectAsState()
    val conn by vm.conn.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val aiState by vm.aiState.collectAsState()
    val ambient by vm.ambient.collectAsState()
    val focus by vm.focus.collectAsState()
    val focusTask by vm.focusTask.collectAsState()
    val toast by vm.toast.collectAsState()
    val calibrate by vm.calibrate.collectAsState()
    val flareAt by vm.flareAt.collectAsState()
    val focusEndAt by vm.focusEndAt.collectAsState()
    val messages by vm.messages.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val testState by vm.testState.collectAsState()

    // 1s clock tick
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    KeepScreenOn(enabled = settings.kiosk)
    val battery = rememberBatteryPct()
    val context = LocalContext.current

    /* ---- device effects requested by the ViewModel ---- */

    // open_url actions → browser
    val openUrl by vm.openUrl.collectAsState()
    LaunchedEffect(openUrl) {
        openUrl?.let { url ->
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { vm.notify("No browser available") }
            vm.openUrl.value = null
        }
    }

    // voice input (chat / voice note) → system speech recognizer
    val voiceTarget by vm.voiceRequest.collectAsState()
    var pendingVoice by remember { mutableStateOf<AppViewModel.VoiceTarget?>(null) }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        pendingVoice?.let { vm.onVoiceResult(it, text) }
        pendingVoice = null
    }
    LaunchedEffect(voiceTarget) {
        val target = voiceTarget ?: return@LaunchedEffect
        pendingVoice = target
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (target == AppViewModel.VoiceTarget.NOTE) "Speak your note…" else "Message Aiva…",
            )
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure {
                pendingVoice = null
                vm.voiceRequest.value = null
                vm.notify("Voice input unavailable on this device")
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .drawBehind {
                // ambient accent washes — violet top-left, mint bottom-right
                drawRect(
                    Brush.radialGradient(
                        listOf(c.violet.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.15f, 0f),
                        radius = size.width * 0.9f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(c.mint.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.width * 0.8f,
                    )
                )
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            if (settings.dockDesign == "rail") {
                NavRail(
                    current = screen,
                    onSelect = vm::nav,
                    collapsed = settings.railCollapsed,
                    onToggleCollapse = {
                        vm.updateSettings(settings.copy(railCollapsed = !settings.railCollapsed))
                    },
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(
                title = TITLES[screen] ?: "AIVA",
                batteryPct = battery,
                onActions = { vm.nav(Screen.Actions) },
                onSettings = { vm.nav(Screen.Settings) },
                onAmbient = { vm.ambient.value = true },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val bodyPad = Modifier
                    .fillMaxSize()
                    .padding(start = 13.dp, end = 13.dp, top = 13.dp, bottom = 12.dp)
                when (screen) {
                    Screen.Chat -> Box(bodyPad) {
                        ChatScreen(
                            messages, streaming,
                            onSend = vm::sendChat,
                            onVoice = { vm.voiceRequest.value = AppViewModel.VoiceTarget.CHAT },
                            listening = voiceTarget == AppViewModel.VoiceTarget.CHAT,
                            onResolveSuggestion = vm::resolveSuggestion,
                            onCopied = { vm.notify("Copied to clipboard") },
                            tools = vm.tools.collectAsState().value,
                        )
                    }
                    else -> Column(bodyPad.verticalScroll(rememberScrollState(), reverseScrolling = false)) {
                        when (screen) {
                            Screen.Home -> HomeScreen(
                                snapshot = snapshot, now = now, aiState = aiState,
                                onToggleTask = vm::toggleTask, onNav = vm::nav, onFire = vm::fireById,
                            )
                            Screen.Monitor -> MonitorScreen(snapshot)
                            Screen.Tasks -> TasksScreen(
                                tasks,
                                onToggle = vm::toggleTask,
                                onAdd = vm::addTask,
                                onDelete = vm::deleteTask,
                            )
                            Screen.Notes -> {
                                val notes by vm.notes.collectAsState()
                                LaunchedEffect(Unit) { vm.refreshNotes() }
                                NotesScreen(
                                    notes = notes,
                                    onAdd = { vm.addNote(it) },
                                    onVoice = { vm.voiceRequest.value = AppViewModel.VoiceTarget.NOTE },
                                )
                            }
                            Screen.Actions -> ActionsScreen(onFire = vm::fire)
                            Screen.Settings -> SettingsScreen(
                                settings = settings, conn = conn, testState = testState,
                                onUpdate = vm::updateSettings, onTest = vm::testConnection,
                                onCalibrate = { vm.calibrate.value = true },
                            )
                            else -> {}
                        }
                    }
                }
            }

            SystemDock(
                current = screen,
                onSelect = vm::nav,
                lensCal = remember(settings.lensCal) { parseLensCal(settings.lensCal) },
                lensStyle = settings.lensStyle,
                bay = BayData(
                    aiState = aiState,
                    cpu = snapshot.metrics.cpuPct,
                    ram = snapshot.metrics.ramPct,
                    disk = snapshot.metrics.diskPct,
                    serviceStates = snapshot.services.map { it.status },
                    alertCount = snapshot.alerts.size,
                ),
                flareAt = flareAt,
                design = settings.dockDesign,
                strip = {
                        StripPager(
                            snapshot = snapshot,
                            streaming = streaming,
                            aiSnippet = messages.lastOrNull { !it.fromMe }?.text,
                            focusEndAt = focusEndAt,
                            now = now,
                            onNav = vm::nav,
                            onAction = vm::fireById,
                            onAmbient = { vm.ambient.value = true },
                            onOpenFocus = { vm.focus.value = true },
                            onFocusExpired = vm::endFocus,
                        )
                },
            )
            }
        }

        // CRT scanline ambience
        Scanlines(Modifier.fillMaxSize())

        // toast — always ABOVE the lens band (computed from calibration)
        val view = androidx.compose.ui.platform.LocalView.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        val toastPad = run {
            val cal = parseLensCal(settings.lensCal)
            if (cal.isNotEmpty() && view.height > 0) {
                val lensTopPx = cal.minOf { it.cy * view.height - it.r * view.width }
                with(density) { (view.height - lensTopPx).toDp() } + 14.dp
            } else 110.dp
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = toastPad)) {
            HudToast(toast)
        }

        // overlays
        if (ambient) {
            AmbientOverlay(
                snapshot = snapshot,
                batteryPct = battery,
                lensCal = parseLensCal(settings.lensCal),
                styleIndex = settings.ambientStyle,
                colorIndex = settings.ambientColor,
                onStyleChange = { i ->
                    if (i != settings.ambientStyle) vm.updateSettings(settings.copy(ambientStyle = i))
                },
                onColorChange = { i ->
                    if (i != settings.ambientColor) vm.updateSettings(settings.copy(ambientColor = i))
                },
                onExit = { vm.ambient.value = false },
            )
        }
        if (focus && focusEndAt != null) {
            FocusOverlay(
                focusTaskTitle = focusTask,
                endAt = focusEndAt!!,
                onMinimize = { vm.focus.value = false },
                onEnd = vm::endFocus,
            )
        }
        if (calibrate) {
            CalibrationScreen(
                initial = parseLensCal(settings.lensCal),
                onSave = { circles ->
                    vm.updateSettings(settings.copy(lensCal = encodeLensCal(circles)))
                    vm.calibrate.value = false
                },
                onClose = { vm.calibrate.value = false },
            )
        }
    }
}

/** FLAG_KEEP_SCREEN_ON while kiosk mode is enabled. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? ComponentActivity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Live battery percentage from ACTION_BATTERY_CHANGED sticky broadcast. */
@Composable
private fun rememberBatteryPct(): Int? {
    val context = LocalContext.current
    val level = remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val lvl = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (lvl >= 0 && scale > 0) level.intValue = lvl * 100 / scale
            }
        }
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return level.intValue.takeIf { it >= 0 }
}
