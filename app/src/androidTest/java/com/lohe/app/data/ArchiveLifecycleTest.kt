package com.lohe.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.db.entity.toSummary
import com.lohe.app.data.fetch.RssPodcastFetcher
import com.lohe.app.download.DownloadManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveLifecycleTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SubscriptionRepository
    private lateinit var downloads: DownloadManager
    private val playlists = PlaylistRepository()
    private val files = mutableListOf<File>()

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val fetcher = RssPodcastFetcher(podcastDao = database.podcastDao(), episodeDao = database.episodeDao())
        repository = SubscriptionRepository(database.podcastDao(), database.episodeDao(), database.listeningHistoryDao(), fetcher,
            onPlayed = { playlists.removeEpisodeFromAll(it) },
            onArchived = { downloads.delete(it.toSummary()) },
        )
        downloads = DownloadManager(context, repository)
        database.podcastDao().upsert(PodcastEntity("archive-test-show", "Test", feedUrl = "https://example.com/feed"))
    }

    @After
    fun teardown() {
        files.forEach { it.delete() }
        database.close()
    }

    private suspend fun episode(): EpisodeEntity {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("archive-test-", ".mp3", context.cacheDir).also { it.writeText("test audio"); files.add(it) }
        val episode = EpisodeEntity("archive-test-episode", "archive-test-show", "Test", "https://example.com/test.mp3", 1L,
            localPath = file.absolutePath, downloadState = DownloadState.DONE, listenedSeconds = 90, starred = true)
        database.episodeDao().upsertAll(listOf(episode))
        return episode
    }

    @Test
    fun playedLeavesPlaylistsImmediatelyButKeepsDownloadUntilDue() = runBlocking {
        val episode = episode()
        val snapshot = playlists.createPlaylist("Test", listOf(episode.uuid), snapshot = true)
        repository.markPlayed(episode.uuid)
        val played = repository.episodeOnce(episode.uuid)!!
        assertTrue(playlists.playlists.value.first { it.id == snapshot.id }.episodeUuids.isEmpty())
        assertFalse(played.isArchived)
        assertTrue(File(episode.localPath!!).exists())
        repository.archivePlayedIfDue(episode.uuid, played.playedAt!! - 1)
        assertFalse(repository.episodeOnce(episode.uuid)!!.isArchived)
        repository.archivePlayedIfDue(episode.uuid, played.playedAt!!)
        val archived = repository.episodeOnce(episode.uuid)!!
        assertTrue(archived.isArchived)
        assertFalse(File(episode.localPath!!).exists())
        assertEquals(null, archived.localPath)
        assertEquals(DownloadState.NONE, archived.downloadState)
        assertEquals(90L, archived.listenedSeconds)
        assertTrue(archived.starred)
    }

    @Test
    fun manualArchiveMarksPlayedAndInvokesCleanup() = runBlocking {
        val episode = episode()
        repository.setArchived(episode.uuid, true)
        val archived = repository.episodeOnce(episode.uuid)!!
        assertTrue(archived.isArchived)
        assertEquals(EpisodeEntity.PLAYED, archived.playingStatus)
        assertFalse(File(episode.localPath!!).exists())
        assertEquals(DownloadState.NONE, archived.downloadState)
        assertEquals(90L, archived.listenedSeconds)
    }

    @Test
    fun reopeningCancelsPendingArchive() = runBlocking {
        val episode = episode()
        repository.markPlayed(episode.uuid)
        repository.reopenEpisode(episode.uuid)
        repository.archivePlayedIfDue(episode.uuid, Long.MAX_VALUE)
        val reopened = repository.episodeOnce(episode.uuid)!!
        assertFalse(reopened.isArchived)
        assertEquals(null, reopened.playedAt)
        assertTrue(File(episode.localPath!!).exists())
    }
}
