package com.example

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.EpisodeDownloadManager
import com.example.data.local.EpisodeEntity
import com.example.data.network.RssAndOpmlParser
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `verify Lohe app_name and tagline resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Lohe", context.getString(R.string.app_name))
        assertEquals("ua lohe ‘oe? • Have you heard?", context.getString(R.string.tagline))
    }

    @Test
    fun `verify OPML and JSON backup parser`() {
        val parser = RssAndOpmlParser()
        val sampleOpml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <body>
                <outline text="Tech" title="Tech">
                  <outline type="rss" text="FOSS Craft" title="FOSS Craft" xmlUrl="https://example.org/foss.xml" />
                </outline>
              </body>
            </opml>
        """.trimIndent()
        val outlines = parser.parseOpml(sampleOpml)
        assertEquals(1, outlines.size)
        assertEquals("FOSS Craft", outlines[0].title)
        assertEquals("https://example.org/foss.xml", outlines[0].xmlUrl)
    }

    @Test
    fun `verify 5-tab navigation, single Settings gear, Playlist & Podcast detail, Appearance & About`() {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("your_signal_card").fetchSemanticsNodes().isNotEmpty()
        }

        // Home screen branding, Kani hero & Your Signal card
        composeTestRule.onNodeWithTag("home_branding_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_kani_mascot").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_lohe_wordmark_animated").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_kani_hero_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("your_signal_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("your_signal_in_progress_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_scrim_container").assertIsDisplayed()
        composeTestRule.onNodeWithText("ua lohe ‘oe? • Have you heard?").assertDoesNotExist()
        composeTestRule.onNodeWithText("v0.12.0").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("top_settings_button").assertCountEquals(1)

        // Podcasts tab & Detail screen
        composeTestRule.onNodeWithTag("tab_podcasts").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("podcasts_tab_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("podcasts_refresh_feeds_button").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("top_settings_button").assertCountEquals(1)
        composeTestRule.onNodeWithTag("podcast_card_sample_pod_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("podcast_detail_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("podcast_detail_back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("podcast_auto_queue_card").assertExists()
        composeTestRule.onNodeWithTag("podcast_detail_back").performClick()
        composeTestRule.waitForIdle()

        // Discover tab
        composeTestRule.onNodeWithTag("tab_discover").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("discover_tab_screen").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("top_settings_button").assertCountEquals(1)

        // Playlists tab & Detail screen
        composeTestRule.onNodeWithTag("tab_playlists").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("playlists_tab_screen").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("top_settings_button").assertCountEquals(1)
        composeTestRule.onNodeWithTag("playlist_card_smart_everything").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("playlist_detail_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("playlist_detail_back").performClick()
        composeTestRule.waitForIdle()

        // Up Next tab
        composeTestRule.onNodeWithTag("tab_up_next").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("up_next_tab_screen").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("top_settings_button").assertCountEquals(1)

        // Settings -> Appearance
        composeTestRule.onNodeWithTag("top_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_item_appearance").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("up_next_indicator_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("appearance_settings_list")
            .performScrollToNode(hasTestTag("indicator_size_medium"))
        composeTestRule.onNodeWithTag("indicator_size_medium").performClick()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // Settings -> Floating player
        composeTestRule.onNodeWithTag("settings_item_floating_player").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_player_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("floating_overlay_permission_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // Settings -> Downloads
        composeTestRule.onNodeWithTag("settings_item_downloads").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("downloads_refresh_feeds_button").assertExists()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // Settings -> Cloud Sync
        composeTestRule.onNodeWithTag("settings_overview_list")
            .performScrollToNode(hasTestTag("settings_item_cloud_sync"))
        composeTestRule.onNodeWithTag("settings_item_cloud_sync").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cloud_sync_auto_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_unlock_pro_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // Settings -> About
        composeTestRule.onNodeWithTag("settings_overview_list")
            .performScrollToNode(hasTestTag("settings_item_about"))
        composeTestRule.onNodeWithTag("settings_item_about").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("about_kani_hero").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_kani_animation_webp").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_lohe_wordmark").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_version_9sp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Version 0.12.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Build 2026.12.11.110", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("about_tagline_box").assertIsDisplayed()
        composeTestRule.onNodeWithText("ua lohe ‘oe? • Have you heard?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_privacy_policy_link").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_sub_screen")
            .performScrollToNode(hasTestTag("about_atomic22_logo"))
        composeTestRule.onNodeWithTag("about_atomic22_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_sub_screen")
            .performScrollToNode(hasTestTag("about_pro_card"))
        composeTestRule.onNodeWithTag("about_pro_card").assertIsDisplayed()
        assertTrue(true)
    }

    @Test
    fun `verify EpisodeDownloadManager resolves cached local file URI before remote URL`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = EpisodeDownloadManager(context)
        val ep = EpisodeEntity(
            id = "ep_local_test_1",
            podcastId = "pod_test",
            podcastTitle = "Test Show",
            title = "Offline Episode",
            descriptionHtml = "<p>Test description</p>",
            audioUrl = "https://cdn.example.org/ep1.mp3",
            artworkUrl = "https://cdn.example.org/art.png",
            publishedAt = 1_700_000_000_000L,
            durationMs = 60_000L
        )
        assertEquals("https://cdn.example.org/ep1.mp3", manager.resolvePlayableUri(ep))

        val cachedFile = manager.fileForEpisode(ep.id)
        cachedFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        try {
            val expectedUri = Uri.fromFile(cachedFile).toString()
            assertEquals(expectedUri, manager.resolvePlayableUri(ep.copy(localFilePath = cachedFile.absolutePath)))
        } finally {
            cachedFile.delete()
        }
    }
}
