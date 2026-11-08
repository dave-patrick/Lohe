package com.lohe.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.fetch.PodcastFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressConsistencyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun episode(id: String, position: Long, duration: Int? = 3600) = EpisodeEntity(
        id, "show", id, "https://example.invalid/$id", 1,
        durationSeconds = duration, playedUpToSeconds = position, queuePosition = 4,
        listenedSeconds = 123, starred = true, localPath = "/preserve/$id",
    )

    @Test fun upgradeRepairsOnlyInconsistentUnplayedRowsAndPreservesLibrary() = runBlocking {
        val name = "progress-upgrade-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            db.podcastDao().upsert(PodcastEntity("show", "Show", feedUrl = "https://example.invalid/feed"))
            val originals = listOf(
                episode("coat", 3530, 3545), episode("systems", 2580, 2435),
                episode("future", 3903, 3928), episode("partial", 600),
                episode("unknown", 600, null), episode("fresh", 0),
                episode("explicit-in-progress", 3590).copy(playingStatus = EpisodeEntity.IN_PROGRESS),
                episode("played", 0).copy(playingStatus = EpisodeEntity.PLAYED, playedAt = 1234),
            )
            db.episodeDao().upsertAll(originals)
            db.close()
            // Schema 13 has the same episode columns; reopen through the production migration path.
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.version = 13
            }
            // The file was created at the current schema, so it already has the
            // feed-validator columns MIGRATION_14_15 adds; step over that one.
            val alreadyAt15 = object : androidx.room.migration.Migration(14, 15) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
            }
            db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_13_14, alreadyAt15).build()
            for (original in originals) {
                val actual = db.episodeDao().get(original.uuid)!!
                when (original.uuid) {
                    "coat", "systems", "future" -> {
                        assertTrue(actual.isPlayed)
                        assertEquals(0L, actual.playedUpToSeconds)
                        assertNull(actual.queuePosition)
                        assertNotNull(actual.playedAt)
                        assertEquals(original, actual.copy(playingStatus = original.playingStatus,
                            playedUpToSeconds = original.playedUpToSeconds, playedAt = original.playedAt,
                            queuePosition = original.queuePosition))
                    }
                    "partial", "unknown" -> assertEquals(original.copy(playingStatus = EpisodeEntity.IN_PROGRESS), actual)
                    else -> assertEquals(original, actual)
                }
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun importedCompletionUsesPlayedLifecycleWithoutInflatingListeningTotals() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.podcastDao().upsert(PodcastEntity("show", "Show", feedUrl = "https://example.invalid/feed"))
            db.episodeDao().upsertAll(listOf(episode("finished", 0, null), episode("partial", 0)))
            val completed = mutableListOf<String>()
            val fetcher = object : PodcastFetcher {
                override suspend fun fetch(feedUrl: String): PodcastEntity = error("No network")
                override suspend fun fetchEpisodes(feedUrl: String): List<EpisodeEntity> = error("No network")
                override suspend fun importFeed(feedUrl: String): Result<PodcastEntity> = error("No network")
            }
            val repository = SubscriptionRepository(db.podcastDao(), db.episodeDao(), db.listeningHistoryDao(),
                fetcher, onPlayed = { completed += it },
                transaction = { block -> db.withTransaction { block() } })
            repository.applyProgress("finished", 3530, 3545)
            repository.applyProgress("partial", 600, 3600)
            val finished = db.episodeDao().get("finished")!!
            assertTrue(finished.isPlayed)
            assertEquals(0L, finished.playedUpToSeconds)
            assertEquals(123L, finished.listenedSeconds)
            assertEquals(3545, finished.durationSeconds)
            assertNull(finished.queuePosition)
            assertEquals(listOf("finished"), completed)
            val partial = db.episodeDao().get("partial")!!
            assertTrue(partial.isInProgress)
            assertEquals(600L, partial.playedUpToSeconds)
            assertEquals(123L, partial.listenedSeconds)
        } finally { db.close() }
    }
}
