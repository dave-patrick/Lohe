package com.lohe.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatBytesTest {

    @Test
    fun `bytes below a kilobyte`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1 B", formatBytes(1))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `exactly one kilobyte`() {
        assertEquals("1 kB", formatBytes(1024))
    }

    @Test
    fun `kilobytes are truncated`() {
        assertEquals("1 kB", formatBytes(1536))
    }

    @Test
    fun `megabytes carry one decimal`() {
        assertEquals("1.5 MB", formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("1 MB", formatBytes(1024 * 1024))
    }

    @Test
    fun `gigabytes carry one decimal`() {
        assertEquals("2.5 GB", formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `a whole value drops its decimal`() {
        // "3 GB" reads better than "3.0 GB" in a storage list.
        assertEquals("3 GB", formatBytes(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun `units never exceed three significant characters`() {
        listOf(0L, 1024L, 5_242_880L, 3_221_225_472L).forEach { bytes ->
            val rendered = formatBytes(bytes)
            assertTrue("'$rendered' is too long", rendered.length <= 7)
        }
    }
}
