package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDatabase
import com.example.data.local.LoheUserSettings
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.local.PodcastFilterOption
import com.example.data.local.PodcastSortOption
import com.example.data.network.RssAndOpmlParser
import com.example.data.repository.DiscoverCatalogItem
import com.example.data.repository.LoheRepository
import com.example.playback.PlaybackUiState
import com.example.ui.components.EpisodeBatchAction
import com.example.ui.components.LibraryDetailsCard
import com.example.ui.components.UniformCountPillBadge
import com.example.ui.components.buildClickableShowNotesAnnotatedString
import com.example.ui.components.sanitizeExternalNoteUrl
import com.example.ui.screens.DiscoverTabScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PodcastsTabScreen
import com.example.ui.screens.countPodcastsByFilter
import com.example.ui.screens.filterAndSortPodcasts
import com.example.ui.theme.LoheTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class DiscoverStatsNotesMultiSelectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun catalogItem(
        title: String,
        author: String = "Host",
        feedUrl: String
    ) = DiscoverCatalogItem(
        title = title,
        author = author,
        category = "Technology",
        description = "Description for $title",
        feedUrl = feedUrl,
        artworkUrl = "https://example.com/art.jpg",
        sampleAudioUrl = "https://example.com/audio.mp3",
        sampleEpisodeTitle = "$title Ep 1",
        sampleEpisodeHtml = "<p>Notes</p>",
        durationMs = 1_800_000L
    )

    @Test
    fun deduplicateDiscoverCatalog_removesDuplicateFeedsAndTitles() {
        val parser = RssAndOpmlParser()
        val raw = listOf(
            catalogItem("Tech Weekly", "Alice", "https://example.com/tech.xml"),
            catalogItem("Tech Weekly", "Alice", "HTTPS://EXAMPLE.COM/tech.xml "), // same feed URL case/space
            catalogItem("Tech Weekly", "Alice", "https://mirror.example.com/tech.xml"), // same title+author
            catalogItem("Science Hour", "Bob", "https://example.com/science.xml"),
            catalogItem("Design Notes", "Carol", "https://example.com/design.xml")
        )

        val deduped = parser.deduplicateDiscoverCatalog(raw)
        assertEquals(3, deduped.size)
        assertEquals(listOf("Tech Weekly", "Science Hour", "Design Notes"), deduped.map { it.title })
    }

    @Test
    fun discoverTabScreen_scrollingWithDuplicateItems_doesNotCrash() {
        val duplicateItems = List(16) { idx ->
            // Intentionally inject identical feedUrl + title duplicates to verify collision-proof keys
            val group = idx % 4
            catalogItem(
                title = "Show $group",
                author = "Creator $group",
                feedUrl = "https://example.com/feed_$group.xml"
            )
        }

        composeTestRule.setContent {
            LoheTheme {
                DiscoverTabScreen(
                    query = "Show",
                    catalog = duplicateItems,
                    isSearching = false,
                    subscribedPodcasts = emptyList(),
                    previewItem = null,
                    previewEpisodes = emptyList(),
                    isPreviewLoading = false,
                    onSearchDiscover = {},
                    onBrowseCategory = {},
                    onOpenPreview = {},
                    onSubscribeCatalog = {},
                    onSubscribeRssUrl = {},
                    onPlayPreviewEpisode = {},
                    onImportOpmlOrJson = {},
                    onOpenSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("discover_tab_screen").assertIsDisplayed()

        // Scroll down through the grid multiple times — must not throw duplicate key IllegalArgumentException
        repeat(3) {
            composeTestRule.onNodeWithTag("discover_tab_screen").performTouchInput {
                swipeUp(startY = height * 0.8f, endY = height * 0.2f, durationMillis = 120L)
            }
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun repository_statePreservingUpsertAndZeroPositionGuard_protectListeningStats() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LoheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.loheDao()
            val repo = LoheRepository(dao)

            val podcast = PodcastEntity(
                id = "pod_1",
                title = "Signal Cast",
                author = "Lohe",
                description = "Test",
                feedUrl = "https://example.com/signal.xml",
                artworkUrl = "https://example.com/art.png",
                category = "Tech"
            )
            dao.insertPodcast(podcast)

            val playedEp = EpisodeEntity(
                id = "ep_1",
                guid = "guid_1",
                podcastId = "pod_1",
                podcastTitle = "Signal Cast",
                title = "Episode One",
                descriptionHtml = "",
                audioUrl = "https://example.com/ep1.mp3",
                artworkUrl = "",
                publishedAt = 1_700_000_000_000L,
                durationMs = 3_600_000L,
                resumePositionMs = 3_600_000L,
                isPlayed = true,
                playedAt = 1_700_000_500_000L,
                isStarred = true,
                isDownloaded = true,
                localFilePath = "/tmp/ep1.mp3"
            )
            val inProgressEp = EpisodeEntity(
                id = "ep_2",
                guid = "guid_2",
                podcastId = "pod_1",
                podcastTitle = "Signal Cast",
                title = "Episode   Two",
                descriptionHtml = "",
                audioUrl = "https://example.com/ep2.mp3",
                artworkUrl = "",
                publishedAt = 1_700_001_000_000L,
                durationMs = 2_400_000L,
                resumePositionMs = 950_000L,
                isPlayed = false,
                inUpNext = true,
                upNextOrder = 0
            )
            dao.insertEpisodes(listOf(playedEp, inProgressEp))

            // Simulate re-sync / re-subscribe returning fresh unplayed copies with changed IDs/URLs
            val incomingFromFeed = listOf(
                EpisodeEntity(
                    id = "ep_1_new_id",
                    guid = "guid_1",
                    podcastId = "pod_1",
                    podcastTitle = "Signal Cast",
                    title = "Episode One",
                    descriptionHtml = "<p>Healed show notes</p>",
                    audioUrl = "https://cdn.example.com/ep1.mp3",
                    artworkUrl = "https://example.com/ep1.jpg",
                    publishedAt = 1_700_000_000_000L,
                    durationMs = 3_600_000L,
                    resumePositionMs = 0L,
                    isPlayed = false,
                    playedAt = 0L
                ),
                EpisodeEntity(
                    id = "ep_2_new_id",
                    guid = "different_guid_2",
                    podcastId = "pod_1",
                    podcastTitle = "Signal Cast",
                    title = "episode two", // normalized title match
                    descriptionHtml = "<p>Ep 2 notes</p>",
                    audioUrl = "https://cdn2.example.com/ep2.mp3",
                    artworkUrl = "",
                    publishedAt = 1_700_001_000_000L,
                    durationMs = 2_400_000L,
                    resumePositionMs = 0L,
                    isPlayed = false
                ),
                EpisodeEntity(
                    id = "ep_3",
                    guid = "guid_3",
                    podcastId = "pod_1",
                    podcastTitle = "Signal Cast",
                    title = "Brand New Episode",
                    descriptionHtml = "",
                    audioUrl = "https://example.com/ep3.mp3",
                    artworkUrl = "",
                    publishedAt = 1_700_002_000_000L,
                    durationMs = 1_500_000L
                )
            )

            val insertedCount = repo.upsertEpisodesPreservingUserState("pod_1", "Signal Cast", incomingFromFeed)
            assertEquals("Only genuinely new ep_3 should be inserted", 1, insertedCount)

            val ep1After = dao.getEpisodeById("ep_1")!!
            assertTrue("ep_1 played flag must be preserved", ep1After.isPlayed)
            assertEquals(1_700_000_500_000L, ep1After.playedAt)
            assertEquals(3_600_000L, ep1After.resumePositionMs)
            assertTrue(ep1After.isStarred)
            assertTrue(ep1After.isDownloaded)
            assertEquals("<p>Healed show notes</p>", ep1After.descriptionHtml)

            val ep2After = dao.getEpisodeById("ep_2")!!
            assertEquals("ep_2 resumePositionMs must be preserved", 950_000L, ep2After.resumePositionMs)
            assertTrue(ep2After.inUpNext)

            // Transient 0ms position tick after crash/reconnect must NOT overwrite saved resumePositionMs
            repo.updatePlaybackPosition("ep_2", 0L, 2_400_000L)
            assertEquals(950_000L, dao.getEpisodeById("ep_2")!!.resumePositionMs)

            // Batch repository actions
            val ep3 = dao.getEpisodeById("ep_3")!!
            repo.markEpisodesPlayed(listOf(ep2After, ep3), played = true)
            assertTrue(dao.getEpisodeById("ep_2")!!.isPlayed)
            assertTrue(dao.getEpisodeById("ep_3")!!.isPlayed)

            repo.setEpisodesStarred(listOf(ep2After, ep3), starred = true)
            assertTrue(dao.getEpisodeById("ep_2")!!.isStarred)
            assertTrue(dao.getEpisodeById("ep_3")!!.isStarred)

            repo.archiveEpisodes(listOf(ep3))
            assertTrue(dao.getEpisodeById("ep_3")!!.isArchived)
        } finally {
            db.close()
        }
    }

    @Test
    fun showNotesLinks_allowSafeSchemes_andRejectUnsafeSchemes() {
        assertEquals("https://lohe.app/notes", sanitizeExternalNoteUrl("https://lohe.app/notes."))
        assertEquals("http://example.org/ep?id=42", sanitizeExternalNoteUrl("http://example.org/ep?id=42)"))
        assertEquals("https://www.example.com/show", sanitizeExternalNoteUrl("www.example.com/show,"))
        assertEquals("mailto:hello@lohe.app", sanitizeExternalNoteUrl("mailto:hello@lohe.app"))

        assertNull(sanitizeExternalNoteUrl("javascript:alert(1)"))
        assertNull(sanitizeExternalNoteUrl("data:text/html,<script>alert(1)</script>"))
        assertNull(sanitizeExternalNoteUrl("file:///etc/passwd"))
        assertNull(sanitizeExternalNoteUrl("content://com.example/secret"))
        assertNull(sanitizeExternalNoteUrl("intent://scan/#Intent;end"))
        assertNull(sanitizeExternalNoteUrl("https://localhost"))

        val opened = mutableListOf<String>()
        val html = """
            <p>Visit <a href="https://lohe.app/episode">Episode Page</a> or www.example.org/docs
            and email mailto:team@lohe.app. Ignore <a href="javascript:alert(1)">bad link</a>.</p>
        """.trimIndent()
        val annotated = buildClickableShowNotesAnnotatedString(html) { opened.add(it) }
        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals("Expected 3 safe clickable links (anchor + www + mailto), skipping javascript:", 3, linkAnnotations.size)

        linkAnnotations.forEach { range ->
            val clickable = range.item as? androidx.compose.ui.text.LinkAnnotation.Clickable
            assertNotNull(clickable)
            clickable?.linkInteractionListener?.onClick(clickable)
        }
        assertEquals(
            listOf("https://lohe.app/episode", "https://www.example.org/docs", "mailto:team@lohe.app"),
            opened
        )
    }

    @Test
    fun filterAndSortPodcasts_supportsExpandedFiltersSortsAndSearchQuery() {
        val podcasts = listOf(
            PodcastEntity(
                id = "p_tech", title = "Signal Tech", author = "Ada", description = "",
                feedUrl = "f1", artworkUrl = "", category = "Technology",
                useCustomAudio = true, autoQueueMode = AutoQueueMode.BEGINNING.name
            ),
            PodcastEntity(
                id = "p_art", title = "Canvas Talk", author = "Frida", description = "",
                feedUrl = "f2", artworkUrl = "", category = "Art",
                useCustomAudio = false, autoQueueMode = AutoQueueMode.OFF.name
            ),
            PodcastEntity(
                id = "p_sci", title = "Deep Orbit", author = "Carl", description = "",
                feedUrl = "f3", artworkUrl = "", category = "Science",
                useCustomAudio = false, autoQueueMode = AutoQueueMode.OFF.name
            )
        )
        val episodes = listOf(
            EpisodeEntity(
                id = "e1", podcastId = "p_tech", podcastTitle = "Signal Tech",
                title = "T1", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 1_000L, durationMs = 600_000L, isPlayed = false, isStarred = true
            ),
            EpisodeEntity(
                id = "e2", podcastId = "p_tech", podcastTitle = "Signal Tech",
                title = "T2", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 2_000L, durationMs = 600_000L, isPlayed = false
            ),
            EpisodeEntity(
                id = "e3", podcastId = "p_art", podcastTitle = "Canvas Talk",
                title = "A1", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 3_000L, durationMs = 600_000L, isPlayed = false
            ),
            EpisodeEntity(
                id = "e4", podcastId = "p_sci", podcastTitle = "Deep Orbit",
                title = "S1", descriptionHtml = "", audioUrl = "", artworkUrl = "",
                publishedAt = 4_000L, durationMs = 600_000L, isPlayed = true
            )
        )

        val counts = countPodcastsByFilter(podcasts, episodes)
        assertEquals(1, counts[PodcastFilterOption.STARRED])
        assertEquals(1, counts[PodcastFilterOption.CUSTOM_AUDIO])

        assertEquals(
            listOf("p_tech"),
            filterAndSortPodcasts(podcasts, episodes, PodcastFilterOption.STARRED, PodcastSortOption.TITLE_ASC).map { it.id }
        )
        assertEquals(
            listOf("p_tech"),
            filterAndSortPodcasts(podcasts, episodes, PodcastFilterOption.CUSTOM_AUDIO, PodcastSortOption.TITLE_ASC).map { it.id }
        )
        assertEquals(
            listOf("p_sci", "p_art", "p_tech"),
            filterAndSortPodcasts(podcasts, episodes, PodcastFilterOption.ALL, PodcastSortOption.LEAST_UNPLAYED).map { it.id }
        )
        assertEquals(
            listOf("p_art", "p_sci", "p_tech"),
            filterAndSortPodcasts(podcasts, episodes, PodcastFilterOption.ALL, PodcastSortOption.CATEGORY_ASC).map { it.id }
        )
        assertEquals(
            listOf("p_art"),
            filterAndSortPodcasts(podcasts, episodes, PodcastFilterOption.ALL, PodcastSortOption.TITLE_ASC, searchQuery = "Frida").map { it.id }
        )
    }

    @Test
    fun libraryDetailsCard_showsWaitingUnplayedAsHero_andTotalEpsInBreakdownTile() {
        composeTestRule.setContent {
            LoheTheme {
                LibraryDetailsCard(
                    totalEpisodes = 148,
                    playedCount = 112,
                    inProgressCount = 5,
                    waitingCount = 36,
                    subscriptionsCount = 12,
                    playlistsCount = 6,
                    onNavigateToPodcasts = {},
                    onNavigateToPlaylists = {},
                    onInProgressClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("library_detail_hero_unplayed_count")
            .assertIsDisplayed()
            .assertTextEquals("36")
        composeTestRule.onNodeWithText("Waiting • Unplayed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Eps").assertIsDisplayed()
        composeTestRule.onNodeWithText("148").assertIsDisplayed()
    }

    @Test
    fun uniformCountPillBadge_displaysExactCountsAndCap() {
        composeTestRule.setContent {
            LoheTheme {
                UniformCountPillBadge(
                    count = 1240,
                    borderColor = androidx.compose.ui.graphics.Color.White
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("999+").assertIsDisplayed()
    }

    @Test
    fun playlistDetailScreen_multiSelectMode_selectsMultipleEpisodesAndTriggersBatchAction() {
        val playlist = PlaylistEntity(
            id = "pl_test",
            name = "Morning Queue",
            smartRuleType = "",
            assignedPodcastIdsCsv = "pod_1"
        )
        val eps = listOf(
            EpisodeEntity(
                id = "ep_ms_1", podcastId = "pod_1", podcastTitle = "Show A",
                title = "First Episode", descriptionHtml = "", audioUrl = "https://example.com/1.mp3",
                artworkUrl = "", publishedAt = 2_000L, durationMs = 1_200_000L, isPlayed = false
            ),
            EpisodeEntity(
                id = "ep_ms_2", podcastId = "pod_1", podcastTitle = "Show A",
                title = "Second Episode", descriptionHtml = "", audioUrl = "https://example.com/2.mp3",
                artworkUrl = "", publishedAt = 1_000L, durationMs = 1_200_000L, isPlayed = false
            )
        )
        var capturedAction: EpisodeBatchAction? = null
        var capturedIds: List<String> = emptyList()

        composeTestRule.setContent {
            LoheTheme {
                PlaylistDetailScreen(
                    playlist = playlist,
                    settings = LoheUserSettings(),
                    podcasts = emptyList(),
                    playlistEpisodes = eps,
                    upNextEpisodes = emptyList(),
                    playbackState = PlaybackUiState(),
                    onBack = {},
                    onDeletePlaylist = {},
                    onUpdateAssignedPodcasts = {},
                    onQueueAll = {},
                    onPlayEpisode = {},
                    onToggleUpNext = { _, _ -> },
                    onBatchEpisodeAction = { selected, action ->
                        capturedIds = selected.map { it.id }
                        capturedAction = action
                    },
                    onToggleStarred = {},
                    onToggleDownloaded = {},
                    onMarkPlayed = { _, _ -> },
                    onOpenNotes = {},
                    onOpenSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Enter multi-select mode from the header toggle button
        composeTestRule.onNodeWithTag("playlist_multiselect_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("episode_multiselect_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("episode_multiselect_action_bar").assertIsDisplayed()

        // Select both episodes
        composeTestRule.onNodeWithTag("playlist_ep_row_ep_ms_1").performClick()
        composeTestRule.onNodeWithTag("playlist_ep_row_ep_ms_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2 selected").assertIsDisplayed()

        // Trigger batch "Played" action
        composeTestRule.onNodeWithTag("multiselect_action_mark_played").performClick()
        composeTestRule.waitForIdle()

        assertEquals(EpisodeBatchAction.MARK_PLAYED, capturedAction)
        assertEquals(listOf("ep_ms_1", "ep_ms_2"), capturedIds)
    }

    @Test
    fun podcastsTabScreen_sortPillAndSearchControls_areAccessible() {
        val podcasts = listOf(
            PodcastEntity(
                id = "p_1", title = "Alpha Show", author = "Host A", description = "",
                feedUrl = "https://example.com/a.xml", artworkUrl = "", category = "Tech"
            ),
            PodcastEntity(
                id = "p_2", title = "Beta Show", author = "Host B", description = "",
                feedUrl = "https://example.com/b.xml", artworkUrl = "", category = "Science"
            )
        )
        var pickedSort: PodcastSortOption? = null

        composeTestRule.setContent {
            LoheTheme {
                PodcastsTabScreen(
                    settings = LoheUserSettings(),
                    podcasts = podcasts,
                    episodes = emptyList(),
                    upNextEpisodes = emptyList(),
                    selectedPodcastId = null,
                    playbackState = PlaybackUiState(),
                    onSelectPodcast = {},
                    onOpenSettings = {},
                    onUpdateAutoQueue = { _, _ -> },
                    onUnsubscribePodcast = {},
                    onImportOpml = {},
                    onAddRssUrl = {},
                    onPlayEpisode = {},
                    onToggleUpNext = { _, _ -> },
                    onToggleStarred = {},
                    onToggleDownloaded = {},
                    onMarkEpisodePlayed = { _, _ -> },
                    onResetEpisodeProgress = {},
                    onOpenNotes = {},
                    onSortChange = { pickedSort = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("podcasts_sort_pill_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("podcast_sort_pill_category_asc").performClick()
        composeTestRule.waitForIdle()

        assertEquals(PodcastSortOption.CATEGORY_ASC, pickedSort)
    }

    @Test
    fun upNextTap_opensEpisodeShowNotes() {
        val ep = EpisodeEntity(
            id = "ep_tap_1",
            podcastId = "pod_1",
            podcastTitle = "Signal Tech",
            title = "Deep Dive Episode",
            descriptionHtml = "<p>Episode show notes content</p>",
            audioUrl = "https://example.com/ep1.mp3",
            artworkUrl = "",
            publishedAt = 1_750_000_000_000L,
            durationMs = 1_800_000L,
            resumePositionMs = 300_000L,
            isPlayed = false,
            inUpNext = true
        )
        var notesOpenedId: String? = null

        composeTestRule.setContent {
            LoheTheme {
                com.example.ui.screens.UpNextTabScreen(
                    settings = LoheUserSettings(),
                    upNextEpisodes = listOf(ep),
                    playbackState = PlaybackUiState(),
                    onOpenSettings = {},
                    onBrowsePodcasts = {},
                    onPlayEpisode = {},
                    onMoveEpisode = { _, _ -> },
                    onMoveEpisodeEdge = { _, _ -> },
                    onMarkPlayed = { _, _ -> },
                    onRemoveFromQueue = {},
                    onClearQueue = {},
                    onOpenNotes = { notesOpenedId = it.id }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("up_next_item_ep_tap_1").performClick()
        assertEquals("ep_tap_1", notesOpenedId)
    }

    @Test
    fun episodeNotesSheet_exposesExpandedActionsAndPlaylistPicker() {
        val ep = EpisodeEntity(
            id = "ep_tap_2",
            podcastId = "pod_1",
            podcastTitle = "Signal Tech",
            title = "Deep Dive Episode",
            descriptionHtml = "<p>Episode show notes content</p>",
            audioUrl = "https://example.com/ep1.mp3",
            artworkUrl = "",
            publishedAt = 1_750_000_000_000L,
            durationMs = 1_800_000L,
            resumePositionMs = 300_000L,
            isPlayed = false,
            inUpNext = false
        )
        val playlist = PlaylistEntity(
            id = "pl_favs",
            name = "Weekend Queue",
            folderName = "General",
            smartRuleType = ""
        )

        var playNextCalled = false
        var openPodcastCalled = false
        var resetProgressCalled = false
        var archiveCalled = false
        var toggledPlaylistId: String? = null

        composeTestRule.setContent {
            LoheTheme {
                com.example.ui.components.EpisodeNotesBottomSheet(
                    episode = ep,
                    onDismiss = {},
                    onSeekToMs = {},
                    onPlayToggle = {},
                    onAddToUpNext = { beg -> if (beg) playNextCalled = true },
                    onToggleStarred = {},
                    onToggleDownloaded = {},
                    onMarkPlayed = {},
                    customPlaylists = listOf(playlist),
                    onToggleEpisodeInPlaylist = { toggledPlaylistId = it.id },
                    onCreatePlaylistWithEpisode = {},
                    onOpenPodcast = { openPodcastCalled = true },
                    onResetProgress = { resetProgressCalled = true },
                    onArchiveEpisode = { archiveCalled = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("episode_sheet_secondary_actions_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("episode_sheet_play_next_chip").performClick()
        assertTrue(playNextCalled)

        composeTestRule.onNodeWithTag("episode_sheet_open_podcast_chip").performClick()
        assertTrue(openPodcastCalled)

        composeTestRule.onNodeWithTag("episode_sheet_reset_progress_chip").performClick()
        assertTrue(resetProgressCalled)

        composeTestRule.onNodeWithTag("episode_sheet_archive_chip").performClick()
        assertTrue(archiveCalled)

        composeTestRule.onNodeWithTag("episode_sheet_playlist_chip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("episode_sheet_playlist_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("episode_sheet_playlist_item_pl_favs").performClick()
        assertEquals("pl_favs", toggledPlaylistId)
    }
}
