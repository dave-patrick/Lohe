package com.lohe.app.playback

import android.content.Context
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.isEpisodeComplete

/** Identity is recorded only after playback starts; positions remain in the episode database. */
internal class PlaybackResumptionStore(context: Context) {
    private val preferences = context.getSharedPreferences("playback-resumption", Context.MODE_PRIVATE)
    val hasSavedEpisode: Boolean get() = preferences.contains("last_episode_id")
    val episodeId: String? get() = preferences.getString("last_episode_id", null)

    fun rememberPlaying(episodeId: String) {
        if (episodeId.isNotBlank()) preferences.edit().putString("last_episode_id", episodeId).apply()
    }
}

/** A stale saved choice never silently changes to some other episode. */
internal suspend fun playbackResumptionEpisode(
    repository: SubscriptionRepository,
    store: PlaybackResumptionStore,
): EpisodeEntity? {
    val episode = if (store.hasSavedEpisode) {
        store.episodeId?.takeIf { it.isNotBlank() }?.let { repository.episodeOnce(it) }
    } else {
        repository.lastPlaybackEpisode()
    } ?: return null
    return episode.takeUnless {
        it.isDeleted || it.isArchived || it.isPlayed ||
            isEpisodeComplete(it.playedUpToSeconds, it.durationSeconds?.toLong() ?: 0L)
    }
}
