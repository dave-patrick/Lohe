package com.lohe.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTimestampTest {

    @Test
    fun `zero and negative render as 00 00`() {
        assertEquals("00:00", formatTimestamp(0L))
        assertEquals("00:00", formatTimestamp(-5_000L))
    }

    @Test
    fun `seconds only`() {
        assertEquals("00:07", formatTimestamp(7_000L))
    }

    @Test
    fun `minutes and seconds are padded`() {
        assertEquals("01:05", formatTimestamp(65_000L))
        assertEquals("09:59", formatTimestamp(599_000L))
    }

    @Test
    fun `an exact minute reads as minutes with a zero seconds part`() {
        assertEquals("05:00", formatTimestamp(300_000L))
    }

    @Test
    fun `hours get their own field`() {
        assertEquals("1:00:00", formatTimestamp(3_600_000L))
        assertEquals("2:03:04", formatTimestamp(2 * 3_600_000L + 3 * 60_000L + 4_000L))
    }

    @Test
    fun `sub-second remainders truncate rather than round up`() {
        assertEquals("00:00", formatTimestamp(999L))
        assertEquals("00:01", formatTimestamp(1_999L))
    }

    @Test
    fun `very long durations still render`() {
        assertEquals("27:46:40", formatTimestamp(100_000_000L))
    }
}
