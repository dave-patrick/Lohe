package com.lohe.app.ui.player

import com.lohe.app.data.PlaylistRepository
import com.lohe.app.data.fetch.FeedParser
import com.lohe.app.ui.navigation.formatDashboardDuration
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PlaylistAndPlayerMathTest {

    @Test
    fun `adjustedRemainingMs scales remaining time by playback speed`() {
        // 30 minutes left (1,800,000 ms) at 1.5x -> 20 minutes real time (1,200,000 ms)
        val remaining = adjustedRemainingMs(
            positionMs = 900_000L,
            durationMs = 2_700_000L,
            speed = 1.5f,
        )
        assertEquals(1_200_000L, remaining)
    }

    @Test
    fun `adjustedElapsedMs scales elapsed time by playback speed`() {
        // 15 minutes into file (900,000 ms) at 1.5x -> 10 minutes real time (600,000 ms)
        val elapsed = adjustedElapsedMs(
            positionMs = 900_000L,
            speed = 1.5f,
        )
        assertEquals(600_000L, elapsed)
    }

    @Test
    fun `formatSpeedLabel formats integer, tenth, and hundredth speeds`() {
        assertEquals("1×", formatSpeedLabel(1.0f))
        assertEquals("1.5×", formatSpeedLabel(1.5f))
        assertEquals("1.25×", formatSpeedLabel(1.25f))
    }

    @Test
    fun `formatDashboardDuration formats hours and minutes cleanly`() {
        assertEquals("0m", formatDashboardDuration(0L))
        assertEquals("45s", formatDashboardDuration(45L))
        assertEquals("12m 30s", formatDashboardDuration(750L))
        assertEquals("2h 15m", formatDashboardDuration(8_100L))
    }

    @Test
    fun `PlaylistRepository persists and modifies playlists`() {
        val tempFile = File.createTempFile("lohe-playlists-test", ".json")
        tempFile.deleteOnExit()
        try {
            val repo = PlaylistRepository(tempFile)
            val created = repo.createPlaylist("Game Day", listOf("ep-1", "ep-2"))
            assertEquals("Game Day", created.name)
            assertEquals(listOf("ep-1", "ep-2"), created.episodeUuids)

            repo.addEpisodes(created.id, listOf("ep-2", "ep-3"))
            assertEquals(
                listOf("ep-1", "ep-2", "ep-3"),
                repo.playlists.value.single().episodeUuids,
            )

            repo.removeEpisode(created.id, "ep-2")
            assertEquals(
                listOf("ep-1", "ep-3"),
                repo.playlists.value.single().episodeUuids,
            )

            // Reload from disk to verify persistence
            val reloaded = PlaylistRepository(tempFile)
            assertEquals(1, reloaded.playlists.value.size)
            assertEquals("Game Day", reloaded.playlists.value.single().name)
            assertEquals(listOf("ep-1", "ep-3"), reloaded.playlists.value.single().episodeUuids)

            reloaded.deletePlaylist(created.id)
            assertEquals(0, reloaded.playlists.value.size)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `FeedParser resolves item artwork and falls back to channel artwork`() {
        val xml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Show</title>
                <itunes:image href="https://example.com/show.jpg"/>
                <item>
                  <title>Ep With Own Art</title>
                  <guid>1</guid>
                  <itunes:image href="https://example.com/ep1.jpg"/>
                  <enclosure url="https://example.com/1.mp3" type="audio/mpeg"/>
                </item>
                <item>
                  <title>Ep Without Own Art</title>
                  <guid>2</guid>
                  <enclosure url="https://example.com/2.mp3" type="audio/mpeg"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser().parse("https://example.com/feed.xml", xml)
        assertEquals(2, parsed.episodes.size)
        assertEquals("https://example.com/ep1.jpg", parsed.episodes[0].artworkUrl)
        assertEquals("https://example.com/show.jpg", parsed.episodes[1].artworkUrl)
    }
}
