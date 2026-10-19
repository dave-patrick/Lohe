package com.lohe.app.data

import android.content.Context
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import com.lohe.app.data.fetch.CatalogSearcher
import com.lohe.app.data.fetch.OpmlCodec
import com.lohe.app.data.fetch.PodcastFetcher
import com.lohe.app.data.fetch.RssPodcastFetcher
import com.lohe.app.download.DownloadManager
import com.lohe.app.ui.image.ArtworkLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) {
    fun observePodcasts(): Flow<List<PodcastEntity>> = podcasts.observeAll()

    fun observeSearchResults(query: String): Flow<List<PodcastEntity>> = 
        if (query.isBlank()) podcasts.observeAll() 
        else podcasts.observeSearchResults(query)

    fun observeAllEpisodes(): Flow<List<EpisodeEntity>> = episodes.observeAll()

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
    fun observeDownloads(): Flow<List<EpisodeEntity>> = episodes.observeDownloads()

    suspend fun setDownloadState(uuid: String, state: Int, path: String?) =
        episodes.setDownloadState(uuid, state, path)

    suspend fun clearDownload(uuid: String) = episodes.clearDownload(uuid)

    /** Snapshot of the queue for read-modify-write callers such as auto-advance. */
    suspend fun queueSnapshot(): List<EpisodeEntity> = episodes.queueSnapshot()

    /** Appends [uuid] to the queue; a no-op if it is already queued. */
    suspend fun enqueue(uuid: String) = episodes.enqueue(uuid, System.currentTimeMillis())

    suspend fun dequeue(uuid: String) = episodes.dequeue(uuid)

    /** Moves a queued episode to the front of the queue. */
    suspend fun promote(uuid: String) = episodes.promoteInQueue(uuid)

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

    /** Persist a resume point, recording the duration when it was unknown before. */
    suspend fun saveProgress(uuid: String, positionSeconds: Long, durationSeconds: Long) {
        episodes.saveProgress(uuid, positionSeconds, durationSeconds)
    }

    /** Mark finished and drop the resume point so it never restarts. */
    suspend fun markPlayed(uuid: String) = episodes.markPlayedAndClearProgress(uuid)

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

    /** Get total storage size used by local files (content:// URIs) */
    fun getLocalFilesStorageSize(): Flow<Long> =
        episodes.observeAll().map { list ->
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
        podcasts.byFeedUrl(feedUrl)

    /** Per-show setting: download new episodes as soon as a refresh finds them. */
    suspend fun setAutoDownload(uuid: String, enabled: Boolean) =
        podcasts.setAutoDownload(uuid, enabled)

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
    suspend fun importOpml(opml: String): OpmlImportResult {
        val subscriptions = OpmlCodec.read(opml)
        val failures = mutableListOf<String>()
        var imported = 0

        subscriptions.forEach { subscription ->
            fetcher.importFeed(subscription.feedUrl)
                .onSuccess { imported++ }
                .onFailure { failures += subscription.title }
        }

        return OpmlImportResult(
            total = subscriptions.size,
            imported = imported,
            failed = failures.size,
            failureTitles = failures,
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
     * Shows with auto-download enabled get each *newly seen* episode handed to
     * [autoDownload]. "Newly seen" means absent before this refresh and present
     * after — a refresh must never re-download a back catalogue. The first
     * fetch of a show is deliberately exempt: a new subscription arrives with
     * its whole history attached, and pulling all of it unbidden is not what
     * "save new episodes" asks for.
     */
    suspend fun refreshAllFeeds(): FeedRefreshSummary {
        val targets = podcasts.allOnce().filter { it.feedUrl.isNotBlank() }

        var refreshed = 0
        val failures = mutableListOf<String>()

        targets.forEach { podcast ->
            // Captured before the fetch so the diff is against pre-refresh state.
            val known = if (podcast.autoDownload) {
                episodes.byPodcastOnce(podcast.uuid).mapTo(mutableSetOf()) { it.uuid }
            } else {
                null
            }

            fetcher.importFeed(podcast.feedUrl)
                .onSuccess {
                    refreshed++
                    if (known != null && known.isNotEmpty()) {
                        autoDownloadNewEpisodes(podcast.uuid, known)
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

    /**
     * Hands every episode of [podcastUuid] absent from [known] to [autoDownload].
     *
     * Finished episodes are skipped: they cost bandwidth and will never be
     * played again.
     */
    private suspend fun autoDownloadNewEpisodes(
        podcastUuid: String,
        known: Set<String>,
    ) {
        episodes.byPodcastOnce(podcastUuid)
            .filter { it.uuid !in known && !it.isPlayed }
            .forEach(autoDownload)
    }
}

/** Outcome of refreshing every subscription. */
data class FeedRefreshSummary(
    val total: Int,
    val refreshed: Int,
    val failed: Int,
    val failureTitles: List<String> = emptyList(),
)

/** Outcome of an OPML import, for the UI to summarise. */
data class OpmlImportResult(
    val total: Int,
    val imported: Int,
    val failed: Int,
    val failureTitles: List<String> = emptyList(),
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
            autoDownload = { episode -> downloadManager().start(episode) },
        )

    fun podcastFetcher(): PodcastFetcher =
        fetcher ?: synchronized(this) {
            fetcher ?: RssPodcastFetcher(
                client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build(),
                podcastDao = database().podcastDao(),
                episodeDao = database().episodeDao(),
                historyDao = database().listeningHistoryDao(),
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
                client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build(),
            ).also { searcher = it }
        }

    @Volatile private var artworkLoader: ArtworkLoader? = null

    fun artworkLoader(): ArtworkLoader =
        artworkLoader ?: synchronized(this) {
            artworkLoader ?: ArtworkLoader(
                context = context ?: error("ServiceLocator not initialized"),
                client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build(),
            ).also { artworkLoader = it }
        }

    @Volatile private var downloads: DownloadManager? = null

    fun downloadManager(): DownloadManager =
        downloads ?: synchronized(this) {
            downloads ?: DownloadManager(
                context = context ?: error("ServiceLocator not initialized"),
                repository = subscriptionRepository(),
            ).also { downloads = it }
        }

    @Volatile private var playlists: PlaylistRepository? = null

    fun playlistRepository(): PlaylistRepository =
        playlists ?: synchronized(this) {
            val file = context?.let { java.io.File(it.filesDir, "playlists.json") }
            playlists ?: PlaylistRepository(file).also { playlists = it }
        }
}