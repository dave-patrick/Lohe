package com.example

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDatabase
import com.example.data.local.LoheUserSettings
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.repository.LoheRepository
import com.example.data.repository.isEpisodeNearCompletion
import com.example.playback.PlaybackUiState
import com.example.ui.components.EpisodeBatchAction
import com.example.ui.components.FullPlayerModalDialog
import com.example.ui.components.PlaylistBatchAction
import com.example.ui.components.PodcastBatchAction
import com.example.ui.components.YourSignalCard
import com.example.ui.components.computeAudiogramBarHeightDp
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PlaylistsTabScreen
import com.example.ui.screens.PodcastsTabScreen
import com.example.ui.screens.UpNextTabScreen
import com.example.ui.theme.LoheTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class MultiSelectAutoPlayedPlayerAndAudiogramTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun samplePodcast(id: String, title: String) = PodcastEntity(
        id = id,
        title = title,
        author = "Host $id",
        description = "About $title",
        feedUrl = "https://example.com/$id.xml",
        artworkUrl = "https://example.com/$id.jpg",
        category = "Technology"
    )

    private fun sampleEpisode(
        id: String,
        podcastId: String = "p_1",
        title: String = "Episode $id",
        isPlayed: Boolean = false,
        inUpNext: Boolean = false,
        upNextOrder: Int = 0
    ) = EpisodeEntity(
        id = id,
        guid = "guid_$id",
        podcastId = podcastId,
        podcastTitle = "Podcast $podcastId",
        title = title,
        descriptionHtml = "<p>$title notes</p>",
        audioUrl = "https://example.com/$id.mp3",
        artworkUrl = "https://example.com/$podcastId.jpg",
        publishedAt = 1_700_000_000_000L,
        durationMs = 1_000_000L,
        resumePositionMs = 120_000L,
        isPlayed = isPlayed,
        inUpNext = inUpNext,
        upNextOrder = upNextOrder
    )

    private fun samplePlaylist(id: String, name: String, smartRuleType: String = "") = PlaylistEntity(
        id = id,
        name = name,
        folderName = "General",
        smartRuleType = smartRuleType,
        assignedPodcastIdsCsv = if (smartRuleType.isEmpty()) "p_1" else ""
    )

    @Test
    fun episodeNearCompletion_marksPlayedAt98Percent_andHidesFromUpNext() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LoheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.loheDao()
            val repo = LoheRepository(dao)

            assertFalse(isEpisodeNearCompletion(979_000L, 1_000_000L))
            assertTrue(isEpisodeNearCompletion(980_000L, 1_000_000L))
            assertTrue(isEpisodeNearCompletion(995_000L, 1_000_000L))

            dao.insertPodcast(samplePodcast("p_1", "Signal Show"))
            val epA = sampleEpisode("ep_a", inUpNext = true, upNextOrder = 0)
            val epB = sampleEpisode("ep_b", inUpNext = true, upNextOrder = 1)
            val epAlreadyPlayedQueued = sampleEpisode("ep_c", isPlayed = true, inUpNext = true, upNextOrder = 2)
            dao.insertEpisodes(listOf(epA, epB, epAlreadyPlayedQueued))

            // Even before 98%, played items with inUpNext=true must be excluded by SQL query
            val initialQueue = dao.getUpNextEpisodes().first()
            assertEquals(listOf("ep_a", "ep_b"), initialQueue.map { it.id })

            // At 97.0% complete -> stays unplayed & queued
            val autoMarkedAt97 = repo.updatePlaybackPosition("ep_a", 970_000L, 1_000_000L)
            assertFalse(autoMarkedAt97)
            assertFalse(dao.getEpisodeById("ep_a")!!.isPlayed)
            assertEquals(listOf("ep_a", "ep_b"), dao.getUpNextEpisodes().first().map { it.id })

            // At 98.0% complete -> automatically marked played & removed from Up Next
            val autoMarkedAt98 = repo.updatePlaybackPosition("ep_a", 980_000L, 1_000_000L)
            assertTrue(autoMarkedAt98)
            val epAAfter = dao.getEpisodeById("ep_a")!!
            assertTrue(epAAfter.isPlayed)
            assertTrue(epAAfter.playedAt > 0L)
            assertFalse(epAAfter.inUpNext)
            assertEquals(listOf("ep_b"), dao.getUpNextEpisodes().first().map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun repository_batchActions_forUpNextPodcastsAndPlaylists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LoheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.loheDao()
            val repo = LoheRepository(dao)

            val p1 = samplePodcast("p_1", "Alpha")
            val p2 = samplePodcast("p_2", "Beta")
            dao.insertPodcast(p1)
            dao.insertPodcast(p2)

            val ep1 = sampleEpisode("ep_1", "p_1", inUpNext = true, upNextOrder = 0)
            val ep2 = sampleEpisode("ep_2", "p_1", inUpNext = true, upNextOrder = 1)
            val ep3 = sampleEpisode("ep_3", "p_2", inUpNext = true, upNextOrder = 2)
            dao.insertEpisodes(listOf(ep1, ep2, ep3))

            // Move ep3 to top of Up Next
            repo.moveEpisodesToQueueEdge(listOf(ep3), toBeginning = true)
            assertEquals(listOf("ep_3", "ep_1", "ep_2"), dao.getUpNextEpisodes().first().map { it.id })

            // Move ep3 to bottom
            repo.moveEpisodesToQueueEdge(listOf(ep3), toBeginning = false)
            assertEquals(listOf("ep_1", "ep_2", "ep_3"), dao.getUpNextEpisodes().first().map { it.id })

            // Remove ep2 from Up Next
            repo.removeEpisodesFromUpNext(listOf(ep2))
            assertEquals(listOf("ep_1", "ep_3"), dao.getUpNextEpisodes().first().map { it.id })

            // Batch Podcast operations
            repo.updatePodcastsAutoQueue(listOf(p1), AutoQueueMode.END)
            assertEquals(AutoQueueMode.END.name, dao.getPodcastById("p_1")!!.autoQueueMode)

            repo.markAllEpisodesPlayedForPodcasts(listOf(p1))
            assertTrue(dao.getEpisodeById("ep_1")!!.isPlayed)
            assertTrue(dao.getEpisodeById("ep_2")!!.isPlayed)

            // Batch Playlist operations (smart playlists preserved on delete)
            val customPl = samplePlaylist("pl_custom", "Custom Mix", smartRuleType = "")
            val smartPl = samplePlaylist("pl_smart", "New Releases", smartRuleType = "RECENT")
            dao.insertPlaylist(customPl)
            dao.insertPlaylist(smartPl)

            repo.setPlaylistsPinned(listOf(customPl, smartPl), pinned = true)
            assertTrue(dao.getCustomPlaylists().first().all { it.isPinned })

            repo.setPlaylistsAutoDownload(listOf(customPl), enabled = true)
            assertTrue(dao.getCustomPlaylists().first().first { it.id == "pl_custom" }.autoDownload)

            repo.deleteCustomPlaylists(listOf(customPl, smartPl))
            val remainingIds = dao.getCustomPlaylists().first().map { it.id }
            assertEquals(listOf("pl_smart"), remainingIds)
        } finally {
            db.close()
        }
    }

    @Test
    fun podcastsScreen_pressAndHold_entersMultiSelectAndFiresBatchAction() {
        val podcasts = listOf(samplePodcast("p_1", "Show Alpha"), samplePodcast("p_2", "Show Beta"))
        val episodes = listOf(sampleEpisode("e_1", "p_1"), sampleEpisode("e_2", "p_2"))
        var capturedPodcasts: List<PodcastEntity> = emptyList()
        var capturedAction: PodcastBatchAction? = null

        composeTestRule.setContent {
            LoheTheme {
                PodcastsTabScreen(
                    settings = LoheUserSettings(),
                    podcasts = podcasts,
                    episodes = episodes,
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
                    onBatchPodcastAction = { pods, action ->
                        capturedPodcasts = pods
                        capturedAction = action
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("podcast_card_p_1").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("episode_multiselect_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("podcast_multiselect_action_bar").assertIsDisplayed()

        // Tap second show card to add it to selection
        composeTestRule.onNodeWithTag("podcast_card_p_2").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("podcast_multiselect_mark_played").performClick()
        composeTestRule.waitForIdle()

        assertEquals(PodcastBatchAction.MARK_ALL_PLAYED, capturedAction)
        assertEquals(setOf("p_1", "p_2"), capturedPodcasts.map { it.id }.toSet())
    }

    @Test
    fun playlistsScreen_pressAndHold_entersMultiSelectAndFiresBatchAction() {
        val pl1 = samplePlaylist("pl_1", "Morning Commute")
        val pl2 = samplePlaylist("pl_2", "Deep Dive")
        val podcasts = listOf(samplePodcast("p_1", "Signal Show"))
        val episodes = listOf(sampleEpisode("ep_1", "p_1"))
        var capturedPlaylists: List<PlaylistEntity> = emptyList()
        var capturedAction: PlaylistBatchAction? = null

        composeTestRule.setContent {
            LoheTheme {
                PlaylistsTabScreen(
                    settings = LoheUserSettings(),
                    playlists = listOf(pl1, pl2),
                    podcasts = podcasts,
                    episodes = episodes,
                    upNextEpisodes = emptyList(),
                    selectedPlaylistId = null,
                    selectedFolder = "All",
                    playbackState = PlaybackUiState(),
                    resolvePlaylistEpisodes = { _, all -> all },
                    onSelectPlaylist = {},
                    onSelectFolder = {},
                    onToggleGridView = {},
                    onCreatePlaylist = { _, _, _ -> },
                    onDeletePlaylist = {},
                    onTogglePinned = {},
                    onUpdateFolder = { _, _ -> },
                    onUpdateAssignedPodcasts = { _, _ -> },
                    onQueueEpisodes = {},
                    onPlayEpisode = {},
                    onToggleUpNext = { _, _ -> },
                    onToggleStarred = {},
                    onToggleDownloaded = {},
                    onMarkPlayed = { _, _ -> },
                    onOpenNotes = {},
                    onOpenSettings = {},
                    onBatchPlaylistAction = { pls, act ->
                        capturedPlaylists = pls
                        capturedAction = act
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("playlist_card_pl_1").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("episode_multiselect_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("playlist_multiselect_action_bar").assertIsDisplayed()

        composeTestRule.onNodeWithTag("playlist_multiselect_pin").performClick()
        composeTestRule.waitForIdle()

        assertEquals(PlaylistBatchAction.PIN, capturedAction)
        assertEquals(listOf("pl_1"), capturedPlaylists.map { it.id })
    }

    @Test
    fun playlistDetailScreen_hidesPlayedEpisodes_andPressAndHoldMultiSelectWorks() {
        val playlist = samplePlaylist("pl_1", "Morning Commute")
        val unplayedEp = sampleEpisode("ep_1", title = "Unplayed Episode", isPlayed = false)
        val playedEp = sampleEpisode("ep_2", title = "Played Hidden Episode", isPlayed = true)
        var capturedEpisodes: List<EpisodeEntity> = emptyList()
        var capturedAction: EpisodeBatchAction? = null

        composeTestRule.setContent {
            LoheTheme {
                PlaylistDetailScreen(
                    playlist = playlist,
                    settings = LoheUserSettings(),
                    podcasts = listOf(samplePodcast("p_1", "Signal Show")),
                    playlistEpisodes = listOf(unplayedEp, playedEp),
                    upNextEpisodes = emptyList(),
                    playbackState = PlaybackUiState(),
                    onBack = {},
                    onDeletePlaylist = {},
                    onUpdateAssignedPodcasts = {},
                    onQueueAll = {},
                    onPlayEpisode = {},
                    onToggleUpNext = { _, _ -> },
                    onToggleStarred = {},
                    onToggleDownloaded = {},
                    onMarkPlayed = { _, _ -> },
                    onOpenNotes = {},
                    onOpenSettings = {},
                    onBatchEpisodeAction = { eps, act ->
                        capturedEpisodes = eps
                        capturedAction = act
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Unplayed Episode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Played Hidden Episode").assertDoesNotExist()

        composeTestRule.onNodeWithTag("playlist_ep_row_ep_1").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("episode_multiselect_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("episode_multiselect_action_bar").assertIsDisplayed()

        composeTestRule.onNodeWithTag("multiselect_action_bookmark").performClick()
        composeTestRule.waitForIdle()

        assertEquals(EpisodeBatchAction.BOOKMARK, capturedAction)
        assertEquals(listOf("ep_1"), capturedEpisodes.map { it.id })
    }

    @Test
    fun upNextScreen_hidesPlayedEpisodes_andPressAndHoldMultiSelectWorks() {
        val unplayedEp = sampleEpisode("ep_u1", title = "Active Queued Episode", isPlayed = false, inUpNext = true)
        val playedEp = sampleEpisode("ep_u2", title = "Completed Queued Episode", isPlayed = true, inUpNext = true)
        var capturedEpisodes: List<EpisodeEntity> = emptyList()
        var capturedAction: EpisodeBatchAction? = null

        composeTestRule.setContent {
            LoheTheme {
                UpNextTabScreen(
                    settings = LoheUserSettings(),
                    upNextEpisodes = listOf(unplayedEp, playedEp),
                    playbackState = PlaybackUiState(),
                    onOpenSettings = {},
                    onBrowsePodcasts = {},
                    onPlayEpisode = {},
                    onMoveEpisode = { _, _ -> },
                    onMoveEpisodeEdge = { _, _ -> },
                    onMarkPlayed = { _, _ -> },
                    onRemoveFromQueue = {},
                    onClearQueue = {},
                    onBatchUpNextAction = { eps, act ->
                        capturedEpisodes = eps
                        capturedAction = act
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Active Queued Episode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed Queued Episode").assertDoesNotExist()

        composeTestRule.onNodeWithTag("up_next_item_ep_u1").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("episode_multiselect_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("up_next_multiselect_action_bar").assertIsDisplayed()

        composeTestRule.onNodeWithTag("up_next_multiselect_remove").performClick()
        composeTestRule.waitForIdle()

        assertEquals(EpisodeBatchAction.REMOVE_FROM_UP_NEXT, capturedAction)
        assertEquals(listOf("ep_u1"), capturedEpisodes.map { it.id })
    }

    @Test
    fun fullPlayerModal_displaysFloatingControlsCard() {
        val ep = sampleEpisode("ep_fp", title = "Full Player Episode")
        composeTestRule.setContent {
            LoheTheme {
                FullPlayerModalDialog(
                    playbackState = PlaybackUiState(currentEpisode = ep, isPlaying = true, durationMs = 1_000_000L),
                    settings = LoheUserSettings(),
                    onDismiss = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipBack = {},
                    onSkipForward = {},
                    onSpeedChange = {},
                    onVolumeBoostChange = {},
                    onSkipSilenceChange = {},
                    onSleepTimerSelect = {},
                    onToggleStarred = {},
                    onOpenNotes = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("full_player_controls_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("full_player_speed_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("full_player_skip_back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("full_player_play_pause").assertIsDisplayed()
        composeTestRule.onNodeWithTag("full_player_skip_forward").assertIsDisplayed()
        composeTestRule.onNodeWithTag("full_player_sleep_timer_button").assertIsDisplayed()
    }

    @Test
    fun audiogramHelper_animatesDynamicallyOnlyWhenPlayingAndMotionEnabled() {
        // Static when paused
        val pausedT0 = computeAudiogramBarHeightDp(3, 12f, isPlaying = false, reducedMotion = false, phaseA = 0f, phaseB = 0f)
        val pausedT1 = computeAudiogramBarHeightDp(3, 12f, isPlaying = false, reducedMotion = false, phaseA = 2.1f, phaseB = 1.4f)
        assertEquals(12f, pausedT0, 0.001f)
        assertEquals(12f, pausedT1, 0.001f)

        // Static when reducedMotion is enabled even if playing
        val reducedT0 = computeAudiogramBarHeightDp(3, 12f, isPlaying = true, reducedMotion = true, phaseA = 0f, phaseB = 0f)
        val reducedT1 = computeAudiogramBarHeightDp(3, 12f, isPlaying = true, reducedMotion = true, phaseA = 2.1f, phaseB = 1.4f)
        assertEquals(12f, reducedT0, 0.001f)
        assertEquals(12f, reducedT1, 0.001f)

        // Dynamically changing across phases and bars when playing with motion enabled
        val playingPhase0 = (0..10).map { idx ->
            computeAudiogramBarHeightDp(idx, 12f, isPlaying = true, reducedMotion = false, phaseA = 0f, phaseB = 0f)
        }
        val playingPhasePi = (0..10).map { idx ->
            computeAudiogramBarHeightDp(idx, 12f, isPlaying = true, reducedMotion = false, phaseA = 3.14f, phaseB = 1.57f)
        }
        assertNotEquals(playingPhase0, playingPhasePi)
        assertTrue(playingPhase0.all { it in 5f..22f })
        assertTrue(playingPhasePi.all { it in 5f..22f })
    }

    @Test
    fun homeYourSignalAudiogram_reflectsPlayingAndPausedStates() {
        val isPlayingState = mutableStateOf(false)
        val reducedMotionState = mutableStateOf(false)

        composeTestRule.setContent {
            LoheTheme {
                YourSignalCard(
                    playedPercent = 45,
                    playedCount = 9,
                    totalEpisodesCount = 20,
                    inProgressCount = 2,
                    timePlayedMs = 3_600_000L,
                    totalDurationMs = 7_200_000L,
                    timeSavedMs = 180_000L,
                    stillToHearMs = 3_600_000L,
                    waitingEpisodesCount = 11,
                    isPlaying = isPlayingState.value,
                    reducedMotion = reducedMotionState.value
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("your_signal_audiogram")
            .assertIsDisplayed()
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "Paused audiogram"
                )
            )

        isPlayingState.value = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("your_signal_audiogram")
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "Playing audiogram"
                )
            )

        reducedMotionState.value = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("your_signal_audiogram")
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "Paused audiogram"
                )
            )
    }
}
