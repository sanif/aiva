package com.aiva.console.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiva.console.data.model.ChatEvent
import com.aiva.console.data.model.ConnState
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.data.model.NoteItem
import com.aiva.console.data.model.TaskItem
import com.aiva.console.data.repo.AivaRepository
import com.aiva.console.data.repo.MockRepository
import com.aiva.console.data.repo.RemoteRepository
import com.aiva.console.data.settings.AivaSettings
import com.aiva.console.data.settings.SettingsStore
import com.aiva.console.ui.Screen
import com.aiva.console.ui.screens.QuickActionDef
import com.aiva.console.ui.screens.TestState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatMessage(
    val fromMe: Boolean,
    val text: String,
    /** Allowlisted action id Aiva proposes — rendered as an approval card. */
    val suggestion: String? = null,
)

/** Detect an actionable intent in a user message (deterministic, testable). */
internal fun actionIntent(text: String): String? {
    val q = text.lowercase()
    return when {
        "focus" in q -> "focus"
        "restart" in q -> "restart_backend"
        "dim" in q || "lock" in q -> "lock_display"
        else -> null
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    val settings: StateFlow<AivaSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AivaSettings())

    /* ---- repository (mock <-> remote, swapped when settings change) ---- */

    private var repo: AivaRepository = MockRepository().also { it.start(viewModelScope) }
    private var mirrorJobs = mutableListOf<Job>()

    private val _snapshot = MutableStateFlow(DashboardSnapshot())
    val snapshot: StateFlow<DashboardSnapshot> = _snapshot.asStateFlow()

    private val _conn = MutableStateFlow(ConnState.MOCK)
    val conn: StateFlow<ConnState> = _conn.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _tools = MutableStateFlow<List<com.aiva.console.data.model.ToolInfo>>(emptyList())
    val tools: StateFlow<List<com.aiva.console.data.model.ToolInfo>> = _tools.asStateFlow()

    init {
        bindRepo()
        viewModelScope.launch {
            settings
                .map { Triple(it.mockMode, it.backendUrl, it.token) }
                .distinctUntilChanged()
                .collect { (mock, url, token) ->
                    repo.stop()
                    repo = if (mock) MockRepository() else RemoteRepository(url, token)
                    repo.start(viewModelScope)
                    bindRepo()
                }
        }
    }

    private fun bindRepo() {
        mirrorJobs.forEach { it.cancel() }
        mirrorJobs.clear()
        historyLoaded = false
        val r = repo
        mirrorJobs += viewModelScope.launch {
            var lastSummary: com.aiva.console.data.model.TasksSummary? = null
            r.snapshots.collect { snap ->
                _snapshot.value = snap
                // chatbot/scheduler mutated tasks server-side → refresh without a restart
                if (lastSummary != null && snap.tasksSummary != lastSummary) {
                    runCatching { _tasks.value = r.fetchTasks() }
                    runCatching { _notes.value = r.notes() }
                }
                lastSummary = snap.tasksSummary
                snap.lastSchedule?.let { maybeNotifySchedule(it) }
            }
        }
        mirrorJobs += viewModelScope.launch { r.connection.collect { _conn.value = it } }
        mirrorJobs += viewModelScope.launch {
            runCatching { _tasks.value = r.fetchTasks() }
            // re-pull data once the link comes up (first fetch may race the backend)
            r.connection.collect { state ->
                if (state == ConnState.CONNECTED || state == ConnState.MOCK) {
                    runCatching { _tasks.value = r.fetchTasks() }
                    runCatching { _notes.value = r.notes() }
                    runCatching { _tools.value = r.tools() }
                    loadChatHistory(r)
                }
            }
        }
    }

    /** Pull persisted exchanges (app + telegram) into the chat view, once per repo. */
    private var historyLoaded = false
    private suspend fun loadChatHistory(r: AivaRepository) {
        if (historyLoaded) return
        val history = runCatching { r.chatHistory(50) }.getOrDefault(emptyList())
        if (history.isNotEmpty()) {
            historyLoaded = true
            messages.value = history.map { entry ->
                ChatMessage(fromMe = entry.role == "user", text = entry.text)
            }
        }
    }

    /* ---- navigation / overlays / toast ---- */

    val screen = MutableStateFlow(Screen.Home)
    val ambient = MutableStateFlow(false)
    val focus = MutableStateFlow(false)
    val focusTask = MutableStateFlow<String?>(null)

    /** End timestamp of the running focus session (null = none). Survives overlay minimize. */
    val focusEndAt = MutableStateFlow<Long?>(null)

    fun endFocus() {
        focus.value = false
        focusEndAt.value = null
    }
    /** Lens calibration debug overlay. */
    val calibrate = MutableStateFlow(false)

    /** Timestamp of the last fired quick action — drives the dock edge flare. */
    val flareAt = MutableStateFlow(0L)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private var toastJob: Job? = null

    fun nav(s: Screen) { screen.value = s }

    /** Raise a system notification when a NEW scheduler run appears. */
    private fun maybeNotifySchedule(run: com.aiva.console.data.model.ScheduleRun) {
        if (run.ts.isBlank()) return
        val s = settings.value
        if (run.ts == s.lastScheduleNotified) return
        val isFirstSight = s.lastScheduleNotified.isBlank()
        viewModelScope.launch { store.update(settings.value.copy(lastScheduleNotified = run.ts)) }
        if (!isFirstSight) {
            com.aiva.console.notifications.SchedulerNotifier.notify(
                getApplication(), run.name, run.result.ifBlank { "Done" },
            )
        }
    }

    /** Public toast hook for UI-layer failures (e.g. missing browser/recognizer). */
    fun notify(msg: String) = showToast(msg)

    private fun showToast(msg: String) {
        toastJob?.cancel()
        _toast.value = msg
        toastJob = viewModelScope.launch { delay(2400); _toast.value = null }
    }

    /* ---- ai state: thinking while streaming, else snapshot's status ---- */

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    val aiState: StateFlow<String> = kotlinx.coroutines.flow.combine(_streaming, _snapshot) { s, snap ->
        if (s) "thinking" else snap.aiStatus
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "idle")

    /* ---- quick actions ---- */

    fun fire(action: QuickActionDef) {
        if (action.nav == null) flareAt.value = System.currentTimeMillis()
        when {
            action.nav != null -> nav(action.nav)
            action.id == "focus" -> {
                focusTask.value = _snapshot.value.topTasks.firstOrNull()?.title
                if (focusEndAt.value == null) focusEndAt.value = System.currentTimeMillis() + 45 * 60_000L
                focus.value = true
                logActionQuietly("focus")
            }
            action.id == "lock_display" -> {
                ambient.value = true
                logActionQuietly("lock_display")
            }
            action.id == "voice" -> voiceRequest.value = VoiceTarget.NOTE
            else -> viewModelScope.launch {
                runCatching { repo.runAction(action.id) }
                    .onSuccess { result ->
                        showToast(result.message.ifBlank { "Action triggered" })
                        if (!result.url.isNullOrBlank()) openUrl.value = result.url
                    }
                    .onFailure { showToast("Action failed — backend unreachable") }
            }
        }
    }

    /* ---- device-side effects (consumed by the UI layer) ---- */

    enum class VoiceTarget { CHAT, NOTE }

    /** Non-null when the UI should launch the speech recognizer. */
    val voiceRequest = MutableStateFlow<VoiceTarget?>(null)

    /** Non-null when the UI should open a link in the browser. */
    val openUrl = MutableStateFlow<String?>(null)

    fun onVoiceResult(target: VoiceTarget, text: String?) {
        voiceRequest.value = null
        if (text.isNullOrBlank()) {
            showToast("Didn't catch that")
            return
        }
        when (target) {
            VoiceTarget.CHAT -> sendChat(text)
            VoiceTarget.NOTE -> addNote(text, kind = "voice")
        }
    }

    /** Fire-and-forget action log for actions that are handled on-device. */
    private fun logActionQuietly(id: String) {
        viewModelScope.launch { runCatching { repo.runAction(id) } }
    }

    fun fireById(id: String) {
        com.aiva.console.ui.screens.QUICK_ACTIONS.find { it.id == id }?.let { fire(it) }
    }

    /* ---- tasks ---- */

    fun toggleTask(task: TaskItem) {
        viewModelScope.launch {
            runCatching { _tasks.value = repo.toggleTask(task) }
                .onFailure { showToast("Task update failed") }
        }
    }

    fun addTask(raw: String) {
        val create = com.aiva.console.data.model.parseTaskInput(raw)
        if (create.title.isBlank()) return
        viewModelScope.launch {
            runCatching { _tasks.value = repo.addTask(create) }
                .onFailure { showToast("Task add failed") }
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            runCatching { _tasks.value = repo.deleteTask(task) }
                .onSuccess { showToast("Task deleted") }
                .onFailure { showToast("Task delete failed") }
        }
    }

    /* ---- notes ---- */

    private val _notes = MutableStateFlow<List<NoteItem>>(emptyList())
    val notes: StateFlow<List<NoteItem>> = _notes.asStateFlow()

    fun refreshNotes() {
        viewModelScope.launch { runCatching { _notes.value = repo.notes() } }
    }

    fun addNote(body: String, kind: String = "text") {
        viewModelScope.launch {
            runCatching { repo.addNote(body, kind) }
                .onSuccess {
                    showToast(if (kind == "voice") "Voice note saved" else "Note saved")
                    runCatching { _notes.value = repo.notes() }
                }
                .onFailure { showToast("Note save failed") }
        }
    }

    /* ---- chat ---- */

    val messages = MutableStateFlow(
        listOf(
            ChatMessage(
                fromMe = false,
                text = "Morning. I'm running locally on your Mac. Ask me to summarize your day, check system status, or start focus mode.",
            )
        )
    )

    fun sendChat(text: String) {
        if (_streaming.value) return
        messages.value = messages.value + ChatMessage(true, text)
        _streaming.value = true
        val intent = actionIntent(text)
        viewModelScope.launch {
            var started = false
            val sb = StringBuilder()
            repo.chatStream(text)
                .collect { ev ->
                    when (ev) {
                        is ChatEvent.Token -> {
                            sb.append(ev.text)
                            if (!started) {
                                started = true
                                messages.value = messages.value + ChatMessage(false, sb.toString())
                            } else {
                                messages.value = messages.value.dropLast(1) + ChatMessage(false, sb.toString())
                            }
                        }
                        is ChatEvent.Done -> {
                            val full = ev.reply.ifBlank { sb.toString() }
                            messages.value =
                                if (started) messages.value.dropLast(1) + ChatMessage(false, full)
                                else messages.value + ChatMessage(false, full)
                            // the agent may have created/changed tasks or notes
                            launch {
                                runCatching { _tasks.value = repo.fetchTasks() }
                                runCatching { _notes.value = repo.notes() }
                            }
                            // propose the detected action — runs only on approval
                            if (intent != null) {
                                messages.value = messages.value + ChatMessage(false, "", suggestion = intent)
                            }
                            _streaming.value = false
                        }
                        is ChatEvent.Error -> {
                            messages.value = messages.value + ChatMessage(false, "⚠ ${ev.message}")
                            _streaming.value = false
                        }
                    }
                }
            _streaming.value = false
        }
    }

    /** Resolve a suggestion card: approve runs the allowlisted action.
     *  Either way the outcome trains the backend's self-learning memory. */
    fun resolveSuggestion(msg: ChatMessage, approved: Boolean) {
        val id = msg.suggestion ?: return
        val label = com.aiva.console.ui.screens.QUICK_ACTIONS.find { it.id == id }?.label ?: id
        messages.value = messages.value.map {
            if (it === msg) {
                ChatMessage(false, if (approved) "✓ Approved — $label" else "✕ Dismissed — $label")
            } else it
        }
        viewModelScope.launch { runCatching { repo.suggestionFeedback(id, approved) } }
        if (approved) fireById(id)
    }

    /* ---- settings ---- */

    val testState = MutableStateFlow<TestState>(TestState.Idle)

    fun updateSettings(s: AivaSettings) {
        viewModelScope.launch { store.update(s) }
    }

    fun testConnection() {
        testState.value = TestState.Testing
        viewModelScope.launch {
            val s = settings.value
            // always probe the REAL configured endpoint (even in demo mode)
            runCatching {
                val api = com.aiva.console.data.api.ApiFactory.create(
                    s.backendUrl,
                    com.aiva.console.data.api.ApiFactory.okHttp(s.token),
                )
                val t0 = System.nanoTime()
                api.health()
                (System.nanoTime() - t0) / 1_000_000
            }.onSuccess { ms ->
                testState.value = TestState.Ok(ms)
                // connection proven — auto-switch off demo mode
                if (s.mockMode) {
                    store.update(s.copy(mockMode = false))
                    showToast("Connected · live mode enabled")
                }
            }.onFailure {
                testState.value = TestState.Fail(it.message ?: "unreachable")
            }
        }
    }

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }
}
