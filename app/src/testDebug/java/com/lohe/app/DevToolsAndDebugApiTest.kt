package com.lohe.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.lohe.app.data.local.EpisodeEntity
import com.lohe.app.data.local.LoheDatabase
import com.lohe.app.data.local.LohePreferencesRepository
import com.lohe.app.dev.DebugDevTools
import com.lohe.app.dev.DiagnosticsReportManager
import com.lohe.app.dev.LocalDebugApiServer
import com.lohe.app.edition.DevTools
import com.lohe.app.playback.PlaybackUiState
import org.json.JSONObject
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
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevToolsAndDebugApiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun getContext(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun diagnostics_offByDefault_showsFloatingBugButtonWhenToggled_redactsUrlsAndKeepsMax5Folders() {
        composeTestRule.waitForIdle()
        val devTools = DevTools.current
        assertTrue("DebugDevTools should register itself at startup in debug builds", devTools.isAvailable)
        devTools.setDiagnosticsEnabled(false)
        composeTestRule.waitForIdle()

        // Floating BUG button is hidden when Diagnostics is off (default)
        composeTestRule.onNodeWithTag("diagnostics_floating_bug_button").assertDoesNotExist()

        // Navigate to Settings > Diagnostics and toggle it on
        composeTestRule.onNodeWithTag("top_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_overview_list")
            .performScrollToNode(hasTestTag("settings_item_diagnostics"))
        composeTestRule.onNodeWithTag("settings_item_diagnostics").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("diagnostics_toggle_switch").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(devTools.diagnosticsEnabled.value)

        // Floating BUG button should now be visible
        composeTestRule.onNodeWithTag("diagnostics_floating_bug_button").assertIsDisplayed()

        // Record an event containing sensitive URLs and verify redaction + max 5 folders cap
        val secretFeedUrl = "https://private.podcast.example.org/secret-token-123/feed.xml"
        val secretAudioUrl = "https://cdn.podcast.example.org/private/audio_99.mp3"
        devTools.recordEvent("network", "Fetched feed from $secretFeedUrl and audio $secretAudioUrl")

        // Tap floating BUG button once via UI
        composeTestRule.onNodeWithTag("diagnostics_floating_bug_button").performClick()
        composeTestRule.waitForIdle()

        // Record 6 more reports (7 total) to verify retention cap of at most 5 folders
        val debugImpl = devTools as DebugDevTools
        repeat(6) { idx ->
            debugImpl.reportManager.recordBugReport(
                PlaybackUiState(
                    isPlaying = true,
                    positionMs = 42_000L + idx,
                    durationMs = 600_000L,
                    currentEpisode = EpisodeEntity(
                        id = "ep_diag_$idx",
                        podcastId = "pod_diag",
                        podcastTitle = "Diag Show",
                        title = "Episode $idx from $secretFeedUrl",
                        descriptionHtml = "",
                        audioUrl = secretAudioUrl,
                        artworkUrl = "https://cdn.podcast.example.org/art.png",
                        publishedAt = 1710000000000L,
                        durationMs = 600_000L
                    )
                )
            )
        }

        val folders = devTools.listReportFolders()
        assertEquals("Must keep at most 5 report folders", DiagnosticsReportManager.MAX_REPORT_FOLDERS, folders.size)

        val latestFolder = folders.last()
        val reportJsonText = File(latestFolder, "report.json").readText()
        val eventsLogText = File(latestFolder, "events.log").readText()
        val playbackJsonText = File(latestFolder, "playback_state.json").readText()
        val deviceInfoText = File(latestFolder, "device_info.json").readText()

        assertFalse("URLs must be redacted from report.json", reportJsonText.contains("private.podcast.example.org"))
        assertFalse("URLs must be redacted from events.log", eventsLogText.contains("cdn.podcast.example.org"))
        assertFalse("URLs must be redacted from playback_state.json", playbackJsonText.contains("audio_99.mp3"))
        assertTrue("Redacted placeholder should be present", reportJsonText.contains(DiagnosticsReportManager.REDACTED_URL_TOKEN))
        assertTrue("Device info should include sdkInt", JSONObject(deviceInfoText).optInt("sdkInt") > 0)
    }

    @Test
    fun localDebugApi_enforcesTokenBeforeReadingBody_limits1MiB_mcpGet405_andServesRestAndMcpEndpoints() {
        val context = getContext()
        val database = LoheDatabase.getInstance(context)
        val prefs = LohePreferencesRepository(context)
        val server = LocalDebugApiServer(
            context = context,
            databaseProvider = { database },
            preferencesProvider = { prefs },
            playerControllerProvider = { null }
        )

        val port = server.start(preferredPort = 0)
        try {
            assertTrue("Server must bind to a valid local port", port > 0)
            val tokenFile = File(context.noBackupFilesDir, "debug-api-token")
            assertTrue("Token file must exist in noBackupFilesDir/debug-api-token", tokenFile.exists())
            val token = tokenFile.readText().trim()
            assertTrue("Token must be non-empty", token.length >= 32)

            // 1. Unauthorized POST with body: token must be checked BEFORE reading the request body
            Socket("127.0.0.1", port).use { rawSocket ->
                rawSocket.soTimeout = 5_000
                val payload = """{"action":"play"}"""
                val rawRequest = buildString {
                    append("POST /playback HTTP/1.1\r\n")
                    append("Host: 127.0.0.1:$port\r\n")
                    append("Authorization: Bearer wrong-token\r\n")
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: ${payload.length}\r\n")
                    append("\r\n")
                    append(payload)
                }
                rawSocket.getOutputStream().write(rawRequest.toByteArray(Charsets.UTF_8))
                rawSocket.getOutputStream().flush()
                val statusLine = rawSocket.getInputStream().bufferedReader().readLine()
                assertTrue("Expected 401 Unauthorized but got: $statusLine", statusLine.contains("401"))
                assertEquals(
                    "Server must check token BEFORE reading any bytes of the request body",
                    0,
                    server.lastRequestBodyBytesRead.get()
                )
            }

            // 2. MCP GET returns 405 Method Not Allowed
            val mcpGetConn = (URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            assertEquals(405, mcpGetConn.responseCode)
            mcpGetConn.disconnect()

            // 3. Request exceeding 1 MiB returns 413 Payload Too Large
            Socket("127.0.0.1", port).use { rawSocket ->
                rawSocket.soTimeout = 5_000
                val oversizeLength = LocalDebugApiServer.MAX_REQUEST_BODY_BYTES + 1024
                val rawHeaders = buildString {
                    append("POST /import/pocketcasts HTTP/1.1\r\n")
                    append("Host: 127.0.0.1:$port\r\n")
                    append("Authorization: Bearer $token\r\n")
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: $oversizeLength\r\n")
                    append("\r\n")
                }
                rawSocket.getOutputStream().write(rawHeaders.toByteArray(Charsets.UTF_8))
                rawSocket.getOutputStream().flush()
                val statusLine = rawSocket.getInputStream().bufferedReader().readLine()
                assertTrue("Expected 413 Payload Too Large but got: $statusLine", statusLine.contains("413"))
                assertEquals(0, server.lastRequestBodyBytesRead.get())
            }

            // 4. REST: Import Pocket Casts JSON export
            val pocketCastsExport = JSONObject().apply {
                put(
                    "podcasts",
                    org.json.JSONArray().put(
                        JSONObject().apply {
                            put("uuid", "pc-show-uuid-1")
                            put("title", "Pocket Casts Signal Daily")
                            put("author", "PC Host")
                            put("url", "https://pc.example.org/signal.xml")
                        }
                    )
                )
                put(
                    "episodes",
                    org.json.JSONArray().put(
                        JSONObject().apply {
                            put("uuid", "pc-ep-guid-1")
                            put("podcastUuid", "pc-show-uuid-1")
                            put("title", "PC Episode 1")
                            put("url", "https://pc.example.org/ep1.mp3")
                            put("duration", 1800)
                            put("playedUpTo", 600)
                            put("playingStatus", 2)
                        }
                    )
                )
            }
            val importResp = httpPostJson("http://127.0.0.1:$port/import/pocketcasts", token, pocketCastsExport.toString())
            assertTrue(importResp.optBoolean("ok"))
            assertEquals(1, importResp.optInt("showsAdded"))
            assertEquals(1, importResp.optInt("episodesUpdated"))

            // 5. REST: Read library, queue, playback state, and settings
            val libraryResp = httpGetJson("http://127.0.0.1:$port/library", token)
            assertTrue(libraryResp.getJSONArray("podcasts").length() >= 1)
            assertTrue(libraryResp.getJSONArray("episodes").length() >= 1)

            val queueResp = httpGetJson("http://127.0.0.1:$port/queue", token)
            assertNotNull(queueResp.optJSONArray("queue"))

            val settingsResp = httpGetJson("http://127.0.0.1:$port/settings", token)
            assertTrue(settingsResp.optDouble("playbackSpeed", 0.0) > 0.0)

            // 6. REST: Control playback
            val playControlResp = httpPostJson(
                "http://127.0.0.1:$port/playback",
                token,
                """{"action":"play","positionMs":125000}"""
            )
            assertTrue(playControlResp.optBoolean("ok"))
            assertTrue(playControlResp.optBoolean("isPlaying"))
            assertEquals(125_000L, playControlResp.optLong("positionMs"))

            val playbackGetResp = httpGetJson("http://127.0.0.1:$port/playback", token)
            assertTrue(playbackGetResp.optBoolean("isPlaying"))

            // 7. MCP JSON-RPC over POST (/mcp)
            val mcpToolsList = httpPostJson(
                "http://127.0.0.1:$port/mcp",
                token,
                """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
            )
            val toolsArr = mcpToolsList.getJSONObject("result").getJSONArray("tools")
            assertEquals(6, toolsArr.length())

            val mcpCallPlayback = httpPostJson(
                "http://127.0.0.1:$port/mcp",
                token,
                """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"control_playback","arguments":{"action":"pause"}}}"""
            )
            val mcpResult = mcpCallPlayback.getJSONObject("result")
            assertFalse(mcpResult.optBoolean("isPlaying", true))
        } finally {
            server.stop()
        }
    }

    private fun httpGetJson(url: String, token: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            assertEquals(200, conn.responseCode)
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPostJson(url: String, token: String, jsonBody: String): JSONObject {
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            conn.outputStream.use { it.write(bytes) }
            assertEquals(200, conn.responseCode)
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
