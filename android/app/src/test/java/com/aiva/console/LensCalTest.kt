package com.aiva.console

import com.aiva.console.ui.components.LensCircle
import com.aiva.console.ui.components.encodeLensCal
import com.aiva.console.ui.components.parseLensCal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LensCalTest {

    @Test
    fun `encode then parse roundtrips`() {
        val circles = listOf(
            LensCircle(0.5031f, 0.8562f, 0.0371f),
            LensCircle(0.6651f, 0.8543f, 0.0972f),
            LensCircle(0.8740f, 0.8521f, 0.0970f),
        )
        val parsed = parseLensCal(encodeLensCal(circles))
        assertEquals(3, parsed.size)
        circles.zip(parsed).forEach { (a, b) ->
            assertEquals(a.cx, b.cx, 0.0001f)
            assertEquals(a.cy, b.cy, 0.0001f)
            assertEquals(a.r, b.r, 0.0001f)
        }
    }

    @Test
    fun `parse skips malformed segments`() {
        val parsed = parseLensCal("0.5,0.9,0.05;garbage;0.7,0.9")
        assertEquals(1, parsed.size)
        assertEquals(0.5f, parsed[0].cx, 0.0001f)
    }

    @Test
    fun `parse of empty string is empty`() {
        assertTrue(parseLensCal("").isEmpty())
        assertTrue(parseLensCal("   ").isEmpty())
    }
}
