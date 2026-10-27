package com.lohe.app.data.fetch

import com.lohe.app.LOHE_FALLBACK_USER_AGENT
import com.lohe.app.network.readBoundedBytes
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
    override suspend fun getMany(uuids: List<String>): List<ListeningHistoryEntity> = emptyList()
    override suspend fun deleteMany(uuids: List<String>) = Unit
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
    private val transaction: suspend (suspend () -> Unit) -> Unit = { it() },
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
            transaction {
                podcastDao.upsert(parsed.podcast)
                // Keep each batch below SQLite's bind limit and merge under the write transaction.
                for (batch in parsed.episodes.chunked(400)) {
                    val ids = batch.map { it.uuid }
                    val existing = episodeDao.getMany(ids).associateBy { it.uuid }
                    val history = historyDao.getMany(ids).associateBy { it.episodeUuid }
                    val changed = batch.map { ep ->
                        val old = existing[ep.uuid]
                        val restored = if (old == null) ep else ep.copy(
                            playingStatus = old.playingStatus,
                            playedAt = old.playedAt,
                            playedUpToSeconds = old.playedUpToSeconds,
                            listenedSeconds = old.listenedSeconds,
                            starred = old.starred,
                            queuePosition = old.queuePosition,
                            lastPlayedAt = old.lastPlayedAt,
                            localPath = old.localPath,
                            downloadState = old.downloadState,
                            isDeleted = old.isDeleted,
                            isArchived = old.isArchived,
                            archivedAt = old.archivedAt,
                        )
                        restored.copy(listenedSeconds = maxOf(restored.listenedSeconds, history[ep.uuid]?.listenedSeconds ?: 0))
                    }.filter { it != existing[it.uuid] }
                    if (changed.isNotEmpty()) episodeDao.upsertAll(changed)
                    if (history.isNotEmpty()) historyDao.deleteMany(history.keys.toList())
                }
            }
            parsed.podcast
        }
    }

    /**
     * Fetches a feed's bytes.
     *
     * Three things get a second attempt, because all three are common and
     * none of them mean the feed is gone:
     *
     * - 304 means "the copy you already hold is still good", which only
     *   makes sense to a client that sent a validator, and we send none. Some
     *   hosts answer a plain GET that way anyway, so the request is repeated
     *   with the caches bypassed.
     * - 403 is either a host that wants https and refuses the old scheme, or
     *   a bot wall rather than a decision about this feed. The request is
     *   retried over https and then as a browser. Export files are full of
     *   http:// feeds from years ago, so the first of those is the common one.
     * - A Libsyn feed that still refuses everything is retried at the
     *   per-show address, which is where Libsyn actually serves it.
     */
    private fun download(url: String): ByteArray {
        val first = execute(url, bustCache = false, asBrowser = false)
        val response = when {
            first.code == 304 -> {
                first.close()
                execute(url, bustCache = true, asBrowser = false)
            }

            first.code == 403 -> {
                first.close()
                // The host wants https and is refusing the old scheme, or it
                // has a bot wall. Try the secure address, then the same
                // address as a browser.
                val secure = "https://" + url.removePrefix("http://")
                val upgraded = execute(secure, bustCache = false, asBrowser = false)
                if (upgraded.code == 403) {
                    upgraded.close()
                    execute(secure, bustCache = false, asBrowser = true)
                } else {
                    upgraded
                }
            }

            else -> first
        }

        if (response.isSuccessful) {
            return response.use {
                it.body?.readBoundedBytes(16 * 1024 * 1024)
                    ?: throw IllegalStateException("Feed returned an empty body")
            }
        }

        val status = response.code
        val recovered = recoverLibsyn(response)
        response.close()
        if (recovered != null) return recovered

        throw feedError(status)
    }

    /**
     * Recovers a Libsyn feed that its own vanity address will not serve.
     *
     * Libsyn moved each show's feed to feeds.libsyn.com/<show id>. The
     * friendly address still redirects, but it redirects to an S3 object that
     * is no longer public, so the show answers 403 for a feed it is still
     * serving. The show id only appears in the address the vanity one
     * redirects to, so it is read back from there.
     */
    private fun recoverLibsyn(response: Response): ByteArray? {
        val id = libsynShowId(response.request.url.toString()) ?: return null
        val candidate = "https://feeds.libsyn.com/$id"
        return try {
            val alt = execute(candidate, bustCache = false, asBrowser = false)
            if (alt.isSuccessful) {
                alt.use { it.body?.readBoundedBytes(16 * 1024 * 1024) }
            } else {
                alt.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun execute(url: String, bustCache: Boolean, asBrowser: Boolean): Response {
        val builder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                if (asBrowser) LOHE_FALLBACK_USER_AGENT else LOHE_USER_AGENT,
            )
            .header(
                "Accept",
                if (asBrowser) {
                    "text/html,application/xhtml+xml,application/xml;q=0.9," +
                        "application/rss+xml;q=0.8,*/*;q=0.7"
                } else {
                    "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
                },
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

    internal companion object {
        /** The show id in a Libsyn feed address, such as .../shows/96977/... */
        private val LIBSYN_SHOW = Regex("""libsyn\.com/(?:shows/)?(\d+)""")

        /**
         * The Libsyn show id in an address, or null when there is not one.
         *
         * The vanity address in an export carries no id, so the id is read
         * from the address it redirects to, which does.
         */
        internal fun libsynShowId(url: String): String? =
            LIBSYN_SHOW.find(url)?.groupValues?.getOrNull(1)
    }
}
