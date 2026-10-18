package com.lohe.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A clock the test moves by hand, so timer behaviour is exact rather than
 * dependent on real elapsed time.
 */
private class FakeClock(var now: Long = 0L) : () -> Long {
    override fun invoke(): Long = now
}

class SleepTimerTest {

    private fun timerAt(vararg marks: Long): Pair<FakeClock, SleepTimer> {
        val clock = FakeClock(marks.firstOrNull() ?: 0L)
        return clock to SleepTimer(clock)
    }

    @Test
    fun `starts inactive`() {
        val (_, timer) = timerAt()
        assertFalse(timer.state.value.active)
        assertFalse(timer.isDue())
        assertEquals(0L, timer.remainingMs())
    }

    @Test
    fun `is not due before its deadline`() {
        val (clock, timer) = timerAt()
        timer.start(SleepTimer.Mode.Duration(10))

        clock.now = 9 * 60_000
        assertFalse("fired early", timer.isDue())
        assertTrue(timer.state.value.active)
    }

    @Test
    fun `is due once the deadline passes`() {
        val (clock, timer) = timerAt()
        timer.start(SleepTimer.Mode.Duration(10))

        clock.now = 10 * 60_000
        assertTrue("never became due", timer.isDue())
    }

    @Test
    fun `a late check still reports due rather than overshooting`() {
        val (clock, timer) = timerAt()
        timer.start(SleepTimer.Mode.Duration(1))

        // Simulates the poller being starved well past the deadline.
        clock.now += 10 * 60_000
        assertTrue("a starved poll left the timer pending", timer.isDue())
    }

    @Test
    fun `remaining counts down with the clock and floors at zero`() {
        val (clock, timer) = timerAt()
        timer.start(SleepTimer.Mode.Duration(5))

        clock.now = 0
        assertEquals(5 * 60_000L, timer.remainingMs())

        clock.now = 2 * 60_000
        assertEquals(3 * 60_000L, timer.remainingMs())

        clock.now = 9 * 60_000
        assertEquals("remaining must never go negative", 0L, timer.remainingMs())
    }

    @Test
    fun `cancel stops a pending deadline`() {
        val (clock, timer) = timerAt()
        timer.start(SleepTimer.Mode.Duration(10))
        clock.now = 5 * 60_000

        timer.cancel()

        assertFalse(timer.state.value.active)
        assertFalse("cancelled timer still came due", timer.isDue())
        assertEquals(0L, timer.remainingMs())
    }

    @Test
    fun `restarting replaces the previous deadline`() {
        val (clock, timer) = timerAt()
        // Arms at t=0 with a 30 minute deadline.
        timer.start(SleepTimer.Mode.Duration(30))

        // At t=20 the user picks 5 minutes instead, so the deadline moves to t=25.
        clock.now = 20 * 60_000
        timer.start(SleepTimer.Mode.Duration(5))

        clock.now = 24 * 60_000
        assertFalse("abandoned 30-minute deadline still applies", timer.isDue())
        assertEquals(60_000L, timer.remainingMs())

        clock.now = 25 * 60_000
        assertTrue("new deadline never applied", timer.isDue())
    }

    @Test
    fun `restarting from end-of-episode to a duration arms the clock`() {
        val (clock, timer) = timerAt()
        timer.start(SleepTimer.Mode.EndOfEpisode)
        assertFalse(timer.isDue())

        timer.start(SleepTimer.Mode.Duration(1))
        clock.now = 60_000
        assertTrue(timer.isDue())
    }

    @Test
    fun `end of episode mode is active but never comes due`() {
        val (clock, timer) = timerAt()
        timer.start(SleepTimer.Mode.EndOfEpisode)

        assertTrue(timer.state.value.active)
        assertTrue(timer.state.value.stopsAtEpisodeEnd)
        assertEquals(SleepTimer.Mode.EndOfEpisode, timer.state.value.mode)

        clock.now += 60 * 60_000
        assertFalse("end-of-episode must not fire on a timer", timer.isDue())
        assertEquals(0L, timer.remainingMs())
    }

    @Test
    fun `duration mode does not stop at episode end`() {
        val (_, timer) = timerAt()
        timer.start(SleepTimer.Mode.Duration(30))
        assertFalse(timer.state.value.stopsAtEpisodeEnd)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a zero duration`() {
        timerAt().second.start(SleepTimer.Mode.Duration(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a negative duration`() {
        timerAt().second.start(SleepTimer.Mode.Duration(-5))
    }

    @Test
    fun `a cleared timer reports neither due nor active`() {
        val (clock, timer) = timerAt()
        timer.start(SleepTimer.Mode.Duration(1))
        clock.now = 120_000
        timer.cancel()

        assertFalse(timer.isDue())
        assertFalse(timer.state.value.active)
        assertFalse(timer.state.value.stopsAtEpisodeEnd)
    }

    @Test
    fun `state flow reflects arming and cancelling`() {
        val (_, timer) = timerAt()
        assertEquals(null, timer.state.value.mode)

        timer.start(SleepTimer.Mode.Duration(15))
        assertEquals(SleepTimer.Mode.Duration(15), timer.state.value.mode)

        timer.cancel()
        assertEquals(null, timer.state.value.mode)
    }

    @Test
    fun `exact deadline boundary is due`() {
        val (clock, timer) = timerAt()
        timer.start(SleepTimer.Mode.Duration(3))
        clock.now = 3 * 60_000 - 1
        assertFalse(timer.isDue())
        clock.now = 3 * 60_000
        assertTrue(timer.isDue())
    }
}
