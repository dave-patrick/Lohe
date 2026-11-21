package com.example

import com.example.data.local.HomeCardType
import com.example.data.local.ListeningHistoryRange
import com.example.data.network.RssAndOpmlParser
import com.example.ui.components.formatCompactCount
import com.example.ui.components.formatCompactMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeCardsAndFeedSyncTest {

    @Test
    fun rssFeedParser_syncsAllEpisodes_beyond25Limit() {
        val itemsXml = buildString {
            for (i in 1..42) {
                append(
                    """
                    <item>
                        <title>Episode $i</title>
                        <guid>guid-$i</guid>
                        <enclosure url="https://example.com/ep$i.mp3" type="audio/mpeg" />
                        <itunes:duration>30:00</itunes:duration>
                    </item>
                    """.trimIndent()
                )
            }
        }
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                <channel>
                    <title>Long Running Show</title>
                    <description>Archive test</description>
                    $itemsXml
                </channel>
            </rss>
        """.trimIndent()

        val parser = RssAndOpmlParser()
        val (_, episodes) = parser.parseRssFeedXml(rssXml, "https://example.com/feed.xml")
        assertEquals("RSS parser should sync all 42 episodes instead of capping at 25", 42, episodes.size)
    }

    @Test
    fun homeCardDefaults_matchFreeAndProExpectations() {
        val freeDefaults = HomeCardType.defaultEnabledFor(isProUnlocked = false)
        assertTrue(HomeCardType.YOUR_SIGNAL in freeDefaults)
        assertTrue(HomeCardType.LISTENING_ACTIVITY in freeDefaults)
        assertEquals("Listening History", HomeCardType.LISTENING_ACTIVITY.title)
        assertTrue(HomeCardType.LIBRARY_DETAILS in freeDefaults)
        assertFalse("Most Episodes should now be a Pro card", HomeCardType.MOST_EPISODES in freeDefaults)
        assertTrue(HomeCardType.MOST_EPISODES.isPro)
        assertFalse(HomeCardType.CONTINUE_LISTENING in freeDefaults)
        assertFalse(HomeCardType.RECENT_EPISODES in freeDefaults)
        assertFalse(HomeCardType.MOST_LISTENED in freeDefaults)

        val proDefaults = HomeCardType.defaultEnabledFor(isProUnlocked = true)
        assertTrue(HomeCardType.MOST_EPISODES in proDefaults)
        assertTrue(HomeCardType.MOST_LISTENED in proDefaults)
        assertFalse(HomeCardType.UP_NEXT_SPOTLIGHT in proDefaults)

        // Locked Pro cards are filtered out if Pro is not unlocked
        val parsedWhileLocked = HomeCardType.fromCsv(
            "YOUR_SIGNAL,MOST_EPISODES,MOST_LISTENED,UP_NEXT_SPOTLIGHT",
            isProUnlocked = false
        )
        assertEquals(setOf(HomeCardType.YOUR_SIGNAL), parsedWhileLocked)

        // Listening History range mapping
        assertEquals(ListeningHistoryRange.DAYS_7, ListeningHistoryRange.fromDays(7))
        assertEquals(ListeningHistoryRange.DAYS_14, ListeningHistoryRange.fromDays(14))
        assertEquals(ListeningHistoryRange.DAYS_30, ListeningHistoryRange.fromDays(30))
        assertEquals(ListeningHistoryRange.DAYS_90, ListeningHistoryRange.fromDays(90))
        assertEquals(ListeningHistoryRange.ALL_TIME, ListeningHistoryRange.fromDays(0))
        assertEquals(ListeningHistoryRange.DAYS_14, ListeningHistoryRange.fromDays(999))
    }

    @Test
    fun compactFormatters_handleLargeNumbersCleanly() {
        assertEquals("45m", formatCompactMinutes(45))
        assertEquals("3h", formatCompactMinutes(180))
        assertEquals("2h 15m", formatCompactMinutes(135))
        assertEquals("250h", formatCompactMinutes(15_000))
        assertEquals("1,250h", formatCompactMinutes(75_000))

        assertEquals("142", formatCompactCount(142))
        assertEquals("1,240", formatCompactCount(1240))
        assertEquals("12.4K", formatCompactCount(12400))
        assertEquals("1.2M", formatCompactCount(1_200_000))
    }

    @Test
    fun listeningHistoryDefaults_haveNoFakeStreak_andAllSignalRenamed() {
        val defaults = com.example.data.local.LoheUserSettings()
        assertEquals("Fresh settings should start with 0 logged listening minutes", 0, defaults.dailyListeningMinutes.sum())
        assertEquals("Fresh settings should start with 0ms saved", 0L, defaults.totalTimeSavedMs)
        assertEquals("All Episodes", com.example.data.local.SmartPlaylistType.EVERYTHING.title)
    }

    @Test
    fun variableSpeedFormatter_handlesKeyAndFineSpeeds() {
        assertEquals("0.5x", com.example.ui.components.formatPlaybackSpeedLabel(0.5f))
        assertEquals("1.0x", com.example.ui.components.formatPlaybackSpeedLabel(1.0f))
        assertEquals("1.25x", com.example.ui.components.formatPlaybackSpeedLabel(1.25f))
        assertEquals("1.5x", com.example.ui.components.formatPlaybackSpeedLabel(1.5f))
        assertEquals("2.0x", com.example.ui.components.formatPlaybackSpeedLabel(2.0f))
        assertEquals("3.0x", com.example.ui.components.formatPlaybackSpeedLabel(3.0f))
    }

    @Test
    fun swipeActions_defaultsAndParsing_areValid() {
        val defaults = com.example.data.local.LoheUserSettings()
        assertEquals(com.example.data.local.EpisodeSwipeAction.UP_NEXT_CHOOSE, defaults.swipeRightAction)
        assertEquals(com.example.data.local.EpisodeSwipeAction.MARK_PLAYED, defaults.swipeLeftAction)
        assertEquals(
            com.example.data.local.EpisodeSwipeAction.PLAY_LAST,
            com.example.data.local.EpisodeSwipeAction.fromName("PLAY_LAST", com.example.data.local.EpisodeSwipeAction.NONE)
        )
        assertEquals(
            com.example.data.local.EpisodeSwipeAction.DOWNLOAD,
            com.example.data.local.EpisodeSwipeAction.fromName("UNKNOWN_ACTION", com.example.data.local.EpisodeSwipeAction.DOWNLOAD)
        )
    }

    @Test
    fun discoverCatalog_containsRealShowsWithRealArtworkAndFeeds() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.LoheDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repo = com.example.data.repository.LoheRepository(db.loheDao())
            val catalog = repo.publicDiscoveryCatalog
            assertTrue("Discover catalog should contain real shows", catalog.size >= 6)
            catalog.forEach { show ->
                assertTrue("Show ${show.title} should have real HTTPS artwork", show.artworkUrl.startsWith("https://"))
                assertTrue("Show ${show.title} should have real HTTPS RSS feed", show.feedUrl.startsWith("https://"))
            }
            assertTrue(catalog.any { it.title.contains("99% Invisible", ignoreCase = true) })
            assertTrue(catalog.any { it.title.contains("Darknet Diaries", ignoreCase = true) })
        } finally {
            db.close()
        }
    }

    @Test
    fun listeningHistory_strictlyRespectsSelectedRange_andIncludesRealPlayedDates() {
        val msPerDay = 86_400_000L
        val nowMs = 1_800_000_000_000L
        val episodes = listOf(
            com.example.data.local.EpisodeEntity(
                id = "ep_recent",
                guid = "g1",
                podcastId = "p1",
                podcastTitle = "Show 1",
                title = "Played 3 days ago",
                descriptionHtml = "",
                audioUrl = "https://example.com/1.mp3",
                artworkUrl = "",
                publishedAt = nowMs - 10 * msPerDay,
                durationMs = 30 * 60_000L, // 30m
                isPlayed = true,
                playedAt = nowMs - 3 * msPerDay
            ),
            com.example.data.local.EpisodeEntity(
                id = "ep_mid",
                guid = "g2",
                podcastId = "p1",
                podcastTitle = "Show 1",
                title = "Played 40 days ago",
                descriptionHtml = "",
                audioUrl = "https://example.com/2.mp3",
                artworkUrl = "",
                publishedAt = nowMs - 50 * msPerDay,
                durationMs = 45 * 60_000L, // 45m
                isPlayed = true,
                playedAt = nowMs - 40 * msPerDay
            ),
            com.example.data.local.EpisodeEntity(
                id = "ep_old",
                guid = "g3",
                podcastId = "p1",
                podcastTitle = "Show 1",
                title = "Played 200 days ago",
                descriptionHtml = "",
                audioUrl = "https://example.com/3.mp3",
                artworkUrl = "",
                publishedAt = nowMs - 210 * msPerDay,
                durationMs = 60 * 60_000L, // 60m
                isPlayed = true,
                playedAt = nowMs - 200 * msPerDay
            )
        )

        val summary7d = com.example.ui.components.computeListeningHistorySummary(
            episodes = episodes,
            dailyMinutes = List(90) { 0 },
            historyRange = ListeningHistoryRange.DAYS_7,
            nowMs = nowMs
        )
        assertEquals("7D should only count the 30m episode from 3 days ago", 30L, summary7d.totalMinutes)

        val summary90d = com.example.ui.components.computeListeningHistorySummary(
            episodes = episodes,
            dailyMinutes = List(90) { 0 },
            historyRange = ListeningHistoryRange.DAYS_90,
            nowMs = nowMs
        )
        assertEquals("90D should count 30m + 45m = 75m and exclude 200d-old episode", 75L, summary90d.totalMinutes)

        val summaryAll = com.example.ui.components.computeListeningHistorySummary(
            episodes = episodes,
            dailyMinutes = List(90) { 0 },
            historyRange = ListeningHistoryRange.ALL_TIME,
            nowMs = nowMs
        )
        assertEquals("ALL_TIME should count complete history 30m + 45m + 60m = 135m", 135L, summaryAll.totalMinutes)
    }

    @Test
    fun playedStats_handlesEpochSeconds_archivedAndUndatedEpisodes_andTextDurations() {
        // Verify Podcast Addict HH:MM:SS text duration & Unix seconds normalization
        assertEquals(4_530_000L, com.example.data.importer.PodcastAppImporter.parseDurationTextToMs("01:15:30"))
        assertEquals(2_712_000L, com.example.data.importer.PodcastAppImporter.parseDurationTextToMs("45:12"))
        assertEquals(1_760_000_000_000L, com.example.data.importer.PodcastAppImporter.normalizeEpochMillis(1_760_000_000L))

        val msPerDay = 86_400_000L
        val nowMs = 1_800_000_000_000L
        val episodes = listOf(
            // Archived played episode with Unix-seconds playedAt
            com.example.data.local.EpisodeEntity(
                id = "ep_archived_sec",
                guid = "g_arch",
                podcastId = "p1",
                podcastTitle = "Show 1",
                title = "Archived Played",
                descriptionHtml = "",
                audioUrl = "https://example.com/a.mp3",
                artworkUrl = "",
                publishedAt = nowMs - 5 * msPerDay,
                durationMs = 40 * 60_000L,
                isPlayed = true,
                isArchived = true,
                playedAt = (nowMs - 2 * msPerDay) / 1000L // stored in epoch seconds
            ),
            // Played episode without playedAt (falls back to publishedAt)
            com.example.data.local.EpisodeEntity(
                id = "ep_no_played_at",
                guid = "g_nop",
                podcastId = "p1",
                podcastTitle = "Show 1",
                title = "Legacy Played",
                descriptionHtml = "",
                audioUrl = "https://example.com/b.mp3",
                artworkUrl = "",
                publishedAt = nowMs - 4 * msPerDay,
                durationMs = 25 * 60_000L,
                isPlayed = true,
                playedAt = 0L
            ),
            // Completely undated played episode
            com.example.data.local.EpisodeEntity(
                id = "ep_undated",
                guid = "g_und",
                podcastId = "p1",
                podcastTitle = "Show 1",
                title = "Undated Played",
                descriptionHtml = "",
                audioUrl = "https://example.com/c.mp3",
                artworkUrl = "",
                publishedAt = 0L,
                durationMs = 35 * 60_000L,
                isPlayed = true,
                playedAt = 0L
            )
        )

        val summary7d = com.example.ui.components.computeListeningHistorySummary(
            episodes = episodes,
            dailyMinutes = List(90) { 0 },
            historyRange = ListeningHistoryRange.DAYS_7,
            nowMs = nowMs
        )
        assertEquals("7D should normalize epoch seconds and use publishedAt fallback (40 + 25 = 65m)", 65L, summary7d.totalMinutes)
        assertEquals(2, summary7d.playedEpisodesCount)

        val summaryAll = com.example.ui.components.computeListeningHistorySummary(
            episodes = episodes,
            dailyMinutes = List(90) { 0 },
            historyRange = ListeningHistoryRange.ALL_TIME,
            nowMs = nowMs
        )
        assertEquals("ALL_TIME should include archived, legacy, and undated played episodes (40 + 25 + 35 = 100m)", 100L, summaryAll.totalMinutes)
        assertEquals(3, summaryAll.playedEpisodesCount)
        assertEquals("ALL_TIME chart bars should sum to totalMinutes including undated", 100L, summaryAll.buckets.sumOf { it.minutes.toLong() })
    }

    @Test
    fun cloudSyncProviders_includeMultipleDrives() {
        val providers = com.example.data.local.CloudSyncProvider.entries
        assertTrue(com.example.data.local.CloudSyncProvider.GOOGLE_DRIVE in providers)
        assertTrue(com.example.data.local.CloudSyncProvider.ONEDRIVE in providers)
        assertTrue(com.example.data.local.CloudSyncProvider.DROPBOX in providers)
        assertTrue(com.example.data.local.CloudSyncProvider.WEBDAV in providers)
        assertTrue(com.example.data.local.CloudSyncProvider.OTHER_CLOUD in providers)
    }

    @Test
    fun podcastFilterAndSort_coversAllFiltersAndSortOrders() {
        val pAlpha = com.example.data.local.PodcastEntity(
            id = "p_alpha",
            title = "Alpha Show",
            author = "Host A",
            description = "",
            feedUrl = "https://example.com/a.xml",
            artworkUrl = "",
            category = "Technology",
            autoQueueMode = com.example.data.local.AutoQueueMode.BEGINNING.name,
            lastSyncedAt = 1_000L
        )
        val pBeta = com.example.data.local.PodcastEntity(
            id = "p_beta",
            title = "Beta Cast",
            author = "Host B",
            description = "",
            feedUrl = "https://example.com/b.xml",
            artworkUrl = "",
            category = "News",
            autoQueueMode = com.example.data.local.AutoQueueMode.OFF.name,
            lastSyncedAt = 3_000L
        )
        val pGamma = com.example.data.local.PodcastEntity(
            id = "p_gamma",
            title = "Gamma Daily",
            author = "Host C",
            description = "",
            feedUrl = "https://example.com/c.xml",
            artworkUrl = "",
            category = "Science",
            autoQueueMode = com.example.data.local.AutoQueueMode.OFF.name,
            lastSyncedAt = 2_000L
        )
        val podcasts = listOf(pBeta, pGamma, pAlpha)

        val episodes = listOf(
            // Alpha: 2 unplayed (1 in progress + downloaded), 1 played (30m)
            com.example.data.local.EpisodeEntity(
                id = "a1", guid = "a1", podcastId = "p_alpha", podcastTitle = "Alpha Show",
                title = "A1", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 500L, durationMs = 30 * 60_000L, isPlayed = true
            ),
            com.example.data.local.EpisodeEntity(
                id = "a2", guid = "a2", podcastId = "p_alpha", podcastTitle = "Alpha Show",
                title = "A2", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 1_500L, durationMs = 20 * 60_000L, resumePositionMs = 5 * 60_000L,
                isPlayed = false, isDownloaded = true
            ),
            com.example.data.local.EpisodeEntity(
                id = "a3", guid = "a3", podcastId = "p_alpha", podcastTitle = "Alpha Show",
                title = "A3", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 1_600L, durationMs = 20 * 60_000L, isPlayed = false
            ),
            // Beta: 1 unplayed (latest publishedAt = 5000)
            com.example.data.local.EpisodeEntity(
                id = "b1", guid = "b1", podcastId = "p_beta", podcastTitle = "Beta Cast",
                title = "B1", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 5_000L, durationMs = 15 * 60_000L, isPlayed = false
            ),
            // Gamma: 2 played (120m total listened), 0 unplayed -> Caught Up
            com.example.data.local.EpisodeEntity(
                id = "g1", guid = "g1", podcastId = "p_gamma", podcastTitle = "Gamma Daily",
                title = "G1", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 900L, durationMs = 60 * 60_000L, isPlayed = true
            ),
            com.example.data.local.EpisodeEntity(
                id = "g2", guid = "g2", podcastId = "p_gamma", podcastTitle = "Gamma Daily",
                title = "G2", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 1_100L, durationMs = 60 * 60_000L, isPlayed = true
            )
        )

        val counts = com.example.ui.screens.countPodcastsByFilter(podcasts, episodes)
        assertEquals(3, counts[com.example.data.local.PodcastFilterOption.ALL])
        assertEquals(2, counts[com.example.data.local.PodcastFilterOption.UNPLAYED])
        assertEquals(1, counts[com.example.data.local.PodcastFilterOption.IN_PROGRESS])
        assertEquals(1, counts[com.example.data.local.PodcastFilterOption.DOWNLOADED])
        assertEquals(1, counts[com.example.data.local.PodcastFilterOption.AUTO_QUEUE])
        assertEquals(1, counts[com.example.data.local.PodcastFilterOption.CAUGHT_UP])

        // Filter checks
        assertEquals(
            listOf("p_alpha"),
            com.example.ui.screens.filterAndSortPodcasts(
                podcasts, episodes,
                com.example.data.local.PodcastFilterOption.IN_PROGRESS,
                com.example.data.local.PodcastSortOption.TITLE_ASC
            ).map { it.id }
        )
        assertEquals(
            listOf("p_gamma"),
            com.example.ui.screens.filterAndSortPodcasts(
                podcasts, episodes,
                com.example.data.local.PodcastFilterOption.CAUGHT_UP,
                com.example.data.local.PodcastSortOption.TITLE_ASC
            ).map { it.id }
        )

        // Sort checks
        assertEquals(
            listOf("p_alpha", "p_beta", "p_gamma"),
            com.example.ui.screens.filterAndSortPodcasts(
                podcasts, episodes,
                com.example.data.local.PodcastFilterOption.ALL,
                com.example.data.local.PodcastSortOption.TITLE_ASC
            ).map { it.id }
        )
        assertEquals(
            listOf("p_gamma", "p_beta", "p_alpha"),
            com.example.ui.screens.filterAndSortPodcasts(
                podcasts, episodes,
                com.example.data.local.PodcastFilterOption.ALL,
                com.example.data.local.PodcastSortOption.TITLE_DESC
            ).map { it.id }
        )
        assertEquals(
            listOf("p_beta", "p_alpha", "p_gamma"),
            com.example.ui.screens.filterAndSortPodcasts(
                podcasts, episodes,
                com.example.data.local.PodcastFilterOption.ALL,
                com.example.data.local.PodcastSortOption.LATEST_EPISODE
            ).map { it.id }
        )
        assertEquals(
            listOf("p_alpha", "p_beta", "p_gamma"),
            com.example.ui.screens.filterAndSortPodcasts(
                podcasts, episodes,
                com.example.data.local.PodcastFilterOption.ALL,
                com.example.data.local.PodcastSortOption.MOST_UNPLAYED
            ).map { it.id }
        )
        assertEquals(
            listOf("p_alpha", "p_gamma", "p_beta"),
            com.example.ui.screens.filterAndSortPodcasts(
                podcasts, episodes,
                com.example.data.local.PodcastFilterOption.ALL,
                com.example.data.local.PodcastSortOption.MOST_EPISODES
            ).map { it.id }
        )
        assertEquals(
            listOf("p_gamma", "p_alpha", "p_beta"),
            com.example.ui.screens.filterAndSortPodcasts(
                podcasts, episodes,
                com.example.data.local.PodcastFilterOption.ALL,
                com.example.data.local.PodcastSortOption.MOST_LISTENED
            ).map { it.id }
        )
        assertEquals(
            listOf("p_beta", "p_gamma", "p_alpha"),
            com.example.ui.screens.filterAndSortPodcasts(
                podcasts, episodes,
                com.example.data.local.PodcastFilterOption.ALL,
                com.example.data.local.PodcastSortOption.RECENTLY_UPDATED
            ).map { it.id }
        )
    }

    @Test
    fun toastNotificationPreferences_defaultsAndEnumParsing_areValid() {
        val defaults = com.example.data.local.LoheUserSettings()
        assertTrue(defaults.toastNotificationsEnabled)
        assertEquals(com.example.data.local.ToastDurationOption.MEDIUM, defaults.toastDuration)
        assertEquals(3_500L, defaults.toastDuration.durationMs)
        assertEquals(com.example.data.local.ToastVisualStyle.PILL, defaults.toastStyle)
        assertEquals(com.example.data.local.ToastPlacement.BOTTOM, defaults.toastPlacement)

        assertEquals(
            com.example.data.local.ToastDurationOption.PERSISTENT,
            com.example.data.local.ToastDurationOption.fromName("PERSISTENT")
        )
        assertEquals(0L, com.example.data.local.ToastDurationOption.PERSISTENT.durationMs)
        assertEquals(
            com.example.data.local.ToastVisualStyle.ACCENT,
            com.example.data.local.ToastVisualStyle.fromName("ACCENT")
        )
        assertEquals(
            com.example.data.local.ToastPlacement.TOP,
            com.example.data.local.ToastPlacement.fromName("TOP")
        )
        assertEquals(
            com.example.data.local.ToastPlacement.BOTTOM,
            com.example.data.local.ToastPlacement.fromName("INVALID")
        )
    }
}


