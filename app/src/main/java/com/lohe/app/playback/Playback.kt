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

    private val _voiceEnhancement = MutableStateFlow(false)

    /** Whether voice enhancement (mid-frequency boost) is enabled. */
    val voiceEnhancement: StateFlow<Boolean> = _voiceEnhancement.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)

    /** Current playback speed multiplier (0.5x .. 3.0x). */
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)

    /** Whether ExoPlayer silence trimming is enabled. */
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    fun setVolumeBoost(enabled: Boolean) {
        _volumeBoost.value = enabled
    }

    fun setVoiceEnhancement(enabled: Boolean) {
        _voiceEnhancement.value = enabled
    }

    // Aliases for callers that name the feature differently.
    fun setEnhanceVoices(enabled: Boolean) = setVoiceEnhancement(enabled)
    fun setVoiceBoost(enabled: Boolean) = setVoiceEnhancement(enabled)

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed.coerceIn(0.5f, 3.0f)
    }

    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
    }

    /** Test seam: drops any armed timer and resets boost/speed. */
    fun resetForTests() {
        sleepTimer.cancel()
        _volumeBoost.value = false
        _voiceEnhancement.value = false
        _playbackSpeed.value = 1f
        _skipSilence.value = false
    }
}
