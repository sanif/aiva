package com.aiva.console

import com.aiva.console.data.api.AivaJson
import com.aiva.console.data.model.ActionResult
import com.aiva.console.data.model.ChatHistoryEntry
import com.aiva.console.data.model.DashboardSnapshot
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {

    @Test
    fun `dashboard snapshot decodes from backend shape`() {
        val json = """
            {"type":"snapshot","ts":"2026-06-07T10:00:00Z","greeting_name":"Alex",
             "ai_status":"alert",
             "metrics":{"cpu_pct":34.5,"ram_pct":58.0,"disk_pct":71.0,"temp_c":null,
                        "net_up_mbps":2.4,"net_down_mbps":11.6,"uptime_s":120.0,
                        "cpu_history":[30.0,31.5],"net_history":[8.0]},
             "services":[{"name":"Ollama","target":":11434","status":"up","latency_ms":12.5}],
             "docker":{"available":true,"running":2,"total":3,
                       "containers":[{"name":"redis","status":"up","raw_status":"Up 2 hours"}]},
             "alerts":[{"level":"err","title":"Plex exited","meta":"docker","ts":"13:47"}],
             "tasks_summary":{"today":3,"done":1,"upcoming":2},
             "unknown_future_field":123}
        """.trimIndent()
        val snap = AivaJson.decodeFromString(DashboardSnapshot.serializer(), json)
        assertEquals("Alex", snap.greetingName)
        assertEquals("alert", snap.aiStatus)
        assertEquals(34.5f, snap.metrics.cpuPct, 0.001f)
        assertNull(snap.metrics.tempC)
        assertEquals(1, snap.services.size)
        assertEquals(3, snap.docker.total)
        assertEquals("err", snap.alerts[0].level)
    }

    @Test
    fun `action result carries optional url`() {
        val withUrl = AivaJson.decodeFromString(
            ActionResult.serializer(),
            """{"ok":true,"message":"Opening dashboard","url":"http://localhost:8420/docs"}""",
        )
        assertEquals("http://localhost:8420/docs", withUrl.url)

        val without = AivaJson.decodeFromString(
            ActionResult.serializer(),
            """{"ok":true,"message":"Focus mode engaged"}""",
        )
        assertNull(without.url)
    }

    @Test
    fun `chat history entries decode with source`() {
        val history = AivaJson.decodeFromString(
            ListSerializer(ChatHistoryEntry.serializer()),
            """[{"role":"user","text":"hi","ts":"2026-06-07T10:00:00Z","source":"telegram"},
                {"role":"assistant","text":"hello","ts":"2026-06-07T10:00:01Z","source":"app"}]""",
        )
        assertEquals(2, history.size)
        assertEquals("telegram", history[0].source)
        assertEquals("assistant", history[1].role)
    }
}
