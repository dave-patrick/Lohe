package com.lohe.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped playback singletons.
 *
 * The sleep timer and volume-boost toggle live here rather than inside
 * [PlaybackService] because both sides need them: the service applies them to
 * the player, and the UI reads/writes them without custom MediaSession IPC.
 */
object Playback {

    val sleepTimer: SleepTimer by lazy { SleepTimer() }

    /** Current timer state, for collectors. */
    val sleepState: StateFlow<SleepTimer.State> get() = sleepTimer.state

    private val _volumeBoost = MutableStateFlow(false)

    /** Whether loudness boost above 0 dB is enabled. */
    val volumeBoost: StateFlow<Boolean> = _volumeBoost.asStateFlow()

    fun setVolumeBoost(enabled: Boolean) {
        _volumeBoost.value = enabled
    }

    /** Test seam: drops any armed timer and resets boost. */
    fun resetForTests() {
        sleepTimer.cancel()
        _volumeBoost.value = false
    }
}
