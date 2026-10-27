package com.lohe.app.data

import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.EpisodeSummary
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.db.entity.toSummary
import com.lohe.app.data.fetch.OpmlCodec
import com.lohe.app.data.fetch.OpmlSubscription
import com.lohe.app.data.fetch.PodcastFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Exercises the repository's fan-out logic against in-memory DAOs and a
 * scripted fetcher.
 *
 * The behaviour worth pinning here is not the happy path — it is that a single
 * bad feed inside a large import or refresh does not take the rest down with
 * it, and that locally-imported audio is excluded from network operations.
 */
class SubscriptionRepositoryTest {

    // --- Fakes --------------------------------------------------------------

    private class FakePodcastDao(
        private val rows: MutableList<PodcastEntity> = mutableListOf(),
    ) : com.lohe.app.data.db.dao.PodcastDao {
        override fun observeAll() = kotlinx.coroutines.flow.flowOf(rows.toList())
        override suspend fun allOnce() = rows.toList()
        override suspend fun get(uuid: String) = rows.firstOrNull { it.uuid == uuid }

        /**
         * Mirrors the real DAO: the row is never deleted, only its feed-derived
         * columns are refreshed.
         */
        override suspend fun insertIfAbsent(podcast: PodcastEntity): Long {
            if (rows.any { it.uuid == podcast.uuid }) return -1L
            rows += podcast
            return rows.size.toLong()
        }

        override suspend fun updateFeedFields(
            uuid: String,
            title: String,
            author: String?,
            description: String?,
            artworkUrl: String?,
            feedUrl: String,
            lastEpisodePublished: Long?,
        ) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index < 0) return
            rows[index] = rows[index].copy(
                title = title,
                author = author,
                description = description,
                artworkUrl = artworkUrl,
                feedUrl = feedUrl,
                lastEpisodePublished = lastEpisodePublished,
            )
        }

        override suspend fun upsert(podcast: PodcastEntity) {
            if (insertIfAbsent(podcast) == -1L) {
                updateFeedFields(
                    uuid = podcast.uuid,
                    title = podcast.title,
                    author = podcast.author,
                    description = podcast.description,
                    artworkUrl = podcast.artworkUrl,
                    feedUrl = podcast.feedUrl,
                    lastEpisodePublished = podcast.lastEpisodePublished,
                )
            }
        }

        override suspend fun update(podcast: PodcastEntity) = upsert(podcast)
        override suspend fun delete(podcast: PodcastEntity) {
            rows.removeAll { it.uuid == podcast.uuid }
        }

        override suspend fun deleteByUuid(uuid: String) {
            rows.removeAll { it.uuid == uuid }
        }

        override suspend fun count() = rows.size

        override suspend fun byFeedUrl(feedUrl: String) =
            rows.firstOrNull { it.feedUrl == feedUrl }

        override suspend fun setAutoDownload(uuid: String, autoDownload: Boolean) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index >= 0) rows[index] = rows[index].copy(autoDownload = autoDownload)
        }
        override fun observeSearchResults(query: String) =
            kotlinx.coroutines.flow.flowOf(
                rows.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.author?.contains(query, ignoreCase = true) == true
                },
            )

        override suspend fun upsertWithEpisodes(
            podcast: PodcastEntity,
            episodes: List<EpisodeEntity>,
        ) = upsert(podcast)
    }

    private class FakeEpisodeDao : com.lohe.app.data.db.dao.EpisodeDao {
        var upsertCalls = 0
        val rows = mutableListOf<EpisodeEntity>()
        override fun observePodcastStats() = kotlinx.coroutines.flow.flowOf(
            rows.groupBy { it.podcastUuid }.map { (uuid, episodes) ->
                com.lohe.app.data.db.dao.PodcastEpisodeStats(uuid, episodes.count { !it.isDeleted },
                    episodes.count { !it.isDeleted && !it.isPlayed && !it.isArchived }, episodes.maxOf { it.published })
            })
        override fun observeAllSummaries() =
            kotlinx.coroutines.flow.flowOf(rows.map { it.toSummary() }.toList())
        override fun observeAllEntities() = kotlinx.coroutines.flow.flowOf(rows.toList())
        override fun observeDownloads() =
            kotlinx.coroutines.flow.flowOf(
                rows.filter { it.downloadState == DownloadState.DONE && it.localPath != null }
                    .map { it.toSummary() },
            )

        override fun observeInProgress() =
            kotlinx.coroutines.flow.flowOf(rows.filter { it.isInProgress })

        override fun observeQueue() =
            kotlinx.coroutines.flow.flowOf(rows.filter { it.isQueued }.sortedBy { it.queuePosition })

        override suspend fun upsertAll(episodes: List<EpisodeEntity>) {
            upsertCalls++
            episodes.forEach { incoming ->
                rows.removeAll { it.uuid == incoming.uuid }
                rows += incoming
            }
        }

        override suspend fun getMany(uuids: List<String>) = rows.filter { it.uuid in uuids }
        override suspend fun get(uuid: String) = rows.firstOrNull { it.uuid == uuid }
        override suspend fun searchByTitle(query: String, limit: Int) = rows.filter { it.title.contains(query, ignoreCase = true) }.take(limit)
        override fun observeByPodcast(podcastUuid: String) =
            kotlinx.coroutines.flow.flowOf(rows.filter { it.podcastUuid == podcastUuid })

        override suspend fun byPodcastOnce(podcastUuid: String) =
            rows.filter { it.podcastUuid == podcastUuid }


        override suspend fun allOnce() = rows.sortedByDescending { it.published }

        override fun observeLatest(limit: Int) = kotlinx.coroutines.flow.flowOf(rows.take(limit))
        override suspend fun queueSnapshot() =
            rows.filter { it.isQueued }.sortedBy { it.queuePosition }

        override suspend fun enqueue(uuid: String, now: Long) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index < 0) return
            if (rows[index].isQueued) return
            val next = (rows.mapNotNull { it.queuePosition }.maxOrNull() ?: -1) + 1
            rows[index] = rows[index].copy(queuePosition = next, lastPlayedAt = now)
        }

        override suspend fun dequeue(uuid: String) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index >= 0) rows[index] = rows[index].copy(queuePosition = null)
        }

        override suspend fun promoteInQueue(uuid: String) = Unit
        override suspend fun clearQueueFrom(uuid: String) = Unit
        override suspend fun renumber(uuid: String, position: Int) = Unit
        override suspend fun rewriteQueue(items: List<EpisodeEntity>) = Unit
        override suspend fun deleteForPodcast(podcastUuid: String) {
            rows.removeAll { it.podcastUuid == podcastUuid }
        }

        override suspend fun deleteLocalFiles() {
            rows.removeAll { it.audioUrl.startsWith(CONTENT_SCHEME) }
        }

        override suspend fun updateProgress(uuid: String, position: Long) = Unit
        override suspend fun saveProgress(uuid: String, position: Long, duration: Long) = Unit
        override suspend fun applyProgress(uuid: String, position: Long, duration: Long) = Unit
        override suspend fun applyUnplayed(uuid: String) = Unit
        override suspend fun markPlayedAndClearProgress(uuid: String) = Unit
        override suspend fun reopenEpisode(uuid: String) = Unit
        override suspend fun playedPosition(uuid: String) = 0L
        override suspend fun markPlayed(uuid: String) = Unit
        override suspend fun setStarred(uuid: String, starred: Boolean) = Unit
        override suspend fun setArchived(uuid: String, archived: Boolean) = Unit
        override suspend fun archive(uuid: String, now: Long) = Unit
        override suspend fun archivePlayedIfDue(uuid: String, cutoff: Long): Int = 0
        override suspend fun dueArchiveIds(archiveEnabled: Boolean, playedCutoff: Long, deleteCutoff: Long, afterUuid: String, limit: Int): List<String> = emptyList()
        override suspend fun setDeletedFor(uuids: List<String>, deleted: Boolean) = Unit
        override suspend fun setDownloadState(uuid: String, state: Int, path: String?) = Unit
        override suspend fun clearDownload(uuid: String) = Unit
    }

    /** Returns success for the listed URLs and failure for everything else. */
    private class ScriptedFetcher(
        private val working: Set<String>,
    ) : PodcastFetcher {
        var attempts = mutableListOf<String>()

        override suspend fun fetch(feedUrl: String): PodcastEntity =
            importFeed(feedUrl).getOrThrow()

        override suspend fun fetchEpisodes(feedUrl: String): List<EpisodeEntity> =
            emptyList()

        override suspend fun importFeed(feedUrl: String): Result<PodcastEntity> {
            attempts += feedUrl
            return if (feedUrl in working) {
                Result.success(
                    PodcastEntity(
                        uuid = feedUrl,
                        title = feedUrl.substringAfterLast('/'),
                        description = null,
                        feedUrl = feedUrl,
                        artworkUrl = null,
                        lastEpisodePublished = 0L,
                    ),
                )
            } else {
                Result.failure(IllegalStateException("unreachable: $feedUrl"))
            }
        }
    }

    /**
     * Emits episodes into the DAO on every import.
     *
     * [ScriptedFetcher] only hands back the show, so a refresh would have no new
     * rows to diff and the auto-download path could not be observed.
     */
    private class EpisodeEmittingFetcher(
        private val dao: FakeEpisodeDao,
        private val episodesByFeed: Map<String, List<EpisodeEntity>>,
    ) : PodcastFetcher {
        override suspend fun fetch(feedUrl: String): PodcastEntity =
            importFeed(feedUrl).getOrThrow()

        override suspend fun fetchEpisodes(feedUrl: String): List<EpisodeEntity> =
            episodesByFeed[feedUrl] ?: emptyList()

        override suspend fun importFeed(feedUrl: String): Result<PodcastEntity> {
            val incoming = episodesByFeed[feedUrl]
                ?: return Result.failure(IllegalStateException("unreachable: $feedUrl"))
            dao.upsertAll(incoming)
            return Result.success(
                PodcastEntity(
                    uuid = feedUrl,
                    title = feedUrl.substringAfterLast('/'),
                    description = null,
                    feedUrl = feedUrl,
                    artworkUrl = null,
                    lastEpisodePublished = 0L,
                ),
            )
        }
    }

    private fun repository(
        fetcher: PodcastFetcher,
        podcasts: FakePodcastDao = FakePodcastDao(),
        episodes: FakeEpisodeDao = FakeEpisodeDao(),
        autoDownload: (EpisodeEntity) -> Unit = {},
    ) = SubscriptionRepository(podcasts, episodes, FakeHistoryDao(), fetcher, autoDownload)

    /** In-memory stand-in for the archived listening history store. */
    private class FakeHistoryDao : com.lohe.app.data.db.dao.ListeningHistoryDao {
        var batchReads = 0
        var singleReads = 0
        private val rows = mutableMapOf<String, com.lohe.app.data.db.entity.ListeningHistoryEntity>()
        override fun observeAll(): kotlinx.coroutines.flow.Flow<
            List<com.lohe.app.data.db.entity.ListeningHistoryEntity>,
            > = kotlinx.coroutines.flow.flowOf(rows.values.toList())
        override suspend fun allOnce(): List<com.lohe.app.data.db.entity.ListeningHistoryEntity> =
            rows.values.toList()
        override suspend fun getMany(uuids: List<String>): List<com.lohe.app.data.db.entity.ListeningHistoryEntity> {
            batchReads++
            return allOnce().filter { it.episodeUuid in uuids }
        }
        override suspend fun deleteMany(uuids: List<String>) { uuids.forEach { delete(it) } }
        override suspend fun get(uuid: String): com.lohe.app.data.db.entity.ListeningHistoryEntity? { singleReads++; return rows[uuid] }
        override suspend fun upsertAll(
            entries: List<com.lohe.app.data.db.entity.ListeningHistoryEntity>,
        ) {
            entries.forEach { rows[it.episodeUuid] = it }
        }
        override suspend fun delete(uuid: String) {
            rows.remove(uuid)
        }
        override suspend fun clear() = rows.clear()
    }

    private fun show(title: String, feedUrl: String) = PodcastEntity(
        uuid = title,
        title = title,
        author = null,
        description = null,
        feedUrl = feedUrl,
        artworkUrl = null,
        lastEpisodePublished = 0L,
    )

    /** Feed-shaped episode. Named to avoid colliding with [episode] below, which
     *  takes an audio URL and hardcodes a single podcast. */
    private fun feedEpisode(
        uuid: String,
        podcastUuid: String,
        played: Boolean = false,
    ) = EpisodeEntity(
        uuid = uuid,
        podcastUuid = podcastUuid,
        title = uuid,
        audioUrl = "https://a.com/$uuid.mp3",
        published = 0L,
        playingStatus = if (played) EpisodeEntity.PLAYED else EpisodeEntity.UNPLAYED,
    )

    // --- Refresh -------------------------------------------------------------

    @Test
    fun `refresh reports one failure without aborting the sweep`() = runTest {
        val daos = FakePodcastDao(mutableListOf(
            show("Good", "https://good.com/f.xml"),
            show("Bad", "https://bad.com/f.xml"),
            show("AlsoGood", "https://alsogood.com/f.xml"),
        ))
        val repo = repository(
            fetcher = ScriptedFetcher(setOf("https://good.com/f.xml", "https://alsogood.com/f.xml")),
            podcasts = daos,
        )

        val summary = repo.refreshAllFeeds()

        assertEquals(3, summary.total)
        assertEquals(2, summary.refreshed)
        assertEquals(1, summary.failed)
        assertEquals(listOf("Bad"), summary.failureTitles)
    }

    @Test
    fun `refresh skips shows with no feed url`() = runTest {
        val daos = FakePodcastDao(mutableListOf(
            show("Local", ""),
            show("Remote", "https://remote.com/f.xml"),
        ))
        val fetcher = ScriptedFetcher(setOf("https://remote.com/f.xml"))
        val repo = repository(fetcher, podcasts = daos)

        val summary = repo.refreshAllFeeds()

        // A device-local import has nothing to refresh and must not be counted.
        assertEquals(1, summary.total)
        assertEquals(1, summary.refreshed)
        assertEquals(listOf("https://remote.com/f.xml"), fetcher.attempts)
    }

    @Test
    fun `refreshing an empty library is a no-op`() = runTest {
        val fetcher = ScriptedFetcher(emptySet())
        val summary = repository(fetcher).refreshAllFeeds()

        assertEquals(0, summary.total)
        assertEquals(0, summary.refreshed)
        assertTrue(fetcher.attempts.isEmpty())
    }

    @Test
    fun `every feed is attempted even when the first one fails`() = runTest {
        val daos = FakePodcastDao(
            mutableListOf(
                show("A", "https://a.com/f.xml"),
                show("B", "https://b.com/f.xml"),
                show("C", "https://c.com/f.xml"),
            ),
        )
        val repo = repository(ScriptedFetcher(emptySet()), podcasts = daos)

        val summary = repo.refreshAllFeeds()

        assertEquals("a total failure must not stop the sweep", 3, summary.refreshed + summary.failed)
        assertEquals(3, summary.failed)
    }

    // --- OPML ---------------------------------------------------------------

    @Test
    fun `opml import counts partial success`() = runTest {
        val opml = OpmlCodec.write(
            listOf(
                OpmlSubscription("Good", "https://good.com/f.xml"),
                OpmlSubscription("Bad", "https://bad.com/f.xml"),
            ),
        )
        val repo = repository(ScriptedFetcher(setOf("https://good.com/f.xml")))

        val result = repo.importOpml(opml)

        assertEquals(2, result.total)
        assertEquals(1, result.imported)
        assertEquals(1, result.failed)
        assertEquals(listOf("Bad"), result.failures.map { it.title })
    }

    @Test
    fun `importing a document that is not opml throws for the caller to catch`() = runTest {
        val repo = repository(ScriptedFetcher(emptySet()))
        val failure = runCatching { repo.importOpml("this is not xml") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `importing an empty opml imports nothing`() = runTest {
        val fetcher = ScriptedFetcher(emptySet())
        val result = repository(fetcher).importOpml(
            """<opml version="2.0"><head/><body/></opml>""",
        )

        assertEquals(0, result.total)
        assertEquals(0, result.imported)
        assertTrue(fetcher.attempts.isEmpty())
    }

    @Test
    fun `export omits device-local shows`() = runTest {
        val daos = FakePodcastDao(
            mutableListOf(
                show("Remote", "https://remote.com/f.xml"),
                show("Imported", ""),
            ),
        )
        val xml = repository(ScriptedFetcher(emptySet()), podcasts = daos).exportOpml()

        val titles = OpmlCodec.read(xml).map { it.title }
        assertEquals(listOf("Remote"), titles)
    }

    @Test
    fun `export of an empty library still produces a valid document`() = runTest {
        val xml = repository(ScriptedFetcher(emptySet())).exportOpml()
        assertTrue(xml.contains("<opml"))
        assertTrue(OpmlCodec.read(xml).isEmpty())
    }

    @Test
    fun `export then import preserves the subscription set`() = runTest {
        val originals = listOf(
            OpmlSubscription("Alpha", "https://a.com/f.xml"),
            OpmlSubscription("Beta", "https://b.com/f.xml"),
        )
        val exporter = repository(ScriptedFetcher(emptySet()))
        val document = OpmlCodec.write(originals)

        val importer = repository(ScriptedFetcher(originals.map { it.feedUrl }.toSet()))
        val result = importer.importOpml(document)

        assertEquals(2, result.imported)
        assertEquals(0, result.failed)
    }

    // --- Per-show settings -----------------------------------------------------

    @Test
    fun `a show is looked up by its feed url`() = runTest {
        val daos = FakePodcastDao(mutableListOf(show("Remote", "https://remote.com/f.xml")))
        val repo = repository(ScriptedFetcher(emptySet()), podcasts = daos)

        assertEquals("Remote", repo.subscriptionForFeed("https://remote.com/f.xml")?.title)
    }

    @Test
    fun `an unsubscribed feed has no library row`() = runTest {
        val repo = repository(ScriptedFetcher(emptySet()))
        assertEquals(null, repo.subscriptionForFeed("https://not-followed.com/f.xml"))
    }

    @Test
    fun `auto-download defaults to off`() = runTest {
        assertFalse(show("Remote", "https://a.com/f.xml").autoDownload)
    }

    @Test
    fun `auto-download is per show`() = runTest {
        val daos = FakePodcastDao(
            mutableListOf(
                show("On", "https://on.com/f.xml"),
                show("Off", "https://off.com/f.xml"),
            ),
        )
        val repo = repository(ScriptedFetcher(emptySet()), podcasts = daos)

        repo.setAutoDownload("On", true)

        assertTrue(repo.subscriptionForFeed("https://on.com/f.xml")!!.autoDownload)
        assertFalse(
            "turning it on for one show must not affect the other",
            repo.subscriptionForFeed("https://off.com/f.xml")!!.autoDownload,
        )
    }

    // --- Auto-download on refresh --------------------------------------------
    //
    // The setting persisted and rendered long before anything acted on it, so
    // these pin the behaviour that was missing: which episodes a refresh hands
    // over, and — more importantly — which it must not.

    private val feed = "https://s.com/f.xml"

    @Test
    fun `refresh does not auto-download when the setting is off`() = runTest {
        val eps = FakeEpisodeDao()
        eps.upsertAll(listOf(feedEpisode("e1", "S")))
        val requested = mutableListOf<String>()
        val repo = repository(
            fetcher = EpisodeEmittingFetcher(
                eps,
                mapOf(feed to listOf(feedEpisode("e1", "S"), feedEpisode("e2", "S"))),
            ),
            podcasts = FakePodcastDao(mutableListOf(show("S", feed))),
            episodes = eps,
        ) { requested += it.uuid }

        repo.refreshAllFeeds()

        assertTrue("nothing should be fetched when the flag is off", requested.isEmpty())
    }

    @Test
    fun `refresh auto-downloads only episodes it has not seen before`() = runTest {
        val eps = FakeEpisodeDao()
        eps.upsertAll(listOf(feedEpisode("old", "S")))
        val requested = mutableListOf<String>()
        val repo = repository(
            fetcher = EpisodeEmittingFetcher(
                eps,
                mapOf(feed to listOf(feedEpisode("old", "S"), feedEpisode("new", "S"))),
            ),
            podcasts = FakePodcastDao(mutableListOf(show("S", feed).copy(autoDownload = true))),
            episodes = eps,
        ) { requested += it.uuid }

        repo.refreshAllFeeds()

        assertEquals(listOf("new"), requested)
    }

    @Test
    fun `the first fetch of a show does not pull its back catalogue`() = runTest {
        val eps = FakeEpisodeDao()
        val requested = mutableListOf<String>()
        val repo = repository(
            fetcher = EpisodeEmittingFetcher(
                eps,
                mapOf(
                    feed to listOf(
                        feedEpisode("e1", "S"), feedEpisode("e2", "S"), feedEpisode("e3", "S"),
                    ),
                ),
            ),
            podcasts = FakePodcastDao(
                mutableListOf(show("S", feed).copy(autoDownload = true)),
            ),
            episodes = eps,
        ) { requested += it.uuid }

        repo.refreshAllFeeds()

        assertTrue(
            "a new subscription arrives with its whole history; none of it is \"new\"",
            requested.isEmpty(),
        )
    }

    @Test
    fun `a newly seen episode that is already finished is not downloaded`() = runTest {
        val eps = FakeEpisodeDao()
        eps.upsertAll(listOf(feedEpisode("old", "S")))
        val requested = mutableListOf<String>()
        val repo = repository(
            fetcher = EpisodeEmittingFetcher(
                eps,
                mapOf(
                    feed to listOf(
                        feedEpisode("old", "S"),
                        feedEpisode("heard", "S", played = true),
                        feedEpisode("unheard", "S"),
                    ),
                ),
            ),
            podcasts = FakePodcastDao(
                mutableListOf(show("S", feed).copy(autoDownload = true)),
            ),
            episodes = eps,
        ) { requested += it.uuid }

        repo.refreshAllFeeds()

        assertEquals(listOf("unheard"), requested)
    }

    // --- Storage -------------------------------------------------------------

    @Test
    fun `local storage size counts only content uris`() = runTest {
        val episodes = FakeEpisodeDao().apply {
            rows += episode("local", "content://media/1.mp3", size = 1_000)
            rows += episode("remote", "https://cdn.com/1.mp3", size = 5_000)
            rows += episode("local2", "content://media/2.mp3", size = 2_000)
        }
        val repo = repository(ScriptedFetcher(emptySet()), episodes = episodes)

        val size = repo.getLocalFilesStorageSize().let { flow ->
            var captured = 0L
            flow.collect { captured = it }
            captured
        }

        // Remote episodes are not storage this app owns.
        assertEquals(3_000L, size)
    }

    @Test
    fun `deleting local files leaves remote episodes alone`() = runTest {
        val episodes = FakeEpisodeDao().apply {
            rows += episode("local", "content://media/1.mp3")
            rows += episode("remote", "https://cdn.com/1.mp3")
        }
        val repo = repository(ScriptedFetcher(emptySet()), episodes = episodes)

        repo.deleteLocalFiles()

        assertEquals(listOf("remote"), episodes.rows.map { it.uuid })
    }

    @Test
    fun `episodes with an unknown size do not break the total`() = runTest {
        val episodes = FakeEpisodeDao().apply {
            rows += episode("known", "content://media/1.mp3", size = 1_000)
            rows += episode("unknown", "content://media/2.mp3", size = null)
        }
        val repo = repository(ScriptedFetcher(emptySet()), episodes = episodes)

        var captured = 0L
        repo.getLocalFilesStorageSize().collect { captured = it }
        assertEquals(1_000L, captured)
    }

    // --- Helpers -------------------------------------------------------------

    private fun episode(
        uuid: String,
        audioUrl: String,
        size: Long? = null,
        downloadState: Int = DownloadState.NONE,
    ) = EpisodeEntity(
        uuid = uuid,
        podcastUuid = "p1",
        title = uuid,
        audioUrl = audioUrl,
        published = 0L,
        sizeBytes = size,
        downloadState = downloadState,
    )

    @Test
    fun `feed refresh batches history preserves local state and skips unchanged episodes`() = runTest {
        val feed = """<rss version="2.0"><channel><title>Show</title><item><guid>one</guid><title>Updated</title><enclosure url="https://example.com/audio.mp3" type="audio/mpeg"/><pubDate>Mon, 26 Oct 2026 10:00:00 GMT</pubDate></item></channel></rss>"""
        val url = "https://example.com/feed"
        val parsed = com.lohe.app.data.fetch.FeedParser().parse(url, feed.toByteArray())
        val episode = parsed.episodes.single().copy(title = "Old", playingStatus = 1,
            playedUpToSeconds = 42, listenedSeconds = 40, starred = true, queuePosition = 3,
            localPath = "/private/audio.mp3", downloadState = DownloadState.DONE, isArchived = true, archivedAt = 100L)
        val episodes = FakeEpisodeDao().also { it.rows.add(episode) }
        val history = FakeHistoryDao()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(feed.toResponseBody()).build()
        }.build()
        var transactions = 0
        val fetcher = com.lohe.app.data.fetch.RssPodcastFetcher(client, FakePodcastDao(), episodes, history,
            transaction = { block -> transactions++; block() })
        assertTrue(fetcher.importFeed(url).isSuccess)
        assertEquals(episode.copy(title = "Updated"), episodes.rows.single())
        assertTrue(fetcher.importFeed(url).isSuccess)
        assertEquals(1, episodes.upsertCalls)
        assertEquals(2, history.batchReads)
        assertEquals(0, history.singleReads)
        assertEquals(2, transactions)
    }
}
