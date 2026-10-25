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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
     * True for built-in playlists derived from episode state.
     *
     * They are derived from the library rather than stored, so they cannot be
     * renamed, reordered, deleted, or filed into a folder.
     */
    val system: Boolean = false,
    val pinned: Boolean = false,
) {
    val totalSeconds: Long
        get() = entries.sumOf { (it.episode.durationSeconds ?: 0).toLong() }
}

/** Where a batch of episodes lands when it is added to Up Next. */
enum class QueuePosition { TOP, BOTTOM }
enum class UpNextPosition { TOP, BOTTOM }
enum class Position { TOP, BOTTOM }
enum class AddPosition { TOP, BOTTOM }
enum class InsertPosition { TOP, BOTTOM }
enum class QueueInsertPosition { TOP, BOTTOM }

/** Ids of the built-in smart playlists. */
const val PLAYED_ID = "__system_played__"
const val DOWNLOADED_ID = "__system_downloaded__"
const val IN_PROGRESS_ID = "__system_in_progress__"
const val UNPLAYED_ID = "__system_unplayed__"
const val NOT_DOWNLOADED_ID = "__system_not_downloaded__"
const val NEW_RELEASES_ID = "__system_new_releases__"

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
     * Time saved by playing faster and skipping silence, in seconds.
     *
     * The difference between how long an episode runs and how long it actually
     * took to get through it. Only counted for episodes that were really
     * listened to: an episode marked played without being heard is not a
     * saving, and counting it as one would be a number that means nothing.
     */
    val timeSavedSeconds: Long = 0L,
    /**
     * Minutes listened per day, oldest first.
     *
     * Drives the activity chart on the Home dashboard. Empty when nothing has
     * been played yet.
     */
    val dailyActivityMinutes: List<Int> = emptyList(),
    /**
     * The shows that carry the library, and how much of each has been heard.
     *
     * Two rankings in one list: the shows with the most episodes, and the
     * shows with the most listened to, merged so a show that is short but
     * much listened to still appears.
     */
    val showActivity: List<ShowActivity> = emptyList(),
    /**
     * The shows with the most episodes listened to, ranked by that count.
     *
     * A different ranking from [showActivity]: the show listened to most is
     * not the one with the longest back catalogue.
     */
    val mostListenedShows: List<ShowActivity> = emptyList(),
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
    /** How many of those episodes have been listened to. */
    val listenedCount: Int = 0,
    /** The show's cover, so the chart can be looked at rather than read. */
    val artworkUrl: String? = null,
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
        settingsStore?.settings ?: flowOf(com.lohe.app.ui.theme.PlaybackSettings()),
    ) { shows, allEpisodes, history, playlists, settings ->
        // Archived episodes leave active library counts. Keep archived rows
        // with real listening time in the signal totals, though, so the 24-hour
        // archive policy cannot erase listening progress from the dashboard.
        val activeEpisodes = allEpisodes.filter { !it.isDeleted && !it.isArchived }
        val liveUuids = allEpisodes.mapTo(mutableSetOf()) { it.uuid }
        var totalRemaining = 0L
        var totalPlayed = 0L
        var totalDuration = 0L
        var timeSaved = 0L
        var downloaded = 0
        var playedCount = 0
        var inProgressCount = 0

        for (ep in allEpisodes) {
            val archivedListening = ep.isArchived && ep.listenedSeconds > 0L
            // Old/imported archives without actual listening time should not
            // inflate either side of the listening signal.
            if (ep.isArchived && !archivedListening) continue

            // Deleted items that were never played should not be in any count.
            if (!ep.isArchived && ep.isDeleted && !ep.isPlayed) continue

            // Episode counts and downloads describe the active library only.
            if (!ep.isArchived) {
                if (ep.isPlayed) playedCount++
                if (!ep.isDeleted) {
                    if (ep.isDownloaded) downloaded++
                    if (ep.isInProgress) inProgressCount++
                }
            }

            // Time stats retain actual listening after an episode is archived.
            if (ep.isArchived || !ep.isDeleted || ep.isPlayed) {
                val dur = (ep.durationSeconds?.toLong() ?: 0L)
                    .coerceAtLeast(ep.playedUpToSeconds)
                    .coerceAtLeast(if (ep.isArchived) ep.listenedSeconds else 0L)
                // Actual listening time, not the full runtime: marking an
                // episode listened without playing it must not add its whole
                // duration to the total.
                val played = ep.listenedSeconds.coerceIn(0L, dur)
                val rem = if (ep.isArchived || ep.isPlayed) 0L else (dur - played).coerceAtLeast(0L)

                totalDuration += dur
                totalPlayed += played
                totalRemaining += rem

                // Time saved by playing faster and skipping silence. Only
                // for episodes that were really listened to: one marked
                // played without being heard is not a saving.
                if (ep.isPlayed && ep.listenedSeconds > 0L) {
                    timeSaved += (dur - ep.listenedSeconds).coerceAtLeast(0L)
                }
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
            // The Playlists tab shows the built-in smart lists as well, so the
            // dashboard count has to match what the listener will find there.
            playlistCount = playlists.count { !it.name.isLegacySmartPlaylistName() } + listOf(
                "Played" to settings.showPlayedSmartPlaylist,
                "In Progress" to settings.showInProgressSmartPlaylist,
                "Unplayed" to settings.showUnplayedSmartPlaylist,
                "Not Downloaded" to settings.showNotDownloadedSmartPlaylist,
                "Downloaded" to settings.showDownloadedSmartPlaylist,
                "New Releases" to settings.showNewReleasesSmartPlaylist,
            ).count { it.second },
            totalEpisodes = activeEpisodes.size,
            downloadedCount = downloaded,
            playedEpisodesCount = playedCount,
            inProgressCount = inProgressCount,
            totalRemainingSeconds = totalRemaining,
            totalPlayedSeconds = totalPlayed,
            totalDurationSeconds = totalDuration,
            timeSavedSeconds = timeSaved,
            // Archived episodes stay out of library counts, but their recorded
            // listening still belongs in the activity chart.
            dailyActivityMinutes = dailyActivityMinutes(allEpisodes),
            showActivity = showActivity(allEpisodes, shows),
            mostListenedShows = mostListenedShows(allEpisodes, shows),
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

        val zone = ZoneId.systemDefault()
        val todayEpochDay = LocalDate.now(zone).toEpochDay()
        val buckets = IntArray(ACTIVITY_DAYS)

        episodes.forEach { ep ->
            if (!ep.isPlayed && ep.listenedSeconds <= 0L) return@forEach
            val playedAt = (if (ep.isPlayed) ep.playedAt ?: ep.lastPlayedAt else ep.lastPlayedAt)
                ?: return@forEach
            val playedDate = Instant.ofEpochMilli(playedAt).atZone(zone).toLocalDate()
            val dayIndex = (todayEpochDay - playedDate.toEpochDay()).toInt()
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
    /**
     * The shows that carry the library, and how much of each has been heard.
     *
     * Two rankings in one list: the shows with the most episodes, and the
     * shows with the most listened to, merged so a show that is short but
     * much listened to still appears.
     */
    private fun showActivity(
        episodes: List<EpisodeSummary>,
        shows: List<PodcastEntity>,
    ): List<ShowActivity> = rankShows(episodes, shows).first

    /**
     * The shows with the most episodes listened to.
     *
     * A different ranking from the one by episode count: the show listened
     * to most is not the one with the longest back catalogue.
     */
    private fun mostListenedShows(
        episodes: List<EpisodeSummary>,
        shows: List<PodcastEntity>,
    ): List<ShowActivity> = rankShows(episodes, shows).second

    /**
     * The shows that carry the library, and the ones most listened to.
     *
     * Both rankings come from the same walk over the episodes, because they
     * are two orderings of the same set.
     *
     * Deleted episodes are included, because the listening figure on the
     * signal card counts them and the two have to agree. An archived episode
     * is not: it has been filed away, and it stops counting.
     */
    private fun rankShows(
        episodes: List<EpisodeSummary>,
        shows: List<PodcastEntity>,
    ): Pair<List<ShowActivity>, List<ShowActivity>> {
        val titles = shows.associate { it.uuid to it.title }
        val counts = mutableMapOf<String, Int>()
        val listened = mutableMapOf<String, Int>()
        episodes.forEach { ep ->
            if (ep.isArchived) return@forEach
            val title = titles[ep.podcastUuid] ?: return@forEach
            counts[title] = (counts[title] ?: 0) + 1
            if (ep.isPlayed) listened[title] = (listened[title] ?: 0) + 1
        }

        fun art(title: String) = shows.firstOrNull { it.title == title }?.artworkUrl

        val byEpisodes = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_SHOWS_COUNT)
            .map {
                ShowActivity(it.key, it.value, listened[it.key] ?: 0, art(it.key))
            }

        val byListened = listened.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_SHOWS_COUNT)
            .map {
                ShowActivity(it.key, counts[it.key] ?: 0, it.value, art(it.key))
            }

        return byEpisodes to byListened
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

    fun promoteBatch(uuids: List<String>) = viewModelScope.launch { repository.promoteBatch(uuids) }

    fun moveInQueue(uuid: String, delta: Int) = viewModelScope.launch {
        repository.moveInQueue(uuid, delta)
    }

    /** Writes a new queue order, in full, after a row has been dragged. */
    fun reorderQueue(uuids: List<String>) = viewModelScope.launch {
        repository.reorderQueue(uuids)
    }

    fun clearAllQueue() = viewModelScope.launch { repository.clearAllQueue() }

    /** Removes this entry and every later entry from Up Next without changing played state. */
    fun clearFrom(uuid: String) = viewModelScope.launch { repository.clearQueueFrom(uuid) }

    fun enqueue(uuid: String, toTop: Boolean = false) = viewModelScope.launch {
        repository.enqueue(uuid, toTop)
        if (_autoDownloadUpNext.value) {
            triggerDownloadByUuids(listOf(uuid))
        }
    }

    fun enqueueAll(uuids: Collection<String>, toTop: Boolean = false) = viewModelScope.launch {
        uuids.forEach { repository.enqueue(it) }
        if (toTop) {
            repository.promoteBatch(uuids.toList())
        }
        if (_autoDownloadUpNext.value) {
            triggerDownloadByUuids(uuids)
        }
    }

    fun addPlaylistToUpNext(playlistId: String, toTop: Boolean = false) = viewModelScope.launch {
        // Resolved through userPlaylists so the built-in Played and Downloaded
        // lists work the same way as the ones the user made.
        val entries = userPlaylists.value.firstOrNull { it.id == playlistId }
            ?.entries
            ?: return@launch
        val uuids = entries.map { it.episode.uuid }
        uuids.forEach { repository.enqueue(it) }
        if (toTop) {
            repository.promoteBatch(uuids)
        }
        if (_autoDownloadUpNext.value) {
            triggerDownloadByUuids(uuids)
        }
    }

    // Convenience aliases so callers can name the choice either way.
    fun addPlaylistToUpNext(playlistId: String, position: String) =
        addPlaylistToUpNext(playlistId, position.equals("top", ignoreCase = true))

    fun addPlaylistToUpNext(playlistId: String, position: QueuePosition) =
        addPlaylistToUpNext(playlistId, position == QueuePosition.TOP)

    fun addPlaylistToUpNext(playlistId: String, position: UpNextPosition) =
        addPlaylistToUpNext(playlistId, position == UpNextPosition.TOP)

    fun addPlaylistToUpNext(playlistId: String, position: Position) =
        addPlaylistToUpNext(playlistId, position == Position.TOP)

    fun addPlaylistToUpNext(playlistId: String, position: AddPosition) =
        addPlaylistToUpNext(playlistId, position == AddPosition.TOP)

    fun addPlaylistToUpNext(playlistId: String, position: InsertPosition) =
        addPlaylistToUpNext(playlistId, position == InsertPosition.TOP)

    fun addPlaylistToUpNext(playlistId: String, position: QueueInsertPosition) =
        addPlaylistToUpNext(playlistId, position == QueueInsertPosition.TOP)

    // Alias entry points for callers that name the choice directly.
    fun addPlaylistToTop(playlistId: String) = addPlaylistToUpNext(playlistId, true)
    fun addPlaylistToBottom(playlistId: String) = addPlaylistToUpNext(playlistId, false)
    fun addPlaylistToUpNextTop(playlistId: String) = addPlaylistToUpNext(playlistId, true)
    fun addPlaylistToUpNextBottom(playlistId: String) = addPlaylistToUpNext(playlistId, false)
    fun addPlaylistToTopOfUpNext(playlistId: String) = addPlaylistToUpNext(playlistId, true)
    fun addPlaylistToBottomOfUpNext(playlistId: String) = addPlaylistToUpNext(playlistId, false)

    /**
     * Every playlist, including the two system ones.
     *
     * Built-in smart playlists are computed rather than stored: they
     * are defined by the state of the library, so they cannot drift out of
     * date the way a stored list would.
     */
    val userPlaylists: StateFlow<List<ResolvedPlaylist>> = combine(
        playlistRepository.playlists,
        repository.observeAllEpisodes(),
        podcasts,
        playlistRepository.podcastFolders,
    ) { rawPlaylists, allEpisodes, shows, podcastFolders ->
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

        val custom = rawPlaylists
            .filterNot { it.name.isLegacySmartPlaylistName() }
            .map { pl ->
            // A playlist can follow a set of shows, a whole folder, a length
            // rule, or a fixed list of episodes. Archived episodes are filed
            // away, so like everywhere else in the library they stay out of
            // the playlist.
            val hasLengthRule = pl.minDurationMinutes > 0 || pl.maxDurationMinutes > 0
            val minSeconds = pl.minDurationMinutes * 60L
            val maxSeconds = if (pl.maxDurationMinutes > 0) pl.maxDurationMinutes * 60L else Long.MAX_VALUE
            val followed = pl.shows.toSet()

            // A length rule on its own collects from every show; on a
            // playlist that follows shows it narrows what counts.
            fun fitsLength(ep: com.lohe.app.data.db.entity.EpisodeSummary): Boolean {
                if (!hasLengthRule) return true
                val length = (ep.durationSeconds ?: 0).toLong()
                return length > 0L && length > minSeconds && length <= maxSeconds
            }

            val finishedFilter = if (pl.finished) { ep: com.lohe.app.data.db.entity.EpisodeSummary -> ep.isPlayed } else { ep: com.lohe.app.data.db.entity.EpisodeSummary -> !ep.isPlayed }

            val resolvedEntries = if (pl.snapshot) {
                pl.resolveExplicitEpisodes(epMap).map(::resolve)
            } else if (followed.isNotEmpty()) {
                // Following shows: they add their new episodes on their own,
                // without the playlist being touched. A length rule, if the
                // playlist has one, narrows what they bring.
                allEpisodes
                    .filter { ep ->
                        ep.podcastUuid in followed && !ep.isDeleted && !ep.isArchived &&
                            finishedFilter(ep) && fitsLength(ep)
                    }
                    .sortedByDescending { it.published }
                    .map(::resolve)
            } else if (hasLengthRule) {
                // Length playlists collect short (or long) episodes from every
                // show, so new ones land in the list on their own.
                allEpisodes
                    .filter { ep ->
                        !ep.isDeleted && !ep.isArchived && finishedFilter(ep) && fitsLength(ep)
                    }
                    .sortedByDescending { it.published }
                    .map(::resolve)
            } else if (pl.folder.isNotBlank()) {
                // A playlist tied to a folder follows it. Putting a show in
                // the folder adds it to the playlist, and taking it out
                // takes it away, without the playlist being touched.
                val inFolder = shows
                    .filter { podcastFolders[it.uuid] == pl.folder }
                    .map { it.uuid }
                    .toSet()
                allEpisodes
                    .filter { it.podcastUuid in inFolder && !it.isDeleted && !it.isArchived && finishedFilter(it) }
                    .sortedByDescending { it.published }
                    .map(::resolve)
            } else {
                pl.resolveExplicitEpisodes(epMap).map(::resolve)
            }
            ResolvedPlaylist(
                id = pl.id,
                name = pl.name,
                entries = resolvedEntries,
                folder = pl.folder,
                autoDownload = pl.autoDownload,
                pinned = pl.pinned,
            )
        }

        val active = allEpisodes.filter { !it.isDeleted && !it.isArchived }
        val newReleaseCutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val smartPlaylists = listOf(
            PLAYED_ID to ("Played" to active.filter { it.isPlayed }),
            IN_PROGRESS_ID to ("In Progress" to active.filter { it.isInProgress }),
            UNPLAYED_ID to ("Unplayed" to active.filter { !it.isPlayed && !it.isInProgress }),
            NOT_DOWNLOADED_ID to ("Not Downloaded" to active.filter { !it.isDownloaded }),
            DOWNLOADED_ID to ("Downloaded" to active.filter { it.isDownloaded }),
            NEW_RELEASES_ID to ("New Releases" to active.filter { it.published >= newReleaseCutoff && !it.isPlayed }),
        )
        custom + smartPlaylists.mapNotNull { (id, definition) ->
            val (name, episodes) = definition
            ResolvedPlaylist(
                id = id,
                name = name,
                entries = episodes.sortedByDescending { it.published }.map(::resolve),
                autoDownload = false,
                system = true,
            )
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    fun setPlaylistPinned(playlistId: String, pinned: Boolean) =
        playlistRepository.setPlaylistPinned(playlistId, pinned)

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
