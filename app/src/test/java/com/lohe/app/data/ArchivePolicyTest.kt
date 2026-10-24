package com.lohe.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePolicyTest {
    private val completedAt = 1_800_000_000_000L

    @Test
    fun `waits for the full 24 hours`() {
        assertFalse(shouldAutoArchive(true, false, completedAt, 24, completedAt + 86_399_999L))
        assertTrue(shouldAutoArchive(true, false, completedAt, 24, completedAt + 86_400_000L))
        assertTrue(shouldAutoArchive(true, false, completedAt, 24, completedAt + 90_000_000L))
    }

    @Test
    fun `off unplayed archived and unknown completion times are not eligible`() {
        assertFalse(shouldAutoArchive(true, false, completedAt, -1, Long.MAX_VALUE))
        assertFalse(shouldAutoArchive(false, false, completedAt, 24, Long.MAX_VALUE))
        assertFalse(shouldAutoArchive(true, true, completedAt, 24, Long.MAX_VALUE))
        assertFalse(shouldAutoArchive(true, false, null, 24, Long.MAX_VALUE))
    }

    @Test
    fun `supports immediate one hour and seven day delays without early clock rollback cleanup`() {
        assertTrue(shouldAutoArchive(true, false, completedAt, 0, completedAt))
        assertTrue(shouldAutoArchive(true, false, completedAt, 1, completedAt + 3_600_000L))
        assertFalse(shouldAutoArchive(true, false, completedAt, 168, completedAt + 86_400_000L))
        assertTrue(shouldAutoArchive(true, false, completedAt, 168, completedAt + 604_800_000L))
        assertFalse(shouldAutoArchive(true, false, completedAt, 0, completedAt - 1))
    }
}
