package com.aiva.console

import com.aiva.console.data.api.AivaJson
import com.aiva.console.data.model.TaskItem
import com.aiva.console.data.model.parseTaskInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseTaskInputTest {

    @Test
    fun `plain text is just a title`() {
        val t = parseTaskInput("Buy a USB-C cable")
        assertEquals("Buy a USB-C cable", t.title)
        assertNull(t.project)
        assertNull(t.tags)
        assertNull(t.priority)
    }

    @Test
    fun `hash project at tags bang priority`() {
        val t = parseTaskInput("Ship the build #aiva @release @android !high")
        assertEquals("Ship the build", t.title)
        assertEquals("aiva", t.project)
        assertEquals("release,android", t.tags)
        assertEquals("high", t.priority)
    }

    @Test
    fun `invalid priority token stays in title`() {
        val t = parseTaskInput("fix !urgent thing")
        assertEquals("fix !urgent thing", t.title)
        assertNull(t.priority)
    }

    @Test
    fun `task item decodes tracker fields`() {
        val t = AivaJson.decodeFromString(
            TaskItem.serializer(),
            """{"id":1,"title":"x","status":"done","priority":"high","category":"work",
                "project":"aiva","notes":"n","tags":"a,b","parent_id":7,
                "completed_at":"2026-06-07T10:00:00Z","created_at":"","updated_at":""}""",
        )
        assertEquals("aiva", t.project)
        assertEquals(7, t.parentId)
        assertEquals("2026-06-07T10:00:00Z", t.completedAt)
    }
}
