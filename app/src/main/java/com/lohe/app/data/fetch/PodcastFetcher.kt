package com.lohe.app.data.fetch

import com.lohe.app.LOHE_USER_AGENT
import com.lohe.app.data.db.dao.EpisodeDao
import com.lohe.app.data.db.dao.ListeningHistoryDao
import com.lohe.app.data.db.dao.PodcastDao
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Fetches a podcast feed over the network and folds it into the database. */
interface PodcastFetcher {
    /** Parses feed metadata without touching the database. Throws on failure. */
    suspend fun fetch(feedUrl: String): PodcastEntity

    /**
     * Parses a feed's episodes without saving them to the database.
     *
     * Used for previewing a show's episode list before subscribing.
     */
    suspend fun fetchEpisodes(feedUrl: String): List<EpisodeEntity>

    /** Fetches and upserts both the podcast metadata and its episodes. */
    suspend fun importFeed(feedUrl: String): Result<PodcastEntity>
}

/**
 * Stand-in used when no history store is supplied, so the fetcher stays
 * constructible in unit tests that do not care about archived history.
 */
private object NoOpListeningHistoryDao : ListeningHistoryDao {
    override fun observeAll(): Flow<List<ListeningHistoryEntity>> = flowOf(emptyList())
    override suspend fun allOnce(): List<ListeningHistoryEntity> = emptyList()
    override suspend fun get(uuid: String): ListeningHistoryEntity? = null
    override suspend fun upsertAll(entries: List<ListeningHistoryEntity>) = Unit
    override suspend fun delete(uuid: String) = Unit
    override suspend fun clear() = Unit
}

/**
 * Network + persistence around a [FeedParser].
 *
 * All parsing lives in [FeedParser] so it stays unit-testable; this class only
 * downloads bytes and writes rows.
 */
class RssPodcastFetcher(
    private val client: OkHttpClient = OkHttpClient(),
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val historyDao: ListeningHistoryDao = NoOpListeningHistoryDao,
    private val parser: FeedParser = FeedParser(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PodcastFetcher {

    override suspend fun fetch(feedUrl: String): PodcastEntity = withContext(io) {
        parser.parse(feedUrl, download(feedUrl)).podcast
    }

    override suspend fun fetchEpisodes(feedUrl: String): List<EpisodeEntity> = withContext(io) {
        parser.parse(feedUrl, download(feedUrl)).episodes
    }

    override suspend fun importFeed(feedUrl: String): Result<PodcastEntity> = runCatching {
        withContext(io) {
            val parsed = parser.parse(feedUrl, download(feedUrl))
            podcastDao.upsert(parsed.podcast)

            // Preserve user-controlled fields from existing episodes so a feed
            // refresh never wipes out played status, resume position, downloads,
            // or queue position.
            val existing = episodeDao.byPodcastOnce(parsed.podcast.uuid)
                .associateBy { it.uuid }
            val mergedEpisodes = parsed.episodes.map { ep ->
                val old = existing[ep.uuid]
                // A show that was re-subscribed brings back episodes whose
                // listening time was archived when it was removed. Fold the
                // archived time back in so it is not counted twice.
                val archived = historyDao.get(ep.uuid)
                val restored = when {
                    archived != null -> ep.copy(listenedSeconds = archived.listenedSeconds)
                    old != null -> ep.copy(
                        playingStatus = old.playingStatus,
                        playedUpToSeconds = old.playedUpToSeconds,
                        listenedSeconds = old.listenedSeconds,
                        starred = old.starred,
                        queuePosition = old.queuePosition,
                        lastPlayedAt = old.lastPlayedAt,
                        localPath = old.localPath,
                        downloadState = old.downloadState,
                        isDeleted = old.isDeleted,
                    )
                    else -> ep
                }
                if (archived != null) historyDao.delete(ep.uuid)
                restored
            }
            episodeDao.upsertAll(mergedEpisodes)
            parsed.podcast
        }
    }

    /**
     * Fetches a feed's bytes.
     *
     * A 304 is retried once with the caches bypassed. 304 means "the copy you
     * already hold is still good", which only makes sense to a client that
     * sent a validator, and we send none. Some hosts answer a plain GET that
     * way anyway, so the first attempt is repeated with no-cache headers
     * before the feed is called unreachable.
     */
    private fun download(url: String): ByteArray {
        val first = execute(url, bustCache = false)
        val response = if (first.code == 304) {
            first.close()
            execute(url, bustCache = true)
        } else {
            first
        }
        return response.use {
            if (!it.isSuccessful) throw feedError(it.code)
            it.body?.bytes()
                ?: throw IllegalStateException("Feed returned an empty body")
        }
    }

    private fun execute(url: String, bustCache: Boolean): Response {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", LOHE_USER_AGENT)
            .header(
                "Accept",
                "application/rss+xml, application/atom+xml, application/xml, text/xml, */*",
            )
        if (bustCache) {
            builder.header("Cache-Control", "no-cache")
            builder.header("Pragma", "no-cache")
        }
        return client.newCall(builder.build()).execute()
    }

    /**
     * Turns an HTTP status into something a listener can act on.
     *
     * "Feed returned HTTP 403" tells the reader nothing. A 403 usually means
     * the host blocks this network or wants a subscription; a 404 means the
     * show moved and the old address is dead.
     */
    private fun feedError(code: Int): IllegalStateException = when (code) {
        403 -> IllegalStateException(
            "The server refused this feed (403). It may block this network, or want a subscription.",
        )
        404 -> IllegalStateException(
            "This feed is gone (404). The show may have moved to a new address.",
        )
        304 -> IllegalStateException(
            "The server kept replying 304 and would not send the feed.",
        )
        else -> IllegalStateException("Feed returned HTTP $code")
    }
}
