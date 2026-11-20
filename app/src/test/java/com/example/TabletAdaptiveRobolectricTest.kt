package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.edition.Edition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h1200dp")
class TabletAdaptiveRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `verify tablet NavigationRail, two-column Home, and two-pane Podcasts, Playlists, and Settings`() {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("tablet_navigation_rail").fetchSemanticsNodes().isNotEmpty()
        }

        // Tablet Home: NavigationRail instead of bottom nav scrim, two-column dashboard
        composeTestRule.onNodeWithTag("tablet_navigation_rail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_scrim_container").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_two_column_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("your_signal_card").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("top_settings_button").assertCountEquals(1)

        // Tablet Podcasts: two-pane master-detail side-by-side without duplicate Settings gear
        composeTestRule.onNodeWithTag("tab_podcasts").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("podcasts_tab_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("podcasts_two_pane_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("podcast_card_sample_pod_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("podcast_detail_screen").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("top_settings_button").assertCountEquals(1)

        // Tablet Playlists: two-pane master-detail side-by-side
        composeTestRule.onNodeWithTag("tab_playlists").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("playlists_tab_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("playlists_two_pane_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("playlist_card_smart_everything").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("playlist_detail_screen").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("top_settings_button").assertCountEquals(1)

        // Tablet Settings: two-pane overview + detail simultaneously visible
        composeTestRule.onNodeWithTag("top_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_two_pane_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_overview_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("up_next_indicator_switch").assertIsDisplayed()

        // Switch right pane in-place while overview remains visible
        if (Edition.HAS_PRO) {
            composeTestRule.onNodeWithTag("indicator_style_pill").assertExists()
            composeTestRule.onNodeWithTag("indicator_style_diamond").assertExists()
            composeTestRule.onNodeWithTag("indicator_style_glow").assertExists()
            composeTestRule.onNodeWithTag("appearance_settings_list")
                .performScrollToNode(hasTestTag("custom_hex_input"))
            composeTestRule.onNodeWithTag("custom_hex_input").assertIsDisplayed()

            composeTestRule.onNodeWithTag("settings_item_floating_player").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("settings_overview_list").assertIsDisplayed()
            composeTestRule.onNodeWithTag("floating_player_switch").assertIsDisplayed()
            composeTestRule.onNodeWithTag("floating_overlay_permission_button").assertIsDisplayed()

            composeTestRule.onNodeWithTag("settings_overview_list")
                .performScrollToNode(hasTestTag("settings_item_cloud_sync"))
            composeTestRule.onNodeWithTag("settings_item_cloud_sync").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("cloud_sync_auto_switch").assertIsDisplayed()
            composeTestRule.onNodeWithTag("cloud_sync_unlock_pro_button").assertIsDisplayed()
        } else {
            composeTestRule.onNodeWithTag("settings_pro_banner").assertDoesNotExist()
            composeTestRule.onNodeWithTag("indicator_style_pill").assertDoesNotExist()
            composeTestRule.onNodeWithTag("indicator_style_diamond").assertDoesNotExist()
            composeTestRule.onNodeWithTag("indicator_style_glow").assertDoesNotExist()
            composeTestRule.onNodeWithTag("custom_hex_input").assertDoesNotExist()
            composeTestRule.onNodeWithTag("settings_item_floating_player").assertDoesNotExist()
            composeTestRule.onNodeWithTag("settings_item_cloud_sync").assertDoesNotExist()
        }
    }
}
