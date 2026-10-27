package com.lohe.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuditDatabaseTest {
    private lateinit var db: AppDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }
    @After fun close() { db.close() }

    @Test fun searchTreatsWildcardsAsLiteralText() = runBlocking {
        db.podcastDao().upsert(PodcastEntity("p", "100% show_one!", feedUrl = "https://example.com/feed"))
        db.podcastDao().upsert(PodcastEntity("q", "Ordinary show", feedUrl = "https://example.com/other"))
        db.episodeDao().upsertAll(listOf(
            EpisodeEntity("a", "p", "100% episode_one!", "https://example.com/a", 1),
            EpisodeEntity("b", "q", "Other episode", "https://example.com/b", 2)))
        for (query in listOf("%", "_", "!")) {
            assertEquals(listOf("p"), db.podcastDao().observeSearchResults(query).first().map { it.uuid })
            assertEquals(listOf("a"), db.episodeDao().searchByTitle(query, 10).map { it.uuid })
        }
    }

    @Test fun archiveSelectsOnlyDueIdsAndPagesWithoutStarvation() = runBlocking {
        db.podcastDao().upsert(PodcastEntity("p", "Show", feedUrl = "https://example.com/feed"))
        val base = EpisodeEntity("a", "p", "Episode", "https://example.com/a", 1, playingStatus = 2, playedAt = 10)
        db.episodeDao().upsertAll(listOf(base,
            base.copy(uuid = "b", playedAt = 1000),
            base.copy(uuid = "c", isArchived = true, archivedAt = 10, localPath = "/download/c", downloadState = 2),
            base.copy(uuid = "d", isArchived = true, archivedAt = null, localPath = "/legacy/d", downloadState = 2)))
        assertEquals(listOf("a"), db.episodeDao().dueArchiveIds(true, 20, 20, "", 1))
        assertEquals(listOf("c"), db.episodeDao().dueArchiveIds(true, 20, 20, "a", 1))
        assertEquals(listOf("c"), db.episodeDao().dueArchiveIds(false, 20, 20, "", 10))
    }

    @Test fun aggregateCountsExcludeDeletedAndArchivedUnplayedEpisodes() = runBlocking {
        db.podcastDao().upsert(PodcastEntity("p", "Show", feedUrl = "https://example.com/feed"))
        val base = EpisodeEntity("a", "p", "Episode", "https://example.com/a", 1)
        db.episodeDao().upsertAll(listOf(base, base.copy(uuid = "b", isDeleted = true, published = 10),
            base.copy(uuid = "c", isArchived = true), base.copy(uuid = "d", playingStatus = 2)))
        val stats = db.episodeDao().observePodcastStats().first().single()
        assertEquals(3, stats.total)
        assertEquals(1, stats.unplayed)
        assertEquals(10L, stats.latest)
    }

    @Test fun failedImportTransactionRollsBackMetadataAndEpisodesTogether() = runBlocking {
        try {
            db.withTransaction {
                db.podcastDao().upsert(PodcastEntity("p", "Show", feedUrl = "https://example.com/feed"))
                db.episodeDao().upsertAll(listOf(EpisodeEntity("a", "p", "Episode", "https://example.com/a", 1)))
                error("Simulated persistence failure")
            }
        } catch (_: IllegalStateException) { }
        assertNull(db.podcastDao().get("p"))
        assertNull(db.episodeDao().get("a"))
    }
}
