package com.lohe.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sleep timer for the player.
 *
 * The rules are synchronous and side-effect free: [start] records a deadline,
 * [isDue] reports whether it has passed, and the caller decides what to do
 * about it. Nothing here schedules or launches work.
 *
 * That split is deliberate. When this owned its own coroutine, the interesting
 * behaviour — what happens after a delayed tick, after cancel, after restart —
 * could only be tested with a virtual clock and a test dispatcher. As a pure
 * predicate it is checked directly, and the only untested part left is the
 * caller's polling loop.
 *
 * Polling frequency is the caller's business; [remainingMs] exists so a UI can
 * show a countdown without owning a timer of its own.
 */
class SleepTimer(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    sealed interface Mode {
        /** Stop when the current episode reaches its end. */
        data object EndOfEpisode : Mode

        /** Stop after a fixed wall-clock duration. */
        data class Duration(val minutes: Int) : Mode
    }

    data class State(
        val mode: Mode? = null,
        val deadlineMs: Long? = null,
    ) {
        val active: Boolean get() = mode != null

        /** True when the episode finishing is what should end playback. */
        val stopsAtEpisodeEnd: Boolean get() = mode == Mode.EndOfEpisode
    }

    private val _state = MutableStateFlow(State())

    /** Observable for UI; a StateFlow so a countdown can collect remaining time. */
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Arms the timer, replacing any existing deadline.
     *
     * @throws IllegalArgumentException if [mode] is a non-positive duration.
     */
    fun start(mode: Mode) {
        _state.value = when (mode) {
            is Mode.EndOfEpisode -> State(mode = mode, deadlineMs = null)
            is Mode.Duration -> {
                require(mode.minutes > 0) { "duration must be positive" }
                State(mode = mode, deadlineMs = nowMs() + mode.minutes * 60_000L)
            }
        }
    }

    fun cancel() {
        _state.value = State()
    }

    /**
     * Whether a timed deadline has passed.
     *
     * Always false for [Mode.EndOfEpisode], which is triggered by the player
     * reporting [endReached] rather than by the clock.
     */
    fun isDue(): Boolean {
        val deadline = _state.value.deadlineMs ?: return false
        return nowMs() >= deadline
    }

    /** Milliseconds left, or 0 when the timer is off or already elapsed. */
    fun remainingMs(): Long {
        val deadline = _state.value.deadlineMs ?: return 0L
        return (deadline - nowMs()).coerceAtLeast(0L)
    }
}
