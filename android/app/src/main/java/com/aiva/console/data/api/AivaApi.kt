package com.aiva.console.data.api

import com.aiva.console.data.model.ActionInfo
import com.aiva.console.data.model.ActionResult
import com.aiva.console.data.model.ChatHistoryEntry
import com.aiva.console.data.model.ChatRequest
import com.aiva.console.data.model.ChatResponse
import com.aiva.console.data.model.DashboardSnapshot
import com.aiva.console.data.model.HealthResponse
import com.aiva.console.data.model.Metrics
import com.aiva.console.data.model.FeedbackResponse
import com.aiva.console.data.model.NoteCreate
import com.aiva.console.data.model.NoteItem
import com.aiva.console.data.model.ServiceStatus
import com.aiva.console.data.model.SuggestionFeedback
import com.aiva.console.data.model.ToolInfo
import com.aiva.console.data.model.TaskCreate
import com.aiva.console.data.model.TaskItem
import com.aiva.console.data.model.TaskPatch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface AivaApi {
    @GET("api/health") suspend fun health(): HealthResponse
    @GET("api/dashboard") suspend fun dashboard(): DashboardSnapshot
    @GET("api/metrics") suspend fun metrics(): Metrics
    @GET("api/services") suspend fun services(): List<ServiceStatus>

    @GET("api/tasks") suspend fun tasks(@Query("status") status: String? = null): List<TaskItem>
    @POST("api/tasks") suspend fun createTask(@Body body: TaskCreate): TaskItem
    @PATCH("api/tasks/{id}") suspend fun patchTask(@Path("id") id: Int, @Body body: TaskPatch): TaskItem
    @DELETE("api/tasks/{id}") suspend fun deleteTask(@Path("id") id: Int)

    @GET("api/notes") suspend fun notes(@Query("limit") limit: Int = 20): List<NoteItem>
    @POST("api/notes") suspend fun createNote(@Body body: NoteCreate): NoteItem

    @POST("api/chat") suspend fun chat(@Body body: ChatRequest): ChatResponse
    @GET("api/chat/history") suspend fun chatHistory(@Query("limit") limit: Int = 50): List<ChatHistoryEntry>
    @POST("api/chat/feedback") suspend fun suggestionFeedback(@Body body: SuggestionFeedback): FeedbackResponse

    @GET("api/actions") suspend fun actions(): List<ActionInfo>
    @GET("api/tools") suspend fun tools(): List<ToolInfo>
    @POST("api/actions/{id}") suspend fun runAction(@Path("id") id: String): ActionResult
}

val AivaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}

object ApiFactory {

    fun okHttp(token: String): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-API-Token", token)
                        .build()
                )
            }
            .build()

    fun create(baseUrl: String, client: OkHttpClient): AivaApi {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(AivaJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AivaApi::class.java)
    }
}
