package com.aiva.console.data.repo

import com.aiva.console.data.api.AivaApi
import com.aiva.console.data.api.AivaJson
import com.aiva.console.data.api.ApiFactory
import com.aiva.console.data.model.ActionResult
import com.aiva.console.data.model.ChatEvent
import com.aiva.console.data.model.ChatFrame
import com.aiva.console.data.model.ChatHistoryEntry
import com.aiva.console.data.model.ChatRequest
import com.aiva.console.data.model.ConnState
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.data.model.NoteCreate
import com.aiva.console.data.model.NoteItem
import com.aiva.console.data.model.SuggestionFeedback
import com.aiva.console.data.model.ToolInfo
import com.aiva.console.data.model.TaskCreate
import com.aiva.console.data.model.TaskItem
import com.aiva.console.data.model.TaskPatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.coroutines.resume

/**
 * Live backend data source: REST via Retrofit + /ws/dashboard push.
 * Auto-reconnects with exponential backoff; merges WS frames over the
 * richer REST /api/dashboard payload (agenda, top tasks, greeting).
 */
class RemoteRepository(
    private val baseUrl: String,
    private val token: String,
) : AivaRepository {

    private val client: OkHttpClient = ApiFactory.okHttp(token)
    private val api: AivaApi = ApiFactory.create(baseUrl, client)

    private val _snapshots = MutableStateFlow(DashboardSnapshot())
    override val snapshots: StateFlow<DashboardSnapshot> = _snapshots.asStateFlow()

    private val _connection = MutableStateFlow(ConnState.CONNECTING)
    override val connection: StateFlow<ConnState> = _connection.asStateFlow()

    private var scope: CoroutineScope? = null
    private var wsJob: Job? = null
    private var restJob: Job? = null
    private var socket: WebSocket? = null

    private fun wsUrl(path: String): String {
        val base = baseUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        return "$base$path?token=$token"
    }

    override fun start(scope: CoroutineScope) {
        stop()
        this.scope = scope

        // Periodic REST refresh for the fields the WS stream doesn't carry.
        restJob = scope.launch {
            while (isActive) {
                refreshDashboard()
                delay(60_000)
            }
        }

        // WS connect loop with backoff.
        wsJob = scope.launch {
            var backoffMs = 1_000L
            while (isActive) {
                _connection.value = ConnState.CONNECTING
                val closed = suspendCancellableCoroutine<Unit> { cont ->
                    val req = Request.Builder().url(wsUrl("/ws/dashboard")).build()
                    val ws = client.newWebSocket(req, object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            runCatching {
                                val frame = AivaJson.decodeFromString(DashboardSnapshot.serializer(), text)
                                mergeFrame(frame)
                                _connection.value = ConnState.CONNECTED
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    })
                    socket = ws
                    cont.invokeOnCancellation { ws.cancel() }
                }
                closed // (value unused — we only care that the socket ended)
                _connection.value = ConnState.OFFLINE
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(10_000L)
                if (_connection.value == ConnState.CONNECTED) backoffMs = 1_000L
            }
        }
    }

    override fun stop() {
        socket?.cancel(); socket = null
        wsJob?.cancel(); wsJob = null
        restJob?.cancel(); restJob = null
        scope = null
    }

    /** WS snapshots carry live fields; keep the REST-only fields we already have. */
    private fun mergeFrame(frame: DashboardSnapshot) {
        val prev = _snapshots.value
        _snapshots.value = frame.copy(
            greetingName = if (frame.greetingName.isNotBlank()) frame.greetingName else prev.greetingName,
            topTasks = if (frame.topTasks.isNotEmpty()) frame.topTasks else prev.topTasks,
            agenda = if (frame.agenda.isNotEmpty()) frame.agenda else prev.agenda,
        )
    }

    private suspend fun refreshDashboard() {
        runCatching { api.dashboard() }
            .onSuccess { dash ->
                _snapshots.value = dash
                if (_connection.value != ConnState.CONNECTED) _connection.value = ConnState.CONNECTED
            }
            .onFailure {
                if (_connection.value != ConnState.CONNECTED) _connection.value = ConnState.OFFLINE
            }
    }

    /* ---- tasks ---- */

    override suspend fun fetchTasks(): List<TaskItem> = api.tasks()

    override suspend fun addTask(create: TaskCreate): List<TaskItem> {
        api.createTask(create)
        refreshDashboard()
        return api.tasks()
    }

    override suspend fun toggleTask(task: TaskItem): List<TaskItem> {
        val newStatus = if (task.status == "done") "today" else "done"
        api.patchTask(task.id, TaskPatch(status = newStatus))
        refreshDashboard()
        return api.tasks()
    }

    override suspend fun deleteTask(task: TaskItem): List<TaskItem> {
        api.deleteTask(task.id)
        refreshDashboard()
        return api.tasks()
    }

    /* ---- actions / notes / history ---- */

    override suspend fun runAction(id: String): ActionResult = api.runAction(id)

    override suspend fun notes(limit: Int): List<NoteItem> = api.notes(limit)

    override suspend fun addNote(body: String, kind: String): Boolean =
        runCatching { api.createNote(NoteCreate(body = body, kind = kind)) }.isSuccess

    override suspend fun chatHistory(limit: Int): List<ChatHistoryEntry> =
        runCatching { api.chatHistory(limit) }.getOrDefault(emptyList())

    override suspend fun suggestionFeedback(actionId: String, approved: Boolean) {
        runCatching { api.suggestionFeedback(SuggestionFeedback(actionId, approved)) }
    }

    override suspend fun tools(): List<ToolInfo> =
        runCatching { api.tools() }.getOrDefault(emptyList())

    override suspend fun testConnection(): Result<Long> = runCatching {
        val t0 = System.nanoTime()
        api.health()
        (System.nanoTime() - t0) / 1_000_000
    }

    /* ---- chat: WS streaming with REST fallback ---- */

    override fun chatStream(message: String): Flow<ChatEvent> = callbackFlow {
        val req = Request.Builder().url(wsUrl("/ws/chat")).build()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(AivaJson.encodeToString(ChatRequest.serializer(), ChatRequest(message)))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = runCatching {
                    AivaJson.decodeFromString(ChatFrame.serializer(), text)
                }.getOrNull() ?: return
                when (frame.type) {
                    "token" -> trySend(ChatEvent.Token(frame.text))
                    "done" -> { trySend(ChatEvent.Done(frame.reply)); close() }
                    "error" -> { trySend(ChatEvent.Error(frame.message)); close() }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // WS failed — fall back to non-streaming REST.
                launch {
                    runCatching { api.chat(ChatRequest(message)) }
                        .onSuccess { trySend(ChatEvent.Done(it.reply)) }
                        .onFailure { trySend(ChatEvent.Error(it.message ?: "Backend unreachable")) }
                    close()
                }
            }
        })
        awaitClose { ws.cancel() }
    }
}
