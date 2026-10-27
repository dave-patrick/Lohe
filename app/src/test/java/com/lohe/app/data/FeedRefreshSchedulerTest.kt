package com.lohe.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRefreshSchedulerTest {
    private val interval = FeedRefreshScheduler.MIN_INTERVAL_MILLIS

    @Test
    fun `a first run is always due`() {
        assertTrue(FeedRefreshScheduler.isDue(0L, 1_000L))
    }

    @Test
    fun `a recent success is not due again`() {
        assertFalse(FeedRefreshScheduler.isDue(10_000L, 10_000L + interval - 1))
    }

    @Test
    fun `becomes due once the interval has passed`() {
        assertTrue(FeedRefreshScheduler.isDue(10_000L, 10_000L + interval))
    }

    @Test
    fun `a clock that moved backwards does not block refreshing`() {
        assertTrue(FeedRefreshScheduler.isDue(50_000L, 1_000L))
    }

    @Test
    fun `force overrides the throttle`() {
        assertTrue(FeedRefreshScheduler.isDue(10_000L, 10_001L, force = true))
    }
}
