package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.robolectric.Shadows
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDatabase
import com.example.data.local.LohePreferencesRepository
import com.example.data.local.LoheUserSettings
import com.example.data.local.PodcastEntity
import com.example.data.repository.LoheRepository
import com.example.playback.PlaybackUiState
import com.example.ui.LoheViewModel
import com.example.ui.components.FullPlayerModalDialog
import com.example.ui.components.HomeAudiogramStrip
import com.example.ui.components.MiniPlayerBar
import com.example.ui.theme.LoheTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class CrashAndPlayStateRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun testEpisode(
        id: String,
        podcastId: String = "pod_1",
        isPlayed: Boolean = false,
        resumePositionMs: Long = 0L,
        durationMs: Long = 1_800_000L,
        inUpNext: Boolean = false
    ) = EpisodeEntity(
        id = id,
        guid = "guid_$id",
        podcastId = podcastId,
        podcastTitle = "Podcast $podcastId",
        title = "Episode $id",
        descriptionHtml = "<p>Notes for $id</p>",
        audioUrl = "https://example.com/$id.mp3",
        artworkUrl = "",
        publishedAt = 1_700_000_000_000L,
        durationMs = durationMs,
        resumePositionMs = resumePositionMs,
        isPlayed = isPlayed,
        inUpNext = inUpNext,
        upNextOrder = 0
    )

    @Test
    fun homeAudiogramStrip_rapidPlayPauseAndReducedMotionToggles_doNotCrash() {
        val isPlayingState = mutableStateOf(false)
        val reducedMotionState = mutableStateOf(false)

        composeTestRule.setContent {
            LoheTheme {
                HomeAudiogramStrip(
                    playedPercent = 52,
                    isPlaying = isPlayingState.value,
                    reducedMotion = reducedMotionState.value,
                    trackColor = androidx.compose.ui.graphics.Color.DarkGray
                )
            }
        }
        composeTestRule.waitForIdle()

        // Rapidly toggle play/pause and reducedMotion across recompositions
        // (regression guard for conditional rememberInfiniteTransition slot-table crash)
        repeat(6) { step ->
            isPlayingState.value = step % 2 == 0
            reducedMotionState.value = step % 3 == 0
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithTag("your_signal_audiogram").assertIsDisplayed()
    }

    @Test
    fun fullPlayerAndMiniPlayer_withNarrowWidthAndPositionExceedingDuration_doNotCrash() {
        val ep = testEpisode("ep_narrow", durationMs = 1_000_000L, resumePositionMs = 1_500_000L)
        val badState = PlaybackUiState(
            currentEpisode = ep,
            isPlaying = true,
            positionMs = 1_500_000L,
            durationMs = 0L
        )

        composeTestRule.setContent {
            LoheTheme {
                Box(modifier = Modifier.width(20.dp)) {
                    MiniPlayerBar(
                        playbackState = badState,
                        nextUpEpisode = null,
                        settings = LoheUserSettings(),
                        onTogglePlayPause = {},
                        onSkipBack = {},
                        onSkipForward = {},
                        onOpenFullPlayer = {},
                        onOpenNotes = {}
                    )
                    FullPlayerModalDialog(
                        playbackState = badState,
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
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_player_controls_card").performScrollTo().assertExists()
    }

    @Test
    fun repositoryEpisodeMutations_withStaleSnapshots_neverRevertPlayState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LoheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.loheDao()
            val repo = LoheRepository(dao)
            val staleUiSnapshot = testEpisode(
                id = "ep_live",
                isPlayed = false,
                resumePositionMs = 0L,
                inUpNext = false
            )
            dao.insertEpisode(staleUiSnapshot)

            // Playback progresses to 720_000ms in Room
            repo.updatePlaybackPosition("ep_live", positionMs = 720_000L, durationMs = 1_800_000L)

            // User stars, queues, unqueues, and downloads using the STALE pre-playback snapshot
            repo.toggleEpisodeStarred(staleUiSnapshot)
            repo.toggleEpisodeUpNext(staleUiSnapshot, addAtBeginning = true)
            repo.toggleEpisodeDownloaded(staleUiSnapshot)
            repo.removeEpisodesFromUpNext(listOf(staleUiSnapshot))

            val afterInProgressOps = dao.getEpisodeById("ep_live")!!
            assertFalse(afterInProgressOps.isPlayed)
            assertEquals(720_000L, afterInProgressOps.resumePositionMs)
            assertTrue(afterInProgressOps.isStarred)
            assertTrue(afterInProgressOps.isDownloaded)

            // Episode completes -> playedAt > 0, isPlayed = true
            repo.markEpisodePlayed("ep_live", played = true)
            val playedTs = dao.getEpisodeById("ep_live")!!.playedAt
            assertTrue(playedTs > 0L)

            // Subsequent metadata healing, starring, or download updates with stale snapshot must NOT unplay it
            repo.toggleEpisodeStarred(staleUiSnapshot)
            repo.upsertEpisodesPreservingUserState(
                podcastId = "pod_1",
                podcastTitle = "Podcast pod_1",
                incoming = listOf(staleUiSnapshot.copy(durationMs = 1_900_000L, isPlayed = false, resumePositionMs = 0L))
            )

            val afterPlayedOps = dao.getEpisodeById("ep_live")!!
            assertTrue(afterPlayedOps.isPlayed)
            assertEquals(playedTs, afterPlayedOps.playedAt)
            assertEquals(0L, afterPlayedOps.resumePositionMs)
        } finally {
            db.close()
        }
    }

    @Test
    fun loheViewModel_launchRestoration_honorsLastPlayingEpisodeId() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LoheDatabase.getInstance(app)
        val dao = db.loheDao()
        val prefs = LohePreferencesRepository(app)
        val store = ViewModelStore()

        val pod = PodcastEntity(
            id = "restore_pod",
            title = "Restore Show",
            author = "Host",
            description = "Desc",
            feedUrl = "https://example.com/restore.xml",
            artworkUrl = "",
            category = "Tech"
        )
        val epOtherInProgress = testEpisode(
            id = "ep_other_in_prog",
            podcastId = "restore_pod",
            isPlayed = false,
            resumePositionMs = 300_000L,
            inUpNext = true
        ).copy(publishedAt = 1_700_000_900_000L)
        val epLastPlayedSession = testEpisode(
            id = "ep_saved_last",
            podcastId = "restore_pod",
            isPlayed = false,
            resumePositionMs = 540_000L,
            inUpNext = false
        ).copy(publishedAt = 1_700_000_100_000L)

        try {
            runBlocking(Dispatchers.IO) {
                db.clearAllTables()
                dao.insertPodcast(pod)
                dao.insertEpisodes(listOf(epOtherInProgress, epLastPlayedSession))
                prefs.setLastPlayingEpisodeId("ep_saved_last")
            }

            val vm = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            )[LoheViewModel::class.java]

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.playbackState.value.currentEpisode != null
            }

            assertEquals("ep_saved_last", vm.playbackState.value.currentEpisode?.id)
            assertEquals(540_000L, vm.playbackState.value.positionMs)
        } finally {
            store.clear()
            runBlocking(Dispatchers.IO) {
                db.clearAllTables()
                prefs.setLastPlayingEpisodeId("")
            }
        }
    }
}
