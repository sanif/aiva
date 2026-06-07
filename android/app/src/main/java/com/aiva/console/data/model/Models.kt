package com.aiva.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* Shapes mirror the FastAPI backend contract (snake_case on the wire). */

@Serializable
data class Metrics(
    @SerialName("cpu_pct") val cpuPct: Float = 0f,
    @SerialName("ram_pct") val ramPct: Float = 0f,
    @SerialName("disk_pct") val diskPct: Float = 0f,
    @SerialName("temp_c") val tempC: Float? = null,
    @SerialName("net_up_mbps") val netUpMbps: Float = 0f,
    @SerialName("net_down_mbps") val netDownMbps: Float = 0f,
    @SerialName("uptime_s") val uptimeS: Float = 0f,
    @SerialName("battery_pct") val batteryPct: Float? = null,
    @SerialName("power_plugged") val powerPlugged: Boolean? = null,
    @SerialName("cpu_history") val cpuHistory: List<Float> = emptyList(),
    @SerialName("net_history") val netHistory: List<Float> = emptyList(),
)

@Serializable
data class ServiceStatus(
    val name: String,
    val target: String = "",
    val status: String = "down", // up | warn | down
    @SerialName("latency_ms") val latencyMs: Float? = null,
)

@Serializable
data class ContainerInfo(
    val name: String,
    val status: String = "down", // up | down
    @SerialName("raw_status") val rawStatus: String = "",
)

@Serializable
data class DockerInfo(
    val available: Boolean = false,
    val running: Int = 0,
    val total: Int = 0,
    val containers: List<ContainerInfo> = emptyList(),
)

@Serializable
data class Alert(
    val level: String, // warn | err
    val title: String,
    val meta: String = "",
    val ts: String = "",
)

@Serializable
data class TaskItem(
    val id: Int,
    val title: String,
    val description: String? = null,
    val status: String = "today",   // today | upcoming | done
    val priority: String = "med",   // high | med | low
    val category: String = "work",  // work | personal | system
    val due: String? = null,
    val project: String? = null,
    val notes: String? = null,
    val tags: String? = null,       // comma-separated
    @SerialName("parent_id") val parentId: Int? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class TasksSummary(val today: Int = 0, val done: Int = 0, val upcoming: Int = 0)

@Serializable
data class AgendaItem(
    val time: String,
    val title: String,
    val meta: String = "",
    val now: Boolean = false,
)

/** Most recent completed scheduler run (pushed in WS snapshots). */
@Serializable
data class ScheduleRun(
    val id: Int = 0,
    val name: String = "",
    val ts: String = "",
    val result: String = "",
)

/** Served by GET /api/dashboard and pushed on /ws/dashboard. */
@Serializable
data class DashboardSnapshot(
    val type: String = "snapshot",
    val ts: String = "",
    @SerialName("greeting_name") val greetingName: String = "Alex",
    @SerialName("ai_status") val aiStatus: String = "idle", // idle | thinking | alert
    val metrics: Metrics = Metrics(),
    val services: List<ServiceStatus> = emptyList(),
    val docker: DockerInfo = DockerInfo(),
    val alerts: List<Alert> = emptyList(),
    @SerialName("tasks_summary") val tasksSummary: TasksSummary = TasksSummary(),
    @SerialName("last_schedule") val lastSchedule: ScheduleRun? = null,
    @SerialName("top_tasks") val topTasks: List<TaskItem> = emptyList(),
    val agenda: List<AgendaItem> = emptyList(),
)

@Serializable
data class HealthResponse(
    val status: String = "",
    val version: String = "",
    @SerialName("uptime_s") val uptimeS: Float = 0f,
    val mock: Boolean = false,
)

@Serializable
data class NoteItem(
    val id: Int,
    val title: String? = null,
    val body: String,
    val kind: String = "text",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable data class NoteCreate(val body: String, val title: String? = null, val kind: String = "text")

@Serializable
data class TaskCreate(
    val title: String,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val category: String? = null,
    val due: String? = null,
    val project: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    @SerialName("parent_id") val parentId: Int? = null,
)

/**
 * Quick-add syntax: "Ship the build #aiva @release @android !high"
 * → title "Ship the build", project "aiva", tags "release,android", priority high.
 */
fun parseTaskInput(raw: String): TaskCreate {
    val words = mutableListOf<String>()
    val tags = mutableListOf<String>()
    var project: String? = null
    var priority: String? = null
    for (tok in raw.trim().split(Regex("\\s+"))) {
        when {
            tok.length > 1 && tok.startsWith("#") -> project = tok.drop(1)
            tok.length > 1 && tok.startsWith("@") -> tags += tok.drop(1)
            tok.length > 1 && tok.startsWith("!") &&
                tok.drop(1).lowercase() in listOf("high", "med", "low") ->
                priority = tok.drop(1).lowercase()
            tok.isNotEmpty() -> words += tok
        }
    }
    return TaskCreate(
        title = words.joinToString(" "),
        project = project,
        tags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
        priority = priority,
    )
}

@Serializable
data class TaskPatch(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val category: String? = null,
    val due: String? = null,
)

@Serializable data class ChatRequest(val message: String)
@Serializable data class ChatResponse(val reply: String)

/** Approval-card outcome — feeds the backend's self-learning memory. */
@Serializable
data class SuggestionFeedback(@SerialName("action_id") val actionId: String, val approved: Boolean)

@Serializable data class FeedbackResponse(val ok: Boolean = false, val score: Float = 0f)

@Serializable data class ActionInfo(val id: String, val label: String, val description: String = "")

/** One entry of the agent's tool registry (GET /api/tools). */
@Serializable data class ToolInfo(val name: String, val description: String = "")

@Serializable
data class ActionResult(
    val ok: Boolean = false,
    val message: String = "",
    /** Set for open_url actions — the client launches this link. */
    val url: String? = null,
)

/** One entry of GET /api/chat/history (app + telegram exchanges). */
@Serializable
data class ChatHistoryEntry(
    val role: String, // user | assistant
    val text: String,
    val ts: String = "",
    val source: String = "app", // app | telegram
)

/** Frames on /ws/chat. */
@Serializable
data class ChatFrame(
    val type: String, // token | done | error
    val text: String = "",
    val reply: String = "",
    val message: String = "",
)

/** Streaming chat events surfaced to the UI. */
sealed interface ChatEvent {
    data class Token(val text: String) : ChatEvent
    data class Done(val reply: String) : ChatEvent
    data class Error(val message: String) : ChatEvent
}

enum class ConnState { MOCK, CONNECTING, CONNECTED, OFFLINE }
