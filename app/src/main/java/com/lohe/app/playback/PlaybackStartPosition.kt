package com.lohe.app.playback

/** Stored positions near the end represent completion, rather than a resume. */
internal fun playbackStartPositionMs(savedSeconds: Long, durationMs: Long): Long {
    if (savedSeconds <= 0L) return 0L
    val savedMs = savedSeconds.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
    if (durationMs > 0L && durationMs - savedMs < 10_000L) return 0L
    return savedMs
}
