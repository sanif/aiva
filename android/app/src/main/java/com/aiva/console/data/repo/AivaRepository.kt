package com.aiva.console.data.repo

import com.aiva.console.data.model.ActionResult
import com.aiva.console.data.model.ChatEvent
import com.aiva.console.data.model.ChatHistoryEntry
import com.aiva.console.data.model.ConnState
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.data.model.NoteItem
import com.aiva.console.data.model.TaskCreate
import com.aiva.console.data.model.ToolInfo
import com.aiva.console.data.model.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One data surface for the whole app. Two implementations:
 * [MockRepository] (self-contained demo data) and [RemoteRepository] (FastAPI backend).
 */
interface AivaRepository {
    /** Latest dashboard snapshot — live-updating (WS push or mock ticker). */
    val snapshots: StateFlow<DashboardSnapshot>
    val connection: StateFlow<ConnState>

    suspend fun fetchTasks(): List<TaskItem>
    suspend fun addTask(create: TaskCreate): List<TaskItem>
    suspend fun toggleTask(task: TaskItem): List<TaskItem>
    suspend fun deleteTask(task: TaskItem): List<TaskItem>
    suspend fun runAction(id: String): ActionResult
    suspend fun notes(limit: Int = 20): List<NoteItem>
    suspend fun addNote(body: String, kind: String = "text"): Boolean
    suspend fun chatHistory(limit: Int = 50): List<ChatHistoryEntry>

    /** Returns round-trip latency in ms on success. */
    suspend fun testConnection(): Result<Long>

    fun chatStream(message: String): Flow<ChatEvent>

    /** Approval-card outcome → backend self-learning memory (best-effort). */
    suspend fun suggestionFeedback(actionId: String, approved: Boolean)

    /** The agent's tool registry — what the chat model is allowed to do. */
    suspend fun tools(): List<ToolInfo>

    fun start(scope: CoroutineScope)
    fun stop()
}
