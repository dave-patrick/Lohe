package com.lohe.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.EpisodeSummary
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.db.entity.toSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** One queue entry, paired with its show so the row can name the source. */
data class QueueEntry(val episode: EpisodeSummary, val showTitle: String)

/** One in-progress episode, paired with its show. */
data class ContinueEntry(val episode: EpisodeEntity, val showTitle: String)

/** A named user playlist with its episodes resolved from the library. */
data class ResolvedPlaylist(
    val id: String,
    val name: String,
    val entries: List<QueueEntry>,
    val folder: String = "",
    val autoDownload: Boolean = true,
    /**
     * True for the built-in Played and Downloaded lists.
     *
     * They are derived from the library rather than stored, so they cannot be
     * renamed, reordered, deleted, or filed into a folder.
     */
    val system: Boolean = false,
) {
    val totalSeconds: Long
        get() = entries.sumOf { (it.episode.durationSeconds ?: 0).toLong() }
}

/** Ids of the two built-in playlists. */
const val PLAYED_ID = "__system_played__"
const val DOWNLOADED_ID = "__system_downloaded__"

/** How many playlists the Playlists tab shows beyond the listener's own. */
private const val BUILT_IN_PLAYLIST_COUNT = 2

/** How many days the Home activity chart covers. */
private const val ACTIVITY_DAYS = 14

/** How many shows the Home "top shows" chart lists. */
private const val TOP_SHOWS_COUNT = 4

/**
 * Library-wide telemetry for the Home dashboard.
 */
data class HomeDashboardStats(
    /**
     * Whether the library has answered yet.
     *
     * Every count here starts at zero, which is also what an empty library
     * looks like. Without this flag the Home screen cannot tell "no podcasts
     * yet" from "not loaded yet", and briefly shows the empty state to a
     * listener who has three thousand episodes.
     */
    val isLoaded: Boolean = false,
    val subscriptionCount: Int = 0,
    val playlistCount: Int = 0,
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
    /**
     * Minutes listened per day, oldest first.
     *
     * Drives the activity chart on the Home dashboard. Empty when nothing has
     * been played yet.
     */
    val dailyActivityMinutes: List<Int> = emptyList(),
    /**
     * The shows with the most episodes, as (title, episode count).
     *
     * The library's shape is the one thing a count cannot convey, so it is
     * worth showing as a ranked bar chart.
     */
    val topShows: List<ShowActivity> = emptyList(),
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

/** How many episodes of a show are in the library. */
data class ShowActivity(
    val title: String,
    val episodeCount: Int,
)

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
    private val settingsStore: com.lohe.app.ui.theme.SettingsStore? = null,
) : ViewModel() {

    /**
     * Moves listened episodes out of the playlists they were added to.
     *
     * Driven by the library rather than by the individual "mark listened"
     * actions, so an episode that finishes on its own during playback leaves
     * the playlists just as a manual mark does.
     */
    init {
        viewModelScope.launch {
            // The auto-download choice is a setting, not a toggle on the
            // queue's own header: it is about data, and it should still be
            // there the next time the app is opened.
            settingsStore?.settings?.collect { prefs ->
                _autoDownloadUpNext.value = prefs.autoDownloadQueue
                _podcastSort.value = prefs.podcastSort
                _playlistSort.value = prefs.playlistSort
            }
        }
        viewModelScope.launch {
            repository.observeAllEpisodes().collect { episodes ->
                val playedUuids = episodes
                    .filter { it.isPlayed }
                    .mapTo(mutableSetOf()) { it.uuid }
                if (playedUuids.isEmpty()) return@collect
                playlistRepository.playlists.value.forEach { pl ->
                    pl.episodeUuids
                        .filter { it in playedUuids }
                        .forEach { playlistRepository.removeEpisode(pl.id, it) }
                }
            }
        }
    }

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
            QueueEntry(resolved.toSummary(), show?.title.orEmpty())
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
        repository.observeListeningHistory(),
        playlistRepository.playlists,
    ) { shows, allEpisodes, history, playlists ->
        val activeEpisodes = allEpisodes.filter { !it.isDeleted }
        val liveUuids = allEpisodes.mapTo(mutableSetOf()) { it.uuid }
        var totalRemaining = 0L
        var totalPlayed = 0L
        var totalDuration = 0L
        var downloaded = 0
        var playedCount = 0
        var inProgressCount = 0

        for (ep in allEpisodes) {
            // Deleted items that were never played should not be in any count.
            if (ep.isDeleted && !ep.isPlayed) continue

            if (ep.isPlayed) playedCount++

            // Only non-deleted episodes count toward library-level stats.
            if (!ep.isDeleted) {
                if (ep.isDownloaded) downloaded++
                if (ep.isInProgress) inProgressCount++
            }

            // Time stats: played episodes count even if deleted; non-deleted
            // episodes count too.
            if (!ep.isDeleted || ep.isPlayed) {
                val dur = (ep.durationSeconds?.toLong() ?: 0L).coerceAtLeast(ep.playedUpToSeconds)
                // Actual listening time, not the full runtime: marking an
                // episode listened without playing it must not add its whole
                // duration to the total.
                val played = ep.listenedSeconds.coerceIn(0L, dur)
                val rem = if (ep.isPlayed) 0L else (dur - played).coerceAtLeast(0L)

                totalDuration += dur
                totalPlayed += played
                totalRemaining += rem
            }
        }

        // Episodes whose podcast was removed keep their listening time in the
        // history table, so the totals survive unsubscribing.
        for (entry in history) {
            if (entry.episodeUuid in liveUuids) continue
            val dur = (entry.durationSeconds?.toLong() ?: 0L)
            totalDuration += dur
            totalPlayed += entry.listenedSeconds.coerceIn(0L, dur.coerceAtLeast(entry.listenedSeconds))
        }

        HomeDashboardStats(
            isLoaded = true,
            subscriptionCount = shows.size,
            // The Playlists tab shows the two built-in lists as well, so the
            // dashboard count has to match what the listener will find there.
            playlistCount = playlists.size + BUILT_IN_PLAYLIST_COUNT,
            totalEpisodes = activeEpisodes.size,
            downloadedCount = downloaded,
            playedEpisodesCount = playedCount,
            inProgressCount = inProgressCount,
            totalRemainingSeconds = totalRemaining,
            totalPlayedSeconds = totalPlayed,
            totalDurationSeconds = totalDuration,
            dailyActivityMinutes = dailyActivityMinutes(activeEpisodes),
            topShows = topShows(activeEpisodes, shows),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeDashboardStats())

    /**
     * Minutes listened per day for the last [ACTIVITY_DAYS] days, oldest first.
     *
     * Bucketed by the local calendar day an episode was last played on, so
     * the chart answers "when do I actually listen" rather than repeating the
     * same total every day.
     */
    private fun dailyActivityMinutes(episodes: List<EpisodeSummary>): List<Int> {
        if (episodes.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val startOfToday = now - (now % dayMs)
        val buckets = IntArray(ACTIVITY_DAYS)

        episodes.forEach { ep ->
            if (!ep.isPlayed && ep.listenedSeconds <= 0L) return@forEach
            val playedAt = ep.lastPlayedAt ?: return@forEach
            val dayIndex = ((startOfToday - playedAt) / dayMs).toInt()
            if (dayIndex !in 0 until ACTIVITY_DAYS) return@forEach
            val minutes = (ep.listenedSeconds / 60L).toInt()
            buckets[ACTIVITY_DAYS - 1 - dayIndex] += minutes
        }

        return buckets.toList()
    }

    /**
     * The shows with the most episodes, richest first.
     *
     * A count cannot convey the shape of a library, so the dashboard shows
     * the shape instead of another number.
     */
    private fun topShows(
        episodes: List<EpisodeSummary>,
        shows: List<PodcastEntity>,
    ): List<ShowActivity> {
        val titles = shows.associate { it.uuid to it.title }
        val counts = mutableMapOf<String, Int>()
        episodes.forEach { ep ->
            if (ep.isDeleted) return@forEach
            val title = titles[ep.podcastUuid] ?: return@forEach
            counts[title] = (counts[title] ?: 0) + 1
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_SHOWS_COUNT)
            .map { ShowActivity(it.key, it.value) }
    }

    private val _autoDownloadUpNext = MutableStateFlow(true)
    val autoDownloadUpNext: StateFlow<Boolean> = _autoDownloadUpNext.asStateFlow()

    private val _podcastSort = MutableStateFlow(PodcastSort.TITLE)
    val podcastSort: StateFlow<PodcastSort> = _podcastSort.asStateFlow()

    private val _playlistSort = MutableStateFlow(PlaylistSort.MANUAL)
    val playlistSort: StateFlow<PlaylistSort> = _playlistSort.asStateFlow()

    fun setPodcastSort(sort: PodcastSort) {
        _podcastSort.value = sort
        viewModelScope.launch { settingsStore?.setPodcastSort(sort) }
    }

    fun setPlaylistSort(sort: PlaylistSort) {
        _playlistSort.value = sort
        viewModelScope.launch { settingsStore?.setPlaylistSort(sort) }
    }

    val podcastFolders: StateFlow<Map<String, String>> = playlistRepository.podcastFolders

    fun setAutoDownloadUpNext(enabled: Boolean) {
        _autoDownloadUpNext.value = enabled
        viewModelScope.launch { settingsStore?.setAutoDownloadQueue(enabled) }
        if (enabled) {
            viewModelScope.launch {
                queue.value.forEach { triggerDownloadIfNeeded(it.episode) }
            }
        }
    }

    fun setPodcastFolder(podcastUuid: String, folder: String) {
        playlistRepository.setPodcastFolder(podcastUuid, folder)
    }

    private suspend fun triggerDownloadByUuids(uuids: Collection<String>) {
        if (uuids.isEmpty()) return
        val all = repository.observeAllEpisodes().firstOrNull().orEmpty().associateBy { it.uuid }
        uuids.forEach { id ->
            all[id]?.let(::triggerDownloadIfNeeded)
        }
    }

    private fun triggerDownloadIfNeeded(ep: EpisodeSummary) {
        if (!ep.isDownloaded && !ep.isDownloading) {
            runCatching { ServiceLocator.downloadManager().start(ep) }
        }
    }

    fun dequeue(uuid: String) = viewModelScope.launch { repository.dequeue(uuid) }

    fun dequeueBatch(uuids: Collection<String>) = viewModelScope.launch {
        uuids.forEach { repository.dequeue(it) }
    }

    fun promote(uuid: String) = viewModelScope.launch { repository.promote(uuid) }

    fun moveInQueue(uuid: String, delta: Int) = viewModelScope.launch {
        repository.moveInQueue(uuid, delta)
    }

    /** Writes a new queue order, in full, after a row has been dragged. */
    fun reorderQueue(uuids: List<String>) = viewModelScope.launch {
        repository.reorderQueue(uuids)
    }

    fun clearAllQueue() = viewModelScope.launch { repository.clearAllQueue() }

    /** "Played up to here": drops this entry and everything after it. */
    fun clearFrom(uuid: String) = viewModelScope.launch { repository.clearQueueFrom(uuid) }

    fun enqueue(uuid: String) = viewModelScope.launch {
        repository.enqueue(uuid)
        if (_autoDownloadUpNext.value) {
            triggerDownloadByUuids(listOf(uuid))
        }
    }

    fun enqueueAll(uuids: Collection<String>) = viewModelScope.launch {
        uuids.forEach { repository.enqueue(it) }
        if (_autoDownloadUpNext.value) {
            triggerDownloadByUuids(uuids)
        }
    }

    fun addPlaylistToUpNext(playlistId: String) = viewModelScope.launch {
        // Resolved through userPlaylists so the built-in Played and Downloaded
        // lists work the same way as the ones the user made.
        val entries = userPlaylists.value.firstOrNull { it.id == playlistId }
            ?.entries
            ?: return@launch
        val uuids = entries.map { it.episode.uuid }
        uuids.forEach { repository.enqueue(it) }
        if (_autoDownloadUpNext.value) {
            triggerDownloadByUuids(uuids)
        }
    }

    /**
     * Every playlist, including the two system ones.
     *
     * [PLAYED_ID] and [DOWNLOADED_ID] are computed rather than stored: they
     * are defined by the state of the library, so they cannot drift out of
     * date the way a stored list would.
     */
    val userPlaylists: StateFlow<List<ResolvedPlaylist>> = combine(
        playlistRepository.playlists,
        repository.observeAllEpisodes(),
        podcasts,
    ) { rawPlaylists, allEpisodes, shows ->
        val epMap = allEpisodes.associateBy { it.uuid }
        val showMap = shows.associateBy { it.uuid }

        fun resolve(ep: EpisodeSummary): QueueEntry {
            val show = showMap[ep.podcastUuid]
            val withArt = if (ep.artworkUrl.isNullOrBlank() && !show?.artworkUrl.isNullOrBlank()) {
                ep.copy(artworkUrl = show?.artworkUrl)
            } else {
                ep
            }
            return QueueEntry(withArt, show?.title.orEmpty())
        }

        val custom = rawPlaylists.map { pl ->
            val resolvedEntries = pl.episodeUuids.mapNotNull { epId ->
                val ep = epMap[epId] ?: return@mapNotNull null
                // Episodes already listened to leave the playlist and show up
                // in Played instead, so the list only holds what is left to
                // hear.
                if (ep.isPlayed) null else resolve(ep)
            }
            ResolvedPlaylist(
                id = pl.id,
                name = pl.name,
                entries = resolvedEntries,
                folder = pl.folder,
                autoDownload = pl.autoDownload,
            )
        }

        val live = allEpisodes.filter { !it.isDeleted }
        val played = live.filter { it.isPlayed }.map(::resolve)
        val downloaded = live.filter { it.isDownloaded }.map(::resolve)

        custom + listOf(
            ResolvedPlaylist(
                id = PLAYED_ID,
                name = "Played",
                entries = played,
                folder = "",
                autoDownload = false,
                system = true,
            ),
            ResolvedPlaylist(
                id = DOWNLOADED_ID,
                name = "Downloaded",
                entries = downloaded,
                folder = "",
                autoDownload = false,
                system = true,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Drops [uuid] from every user playlist — it has been listened to. */
    fun onEpisodePlayed(uuid: String) {
        playlistRepository.removeEpisodeFromAll(uuid)
    }

    /** Marks an episode listened, which also takes it out of the playlists. */
    fun markPlayed(uuid: String) = viewModelScope.launch {
        repository.markPlayed(uuid)
        playlistRepository.removeEpisodeFromAll(uuid)
    }

    /** Undoes [markPlayed]. */
    fun reopenEpisode(uuid: String) = viewModelScope.launch {
        repository.reopenEpisode(uuid)
    }

    /** Soft-deletes an episode and drops it from the playlists. */
    fun deleteEpisode(uuid: String) = viewModelScope.launch {
        repository.deleteEpisode(uuid)
        playlistRepository.removeEpisodeFromAll(uuid)
    }

    fun createPlaylist(
        name: String,
        initialEpisodeUuids: Collection<String> = emptyList(),
        folder: String = "",
        autoDownload: Boolean = true,
    ): UserPlaylist {
        val created = playlistRepository.createPlaylist(name, initialEpisodeUuids, folder, autoDownload)
        if (autoDownload && initialEpisodeUuids.isNotEmpty()) {
            viewModelScope.launch { triggerDownloadByUuids(initialEpisodeUuids) }
        }
        return created
    }

    fun deletePlaylist(playlistId: String) = playlistRepository.deletePlaylist(playlistId)

    fun movePlaylist(playlistId: String, delta: Int) =
        playlistRepository.movePlaylist(playlistId, delta)

    fun reorderPlaylists(playlistIds: List<String>) =
        playlistRepository.reorderPlaylists(playlistIds)

    fun setPlaylistFolder(playlistId: String, folder: String) =
        playlistRepository.setPlaylistFolder(playlistId, folder)

    fun setPlaylistAutoDownload(playlistId: String, enabled: Boolean) {
        playlistRepository.setPlaylistAutoDownload(playlistId, enabled)
        if (enabled) {
            val pl = playlistRepository.playlists.value.firstOrNull { it.id == playlistId } ?: return
            viewModelScope.launch { triggerDownloadByUuids(pl.episodeUuids) }
        }
    }

    fun addEpisodesToPlaylist(playlistId: String, episodeUuids: Collection<String>) {
        playlistRepository.addEpisodes(playlistId, episodeUuids)
        val pl = playlistRepository.playlists.value.firstOrNull { it.id == playlistId }
        if (pl?.autoDownload == true) {
            viewModelScope.launch { triggerDownloadByUuids(episodeUuids) }
        }
    }

    fun removeEpisodeFromPlaylist(playlistId: String, episodeUuid: String) =
        playlistRepository.removeEpisode(playlistId, episodeUuid)

    /** Marks a batch of episodes as listened to. */
    fun markPlayedBatch(uuids: Collection<String>) = viewModelScope.launch {
        uuids.forEach { repository.markPlayed(it) }
    }

    /**
     * Marks a batch as not listened to.
     *
     * This is how a listener clears the Played list: an episode that is no
     * longer played leaves it, because the list is derived from the library
     * rather than stored.
     */
    fun markUnplayedBatch(uuids: Collection<String>) = viewModelScope.launch {
        uuids.forEach { repository.reopenEpisode(it) }
    }

    /** Takes a batch of episodes out of a playlist. */
    fun removeEpisodesFromPlaylist(playlistId: String, uuids: Collection<String>) {
        uuids.forEach { playlistRepository.removeEpisode(playlistId, it) }
    }

    fun moveEpisodeInPlaylist(playlistId: String, episodeUuid: String, delta: Int) =
        playlistRepository.moveEpisode(playlistId, episodeUuid, delta)

    /** Writes a new episode order for a playlist, after a row has been dragged. */
    fun reorderPlaylistEpisodes(playlistId: String, episodeUuids: List<String>) =
        playlistRepository.reorderEpisodes(playlistId, episodeUuids)

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
            ServiceLocator.settingsStore(),
        ) as T
}
