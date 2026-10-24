package com.lohe.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the feed-refresh data loss.
 *
 * `episodes` has a foreign key onto `podcasts` with ON DELETE CASCADE, and
 * Room implements `@Insert(onConflict = REPLACE)` as delete-then-insert. A
 * podcast upsert written that way therefore deleted every episode of the
 * show before the refreshed feed copy was written back in — silently
 * discarding played status, resume positions, downloads, stars and queue
 * order on every refresh.
 *
 * This runs against a real Room database on a real SQLite engine, because
 * the foreign key is the whole point: a fake DAO would not reproduce it.
 */
@RunWith(AndroidJUnit4::class)
class PodcastUpsertCascadeTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun podcast(title: String = "Test Show") = PodcastEntity(
        uuid = "podcast-1",
        title = title,
        feedUrl = "https://example.com/feed.xml",
    )

    private fun episode() = EpisodeEntity(
        uuid = "episode-1",
        podcastUuid = "podcast-1",
        title = "Episode One",
        audioUrl = "https://example.com/one.mp3",
        published = 1_700_000_000L,
    )

    @Test
    fun completedEpisodesWaitForArchiveDelayAndCanBeReopened() = runBlocking {
        db.podcastDao().upsert(podcast())
        val episodes = db.episodeDao()
        episodes.upsertAll(listOf(episode().copy(
            playingStatus = EpisodeEntity.IN_PROGRESS,
            playedUpToSeconds = 90,
            listenedSeconds = 60,
            starred = true,
        )))
        episodes.markPlayedAndClearProgress("episode-1")
        val completed = episodes.get("episode-1")!!
        assertEquals(EpisodeEntity.PLAYED, completed.playingStatus)
        assertTrue(!completed.isArchived)
        assertTrue(completed.playedAt != null)
        val completedAt = completed.playedAt!!
        episodes.markPlayedAndClearProgress("episode-1")
        assertEquals(completedAt, episodes.get("episode-1")!!.playedAt)
        assertEquals(0, episodes.archivePlayedIfDue("episode-1", completedAt - 1))
        assertEquals(1, episodes.archivePlayedIfDue("episode-1", completedAt))
        assertTrue(episodes.get("episode-1")!!.isArchived)
        assertEquals(0L, completed.playedUpToSeconds)
        assertEquals(60L, completed.listenedSeconds)
        assertTrue(completed.starred)
        episodes.reopenEpisode("episode-1")
        val reopened = episodes.get("episode-1")!!
        assertEquals(EpisodeEntity.UNPLAYED, reopened.playingStatus)
        assertTrue(!reopened.isArchived)
        assertEquals(null, reopened.playedAt)
        assertEquals(0, episodes.archivePlayedIfDue("episode-1", Long.MAX_VALUE))
        assertTrue(reopened.starred)
    }

    @Test
    fun retainedUnsubscribedShowsKeepTheirEpisodesAndCanBeFollowedAgain() = runBlocking {
        val podcasts = db.podcastDao()
        val episodes = db.episodeDao()
        podcasts.upsert(podcast())
        episodes.upsertAll(listOf(episode()))
        podcasts.update(podcasts.get("podcast-1")!!.copy(isSubscribed = false))
        assertTrue(podcasts.allOnce().isEmpty())
        assertEquals(0, podcasts.count())
        assertEquals(1, episodes.byPodcastOnce("podcast-1").size)
        podcasts.upsert(podcast())
        assertEquals(1, podcasts.count())
        assertEquals(1, episodes.byPodcastOnce("podcast-1").size)
    }

    @Test
    fun refreshingAPodcastKeepsItsEpisodesAndTheirState() = runBlocking {
        val dao = db.podcastDao()
        val episodes = db.episodeDao()

        dao.upsert(podcast())
        episodes.upsertAll(listOf(episode()))
        episodes.markPlayedAndClearProgress("episode-1")
        episodes.enqueue("episode-1", 1_700_000_000L)

        // A refresh writes the show back with new metadata from the feed.
        dao.upsert(podcast(title = "Test Show (renamed)"))

        val rows = episodes.byPodcastOnce("podcast-1")
        assertEquals("the refresh must not delete the show's episodes", 1, rows.size)
        assertEquals(EpisodeEntity.PLAYED, rows[0].playingStatus)
        assertTrue("the episode must stay queued", rows[0].isQueued)

        val stored = dao.get("podcast-1")
        assertEquals("Test Show (renamed)", stored?.title)
    }

    @Test
    fun refreshingAPodcastKeepsTheListenersOwnSettings() = runBlocking {
        val dao = db.podcastDao()
        dao.upsert(podcast())
        dao.setAutoDownload("podcast-1", true)

        // The feed knows nothing about the per-show auto-download setting.
        dao.upsert(podcast(title = "Test Show (renamed)"))

        assertTrue(
            "a refresh must not switch off auto-download",
            dao.get("podcast-1")?.autoDownload == true,
        )
    }

    @Test
    fun subscribingToAShowAlreadyInTheLibraryKeepsItsEpisodes() = runBlocking {
        val dao = db.podcastDao()
        val episodes = db.episodeDao()

        dao.upsert(podcast())
        episodes.upsertAll(listOf(episode()))
        episodes.markPlayedAndClearProgress("episode-1")

        // Following the same show again is a no-op, not a reset.
        dao.upsert(podcast())

        val rows = episodes.byPodcastOnce("podcast-1")
        assertEquals(1, rows.size)
        assertEquals(EpisodeEntity.PLAYED, rows[0].playingStatus)
    }
}
