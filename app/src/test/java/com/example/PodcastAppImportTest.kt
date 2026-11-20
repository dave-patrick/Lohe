package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importer.ImportProgressState
import com.example.data.importer.ImportSourceFormat
import com.example.data.importer.PodcastAppImporter
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeBookmarkEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDatabase
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.network.RssAndOpmlParser
import com.example.ui.SettingsSubScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastAppImportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun getContext(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun opmlImport_addsNewSubscriptionsAndSkipsDuplicates() = runBlocking {
        val context = getContext()
        val database = LoheDatabase.getInstance(context)
        val importer = PodcastAppImporter(context, database)

        val opmlFile = File.createTempFile("test_subs_", ".opml", context.cacheDir)
        try {
            opmlFile.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="2.0">
                  <body>
                    <outline text="OPML Show Alpha" title="OPML Show Alpha" type="rss" xmlUrl="https://example.com/alpha/feed.xml" />
                    <outline text="OPML Show Beta" title="OPML Show Beta" type="rss" xmlUrl="https://example.com/beta/feed.xml" />
                  </body>
                </opml>
                """.trimIndent()
            )

            val firstResult = importer.importFromFile(opmlFile).getOrThrow()
            assertEquals(ImportSourceFormat.OPML, firstResult.format)
            assertEquals(2, firstResult.showsAdded)
            assertEquals(0, firstResult.episodesUpdated)
            assertEquals(0, firstResult.itemsSkipped)

            // Re-importing the exact same OPML skips both already-subscribed shows
            val secondResult = importer.importFromFile(opmlFile).getOrThrow()
            assertEquals(0, secondResult.showsAdded)
            assertEquals(0, secondResult.episodesUpdated)
            assertEquals(2, secondResult.itemsSkipped)
        } finally {
            opmlFile.delete()
        }
    }

    @Test
    fun antennaPodSqliteImport_importsSubscriptionsPlayedStatePositionsAndPlayedDates() = runBlocking {
        val context = getContext()
        val database = LoheDatabase.getInstance(context)
        val dao = database.loheDao()
        val importer = PodcastAppImporter(context, database)

        val dbFile = File.createTempFile("antennapod_export_", ".db", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqlite ->
                sqlite.execSQL(
                    """
                    CREATE TABLE Feeds (
                        id INTEGER PRIMARY KEY,
                        title TEXT,
                        custom_title TEXT,
                        download_url TEXT,
                        author TEXT,
                        description TEXT,
                        image_url TEXT
                    )
                    """.trimIndent()
                )
                sqlite.execSQL(
                    """
                    CREATE TABLE FeedItems (
                        id INTEGER PRIMARY KEY,
                        feed INTEGER,
                        title TEXT,
                        PubDate INTEGER,
                        read INTEGER,
                        item_identifier TEXT,
                        description TEXT
                    )
                    """.trimIndent()
                )
                sqlite.execSQL(
                    """
                    CREATE TABLE FeedMedia (
                        id INTEGER PRIMARY KEY,
                        feeditem INTEGER,
                        duration INTEGER,
                        position INTEGER,
                        download_url TEXT,
                        playback_completion_date INTEGER,
                        last_played_time INTEGER
                    )
                    """.trimIndent()
                )

                sqlite.execSQL(
                    """
                    INSERT INTO Feeds (id, title, custom_title, download_url, author, description, image_url)
                    VALUES (10, 'AntennaPod Science Weekly', '', 'https://antennapod.example.org/science.xml', 'AP Lab', 'Science podcast', '')
                    """.trimIndent()
                )

                // Episode 1: Marked played with played date
                sqlite.execSQL(
                    """
                    INSERT INTO FeedItems (id, feed, title, PubDate, read, item_identifier, description)
                    VALUES (101, 10, 'AP Episode 1 Played', 1710000000000, 1, 'apod-guid-101', 'Episode 1 notes')
                    """.trimIndent()
                )
                sqlite.execSQL(
                    """
                    INSERT INTO FeedMedia (id, feeditem, duration, position, download_url, playback_completion_date, last_played_time)
                    VALUES (1001, 101, 1800000, 1800000, 'https://antennapod.example.org/ep101.mp3', 1710500000000, 1710500000000)
                    """.trimIndent()
                )

                // Episode 2: In-progress at 540,000 ms
                sqlite.execSQL(
                    """
                    INSERT INTO FeedItems (id, feed, title, PubDate, read, item_identifier, description)
                    VALUES (102, 10, 'AP Episode 2 In Progress', 1710100000000, 0, 'apod-guid-102', 'Episode 2 notes')
                    """.trimIndent()
                )
                sqlite.execSQL(
                    """
                    INSERT INTO FeedMedia (id, feeditem, duration, position, download_url, playback_completion_date, last_played_time)
                    VALUES (1002, 102, 2400000, 540000, 'https://antennapod.example.org/ep102.mp3', 0, 1710600000000)
                    """.trimIndent()
                )
            }

            val progressUpdates = mutableListOf<ImportProgressState>()
            val summary = importer.importFromFile(dbFile) { progressUpdates.add(it) }.getOrThrow()

            assertEquals(ImportSourceFormat.ANTENNAPOD_DB, summary.format)
            assertEquals(1, summary.showsAdded)
            assertEquals(2, summary.episodesUpdated)
            assertEquals(0, summary.itemsSkipped)
            assertTrue("Progress callbacks should be emitted", progressUpdates.isNotEmpty())

            val allEpisodes = dao.getAllEpisodes().first()
            val ep1 = allEpisodes.firstOrNull { it.guid == "apod-guid-101" }
            val ep2 = allEpisodes.firstOrNull { it.guid == "apod-guid-102" }
            assertNotNull(ep1)
            assertTrue(ep1!!.isPlayed)
            assertEquals(1710500000000L, ep1.playedAt)

            assertNotNull(ep2)
            assertFalse(ep2!!.isPlayed)
            assertEquals(540_000L, ep2.resumePositionMs)
            assertEquals(1710600000000L, ep2.playedAt)
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun podcastAddictBackupZipImport_importsSubscriptionsPlayedStateAndPositions() = runBlocking {
        val context = getContext()
        val database = LoheDatabase.getInstance(context)
        val dao = database.loheDao()
        val importer = PodcastAppImporter(context, database)

        val innerDbFile = File.createTempFile("podcastAddict_inner_", ".db", context.cacheDir)
        val backupZipFile = File.createTempFile("PodcastAddict_", ".backup", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(innerDbFile, null).use { sqlite ->
                sqlite.execSQL(
                    """
                    CREATE TABLE podcasts (
                        _id INTEGER PRIMARY KEY,
                        name TEXT,
                        feed_url TEXT,
                        author TEXT,
                        description TEXT,
                        subscribed INTEGER
                    )
                    """.trimIndent()
                )
                sqlite.execSQL(
                    """
                    CREATE TABLE episodes (
                        _id INTEGER PRIMARY KEY,
                        podcast_id INTEGER,
                        name TEXT,
                        uid TEXT,
                        content_url TEXT,
                        publication_date INTEGER,
                        duration INTEGER,
                        position_to_resume INTEGER,
                        seen_status INTEGER,
                        playback_Date INTEGER
                    )
                    """.trimIndent()
                )

                sqlite.execSQL(
                    """
                    INSERT INTO podcasts (_id, name, feed_url, author, description, subscribed)
                    VALUES (20, 'Podcast Addict Daily Tech', 'https://pa.example.org/tech.xml', 'PA Studio', 'Daily tech news', 1)
                    """.trimIndent()
                )
                sqlite.execSQL(
                    """
                    INSERT INTO episodes (_id, podcast_id, name, uid, content_url, publication_date, duration, position_to_resume, seen_status, playback_Date)
                    VALUES (201, 20, 'PA Tech Ep 1', 'pa-guid-201', 'https://pa.example.org/ep201.mp3', 1711000000000, 1200000, 315000, 0, 0)
                    """.trimIndent()
                )
                sqlite.execSQL(
                    """
                    INSERT INTO episodes (_id, podcast_id, name, uid, content_url, publication_date, duration, position_to_resume, seen_status, playback_Date)
                    VALUES (202, 20, 'PA Tech Ep 2 Played', 'pa-guid-202', 'https://pa.example.org/ep202.mp3', 1711100000000, 1500000, 1500000, 1, 1711200000000)
                    """.trimIndent()
                )
            }

            ZipOutputStream(FileOutputStream(backupZipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("prefs.xml"))
                zos.write("<map></map>".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("podcastAddict.db"))
                innerDbFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }

            val summary = importer.importFromFile(backupZipFile).getOrThrow()
            assertEquals(ImportSourceFormat.PODCAST_ADDICT_BACKUP, summary.format)
            assertEquals(1, summary.showsAdded)
            assertEquals(2, summary.episodesUpdated)
            assertEquals(0, summary.itemsSkipped)

            val allEpisodes = dao.getAllEpisodes().first()
            val ep201 = allEpisodes.firstOrNull { it.guid == "pa-guid-201" }
            val ep202 = allEpisodes.firstOrNull { it.guid == "pa-guid-202" }
            assertNotNull(ep201)
            assertEquals(315_000L, ep201!!.resumePositionMs)
            assertFalse(ep201.isPlayed)

            assertNotNull(ep202)
            assertTrue(ep202!!.isPlayed)
        } finally {
            innerDbFile.delete()
            backupZipFile.delete()
        }
    }

    @Test
    fun episodeMatchingPriority_guidFirstThenEnclosureUrlThenTitleAndPublishDate() = runBlocking {
        val context = getContext()
        val database = LoheDatabase.getInstance(context)
        val dao = database.loheDao()
        val importer = PodcastAppImporter(context, database)
        val parser = RssAndOpmlParser()

        val testPod = PodcastEntity(
            id = "pod_match_test",
            title = "Match Priority Podcast",
            author = "Tester",
            description = "Tests GUID, URL, Title+Date",
            feedUrl = "https://match.example.org/rss.xml",
            artworkUrl = "drawable://lohe_logo_square",
            category = "Technology",
            isSubscribed = true
        )
        dao.insertPodcast(testPod)

        // Seed 3 episodes in database to test each matching tier:
        // 1. Episode with GUID 'guid-tier-1' (has a different URL and title in backup)
        // 2. Episode with blank GUID and enclosure URL 'https://match.example.org/audio/tier2.mp3'
        // 3. Episode with blank GUID, different URL, and title 'Tier Three Deep Dive' + publishedAt 1715000000000L
        val epTier1 = EpisodeEntity(
            id = "local_ep_tier_1",
            podcastId = testPod.id,
            podcastTitle = testPod.title,
            guid = "guid-tier-1",
            title = "Original Local Title 1",
            descriptionHtml = "",
            audioUrl = "https://match.example.org/audio/local1.mp3",
            artworkUrl = "drawable://lohe_logo_square",
            publishedAt = 1712000000000L,
            durationMs = 1_000_000L,
            resumePositionMs = 10_000L,
            isPlayed = false
        )
        val epTier2 = EpisodeEntity(
            id = "local_ep_tier_2",
            podcastId = testPod.id,
            podcastTitle = testPod.title,
            guid = "",
            title = "Original Local Title 2",
            descriptionHtml = "",
            audioUrl = "https://match.example.org/audio/tier2.mp3",
            artworkUrl = "drawable://lohe_logo_square",
            publishedAt = 1713000000000L,
            durationMs = 1_000_000L,
            resumePositionMs = 20_000L,
            isPlayed = false
        )
        val epTier3 = EpisodeEntity(
            id = "local_ep_tier_3",
            podcastId = testPod.id,
            podcastTitle = testPod.title,
            guid = "",
            title = "Tier Three Deep Dive",
            descriptionHtml = "",
            audioUrl = "https://match.example.org/audio/local3.mp3",
            artworkUrl = "drawable://lohe_logo_square",
            publishedAt = 1715000000000L,
            durationMs = 1_000_000L,
            resumePositionMs = 30_000L,
            isPlayed = false
        )
        dao.insertEpisodes(listOf(epTier1, epTier2, epTier3))

        // Build a Lohe JSON backup where:
        // - Imported Ep 1 matches epTier1 by GUID ('guid-tier-1') even though its URL matches epTier2!
        //   (Verifies GUID is checked BEFORE enclosure URL)
        // - Imported Ep 2 has no GUID and matches epTier2 by enclosure URL
        // - Imported Ep 3 has no GUID, a CDN-wrapped URL, and matches epTier3 by Title + Publish Date
        val importedEpisodes = listOf(
            EpisodeEntity(
                id = "imp_1",
                podcastId = testPod.id,
                podcastTitle = testPod.title,
                guid = "guid-tier-1",
                title = "Renamed Title in Backup",
                descriptionHtml = "",
                audioUrl = "https://match.example.org/audio/tier2.mp3", // intentionally collides with epTier2's URL
                artworkUrl = "drawable://lohe_logo_square",
                publishedAt = 1719999999000L,
                durationMs = 1_000_000L,
                resumePositionMs = 400_000L,
                isPlayed = false
            ),
            EpisodeEntity(
                id = "imp_2",
                podcastId = testPod.id,
                podcastTitle = testPod.title,
                guid = "",
                title = "Different Title For Tier 2",
                descriptionHtml = "",
                audioUrl = "https://match.example.org/audio/tier2.mp3",
                artworkUrl = "drawable://lohe_logo_square",
                publishedAt = 1718888888000L,
                durationMs = 1_000_000L,
                resumePositionMs = 500_000L,
                isPlayed = false
            ),
            EpisodeEntity(
                id = "imp_3",
                podcastId = testPod.id,
                podcastTitle = testPod.title,
                guid = "",
                title = "  tier three   deep dive ",
                descriptionHtml = "",
                audioUrl = "https://cdn.otherhost.org/audio/redirect3.mp3",
                artworkUrl = "drawable://lohe_logo_square",
                publishedAt = 1715000000000L + 3600_000L, // same publish date (+1h)
                durationMs = 1_000_000L,
                resumePositionMs = 600_000L,
                isPlayed = false
            )
        )

        val jsonBackupFile = File.createTempFile("lohe_priority_", ".json", context.cacheDir)
        try {
            jsonBackupFile.writeText(
                parser.exportJsonBackup(
                    podcasts = listOf(testPod),
                    episodes = importedEpisodes,
                    playlists = emptyList()
                )
            )

            val summary = importer.importFromFile(jsonBackupFile).getOrThrow()
            assertEquals(ImportSourceFormat.LOHE_JSON, summary.format)
            assertEquals(0, summary.showsAdded) // testPod already subscribed
            assertEquals(3, summary.episodesUpdated)
            assertEquals(1, summary.itemsSkipped) // testPod skipped

            assertEquals(400_000L, dao.getEpisodeById("local_ep_tier_1")?.resumePositionMs)
            assertEquals(500_000L, dao.getEpisodeById("local_ep_tier_2")?.resumePositionMs)
            assertEquals(600_000L, dao.getEpisodeById("local_ep_tier_3")?.resumePositionMs)
        } finally {
            jsonBackupFile.delete()
        }
    }

    @Test
    fun forwardOnlyAndCompletionThresholds_neverUnplaysNeverLowersPositionAndMarksNearEndAsPlayed() = runBlocking {
        val context = getContext()
        val database = LoheDatabase.getInstance(context)
        val dao = database.loheDao()
        val importer = PodcastAppImporter(context, database)
        val parser = RssAndOpmlParser()

        val pod = PodcastEntity(
            id = "pod_forward_rules",
            title = "Forward Rules Show",
            author = "Lohe",
            description = "",
            feedUrl = "https://forward.example.org/rss.xml",
            artworkUrl = "drawable://lohe_logo_square",
            category = "Technology",
            isSubscribed = true
        )
        dao.insertPodcast(pod)

        // 1. Already played episode locally -> import tries to mark it unplayed
        val epAlreadyPlayed = EpisodeEntity(
            id = "ep_already_played",
            podcastId = pod.id,
            podcastTitle = pod.title,
            guid = "guid-already-played",
            title = "Already Played Locally",
            descriptionHtml = "",
            audioUrl = "https://forward.example.org/1.mp3",
            artworkUrl = "drawable://lohe_logo_square",
            publishedAt = 1710000000000L,
            durationMs = 1_000_000L,
            resumePositionMs = 500_000L,
            isPlayed = true,
            playedAt = 1710000500000L
        )
        // 2. Episode with local position 800,000 ms -> import tries to lower it to 200,000 ms
        val epHigherLocalPos = EpisodeEntity(
            id = "ep_higher_local_pos",
            podcastId = pod.id,
            podcastTitle = pod.title,
            guid = "guid-higher-pos",
            title = "Higher Local Position",
            descriptionHtml = "",
            audioUrl = "https://forward.example.org/2.mp3",
            artworkUrl = "drawable://lohe_logo_square",
            publishedAt = 1710000000000L,
            durationMs = 1_000_000L,
            resumePositionMs = 800_000L,
            isPlayed = false
        )
        // 3. Episode with position within last 10 seconds in import (992,000 / 1,000,000 ms -> 8s remaining)
        val epLast10Sec = EpisodeEntity(
            id = "ep_last_10_sec",
            podcastId = pod.id,
            podcastTitle = pod.title,
            guid = "guid-last-10-sec",
            title = "Within Last 10 Seconds",
            descriptionHtml = "",
            audioUrl = "https://forward.example.org/3.mp3",
            artworkUrl = "drawable://lohe_logo_square",
            publishedAt = 1710000000000L,
            durationMs = 1_000_000L,
            resumePositionMs = 0L,
            isPlayed = false
        )
        // 4. Episode past 95% in import (1,920,000 / 2,000,000 ms = 96%, 80s remaining)
        val epPast95Pct = EpisodeEntity(
            id = "ep_past_95_pct",
            podcastId = pod.id,
            podcastTitle = pod.title,
            guid = "guid-past-95-pct",
            title = "Past 95 Percent",
            descriptionHtml = "",
            audioUrl = "https://forward.example.org/4.mp3",
            artworkUrl = "drawable://lohe_logo_square",
            publishedAt = 1710000000000L,
            durationMs = 2_000_000L,
            resumePositionMs = 0L,
            isPlayed = false
        )
        dao.insertEpisodes(listOf(epAlreadyPlayed, epHigherLocalPos, epLast10Sec, epPast95Pct))

        val incoming = listOf(
            epAlreadyPlayed.copy(isPlayed = false, resumePositionMs = 100_000L),
            epHigherLocalPos.copy(isPlayed = false, resumePositionMs = 200_000L),
            epLast10Sec.copy(isPlayed = false, resumePositionMs = 992_000L, durationMs = 1_000_000L),
            epPast95Pct.copy(isPlayed = false, resumePositionMs = 1_920_000L, durationMs = 2_000_000L)
        )

        val jsonFile = File.createTempFile("lohe_forward_rules_", ".json", context.cacheDir)
        try {
            jsonFile.writeText(parser.exportJsonBackup(listOf(pod), incoming, emptyList()))
            val summary = importer.importFromFile(jsonFile).getOrThrow()

            // epAlreadyPlayed and epHigherLocalPos should be skipped (not downgraded)
            // epLast10Sec and epPast95Pct should be updated and marked as played
            assertEquals(2, summary.episodesUpdated)
            assertEquals(3, summary.itemsSkipped) // 1 show + 2 episodes skipped

            val afterPlayed = dao.getEpisodeById("ep_already_played")!!
            assertTrue("Never mark a played episode unplayed", afterPlayed.isPlayed)
            assertEquals("Never lower a saved position", 500_000L, afterPlayed.resumePositionMs)

            val afterHigherPos = dao.getEpisodeById("ep_higher_local_pos")!!
            assertEquals("Never lower a saved position", 800_000L, afterHigherPos.resumePositionMs)

            val afterLast10s = dao.getEpisodeById("ep_last_10_sec")!!
            assertTrue("Position within last 10 seconds must count as played", afterLast10s.isPlayed)
            assertEquals(992_000L, afterLast10s.resumePositionMs)

            val afterPast95 = dao.getEpisodeById("ep_past_95_pct")!!
            assertTrue("Position past 95% must count as played", afterPast95.isPlayed)
            assertEquals(1_920_000L, afterPast95.resumePositionMs)
        } finally {
            jsonFile.delete()
        }
    }

    @Test
    fun settingsImportFromAnotherAppScreen_displaysAllFormatsAndOpmlHelpText() {
        composeTestRule.waitForIdle()
        assertFalse("Import from another app must be a free feature, not Pro", SettingsSubScreen.IMPORT_FROM_APP.isProFeatured)

        // Open Settings and navigate to "Import from another app"
        composeTestRule.onNodeWithTag("top_settings_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_overview_list")
            .performScrollToNode(hasTestTag("settings_item_import_from_app"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_item_import_from_app").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_screen_import_from_app").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_from_app_pick_file_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_format_opml_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_format_antennapod_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("import_from_app_settings_list")
            .performScrollToNode(hasTestTag("import_format_podcast_addict_button"))
        composeTestRule.onNodeWithTag("import_format_podcast_addict_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("import_from_app_settings_list")
            .performScrollToNode(hasTestTag("import_format_lohe_json_button"))
        composeTestRule.onNodeWithTag("import_format_lohe_json_button").assertIsDisplayed()

        // Scroll down to verify Lohe DB migration option and export button
        composeTestRule.onNodeWithTag("import_from_app_settings_list")
            .performScrollToNode(hasTestTag("import_format_lohe_db_button"))
        composeTestRule.onNodeWithTag("import_format_lohe_db_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("import_from_app_settings_list")
            .performScrollToNode(hasTestTag("import_screen_export_db_button"))
        composeTestRule.onNodeWithTag("import_screen_export_db_button").assertIsDisplayed()

        // Scroll down to verify the OPML-only help text for Pocket Casts, Apple Podcasts, and Spotify
        composeTestRule.onNodeWithTag("import_from_app_settings_list")
            .performScrollToNode(hasTestTag("import_opml_only_help_text"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("import_opml_only_help_text")
            .assertIsDisplayed()
            .assertTextContains("Pocket Casts, Apple Podcasts and Spotify can only export subscriptions via OPML", substring = true)
    }

    @Test
    fun loheDatabaseExportAndMigration_transfersShowsEpisodesPlaylistsAndBookmarks() = runBlocking {
        val context = getContext()
        val legacyDbName = "legacy_lohe_source_${System.currentTimeMillis()}.db"
        val legacyDb = Room.databaseBuilder(context, LoheDatabase::class.java, legacyDbName)
            .allowMainThreadQueries()
            .build()
        val exportedFile = File.createTempFile("lohe_exported_", ".db", context.cacheDir)

        try {
            val legacyDao = legacyDb.loheDao()
            legacyDao.insertPodcast(
                PodcastEntity(
                    id = "legacy_pod_1",
                    title = "Legacy Lohe Show",
                    author = "Lohe Host",
                    description = "Migrated from earlier Lohe install",
                    feedUrl = "https://example.org/legacy-lohe-show.xml",
                    artworkUrl = "https://example.org/legacy.jpg",
                    category = "Technology",
                    autoQueueMode = AutoQueueMode.BEGINNING.name,
                    useCustomAudio = true,
                    customSpeed = 1.6f,
                    customSkipSilence = true,
                    customVolumeBoostDb = 6,
                    notifyNewEpisodes = true,
                    isSubscribed = true
                )
            )
            legacyDao.insertEpisode(
                EpisodeEntity(
                    id = "legacy_ep_1",
                    podcastId = "legacy_pod_1",
                    podcastTitle = "Legacy Lohe Show",
                    title = "Episode One: Migration",
                    descriptionHtml = "<p>Notes from legacy Lohe DB</p>",
                    audioUrl = "https://example.org/legacy_ep1.mp3",
                    artworkUrl = "https://example.org/legacy.jpg",
                    publishedAt = 1_715_000_000_000L,
                    durationMs = 1_800_000L,
                    resumePositionMs = 420_000L,
                    isPlayed = false,
                    isArchived = false,
                    isDownloaded = true,
                    localFilePath = "/data/user/0/com.lohe.old/files/ep1.mp3",
                    isStarred = true,
                    inUpNext = true,
                    upNextOrder = 2,
                    chaptersUrl = "https://example.org/ep1-chapters.json",
                    transcriptUrl = "https://example.org/ep1.vtt",
                    guid = "legacy-guid-001",
                    playedAt = 1_715_100_000_000L
                )
            )
            legacyDao.insertPlaylist(
                PlaylistEntity(
                    id = "legacy_pl_1",
                    name = "Morning Commute",
                    folderName = "Daily",
                    episodeIdsCsv = "legacy_ep_1",
                    assignedPodcastIdsCsv = "legacy_pod_1",
                    isPinned = true
                )
            )
            legacyDao.insertBookmark(
                EpisodeBookmarkEntity(
                    id = "legacy_bm_1",
                    episodeId = "legacy_ep_1",
                    positionMs = 180_000L,
                    label = "Great insight"
                )
            )

            // Export WAL-checkpointed SQLite database file from legacy Lohe instance
            val legacyExporter = PodcastAppImporter(context, legacyDb)
            val exportResult = legacyExporter.exportLoheDatabaseToFile(exportedFile)
            assertTrue("Expected DB export to succeed: ${exportResult.exceptionOrNull()}", exportResult.isSuccess)
            assertTrue("Exported .db must be non-empty", exportedFile.length() > 0L)

            // Import into fresh target Play Edition DB
            val targetDb = LoheDatabase.getInstance(context)
            val targetImporter = PodcastAppImporter(context, targetDb)
            val importResult = targetImporter.importFromFile(exportedFile, "lohe_preview.db")
            assertTrue("Expected Lohe DB import to succeed: ${importResult.exceptionOrNull()}", importResult.isSuccess)

            val summary = importResult.getOrThrow()
            assertEquals(ImportSourceFormat.LOHE_DB, summary.format)
            assertTrue("Expected show added", summary.showsAdded >= 1)
            assertTrue("Expected episode migrated", summary.episodesUpdated >= 1)
            assertEquals(1, summary.playlistsImported)
            assertEquals(1, summary.bookmarksImported)

            val targetDao = targetDb.loheDao()
            val migratedPod = targetDao.getAllPodcasts().first().firstOrNull {
                it.feedUrl == "https://example.org/legacy-lohe-show.xml"
            }
            assertNotNull("Migrated podcast should exist", migratedPod)
            assertTrue("Per-show custom audio must be preserved", migratedPod!!.useCustomAudio)
            assertEquals(1.6f, migratedPod.customSpeed, 0.01f)
            assertEquals(6, migratedPod.customVolumeBoostDb)
            assertEquals(AutoQueueMode.BEGINNING.name, migratedPod.autoQueueMode)

            val migratedEp = targetDao.getAllEpisodes().first().firstOrNull { it.guid == "legacy-guid-001" }
            assertNotNull("Migrated episode should exist", migratedEp)
            assertEquals(420_000L, migratedEp!!.resumePositionMs)
            assertTrue(migratedEp.isStarred)
            assertTrue(migratedEp.inUpNext)
            assertEquals("https://example.org/ep1-chapters.json", migratedEp.chaptersUrl)
            assertEquals("https://example.org/ep1.vtt", migratedEp.transcriptUrl)
            assertFalse("Cross-app local download flag must reset safely", migratedEp.isDownloaded)
            assertEquals("", migratedEp.localFilePath)

            val migratedPlaylist = targetDao.getCustomPlaylists().first().firstOrNull { it.id == "legacy_pl_1" }
            assertNotNull("Migrated playlist should exist", migratedPlaylist)
            assertEquals("Morning Commute", migratedPlaylist!!.name)

            val migratedBookmarks = targetDao.getBookmarksForEpisode(migratedEp.id).first()
            assertEquals(1, migratedBookmarks.size)
            assertEquals("Great insight", migratedBookmarks.first().label)
            assertEquals(180_000L, migratedBookmarks.first().positionMs)

            // Re-importing the same DB must not duplicate bookmarks
            val secondImport = targetImporter.importFromFile(exportedFile, "lohe_preview.db").getOrThrow()
            assertEquals(0, secondImport.bookmarksImported)
            assertEquals(1, targetDao.getBookmarksForEpisode(migratedEp.id).first().size)
        } finally {
            legacyDb.close()
            context.deleteDatabase(legacyDbName)
            exportedFile.delete()
        }
    }
}
