package com.aiva.console.data.repo

import com.aiva.console.data.model.ActionResult
import com.aiva.console.data.model.AgendaItem
import com.aiva.console.data.model.Alert
import com.aiva.console.data.model.ChatEvent
import com.aiva.console.data.model.ChatHistoryEntry
import com.aiva.console.data.model.ConnState
import com.aiva.console.data.model.ContainerInfo
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.data.model.DockerInfo
import com.aiva.console.data.model.Metrics
import com.aiva.console.data.model.NoteItem
import com.aiva.console.data.model.ServiceStatus
import com.aiva.console.data.model.TaskCreate
import com.aiva.console.data.model.ToolInfo
import com.aiva.console.data.model.TaskItem
import com.aiva.console.data.model.TasksSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Demo-mode data source — mirrors the design prototype's mock simulation
 * (design/prototype/data.jsx): smooth random-walk metrics + seeded entities.
 */
class MockRepository : AivaRepository {

    /* ---- mock entities (verbatim from the design) ---- */

    private val services = listOf(
        ServiceStatus("Backend API", ":8420", "up", 24f),
        ServiceStatus("Ollama", ":11434", "up", 31f),
        ServiceStatus("PostgreSQL", ":5432", "up", 2f),
        ServiceStatus("Redis Cache", ":6379", "up", 1f),
        ServiceStatus("NAS / TrueNAS", "192.168.1.4", "warn", 118f),
        ServiceStatus("Nginx Proxy", ":443", "up", 8f),
        ServiceStatus("Plex Media", ":32400", "down", null),
    )

    private val docker = DockerInfo(
        available = true, running = 6, total = 7,
        containers = listOf(
            ContainerInfo("aiva-backend", "up"), ContainerInfo("ollama", "up"),
            ContainerInfo("postgres", "up"), ContainerInfo("redis", "up"),
            ContainerInfo("nginx-proxy", "up"), ContainerInfo("plex", "down"),
            ContainerInfo("watchtower", "up"),
        ),
    )

    private val alerts = listOf(
        Alert("warn", "NAS volume at 91% capacity", "TANK · 1.2 TB free · 14:02", "14:02"),
        Alert("err", "Plex container exited (code 1)", "docker · restarts: 3 · 13:47", "13:47"),
    )

    private val agenda = listOf(
        AgendaItem("09:30", "Architecture review", "Zoom · 45m", now = true),
        AgendaItem("11:00", "Backend migration block", "Deep work · 2h"),
        AgendaItem("14:00", "Dentist", "Downtown · 30m"),
        AgendaItem("16:30", "1:1 with the team", "Office · 30m"),
    )

    private var nextId = 9
    private val tasks = MutableStateFlow(
        listOf(
            TaskItem(1, "Review Q3 architecture doc", status = "today", priority = "high", category = "work", due = "09:30"),
            TaskItem(2, "Migrate backend to FastAPI", status = "today", priority = "high", category = "system", due = "11:00"),
            TaskItem(3, "Call dentist", status = "today", priority = "med", category = "personal", due = "14:00"),
            TaskItem(4, "Push Aiva v0.3 to TestFlight", status = "upcoming", priority = "med", category = "work", due = "Tomorrow"),
            TaskItem(5, "Renew domain aiva.local", status = "upcoming", priority = "low", category = "system", due = "Jun 9"),
            TaskItem(6, "Grocery run", status = "upcoming", priority = "low", category = "personal", due = "Sat"),
            TaskItem(7, "Set up nightly DB backup", status = "done", priority = "med", category = "system", due = "Yesterday"),
            TaskItem(8, "Reply to investor email", status = "done", priority = "high", category = "work", due = "Yesterday"),
        )
    )

    /* ---- random-walk metrics ---- */

    private var cpu = 34f; private var ram = 58f; private val disk = 71f
    private var temp = 48f; private var up = 2.4f; private var down = 11.6f
    private var cpuH = List(34) { 30f + Random.nextFloat() * 18f }
    private var netH = List(34) { 8f + Random.nextFloat() * 8f }

    private fun walk(v: Float, lo: Float, hi: Float, step: Float): Float {
        var n = v + (Random.nextFloat() - 0.5f) * step
        if (n < lo) n = lo + Random.nextFloat() * step
        if (n > hi) n = hi - Random.nextFloat() * step
        return n
    }

    private fun metrics(): Metrics = Metrics(
        cpuPct = cpu, ramPct = ram, diskPct = disk, tempC = temp,
        netUpMbps = up, netDownMbps = down, uptimeS = 86400f * 2.3f,
        batteryPct = 84f, powerPlugged = true,
        cpuHistory = cpuH, netHistory = netH,
    )

    private fun buildSnapshot(): DashboardSnapshot {
        val ts = tasks.value
        return DashboardSnapshot(
            greetingName = "Alex",
            aiStatus = "idle",
            metrics = metrics(),
            services = services,
            docker = docker,
            alerts = alerts,
            tasksSummary = TasksSummary(
                today = ts.count { it.status == "today" },
                done = ts.count { it.status == "done" },
                upcoming = ts.count { it.status == "upcoming" },
            ),
            topTasks = ts.filter { it.status == "today" }.take(3),
            agenda = agenda,
        )
    }

    private val _snapshots = MutableStateFlow(buildSnapshot())
    override val snapshots: StateFlow<DashboardSnapshot> = _snapshots.asStateFlow()
    override val connection = MutableStateFlow(ConnState.MOCK)

    private var ticker: Job? = null

    override fun start(scope: CoroutineScope) {
        stop()
        ticker = scope.launch {
            while (isActive) {
                cpu = walk(cpu, 12f, 92f, 16f)
                ram = walk(ram, 40f, 84f, 6f)
                temp = walk(temp, 40f, 67f, 4f)
                up = maxOf(0.1f, walk(up, 0.2f, 9f, 2.2f))
                down = maxOf(0.2f, walk(down, 1f, 48f, 12f))
                cpuH = cpuH.drop(1) + cpu
                netH = netH.drop(1) + down
                _snapshots.value = buildSnapshot()
                delay(1600)
            }
        }
    }

    override fun stop() { ticker?.cancel(); ticker = null }

    /* ---- tasks ---- */

    override suspend fun fetchTasks(): List<TaskItem> = tasks.value

    override suspend fun addTask(create: TaskCreate): List<TaskItem> {
        tasks.value = listOf(
            TaskItem(
                nextId++, create.title,
                status = create.status ?: "today",
                priority = create.priority ?: "med",
                category = create.category ?: "work",
                due = create.due ?: "Today",
                project = create.project,
                tags = create.tags,
                notes = create.notes,
            )
        ) + tasks.value
        _snapshots.value = buildSnapshot()
        return tasks.value
    }

    override suspend fun toggleTask(task: TaskItem): List<TaskItem> {
        tasks.value = tasks.value.map {
            if (it.id == task.id) it.copy(status = if (it.status == "done") "today" else "done") else it
        }
        _snapshots.value = buildSnapshot()
        return tasks.value
    }

    /* ---- actions / notes / chat ---- */

    private val actionToast = mapOf(
        "focus" to "Focus mode engaged · 45 min",
        "note" to "New note created",
        "voice" to "Recording voice note…",
        "restart_backend" to "Restarting aiva-backend…",
        "open_dashboard" to "Opening dashboard",
        "backup_status" to "Running backup_status_check…",
        "lock_display" to "Display dimming…",
    )

    override suspend fun runAction(id: String): ActionResult =
        if (id == "open_dashboard") {
            ActionResult(ok = true, message = actionToast[id] ?: "Opening dashboard", url = "http://192.168.1.10:8420/docs")
        } else {
            ActionResult(ok = true, message = actionToast[id] ?: "Action triggered")
        }

    /* ---- notes (in-memory demo) ---- */

    private var nextNoteId = 3
    private val notesList = MutableStateFlow(
        listOf(
            NoteItem(2, title = null, body = "Check NAS capacity before the weekend", kind = "text", createdAt = "2026-06-06T18:20:00"),
            NoteItem(1, title = null, body = "Idea: orb should wink when a task completes", kind = "voice", createdAt = "2026-06-06T09:12:00"),
        )
    )

    override suspend fun notes(limit: Int): List<NoteItem> = notesList.value.take(limit)

    override suspend fun addNote(body: String, kind: String): Boolean {
        notesList.value = listOf(NoteItem(nextNoteId++, body = body, kind = kind, createdAt = "")) + notesList.value
        return true
    }

    override suspend fun chatHistory(limit: Int): List<ChatHistoryEntry> = emptyList()

    override suspend fun suggestionFeedback(actionId: String, approved: Boolean) = Unit

    override suspend fun tools(): List<ToolInfo> = listOf(
        "get_system_status", "list_tasks", "create_task", "update_task", "list_projects",
        "get_agenda", "list_notes", "create_note", "run_action",
        "schedule_task", "list_schedules", "delete_schedule",
        "remember", "recall", "save_memory",
    ).map { ToolInfo(it) }

    override suspend fun deleteTask(task: TaskItem): List<TaskItem> {
        tasks.value = tasks.value.filterNot { it.id == task.id }
        _snapshots.value = buildSnapshot()
        return tasks.value
    }

    override suspend fun testConnection(): Result<Long> {
        delay(1400)
        return Result.success(24L)
    }

    override fun chatStream(message: String): Flow<ChatEvent> = flow {
        val reply = aivaReply(message)
        delay(520)
        val words = reply.split(" ")
        val sb = StringBuilder()
        for ((i, w) in words.withIndex()) {
            sb.append(if (i == 0) w else " $w")
            emit(ChatEvent.Token(if (i == 0) w else " $w"))
            delay(42)
        }
        emit(ChatEvent.Done(sb.toString()))
    }

    /** Canned replies keyed by intent — from the design prototype. */
    private fun aivaReply(text: String): String {
        val q = text.lowercase()
        return when {
            "focus" in q || "now" in q ->
                "Right now: finish the Q3 architecture review — it's high-priority and due at 09:30. " +
                    "After that, protect your 11:00 deep-work block for the FastAPI migration. " +
                    "I can start a 45-minute focus timer if you'd like."
            "status" in q || "system" in q ->
                "All core services are healthy. CPU is at ${cpu.toInt()}%, RAM ${ram.toInt()}%, disk ${disk.toInt()}%. " +
                    "One warning: the NAS volume is at 91% capacity, and the Plex container has exited — I've flagged both in Alerts."
            "summar" in q || "today" in q ->
                "You have 3 tasks today and 4 calendar events. Two tasks are high-priority (architecture review, backend migration). " +
                    "Your next event is the Architecture review at 09:30. System is stable apart from the NAS capacity warning."
            "alert" in q || "urgent" in q ->
                "Two active alerts: the NAS volume is at 91% (warning), and the Plex container exited with code 1 after 3 restarts (error). " +
                    "Want me to attempt a safe restart of Plex?"
            else ->
                "Got it. I'm running locally on your Mac, so this stays on your network. " +
                    "I can pull system metrics, manage tasks, or trigger any allowlisted action — just ask."
        }
    }
}
