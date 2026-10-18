package com.lohe.app.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Process-scoped playback singletons.
 *
 * The sleep timer lives here rather than inside [PlaybackService] because both
 * sides need it: the service polls it to stop playback, and the UI reads it to
 * render the countdown. Passing it across the session boundary would mean
 * custom MediaSession commands for what is purely in-process state.
 */
object Playback {

    val sleepTimer: SleepTimer by lazy { SleepTimer() }

    /** Current timer state, for collectors. */
    val sleepState: StateFlow<SleepTimer.State> get() = sleepTimer.state

    /** Test seam: drops any armed timer. */
    fun resetForTests() {
        sleepTimer.cancel()
    }
}
