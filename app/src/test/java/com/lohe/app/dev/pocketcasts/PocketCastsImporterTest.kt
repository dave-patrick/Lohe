package com.lohe.app.dev.pocketcasts

import com.lohe.app.data.PlaylistRepository
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.dao.EpisodeDao
import com.lohe.app.data.db.dao.ListeningHistoryDao
import com.lohe.app.data.db.dao.PodcastDao
import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.db.entity.toSummary
import com.lohe.app.data.fetch.PodcastFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the import has to get right: the same episodes, in the same state, in
 * the same playlists.
 *
 * The DAOs here are real enough to hold state, because the point of the
 * import is that the state changes — a fake that ignored its writes would
 * pass a test that means nothing.
 */
class PocketCastsImporterTest {

    // --- Fakes --------------------------------------------------------------

    private class FakePodcastDao(
        val rows: MutableList<PodcastEntity> = mutableListOf(),
    ) : PodcastDao {
        override fun observeAll(): Flow<List<PodcastEntity>> = flowOf(rows.toList())
        override suspend fun allOnce() = rows.toList()
        override suspend fun get(uuid: String) = rows.firstOrNull { it.uuid == uuid }
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
                    podcast.uuid,
                    podcast.title,
                    podcast.author,
                    podcast.description,
                    podcast.artworkUrl,
                    podcast.feedUrl,
                    podcast.lastEpisodePublished,
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

        override fun observeSearchResults(query: String) = flowOf(rows.toList())
        override suspend fun upsertWithEpisodes(
            podcast: PodcastEntity,
            episodes: List<EpisodeEntity>,
        ) = upsert(podcast)
    }

    private class FakeEpisodeDao : EpisodeDao {
        val rows = mutableListOf<EpisodeEntity>()
        override fun observeAllSummaries() = flowOf(rows.map { it.toSummary() })
        override fun observeAllEntities() = flowOf(rows.toList())
        override fun observeDownloads() =
            flowOf(
                rows.filter { it.downloadState == DownloadState.DONE && it.localPath != null }
                    .map { it.toSummary() },
            )

        override fun observeInProgress() = flowOf(rows.filter { it.isInProgress })
        override fun observeQueue() =
            flowOf(rows.filter { it.isQueued }.sortedBy { it.queuePosition })

        override suspend fun upsertAll(episodes: List<EpisodeEntity>) {
            episodes.forEach { incoming ->
                rows.removeAll { it.uuid == incoming.uuid }
                rows += incoming
            }
        }

        override suspend fun get(uuid: String) = rows.firstOrNull { it.uuid == uuid }
        override fun observeByPodcast(podcastUuid: String) =
            flowOf(rows.filter { it.podcastUuid == podcastUuid })

        override suspend fun byPodcastOnce(podcastUuid: String) =
            rows.filter { it.podcastUuid == podcastUuid }

        override suspend fun allOnce() = rows.sortedByDescending { it.published }
        override fun observeLatest(limit: Int) = flowOf(rows.take(limit))
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
            rows.removeAll { it.audioUrl.startsWith("content://") }
        }

        override suspend fun updateProgress(uuid: String, position: Long) = Unit
        override suspend fun saveProgress(uuid: String, position: Long, duration: Long) = Unit

        override suspend fun markPlayedAndClearProgress(uuid: String) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index >= 0) {
                rows[index] = rows[index].copy(
                    playingStatus = EpisodeEntity.PLAYED,
                    playedUpToSeconds = 0,
                )
            }
        }

        override suspend fun reopenEpisode(uuid: String) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index >= 0) {
                rows[index] = rows[index].copy(
                    playingStatus = EpisodeEntity.UNPLAYED,
                    playedUpToSeconds = 0,
                    listenedSeconds = 0,
                )
            }
        }

        override suspend fun applyProgress(uuid: String, position: Long, duration: Long) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index >= 0) {
                rows[index] = rows[index].copy(
                    playedUpToSeconds = position,
                    playingStatus = EpisodeEntity.IN_PROGRESS,
                    durationSeconds = rows[index].durationSeconds?.takeIf { it > 0 } ?: duration.toInt(),
                )
            }
        }

        override suspend fun applyUnplayed(uuid: String) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index >= 0) {
                rows[index] = rows[index].copy(
                    playingStatus = EpisodeEntity.UNPLAYED,
                    playedUpToSeconds = 0,
                )
            }
        }

        override suspend fun playedPosition(uuid: String) = 0L
        override suspend fun markPlayed(uuid: String) = Unit
        override suspend fun setStarred(uuid: String, starred: Boolean) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index >= 0) rows[index] = rows[index].copy(starred = starred)
        }

        override suspend fun setArchived(uuid: String, archived: Boolean) {
            val index = rows.indexOfFirst { it.uuid == uuid }
            if (index >= 0) rows[index] = rows[index].copy(isArchived = archived)
        }
        override suspend fun archivePlayedIfDue(uuid: String, cutoff: Long): Int = 0
        override suspend fun archiveCandidates(): List<EpisodeEntity> = emptyList()
        override suspend fun setDeletedFor(uuids: List<String>, deleted: Boolean) = Unit

        override suspend fun setDownloadState(uuid: String, state: Int, path: String?) = Unit
        override suspend fun clearDownload(uuid: String) = Unit
    }

    private class FakeHistoryDao : ListeningHistoryDao {
        val rows = mutableListOf<ListeningHistoryEntity>()
        override fun observeAll(): Flow<List<ListeningHistoryEntity>> = flowOf(rows.toList())
        override suspend fun allOnce() = rows.toList()
        override suspend fun get(uuid: String) = rows.firstOrNull { it.episodeUuid == uuid }

        override suspend fun upsertAll(items: List<ListeningHistoryEntity>) {
            items.forEach { item ->
                rows.removeAll { it.episodeUuid == item.episodeUuid }
                rows += item
            }
        }

        override suspend fun delete(uuid: String) {
            rows.removeAll { it.episodeUuid == uuid }
        }

        override suspend fun clear() {
            rows.clear()
        }
    }

    private class UnusedFetcher : PodcastFetcher {
        override suspend fun fetch(feedUrl: String): PodcastEntity =
            throw IllegalStateException("no network in a test")

        override suspend fun fetchEpisodes(feedUrl: String): List<EpisodeEntity> = emptyList()

        override suspend fun importFeed(feedUrl: String): Result<PodcastEntity> =
            Result.failure(IllegalStateException("no network in a test"))
    }

    // --- Fixtures -----------------------------------------------------------

    private fun show(uuid: String, title: String, feedUrl: String) =
        PodcastEntity(uuid = uuid, title = title, feedUrl = feedUrl)

    private fun episode(
        uuid: String,
        podcastUuid: String,
        title: String,
        mediaUrl: String,
        published: Long = 1_700_000_000L,
    ) = EpisodeEntity(
        uuid = uuid,
        podcastUuid = podcastUuid,
        title = title,
        audioUrl = mediaUrl,
        published = published,
        durationSeconds = 600,
    )

    private fun library(
        shows: List<PcShow> = emptyList(),
        episodes: List<PcEpisode> = emptyList(),
        playlists: List<PcPlaylist> = emptyList(),
        folders: List<PcFolder> = emptyList(),
        upNext: List<String> = emptyList(),
    ) = PocketCastsLibrary(shows, episodes, playlists, folders, upNext)

    private fun pcEpisode(
        uuid: String,
        podcastUuid: String,
        title: String,
        mediaUrl: String,
        status: String = "unplayed",
        playedUpTo: Long = 0,
        starred: Boolean = false,
        deleted: Boolean = false,
    ) = PcEpisode(
        uuid = uuid,
        podcastUuid = podcastUuid,
        title = title,
        mediaUrl = mediaUrl,
        publishedMs = null,
        durationSeconds = 600,
        status = status,
        playedUpToSeconds = playedUpTo,
        starred = starred,
        archived = deleted,
        deleted = deleted,
    )

    private fun harness(
        shows: List<PodcastEntity> = emptyList(),
        episodes: List<EpisodeEntity> = emptyList(),
    ): Triple<SubscriptionRepository, PlaylistRepository, FakeEpisodeDao> {
        val podcastDao = FakePodcastDao(shows.toMutableList())
        val episodeDao = FakeEpisodeDao().apply { rows += episodes }
        val repository = SubscriptionRepository(
            podcasts = podcastDao,
            episodes = episodeDao,
            history = FakeHistoryDao(),
            fetcher = UnusedFetcher(),
        )
        return Triple(repository, PlaylistRepository(file = null), episodeDao)
    }

    // --- Tests --------------------------------------------------------------

    @Test
    fun `an episode played in Pocket Casts is played here`() = runTest {
        val (repository, playlists, dao) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://feed.example/show.xml")),
            episodes = listOf(
                episode("e1", "lohe-show", "Episode One", "https://cdn.example/1.mp3"),
            ),
        )
        val importer = PocketCastsImporter(repository, playlists)

        val report = importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://feed.example/show.xml")),
                episodes = listOf(
                    pcEpisode("pc-e1", "pc-show", "Episode One", "https://cdn.example/1.mp3", status = "played"),
                ),
            ),
        )

        assertEquals(1, report.episodesMatched)
        assertEquals(1, report.markedPlayed)
        assertEquals(EpisodeEntity.PLAYED, dao.rows.first().playingStatus)
    }

    @Test
    fun `a star in Pocket Casts is a star here`() = runTest {
        val (repository, playlists, dao) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://feed.example/show.xml")),
            episodes = listOf(episode("e1", "lohe-show", "Episode One", "https://cdn.example/1.mp3")),
        )
        val importer = PocketCastsImporter(repository, playlists)

        importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://feed.example/show.xml")),
                episodes = listOf(
                    pcEpisode("pc-e1", "pc-show", "Episode One", "https://cdn.example/1.mp3", starred = true),
                ),
            ),
        )

        assertTrue(dao.rows.first().starred)
    }

    @Test
    fun `a part-played episode keeps its place and its listening time`() = runTest {
        val listened = episode("e1", "lohe-show", "Episode One", "https://cdn.example/1.mp3")
            .copy(listenedSeconds = 120)
        val (repository, playlists, dao) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://feed.example/show.xml")),
            episodes = listOf(listened),
        )
        val importer = PocketCastsImporter(repository, playlists)

        importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://feed.example/show.xml")),
                episodes = listOf(
                    pcEpisode(
                        "pc-e1", "pc-show", "Episode One", "https://cdn.example/1.mp3",
                        status = "in_progress", playedUpTo = 42,
                    ),
                ),
            ),
        )

        val row = dao.rows.first()
        assertEquals(EpisodeEntity.IN_PROGRESS, row.playingStatus)
        assertEquals(42, row.playedUpToSeconds)
        // The listening happened elsewhere, so it is not added to here.
        assertEquals(120, row.listenedSeconds)
    }

    @Test
    fun `a show is matched by its title when the feed moved`() = runTest {
        val (repository, playlists, dao) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://new.example/show.xml")),
            episodes = listOf(episode("e1", "lohe-show", "Episode One", "https://cdn.example/1.mp3")),
        )
        val importer = PocketCastsImporter(repository, playlists)

        val report = importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://old.example/show.xml")),
                episodes = listOf(
                    pcEpisode("pc-e1", "pc-show", "Episode One", "https://cdn.example/1.mp3", status = "played"),
                ),
            ),
        )

        assertEquals(1, report.showsMatched)
        assertEquals(1, report.markedPlayed)
    }

    @Test
    fun `an episode Lohe does not have is reported, not invented`() = runTest {
        val (repository, playlists, _) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://feed.example/show.xml")),
        )
        val importer = PocketCastsImporter(repository, playlists)

        val report = importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://feed.example/show.xml")),
                episodes = listOf(
                    pcEpisode("pc-e1", "pc-show", "Unknown Episode", "https://cdn.example/9.mp3"),
                ),
            ),
        )

        assertEquals(0, report.episodesMatched)
        assertEquals(1, report.episodesUnmatched)
    }

    @Test
    fun `a playlist is written with the episodes it holds`() = runTest {
        val (repository, playlists, _) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://feed.example/show.xml")),
            episodes = listOf(
                episode("e1", "lohe-show", "One", "https://cdn.example/1.mp3"),
                episode("e2", "lohe-show", "Two", "https://cdn.example/2.mp3"),
            ),
        )
        val importer = PocketCastsImporter(repository, playlists)

        importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://feed.example/show.xml")),
                episodes = listOf(
                    pcEpisode("pc-e1", "pc-show", "One", "https://cdn.example/1.mp3"),
                    pcEpisode("pc-e2", "pc-show", "Two", "https://cdn.example/2.mp3"),
                ),
                playlists = listOf(PcPlaylist("Weekend", listOf("pc-e2", "pc-e1"))),
            ),
        )

        val written = playlists.playlists.value.single()
        assertEquals("Weekend", written.name)
        assertEquals(listOf("e2", "e1"), written.episodeUuids)
    }

    @Test
    fun `an existing playlist loses the rules that would override it`() = runTest {
        val (repository, playlists, _) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://feed.example/show.xml")),
            episodes = listOf(episode("e1", "lohe-show", "One", "https://cdn.example/1.mp3")),
        )
        playlists.createPlaylist(
            name = "Weekend",
            initialEpisodes = emptyList(),
            folder = "Music",
            shows = listOf("lohe-show"),
            minDurationMinutes = 10,
        )
        val importer = PocketCastsImporter(repository, playlists)

        importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://feed.example/show.xml")),
                episodes = listOf(
                    pcEpisode("pc-e1", "pc-show", "One", "https://cdn.example/1.mp3"),
                ),
                playlists = listOf(PcPlaylist("Weekend", listOf("pc-e1"))),
            ),
        )

        val written = playlists.playlists.value.single()
        assertEquals(listOf("e1"), written.episodeUuids)
        assertTrue(written.shows.isEmpty())
        assertEquals(0, written.minDurationMinutes)
        assertEquals(0, written.maxDurationMinutes)
        assertEquals("", written.folder)
    }

    @Test
    fun `folders are applied and the ones Pocket Casts dropped are cleared`() = runTest {
        val (repository, playlists, _) = harness(
            shows = listOf(
                show("lohe-show", "The Show", "https://feed.example/show.xml"),
                show("other-show", "Other Show", "https://feed.example/other.xml"),
            ),
        )
        playlists.setPodcastFolder("other-show", "Old Folder")
        val importer = PocketCastsImporter(repository, playlists, replaceFolders = true)

        importer.apply(
            library(
                shows = listOf(
                    PcShow("pc-show", "The Show", "https://feed.example/show.xml"),
                    PcShow("pc-other", "Other Show", "https://feed.example/other.xml"),
                ),
                folders = listOf(PcFolder("Music", listOf("pc-show"))),
            ),
        )

        assertEquals("Music", playlists.podcastFolders.value["lohe-show"])
        assertNull(playlists.podcastFolders.value["other-show"])
    }

    @Test
    fun `a folder Pocket Casts does not know about is left alone`() = runTest {
        val (repository, playlists, _) = harness(
            shows = listOf(
                show("lohe-show", "The Show", "https://feed.example/show.xml"),
                show("other-show", "Other Show", "https://feed.example/other.xml"),
            ),
        )
        playlists.setPodcastFolder("other-show", "Old Folder")
        val importer = PocketCastsImporter(repository, playlists)

        importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://feed.example/show.xml")),
                folders = listOf(PcFolder("Music", listOf("pc-show"))),
            ),
        )

        assertEquals("Music", playlists.podcastFolders.value["lohe-show"])
        assertEquals("Old Folder", playlists.podcastFolders.value["other-show"])
    }

    @Test
    fun `the queue becomes the up next list`() = runTest {
        val (repository, playlists, dao) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://feed.example/show.xml")),
            episodes = listOf(
                episode("e1", "lohe-show", "One", "https://cdn.example/1.mp3"),
                episode("e2", "lohe-show", "Two", "https://cdn.example/2.mp3"),
            ),
        )
        val importer = PocketCastsImporter(repository, playlists)

        val report = importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://feed.example/show.xml")),
                episodes = listOf(
                    pcEpisode("pc-e1", "pc-show", "One", "https://cdn.example/1.mp3"),
                    pcEpisode("pc-e2", "pc-show", "Two", "https://cdn.example/2.mp3"),
                ),
                upNext = listOf("pc-e2", "pc-e1"),
            ),
        )

        assertEquals(2, report.queued)
        assertEquals(
            listOf("e2", "e1"),
            dao.rows.filter { it.isQueued }.sortedBy { it.queuePosition }.map { it.uuid },
        )
    }

    @Test
    fun `an archived Pocket Casts episode is archived here and not deleted`() = runTest {
        val (repository, playlists, dao) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://feed.example/show.xml")),
            episodes = listOf(episode("e1", "lohe-show", "One", "https://cdn.example/1.mp3")),
        )
        val importer = PocketCastsImporter(repository, playlists)

        importer.apply(
            library(
                shows = listOf(PcShow("pc-show", "The Show", "https://feed.example/show.xml")),
                episodes = listOf(
                    pcEpisode("pc-e1", "pc-show", "One", "https://cdn.example/1.mp3", deleted = true),
                ),
            ),
        )

        assertTrue(dao.rows.first().isArchived)
        assertFalse(dao.rows.first().isDeleted)
    }

    @Test
    fun `a show neither app has is left alone`() = runTest {
        val (repository, playlists, _) = harness(
            shows = listOf(show("lohe-show", "The Show", "https://feed.example/show.xml")),
        )
        val importer = PocketCastsImporter(repository, playlists)

        val report = importer.apply(
            library(shows = listOf(PcShow("pc-show", "Another Show", "https://feed.example/other.xml"))),
        )

        assertEquals(0, report.showsMatched)
        assertEquals(listOf("Another Show"), report.showsUnmatched)
    }
}
