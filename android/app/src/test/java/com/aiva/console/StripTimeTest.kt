package com.aiva.console

import com.aiva.console.ui.components.minutesUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class StripTimeTest {

    private val now: Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `minutes until upcoming time today`() {
        assertEquals(30L, minutesUntil("09:30", now))
        assertEquals(0L, minutesUntil("09:00", now))
    }

    @Test
    fun `past times yield null`() {
        assertNull(minutesUntil("08:00", now))
    }

    @Test
    fun `unparseable input yields null`() {
        assertNull(minutesUntil("Tomorrow", now))
        assertNull(minutesUntil(null, now))
        assertNull(minutesUntil("", now))
    }
}
