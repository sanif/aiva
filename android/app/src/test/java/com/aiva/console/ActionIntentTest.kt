package com.aiva.console

import com.aiva.console.viewmodel.actionIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionIntentTest {

    @Test
    fun `focus intents map to the focus action`() {
        assertEquals("focus", actionIntent("Start focus mode"))
        assertEquals("focus", actionIntent("can you FOCUS me for a bit"))
    }

    @Test
    fun `restart intents map to restart_backend`() {
        assertEquals("restart_backend", actionIntent("please restart the backend"))
    }

    @Test
    fun `dim and lock map to lock_display`() {
        assertEquals("lock_display", actionIntent("dim the screen"))
        assertEquals("lock_display", actionIntent("lock display please"))
    }

    @Test
    fun `plain questions carry no action`() {
        assertNull(actionIntent("what should I do today?"))
        assertNull(actionIntent("summarize my day"))
    }
}
