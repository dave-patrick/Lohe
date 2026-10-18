package com.lohe.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One queue entry, paired with its show so the row can name the source. */
data class QueueEntry(val episode: EpisodeEntity, val showTitle: String)

/** One in-progress episode, paired with its show. */
data class ContinueEntry(val episode: EpisodeEntity, val showTitle: String)

/** A named user playlist with its episodes resolved from the library. */
data class ResolvedPlaylist(
    val id: String,
    val name: String,
    val entries: List<QueueEntry>,
) {
    val totalSeconds: Long
        get() = entries.sumOf { (it.episode.durationSeconds ?: 0).toLong() }
}

/**
 * Library-wide telemetry for the Home dashboard.
 */
data class HomeDashboardStats(
    val subscriptionCount: Int = 0,
    val totalEpisodes: Int = 0,
    val downloadedCount: Int = 0,
    val playedEpisodesCount: Int = 0,
    val inProgressCount: Int = 0,
    /** Total remaining unplayed time across all files, in seconds. */
    val totalRemainingSeconds: Long = 0L,
    /** Total time played/listened across all files, in seconds. */
    val totalPlayedSeconds: Long = 0L,
    /** Combined duration of all files altogether, in seconds. */
    val totalDurationSeconds: Long = 0L,
) {
    val listenedFraction: Float
        get() = if (totalDurationSeconds > 0L) {
            (totalPlayedSeconds.toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val downloadedFraction: Float
        get() = if (totalEpisodes > 0) {
            (downloadedCount.toFloat() / totalEpisodes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val completedEpisodesFraction: Float
        get() = if (totalEpisodes > 0) {
            (playedEpisodesCount.toFloat() / totalEpisodes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * Backs the queue and the home screen's "continue listening".
 *
 * Both screens need episodes joined to their show title, and both need that
 * join to stay live as the library changes — so the join happens once here
 * rather than twice in the UI.
 */
class QueueViewModel(
    private val repository: SubscriptionRepository,
    private val playlistRepository: PlaylistRepository = PlaylistRepository(),
) : ViewModel() {

    private val podcasts: StateFlow<List<PodcastEntity>> = repository
        .observePodcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val queue: StateFlow<List<QueueEntry>> = combine(
        repository.observeQueue(),
        podcasts,
    ) { episodes, shows ->
        val byUuid = shows.associateBy { it.uuid }
        episodes.map { ep ->
            val show = byUuid[ep.podcastUuid]
            val resolved = if (ep.artworkUrl.isNullOrBlank() && !show?.artworkUrl.isNullOrBlank()) {
                ep.copy(artworkUrl = show?.artworkUrl)
            } else {
                ep
            }
            QueueEntry(resolved, show?.title.orEmpty())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val continueListening: StateFlow<List<ContinueEntry>> = combine(
        repository.observeInProgress(),
        podcasts,
    ) { episodes, shows ->
        val byUuid = shows.associateBy { it.uuid }
        episodes.map { ep ->
            val show = byUuid[ep.podcastUuid]
            val resolved = if (ep.artworkUrl.isNullOrBlank() && !show?.artworkUrl.isNullOrBlank()) {
                ep.copy(artworkUrl = show?.artworkUrl)
            } else {
                ep
            }
            ContinueEntry(resolved, show?.title.orEmpty())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dashboardStats: StateFlow<HomeDashboardStats> = combine(
        podcasts,
        repository.observeAllEpisodes(),
    ) { shows, allEpisodes ->
        var totalRemaining = 0L
        var totalPlayed = 0L
        var totalDuration = 0L
        var downloaded = 0
        var playedCount = 0
        var inProgressCount = 0

        for (ep in allEpisodes) {
            if (ep.isDownloaded) downloaded++
            if (ep.isPlayed) playedCount++
            if (ep.isInProgress) inProgressCount++

            val dur = (ep.durationSeconds?.toLong() ?: 0L).coerceAtLeast(ep.playedUpToSeconds)
            val played = if (ep.isPlayed) dur else ep.playedUpToSeconds.coerceIn(0L, dur)
            val rem = if (ep.isPlayed) 0L else (dur - played).coerceAtLeast(0L)

            totalDuration += dur
            totalPlayed += played
            totalRemaining += rem
        }

        HomeDashboardStats(
            subscriptionCount = shows.size,
            totalEpisodes = allEpisodes.size,
            downloadedCount = downloaded,
            playedEpisodesCount = playedCount,
            inProgressCount = inProgressCount,
            totalRemainingSeconds = totalRemaining,
            totalPlayedSeconds = totalPlayed,
            totalDurationSeconds = totalDuration,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeDashboardStats())

    fun dequeue(uuid: String) = viewModelScope.launch { repository.dequeue(uuid) }

    fun promote(uuid: String) = viewModelScope.launch { repository.promote(uuid) }

    /** "Played up to here": drops this entry and everything after it. */
    fun clearFrom(uuid: String) = viewModelScope.launch { repository.clearQueueFrom(uuid) }

    fun enqueue(uuid: String) = viewModelScope.launch { repository.enqueue(uuid) }

    fun enqueueAll(uuids: Collection<String>) = viewModelScope.launch {
        uuids.forEach { repository.enqueue(it) }
    }

    val userPlaylists: StateFlow<List<ResolvedPlaylist>> = combine(
        playlistRepository.playlists,
        repository.observeAllEpisodes(),
        podcasts,
    ) { rawPlaylists, allEpisodes, shows ->
        val epMap = allEpisodes.associateBy { it.uuid }
        val showMap = shows.associateBy { it.uuid }
        rawPlaylists.map { pl ->
            val resolvedEntries = pl.episodeUuids.mapNotNull { epId ->
                val ep = epMap[epId] ?: return@mapNotNull null
                val show = showMap[ep.podcastUuid]
                val withArt = if (ep.artworkUrl.isNullOrBlank() && !show?.artworkUrl.isNullOrBlank()) {
                    ep.copy(artworkUrl = show?.artworkUrl)
                } else {
                    ep
                }
                QueueEntry(withArt, show?.title.orEmpty())
            }
            ResolvedPlaylist(
                id = pl.id,
                name = pl.name,
                entries = resolvedEntries,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createPlaylist(
        name: String,
        initialEpisodeUuids: Collection<String> = emptyList(),
    ): UserPlaylist = playlistRepository.createPlaylist(name, initialEpisodeUuids)

    fun deletePlaylist(playlistId: String) = playlistRepository.deletePlaylist(playlistId)

    fun addEpisodesToPlaylist(playlistId: String, episodeUuids: Collection<String>) =
        playlistRepository.addEpisodes(playlistId, episodeUuids)

    fun removeEpisodeFromPlaylist(playlistId: String, episodeUuid: String) =
        playlistRepository.removeEpisode(playlistId, episodeUuid)

    /** Total remaining time across the queue, in seconds; unknown durations ignored. */
    val queuedSeconds: StateFlow<Long> = queue
        .map { entries ->
            entries.sumOf { entry ->
                val duration = entry.episode.durationSeconds ?: 0
                (duration - entry.episode.playedUpToSeconds).coerceAtLeast(0L)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
}

class QueueViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        QueueViewModel(
            ServiceLocator.subscriptionRepository(),
            ServiceLocator.playlistRepository(),
        ) as T
}
