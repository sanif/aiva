package com.aiva.console

import com.aiva.console.ui.screens.clockParts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ClockPartsTest {

    private fun at(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }.timeInMillis

    @Test
    fun `greeting follows time of day`() {
        assertEquals("Good morning,", clockParts(at(8)).greeting)
        assertEquals("Good afternoon,", clockParts(at(12)).greeting)
        assertEquals("Good afternoon,", clockParts(at(17, 59)).greeting)
        assertEquals("Good evening,", clockParts(at(18)).greeting)
        assertEquals("Good morning,", clockParts(at(0)).greeting)
    }

    @Test
    fun `twelve hour conversion`() {
        assertEquals("12", clockParts(at(0)).h)   // midnight → 12 AM
        assertEquals("AM", clockParts(at(0)).ap)
        assertEquals("12", clockParts(at(12)).h)  // noon → 12 PM
        assertEquals("PM", clockParts(at(12)).ap)
        assertEquals("11", clockParts(at(23)).h)
        assertEquals("PM", clockParts(at(23)).ap)
    }

    @Test
    fun `minutes are zero padded`() {
        assertEquals("05", clockParts(at(9, 5)).m)
    }
}
