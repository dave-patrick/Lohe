package com.lohe.app.playback

import androidx.media3.common.Player
import com.lohe.app.data.ServiceLocator

/** Completes the outgoing episode work before replacing the session's media. */
internal suspend fun pauseInterruptedPlayback(
    player: Player,
    episodeId: String,
    onBeforeSwitchEpisode: suspend (String) -> Unit,
) {
    // Read the outgoing item before pause/stop and replacement can change the
    // controller snapshot. A paused player may never emit another pause event.
    val positionSeconds = player.currentPosition.coerceAtLeast(0L) / 1_000L
    val durationSeconds = player.duration.takeIf { it > 0L }?.div(1_000L) ?: 0L
    player.pause()
    ServiceLocator.subscriptionRepository().saveProgress(episodeId, positionSeconds, durationSeconds)
    onBeforeSwitchEpisode(episodeId)
}
