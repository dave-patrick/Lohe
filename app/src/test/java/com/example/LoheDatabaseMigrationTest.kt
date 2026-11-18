package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LoheDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoheDatabaseMigrationTest {

    @Test
    fun migrate5To6_preservesPodcastAndEpisodeRowsAndAddsDefaultColumns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "lohe_migration_5_to_6_test.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) {
            dbFile.delete()
        }

        // 1. Create a Version 5 SQLite database with the v5 schema (episodes without guid and playedAt)
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS `podcasts` (" +
                    "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, `feedUrl` TEXT NOT NULL, `artworkUrl` TEXT NOT NULL, " +
                    "`category` TEXT NOT NULL, `autoQueueMode` TEXT NOT NULL, `useCustomAudio` INTEGER NOT NULL, " +
                    "`customSpeed` REAL NOT NULL, `customSkipSilence` INTEGER NOT NULL, " +
                    "`customVolumeBoostDb` INTEGER NOT NULL, `notifyNewEpisodes` INTEGER NOT NULL, " +
                    "`isSubscribed` INTEGER NOT NULL, `lastSyncedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS `episodes` (" +
                    "`id` TEXT NOT NULL, `podcastId` TEXT NOT NULL, `podcastTitle` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `descriptionHtml` TEXT NOT NULL, `audioUrl` TEXT NOT NULL, " +
                    "`artworkUrl` TEXT NOT NULL, `publishedAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                    "`resumePositionMs` INTEGER NOT NULL, `isPlayed` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, " +
                    "`isDownloaded` INTEGER NOT NULL, `localFilePath` TEXT NOT NULL, `isStarred` INTEGER NOT NULL, " +
                    "`inUpNext` INTEGER NOT NULL, `upNextOrder` INTEGER NOT NULL, `chaptersUrl` TEXT NOT NULL, " +
                    "`chaptersJson` TEXT NOT NULL, `transcriptUrl` TEXT NOT NULL, `transcriptMimeType` TEXT NOT NULL, " +
                    "`transcriptText` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_playlists` (" +
                    "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `folderName` TEXT NOT NULL, " +
                    "`episodeIdsCsv` TEXT NOT NULL, `assignedPodcastIdsCsv` TEXT NOT NULL, " +
                    "`smartRuleType` TEXT NOT NULL, `isPinned` INTEGER NOT NULL, " +
                    "`autoDownload` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS `episode_bookmarks` (" +
                    "`id` TEXT NOT NULL, `episodeId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, " +
                    "`label` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sqliteDb.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8a3c421e9f1b4c0d7e2a5b6c8d9e0f1a')"
            )

            // Insert a podcast row in v5
            sqliteDb.execSQL(
                """
                INSERT INTO `podcasts` (
                    `id`, `title`, `author`, `description`, `feedUrl`, `artworkUrl`, `category`,
                    `autoQueueMode`, `useCustomAudio`, `customSpeed`, `customSkipSilence`,
                    `customVolumeBoostDb`, `notifyNewEpisodes`, `isSubscribed`, `lastSyncedAt`
                ) VALUES (
                    'pod_v5_1', 'Nordic Field Notes', 'Aino Lind', 'Acoustic ecology podcast',
                    'https://example.com/nordic/feed.xml', 'https://example.com/nordic/art.jpg',
                    'Science & Nature', 'LAST', 0, 1.0, 0, 0, 1, 1, 1710000000000
                )
                """.trimIndent()
            )

            // Insert an episode row in v5 (no guid or playedAt columns yet)
            sqliteDb.execSQL(
                """
                INSERT INTO `episodes` (
                    `id`, `podcastId`, `podcastTitle`, `title`, `descriptionHtml`, `audioUrl`,
                    `artworkUrl`, `publishedAt`, `durationMs`, `resumePositionMs`, `isPlayed`,
                    `isArchived`, `isDownloaded`, `localFilePath`, `isStarred`, `inUpNext`,
                    `upNextOrder`, `chaptersUrl`, `chaptersJson`, `transcriptUrl`,
                    `transcriptMimeType`, `transcriptText`
                ) VALUES (
                    'ep_v5_1', 'pod_v5_1', 'Nordic Field Notes', 'Episode 1: Dawn Chorus in Lapland',
                    '<p>Field recording notes</p>', 'https://example.com/nordic/ep1.mp3',
                    'https://example.com/nordic/ep1.jpg', 1710000100000, 2400000, 615000, 0,
                    0, 0, '', 1, 1, 0, '', '', '', '', ''
                )
                """.trimIndent()
            )

            sqliteDb.version = 5
        } finally {
            sqliteDb.close()
        }

        // 2. Open the v5 database with Room at v6 using MIGRATION_5_6 (and no destructive migration)
        val roomDb = Room.databaseBuilder(context, LoheDatabase::class.java, dbName)
            .addMigrations(LoheDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = roomDb.loheDao()

            // Verify podcast row survived migration intact
            val migratedPodcast = dao.getPodcastById("pod_v5_1")
            assertNotNull("Podcast row must survive 5->6 migration", migratedPodcast)
            assertEquals("Nordic Field Notes", migratedPodcast?.title)
            assertEquals("Aino Lind", migratedPodcast?.author)
            assertEquals("https://example.com/nordic/feed.xml", migratedPodcast?.feedUrl)
            assertTrue(migratedPodcast?.isSubscribed == true)

            // Verify episode row survived migration intact with new default columns
            val migratedEpisode = dao.getEpisodeById("ep_v5_1")
            assertNotNull("Episode row must survive 5->6 migration", migratedEpisode)
            assertEquals("pod_v5_1", migratedEpisode?.podcastId)
            assertEquals("Episode 1: Dawn Chorus in Lapland", migratedEpisode?.title)
            assertEquals(615000L, migratedEpisode?.resumePositionMs)
            assertTrue(migratedEpisode?.isStarred == true)
            assertEquals("", migratedEpisode?.guid)
            assertEquals(0L, migratedEpisode?.playedAt)
        } finally {
            roomDb.close()
            dbFile.delete()
        }
    }
}
