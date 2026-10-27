package com.lohe.app.data

import android.content.Context
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.EpisodeSummary
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import com.lohe.app.data.db.entity.toSummary
import com.lohe.app.data.fetch.CatalogSearcher
import com.lohe.app.data.fetch.OpmlCodec
import com.lohe.app.data.fetch.PodcastFetcher
import com.lohe.app.data.fetch.RssPodcastFetcher
import com.lohe.app.download.DownloadManager
import com.lohe.app.ui.image.ArtworkLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Episodes whose [EpisodeEntity.audioUrl] starts with this are device-local. */
const val CONTENT_SCHEME = "content://"

/**
 * Simple front-door for subscription management. UI observes [observePodcasts];
 * mutation call-sites run their own coroutines via [kotlinx.coroutines.CoroutineScope].
 *
 * Kept deliberately Hilt-free: an application-scoped singleton via [ServiceLocator].
 *
 * @param fetcher injected rather than reached for through [ServiceLocator] so the
 *   import and refresh logic — which fans out over many feeds and must survive
 *   individual failures — can be tested without a network.
 * @param autoDownload invoked for each newly-seen episode of a show with the
 *   per-show auto-download setting on. A seam rather than a [DownloadManager]
 *   reference: this constructor takes DAOs and a fetcher only, so the whole
 *   refresh sweep stays unit-testable, and resolving the manager lazily avoids
 *   the [ServiceLocator] construction cycle it would otherwise create.
 */
class SubscriptionRepository internal constructor(
    private val podcasts: com.lohe.app.data.db.dao.PodcastDao,
    private val episodes: com.lohe.app.data.db.dao.EpisodeDao,
    private val history: com.lohe.app.data.db.dao.ListeningHistoryDao,
    private val fetcher: PodcastFetcher,
    private val autoDownload: (EpisodeEntity) -> Unit = {},
    private val onPlayed: suspend (String) -> Unit = {},
    private val onArchived: suspend (EpisodeEntity) -> Unit = {},
    private val playlistMayAutoDownload: (String) -> Boolean = { false },
    private val playlistIncludesForAutoDownload: (String, EpisodeEntity) -> Boolean = { _, _ -> false },
) {
    fun observePodcasts(): Flow<List<PodcastEntity>> = podcasts.observeAll()

    fun observeSearchResults(query: String): Flow<List<PodcastEntity>> = 
        if (query.isBlank()) podcasts.observeAll() 
        else podcasts.observeSearchResults(query)

    fun observePodcastStats() = episodes.observePodcastStats()

    fun observeAllEpisodes(): Flow<List<EpisodeSummary>> = episodes.observeAllSummaries()

    /**
     * Listening time recorded for episodes that have left the library.
     *
     * Unsubscribing cascades the episode rows away, so this is the only place
     * the time actually spent listening can still live.
     */
    fun observeListeningHistory(): Flow<List<ListeningHistoryEntity>> = history.observeAll()

    /** Episodes belonging to one podcast, newest first. */
    fun observeEpisodesFor(podcastUuid: String): Flow<List<EpisodeEntity>> =
        episodes.observeByPodcast(podcastUuid)

    /** Episodes still in progress, newest activity first — what "continue" means. */
    fun observeInProgress(): Flow<List<EpisodeEntity>> = episodes.observeInProgress()

    /** Episodes queued by the user, in the order they queued them. */
    fun observeQueue(): Flow<List<EpisodeEntity>> = episodes.observeQueue()

    /** Completed offline downloads, newest first. */
    fun observeDownloads(): Flow<List<EpisodeSummary>> = episodes.observeDownloads()

    /** Episode title matches for media-browser voice/search requests. */
    suspend fun searchEpisodes(query: String, limit: Int = 100): List<EpisodeEntity> =
        episodes.searchByTitle(query.trim(), limit.coerceIn(1, 250))

    suspend fun setDownloadState(uuid: String, state: Int, path: String?) =
        episodes.setDownloadState(uuid, state, path)

    suspend fun clearDownload(uuid: String) = episodes.clearDownload(uuid)

    /** Snapshot of the queue for read-modify-write callers such as auto-advance. */
    suspend fun queueSnapshot(): List<EpisodeEntity> = episodes.queueSnapshot()

    /** Appends [uuid] to the queue; a no-op if it is already queued. */
    suspend fun enqueue(uuid: String, toTop: Boolean = false) {
        episodes.enqueue(uuid, System.currentTimeMillis())
        if (toTop) {
            episodes.promoteInQueue(uuid)
        }
    }

    /**
     * Queues every unplayed episode of a show, oldest first so they play in
     * the order they were published. Returns how many were added.
     *
     * Archived and deleted episodes are left out, and episodes already in Up
     * Next keep their place unless [atTop] pulls the whole batch forward.
     */
    suspend fun addAllEpisodesToUpNext(podcastUuid: String, atTop: Boolean): Int {
        val batch = episodes.byPodcastOnce(podcastUuid)
            .filter { !it.isPlayed && !it.isDeleted && !it.isArchived }
            .sortedBy { it.published }
        if (batch.isEmpty()) return 0
        val now = System.currentTimeMillis()
        batch.forEach { episodes.enqueue(it.uuid, now) }
        if (atTop) episodes.promoteBatchInQueue(batch.map { it.uuid })
        return batch.size
    }

    suspend fun dequeue(uuid: String) = episodes.dequeue(uuid)

    /** A single episode, looked up by uuid. */
    suspend fun episodeOnce(uuid: String): EpisodeEntity? = episodes.get(uuid)

    /** Several episodes by uuid, in the order asked for. */
    suspend fun episodesOnce(uuids: Collection<String>): List<EpisodeEntity> =
        uuids.mapNotNull { episodes.get(it) }

    /** Moves a queued episode to the front of the queue. */
    suspend fun promote(uuid: String) = episodes.promoteInQueue(uuid)

    /** Moves a selected group to the front while preserving its current queue order. */
    suspend fun promoteBatch(uuids: List<String>) = episodes.promoteBatchInQueue(uuids)

    /** Shifts [uuid] up (`delta < 0`) or down (`delta > 0`) in the queue. */
    suspend fun moveInQueue(uuid: String, delta: Int) {
        val current = episodes.queueSnapshot().toMutableList()
        val idx = current.indexOfFirst { it.uuid == uuid }
        if (idx < 0) return
        val targetIdx = (idx + delta).coerceIn(0, current.lastIndex)
        if (targetIdx == idx) return
        val moved = current.removeAt(idx)
        current.add(targetIdx, moved)
        episodes.rewriteQueue(current)
    }

    /**
     * Rewrites the queue order wholesale.
     *
     * A dragged row lands in one position, not in a series of nudges, so the
     * whole order is written at once when the row is dropped.
     */
    suspend fun reorderQueue(uuids: List<String>) = episodes.reorderQueue(uuids)

    /** Empties the entire Up Next queue. */
    suspend fun clearAllQueue() {
        episodes.queueSnapshot().forEach { episodes.dequeue(it.uuid) }
    }

    /** Drops everything from [uuid] onwards — "played up to here". */
    suspend fun clearQueueFrom(uuid: String) = episodes.clearQueueFrom(uuid)

    suspend fun setStarred(uuid: String, starred: Boolean) = episodes.setStarred(uuid, starred)

    /**
     * Files an episode away, or brings it back.
     *
     * Archived episodes stay in the library but are left out of the episode
     * lists and stop counting towards the listening figures.
     */
    suspend fun setArchived(uuid: String, archived: Boolean) {
        if (archived) {
            episodes.archive(uuid, System.currentTimeMillis())
        } else {
            episodes.setArchived(uuid, false)
        }
        if (archived) episodes.get(uuid)?.let { onArchived(it) }
    }

    suspend fun archivePlayedIfDue(uuid: String, cutoff: Long) {
        if (episodes.archivePlayedIfDue(uuid, cutoff) == 1) {
            episodes.get(uuid)?.let { onArchived(it) }
        }
    }

    /** Persist a resume point, recording the duration when it was unknown before. */
    suspend fun saveProgress(uuid: String, positionSeconds: Long, durationSeconds: Long) {
        episodes.saveProgress(uuid, positionSeconds, durationSeconds)
    }

    /** Mark finished and drop the resume point so it never restarts. */
    suspend fun markPlayed(uuid: String) {
        episodes.markPlayedAndClearProgress(uuid)
        onPlayed(uuid)
    }

    /**
     * Sets a resume point without counting it as time listened.
     *
     * Used when an episode's state is copied in from elsewhere: the position
     * is the listener's, but the listening happened somewhere else.
     */
    suspend fun applyProgress(uuid: String, positionSeconds: Long, durationSeconds: Long) =
        episodes.applyProgress(uuid, positionSeconds, durationSeconds)

    /** Puts an episode back to unplayed without erasing the listening total. */
    suspend fun applyUnplayed(uuid: String) = episodes.applyUnplayed(uuid)

    /** Undo [markPlayed]: back to unplayed, restarting from the beginning. */
    suspend fun reopenEpisode(uuid: String) = episodes.reopenEpisode(uuid)

    /** Soft-deletes [uuid] from the episode list and removes it from the queue. */
    suspend fun deleteEpisode(uuid: String) {
        val current = episodes.get(uuid) ?: return
        episodes.upsertAll(
            listOf(
                current.copy(
                    isDeleted = true,
                    queuePosition = null,
                ),
            ),
        )
    }

    /** Saved resume point in seconds; 0 when there is nothing to resume. */
    suspend fun playedPosition(uuid: String): Long = episodes.playedPosition(uuid)

    /**
     * The show an episode belongs to.
     *
     * Playback settings are stored per show, so the service needs the show's
     * uuid; an episode uuid finds no settings at all.
     */
    suspend fun podcastUuidFor(episodeUuid: String): String? =
        episodes.get(episodeUuid)?.podcastUuid

    /** Get total storage size used by local files (content:// URIs) */
    fun getLocalFilesStorageSize(): Flow<Long> =
        episodes.observeAllSummaries().map { list ->
            list.filter { it.audioUrl.startsWith(CONTENT_SCHEME) }
                .sumOf { it.sizeBytes ?: 0L }
        }

    /** Delete every episode backed by a local content:// URI. */
    suspend fun deleteLocalFiles() = episodes.deleteLocalFiles()

    /** Insert or replace episodes (used by the local-file importer). */
    suspend fun upsertEpisodes(items: List<EpisodeEntity>) = episodes.upsertAll(items)

    /** Snapshot of a podcast's episodes — for read-modify-write callers. */
    suspend fun allEpisodesOnce(podcastUuid: String): List<EpisodeEntity> =
        episodes.byPodcastOnce(podcastUuid)

    suspend fun getPodcast(uuid: String): PodcastEntity? = podcasts.get(uuid)

    suspend fun subscribe(podcast: PodcastEntity) = podcasts.upsert(podcast)

    suspend fun unsubscribe(uuid: String) {
        // Archive the listening time before the rows cascade away.
        val orphaned = episodes.byPodcastOnce(uuid)
            .filter { it.listenedSeconds > 0 }
            .map {
                ListeningHistoryEntity(
                    episodeUuid = it.uuid,
                    listenedSeconds = it.listenedSeconds,
                    durationSeconds = it.durationSeconds,
                    title = it.title,
                )
            }
        if (orphaned.isNotEmpty()) {
            history.upsertAll(orphaned)
        }
        podcasts.deleteByUuid(uuid)
    }

    /** Finds an existing subscription by feed URL — how a catalog result is matched. */
    suspend fun subscriptionForFeed(feedUrl: String): PodcastEntity? =
        podcasts.byFeedUrl(feedUrl)?.takeIf { it.isSubscribed }

    /** Per-show setting: download new episodes as soon as a refresh finds them. */
    suspend fun setAutoDownload(uuid: String, enabled: Boolean) =
        podcasts.setAutoDownload(uuid, enabled)

    /** Per-podcast setting: append episodes discovered by the next feed refresh to Up Next. */
    suspend fun setAutoAddNewEpisodesToUpNext(uuid: String, enabled: Boolean) {
        podcasts.get(uuid)?.let {
            podcasts.update(it.copy(autoAddNewEpisodesToUpNext = enabled))
        }
    }

    /** Per-podcast placement for episodes discovered by a feed refresh. */
    suspend fun setAutoAddNewEpisodesAtTop(uuid: String, atTop: Boolean) {
        podcasts.get(uuid)?.let {
            podcasts.update(it.copy(autoAddNewEpisodesAtTop = atTop))
        }
    }

    /** Convenience: fresh UUID for callers that mint one. */
    fun newUuid(): String = UUID.randomUUID().toString()

    /**
     * Fetch a feed and upsert it with its episodes.
     *
     * Returns the failure rather than throwing so the UI can show the reason —
     * a bad URL is an ordinary outcome here, not an exception.
     */
    suspend fun importFeed(feedUrl: String): Result<PodcastEntity> =
        fetcher.importFeed(feedUrl)

    /**
     * Fetches feed metadata purely to show it, without subscribing.
     *
     * The iTunes search API returns no description at all, so opening a show
     * before following it means reading the feed. Failure is returned, not
     * thrown: a missing blurb must not break the detail view.
     */
    suspend fun previewFeed(feedUrl: String): Result<PodcastEntity> =
        runCatching { fetcher.fetch(feedUrl) }

    /**
     * Fetches a feed's episodes purely to show them, without subscribing.
     *
     * Lets a listener browse a show's episode list and download individual
     * episodes before deciding whether to follow the show. Failure is
     * returned, not thrown.
     */
    suspend fun fetchFeedPreview(feedUrl: String): Result<List<EpisodeEntity>> =
        runCatching { fetcher.fetchEpisodes(feedUrl) }

    /** Serialise every subscribed show that has a feed URL as an OPML document. */
    suspend fun exportOpml(): String =
        OpmlCodec.write(OpmlCodec.fromPodcasts(podcasts.allOnce()))
    /**
     * Import subscriptions from an OPML document.
     *
     * Feeds are fetched independently: one dead URL in a 200-show export must
     * not abort the import, so failures are counted and reported rather than
     * propagated.
     */
    /**
     * Import subscriptions from an OPML document.
     *
     * Feeds are fetched independently: one dead URL in a 200-show export must
     * not abort the import, so failures are counted and reported rather than
     * propagated.
     *
     * [onProgress] is invoked with `(completed, total)` as each feed is
     * processed, including an initial `(0, total)` once the document has been
     * parsed, so callers can show progress and the overall count.
     */
    suspend fun importOpml(
        opml: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): OpmlImportResult {
        val subscriptions = OpmlCodec.read(opml)
        val total = subscriptions.size
        onProgress(0, total)
        val failures = mutableListOf<OpmlImportFailure>()
        var imported = 0

        subscriptions.forEachIndexed { index, subscription ->
            fetcher.importFeed(subscription.feedUrl)
                .onSuccess { imported++ }
                .onFailure { e ->
                    failures += OpmlImportFailure(
                        subscription.title,
                        e.message ?: "Couldn't read that feed.",
                    )
                }
            onProgress(index + 1, total)
        }

        return OpmlImportResult(
            total = subscriptions.size,
            imported = imported,
            failed = failures.size,
            failures = failures,
        )
    }
    /**
     * Re-fetches every subscription that has a feed URL.
     *
     * One dead feed must not abort the sweep, so failures are counted rather
     * than propagated. Feeds are refreshed sequentially on purpose: a user with
     * fifty subscriptions would otherwise open fifty sockets at once and get
     * rate-limited.
     *
     * Shows with auto-download or auto-queue enabled only act on *newly seen*
     * episodes: ones absent before this refresh and present after. A refresh
     * must never redownload or queue a back catalogue. The first fetch of a
     * show is deliberately exempt because a new subscription arrives with its
     * whole history attached.
     */
    suspend fun refreshAllFeeds(): FeedRefreshSummary {
        val targets = podcasts.allOnce().filter { it.feedUrl.isNotBlank() }

        var refreshed = 0
        val failures = mutableListOf<String>()

        targets.forEach { podcast ->
            // Captured before the fetch so the diff is against pre-refresh state.
            val playlistMayDownload = playlistMayAutoDownload(podcast.uuid)
            val known = if (podcast.autoDownload || podcast.autoAddNewEpisodesToUpNext || playlistMayDownload) {
                episodes.byPodcastOnce(podcast.uuid).mapTo(mutableSetOf()) { it.uuid }
            } else {
                null
            }

            fetcher.importFeed(podcast.feedUrl)
                .onSuccess {
                    refreshed++
                    if (known != null && known.isNotEmpty()) {
                        val newlySeen = episodes.byPodcastOnce(podcast.uuid)
                            .filter { it.uuid !in known && !it.isPlayed && !it.isDeleted && !it.isArchived }
                        try {
                            if (podcast.autoDownload) newlySeen.forEach(autoDownload)
                            else if (playlistMayDownload) {
                                newlySeen.filter { playlistIncludesForAutoDownload(podcast.uuid, it) }
                                    .forEach(autoDownload)
                            }
                            if (podcast.autoAddNewEpisodesToUpNext) {
                                enqueueNewEpisodes(podcast, newlySeen)
                            }
                        } catch (e: Exception) {
                            // Post-import bookkeeping must not fail the whole sweep.
                        }
                    }
                }
                .onFailure { failures += podcast.title }
        }

        return FeedRefreshSummary(
            total = targets.size,
            refreshed = refreshed,
            failed = failures.size,
            failureTitles = failures,
        )
    }

    /** Adds newly fetched episodes once, preserving the feed's newest-first order. */
    private suspend fun enqueueNewEpisodes(
        podcast: PodcastEntity,
        newlySeen: List<EpisodeEntity>,
    ) {
        if (newlySeen.isEmpty()) return
        newlySeen.forEach { episodes.enqueue(it.uuid, System.currentTimeMillis()) }
        if (podcast.autoAddNewEpisodesAtTop) {
            episodes.promoteBatchInQueue(newlySeen.map { it.uuid })
        }
    }
}

/** Outcome of refreshing every subscription. */
data class FeedRefreshSummary(
    val total: Int,
    val refreshed: Int,
    val failed: Int,
    val failureTitles: List<String> = emptyList(),
)

/** One subscription that failed to import, with the reason. */
data class OpmlImportFailure(val title: String, val reason: String)

/** Outcome of an OPML import, for the UI to summarise. */
data class OpmlImportResult(
    val total: Int,
    val imported: Int,
    val failed: Int,
    val failures: List<OpmlImportFailure> = emptyList(),
)

/**
 * Service-locator (wasn't going to mention Hilt). Swappable in tests.
 */
object ServiceLocator {
    @Volatile private var context: Context? = null
    @Volatile private var db: AppDatabase? = null
    @Volatile private var fetcher: PodcastFetcher? = null

    fun init(context: Context) {
        this.context = context.applicationContext
        EpisodeMaintenance.start(context.applicationContext)
    }

    fun database(): AppDatabase =
        db ?: run {
            val c = context ?: error("ServiceLocator not initialized")
            synchronized(this) { db ?: AppDatabase.get(c).also { db = it } }
        }

    fun subscriptionRepository(): SubscriptionRepository =
        SubscriptionRepository(
            database().podcastDao(),
            database().episodeDao(),
            database().listeningHistoryDao(),
            podcastFetcher(),
            // Resolved lazily on first auto-download, never during construction.
            // Calling downloadManager() here instead would recurse: it builds a
            // DownloadManager that takes a repository, which would come back here.
            autoDownload = { episode -> downloadManager().start(episode.toSummary()) },
            onPlayed = { uuid ->
                playlistRepository().removeEpisodeFromAll(uuid)
                if (settingsStore().settings.first().autoArchiveHours == 0) {
                    subscriptionRepository().setArchived(uuid, true)
                }
            },
            onArchived = { episode ->
                playlistRepository().removeEpisodeFromAll(episode.uuid)
            },
            playlistMayAutoDownload = { podcastUuid ->
                playlistRepository().shouldAutoDownloadPodcast(podcastUuid)
            },
            playlistIncludesForAutoDownload = { podcastUuid, episode ->
                playlistRepository().shouldAutoDownloadEpisode(podcastUuid, episode)
            },
        )

    private val baseHttpClient by lazy { OkHttpClient() }

    fun podcastFetcher(): PodcastFetcher =
        fetcher ?: synchronized(this) {
            fetcher ?: RssPodcastFetcher(
                client = baseHttpClient.newBuilder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .callTimeout(60, TimeUnit.SECONDS)
                    .build(),
                podcastDao = database().podcastDao(),
                episodeDao = database().episodeDao(),
                historyDao = database().listeningHistoryDao(),
                transaction = { block -> database().withTransaction { block() } },
            ).also { fetcher = it }
        }

    @Volatile private var searcher: CatalogSearcher? = null

    /** Durable user preferences, shared by every screen that needs them. */
    @Volatile private var settingsStore: com.lohe.app.ui.theme.SettingsStore? = null

    fun settingsStore(): com.lohe.app.ui.theme.SettingsStore =
        settingsStore ?: synchronized(this) {
            settingsStore ?: com.lohe.app.ui.theme.SettingsStore(
                context ?: error("ServiceLocator not initialized"),
            ).also { settingsStore = it }
        }

    fun catalogSearcher(): CatalogSearcher =
        searcher ?: synchronized(this) {
            searcher ?: CatalogSearcher(
                client = baseHttpClient.newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(30, TimeUnit.SECONDS)
                    .build(),
            ).also { searcher = it }
        }

    @Volatile private var artworkLoader: ArtworkLoader? = null

    fun artworkLoader(): ArtworkLoader =
        artworkLoader ?: synchronized(this) {
            artworkLoader ?: ArtworkLoader(
                context = context ?: error("ServiceLocator not initialized"),
                client = baseHttpClient.newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(30, TimeUnit.SECONDS)
                    .build(),
            ).also { artworkLoader = it }
        }

    @Volatile private var downloads: DownloadManager? = null

    fun downloadManager(): DownloadManager =
        downloads ?: synchronized(this) {
            downloads ?: DownloadManager(
                context = context ?: error("ServiceLocator not initialized"),
                repository = subscriptionRepository(),
                client = baseHttpClient.newBuilder().readTimeout(30, TimeUnit.SECONDS).build(),
            ).also { downloads = it }
        }

    @Volatile private var playlists: PlaylistRepository? = null

    fun playlistRepository(): PlaylistRepository =
        playlists ?: synchronized(this) {
            val file = context?.let { java.io.File(it.filesDir, "playlists.json") }
            playlists ?: PlaylistRepository(file).also { playlists = it }
        }
}
