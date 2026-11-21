package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.example.data.local.EpisodeEntity
import com.example.data.local.EpisodeSwipeAction
import com.example.data.local.LoheUserSettings
import com.example.ui.components.SwipeableEpisodeRow
import com.example.ui.components.SwipeableUpNextRow
import com.example.ui.theme.LoheTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class SwipeActionsAndPillTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleEpisode(id: String = "ep_swipe_1") = EpisodeEntity(
        id = id,
        podcastId = "pod_1",
        podcastTitle = "Test Podcast",
        title = "Swipe Gesture Episode",
        descriptionHtml = "",
        audioUrl = "https://example.com/ep.mp3",
        artworkUrl = "",
        publishedAt = 1_750_000_000_000L,
        durationMs = 1_800_000L
    )

    @Test
    fun episodeRow_slideRight_revealsTopAndBottomUpNextChoices_andSlideLeft_marksPlayed() {
        val triggered = mutableListOf<EpisodeSwipeAction>()
        val ep = sampleEpisode("ep_swipe_1")

        composeTestRule.setContent {
            LoheTheme {
                SwipeableEpisodeRow(
                    episode = ep,
                    settings = LoheUserSettings(),
                    onSwipeAction = { _, action -> triggered.add(action) }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .testTag("ep_card_surface")
                    ) {
                        Box { Text(ep.title) }
                    }
                }
            }
        }

        // Slide right to reveal Top and Bottom of Up Next buttons
        composeTestRule.onNodeWithTag("ep_card_surface").performTouchInput {
            swipeRight(startX = 40f, endX = 360f, durationMillis = 150L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_right_up_next_top_ep_swipe_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("swipe_right_up_next_bottom_ep_swipe_1").assertIsDisplayed()

        // Tap Bottom -> triggers PLAY_LAST
        composeTestRule.onNodeWithTag("swipe_right_up_next_bottom_ep_swipe_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(EpisodeSwipeAction.PLAY_LAST), triggered)

        // Slide right again and tap Top -> triggers PLAY_NEXT
        composeTestRule.onNodeWithTag("ep_card_surface").performTouchInput {
            swipeRight(startX = 40f, endX = 360f, durationMillis = 150L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_right_up_next_top_ep_swipe_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(EpisodeSwipeAction.PLAY_LAST, EpisodeSwipeAction.PLAY_NEXT), triggered)

        // Slide left moderately to reveal Mark Played button and tap it
        composeTestRule.onNodeWithTag("ep_card_surface").performTouchInput {
            swipeLeft(startX = 320f, endX = 210f, durationMillis = 350L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_left_mark_played_ep_swipe_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("swipe_left_mark_played_ep_swipe_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            listOf(EpisodeSwipeAction.PLAY_LAST, EpisodeSwipeAction.PLAY_NEXT, EpisodeSwipeAction.MARK_PLAYED),
            triggered
        )

        // Full deliberate swipe left quick-fires Mark Played immediately
        composeTestRule.onNodeWithTag("ep_card_surface").performTouchInput {
            swipeLeft(startX = 390f, endX = 40f, durationMillis = 150L)
        }
        composeTestRule.waitForIdle()
        assertEquals(
            listOf(
                EpisodeSwipeAction.PLAY_LAST,
                EpisodeSwipeAction.PLAY_NEXT,
                EpisodeSwipeAction.MARK_PLAYED,
                EpisodeSwipeAction.MARK_PLAYED
            ),
            triggered
        )
    }

    @Test
    fun upNextRow_slideRight_movesTopOrBottom_andSlideLeft_marksPlayedOrRemoves() {
        val events = mutableListOf<String>()
        val ep = sampleEpisode("ep_queue_1")

        composeTestRule.setContent {
            LoheTheme {
                SwipeableUpNextRow(
                    episode = ep,
                    onMoveToTop = { events.add("top") },
                    onMoveToBottom = { events.add("bottom") },
                    onMarkPlayed = { events.add("played") },
                    onRemoveFromQueue = { events.add("remove") }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .testTag("up_next_card_surface")
                    ) {
                        Box { Text(ep.title) }
                    }
                }
            }
        }

        // Slide right reveals Move Top & Move Bottom
        composeTestRule.onNodeWithTag("up_next_card_surface").performTouchInput {
            swipeRight(startX = 40f, endX = 360f, durationMillis = 150L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("up_next_swipe_top_ep_queue_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("up_next_swipe_bottom_ep_queue_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("up_next_swipe_top_ep_queue_1").performClick()
        composeTestRule.waitForIdle()

        // Slide right again and tap Bottom
        composeTestRule.onNodeWithTag("up_next_card_surface").performTouchInput {
            swipeRight(startX = 40f, endX = 360f, durationMillis = 150L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("up_next_swipe_bottom_ep_queue_1").performClick()
        composeTestRule.waitForIdle()

        // Slide left reveals Played & Remove
        composeTestRule.onNodeWithTag("up_next_card_surface").performTouchInput {
            swipeLeft(startX = 360f, endX = 40f, durationMillis = 150L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("up_next_swipe_played_ep_queue_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("up_next_swipe_remove_ep_queue_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("up_next_swipe_played_ep_queue_1").performClick()
        composeTestRule.waitForIdle()

        // Slide left again and tap Remove
        composeTestRule.onNodeWithTag("up_next_card_surface").performTouchInput {
            swipeLeft(startX = 360f, endX = 40f, durationMillis = 150L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("up_next_swipe_remove_ep_queue_1").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("top", "bottom", "played", "remove"), events)
    }
}
