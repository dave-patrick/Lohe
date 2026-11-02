package com.lohe.app.data

/** Shared completion policy for playback, imported positions, and legacy repairs. */
const val EPISODE_COMPLETION_FRACTION = 0.95
const val EPISODE_COMPLETION_TAIL_SECONDS = 10L

internal fun isEpisodeComplete(positionSeconds: Long, durationSeconds: Long): Boolean =
    positionSeconds > 0 && durationSeconds > 0 &&
        (positionSeconds.toDouble() / durationSeconds >= EPISODE_COMPLETION_FRACTION ||
            durationSeconds - positionSeconds <= EPISODE_COMPLETION_TAIL_SECONDS)
