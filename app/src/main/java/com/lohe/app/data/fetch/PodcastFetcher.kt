package com.lohe.app.data.fetch

import com.lohe.app.data.db.dao.EpisodeDao
import com.lohe.app.data.db.dao.ListeningHistoryDao
import com.lohe.app.data.db.dao.PodcastDao
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches a podcast feed over the network and folds it into the database. */
interface PodcastFetcher {
    /** Parses feed metadata without touching the database. Throws on failure. */
    suspend fun fetch(feedUrl: String): PodcastEntity

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

    private fun download(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Lohe/0.1 (+podcast reader)")
            .header(
                "Accept",
                "application/rss+xml, application/atom+xml, application/xml, text/xml, */*",
            )
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Feed returned HTTP ${response.code}")
            }
            return response.body?.bytes()
                ?: throw IllegalStateException("Feed returned an empty body")
        }
    }
}
