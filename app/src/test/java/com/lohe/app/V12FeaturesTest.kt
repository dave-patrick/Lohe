package com.lohe.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import com.lohe.app.data.network.PodcastingChaptersAndTranscripts
import com.lohe.app.data.network.RssAndOpmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class V12FeaturesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `verify Podcasting 2_0 RSS chapters and VTT JSON SRT transcript parsers`() {
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:podcast="https://podcastindex.org/namespace/1.0">
              <channel>
                <title>Podcasting 2.0 Test Feed</title>
                <item>
                  <title>Episode 100</title>
                  <enclosure url="https://example.org/ep100.mp3" type="audio/mpeg" />
                  <podcast:chapters url="https://example.org/ep100/chapters.json" type="application/json+chapters" />
                  <podcast:transcript url="https://example.org/ep100/transcript.vtt" type="text/vtt" />
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val (_, episodes) = RssAndOpmlParser().parseRssFeedXml(rssXml, "https://example.org/feed.xml")
        assertEquals(1, episodes.size)
        assertEquals("https://example.org/ep100/chapters.json", episodes[0].chaptersUrl)
        assertEquals("https://example.org/ep100/transcript.vtt", episodes[0].transcriptUrl)
        assertEquals("text/vtt", episodes[0].transcriptMimeType)

        val chapters = PodcastingChaptersAndTranscripts.parsePodcastChaptersJson(
            """{"version":"1.2.0","chapters":[{"startTime":0,"title":"Intro","url":"https://example.org/intro","img":"https://example.org/intro.jpg"},{"startTime":42.5,"title":"Main Topic"}]}"""
        )
        assertEquals(2, chapters.size)
        assertEquals("https://example.org/intro", chapters[0].url)
        assertEquals("https://example.org/intro.jpg", chapters[0].imageUrl)
        assertEquals("Main Topic", chapters[1].title)
        assertEquals(42_500L, chapters[1].startTimeMs)

        val vttCues = PodcastingChaptersAndTranscripts.parseTranscriptContent(
            """
            WEBVTT

            00:00:01.000 --> 00:00:05.000
            <v Alice>Hello listeners!

            00:00:06.000 --> 00:00:12.000
            Welcome to Podcasting 2.0.
            """.trimIndent(),
            mimeTypeHint = "text/vtt"
        )
        assertEquals(2, vttCues.size)
        assertEquals("Alice", vttCues[0].speaker)
        assertTrue(vttCues[0].text.contains("Hello listeners"))

        val srtCues = PodcastingChaptersAndTranscripts.parseTranscriptContent(
            """
            1
            00:00:02,000 --> 00:00:06,500
            SubRip line one
            """.trimIndent(),
            mimeTypeHint = "application/x-subrip"
        )
        assertEquals(1, srtCues.size)
        assertEquals(2000L, srtCues[0].startTimeMs)
    }

    @Test
    fun `verify per-show audio overrides, Podcasting 2_0 Chapters & Transcript sheet, and Listening Stats`() {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("mini_player_bar").fetchSemanticsNodes().isNotEmpty()
        }

        // Home Listening Activity summary card shows streak & saved mins
        composeTestRule.onNodeWithTag("listening_activity_card").assertIsDisplayed()

        // Open Full Player -> verify active Podcasting 2.0 chapter bar & End of Ep sleep timer -> bookmark -> open Notes/Chapters/Transcript/Bookmarks sheet
        composeTestRule.onNodeWithTag("mini_player_bar").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_player_active_chapter").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sleep_timer_end_of_ep").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_player_save_bookmark_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bookmark_note_input").performScrollTo().performTextInput("Key takeaway")
        composeTestRule.onNodeWithTag("save_bookmark_confirm_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("full_player_open_notes_sheet").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("episode_notes_sheet").assertIsDisplayed()

        // Chapters tab + chapter link
        composeTestRule.onNodeWithTag("notes_tab_chapters").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chapter_row_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chapter_link_0").assertIsDisplayed()

        // Transcript tab + keyword search filter
        composeTestRule.onNodeWithTag("notes_tab_transcript").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("transcript_search_input").performTextInput("dragonfly")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("transcript_cue_0").assertIsDisplayed()
        composeTestRule.onNodeWithText("acoustic lead", substring = true, ignoreCase = true).assertIsDisplayed()

        // Bookmarks tab
        composeTestRule.onNodeWithTag("notes_tab_bookmarks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bookmark_row_0").assertIsDisplayed()

        // Dismiss sheet & Full Player
        composeTestRule.onNodeWithContentDescription("Close Notes").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_player_close_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("full_player_modal").fetchSemanticsNodes().isEmpty()
        }

        // Discover Trending chip
        composeTestRule.onNodeWithTag("tab_discover").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("discover_trending_chip").assertIsDisplayed()

        // Check Per-Show Audio Overrides & New Ep Alerts inside Podcasts detail screen
        composeTestRule.onNodeWithTag("tab_podcasts").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("podcast_card_sample_pod_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("podcast_notify_switch").assertExists()
        composeTestRule.onNodeWithTag("podcast_custom_audio_card").assertExists()
        composeTestRule.onNodeWithTag("podcast_detail_list").performScrollToIndex(2)
        composeTestRule.onNodeWithTag("podcast_custom_audio_switch").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("podcast_speed_1.5").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("podcast_detail_list").performScrollToIndex(2)
        composeTestRule.onNodeWithTag("podcast_speed_1.5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("podcast_speed_1.5").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithText("Custom 1.5x", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("podcast_detail_back").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("podcasts_tab_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("podcasts_tab_screen").assertIsDisplayed()

        // Check Downloads -> Background sync & Storage Breakdown card
        composeTestRule.onNodeWithTag("top_settings_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("settings_overview_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("settings_overview_list")
            .performScrollToNode(hasTestTag("settings_item_downloads"))
        composeTestRule.onNodeWithTag("settings_item_downloads").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("downloads_settings_list")
            .performScrollToNode(hasTestTag("new_episode_notifications_switch"))
        composeTestRule.onNodeWithTag("new_episode_notifications_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("background_sync_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("downloads_settings_list")
            .performScrollToNode(hasTestTag("storage_breakdown_card"))
        composeTestRule.onNodeWithTag("storage_breakdown_card").assertIsDisplayed()
    }
}
