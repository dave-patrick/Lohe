package com.lohe.app.data

import com.lohe.app.data.db.entity.EpisodeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaylistSnapshotTest {
    private fun episode(uuid: String, played: Boolean = false, archived: Boolean = false) = EpisodeSummary(
        uuid = uuid,
        podcastUuid = "show",
        title = uuid,
        audioUrl = "https://example.com/$uuid.mp3",
        published = 1L,
        playingStatus = if (played) EpisodeSummary.PLAYED else EpisodeSummary.UNPLAYED,
        isArchived = archived,
    )

    @Test
    fun `snapshot preserves explicit order while excluding played and archived entries`() {
        val episodes = listOf(episode("unplayed"), episode("second"), episode("played", played = true), episode("archived", archived = true))
            .associateBy { it.uuid }
        val playlist = UserPlaylist("snapshot", "Mirror", listOf("second", "archived", "missing", "played", "unplayed"), snapshot = true)
        assertEquals(listOf("second", "unplayed"), playlist.resolveExplicitEpisodes(episodes).map { it.uuid })
        assertEquals(listOf("second", "unplayed"), playlist.copy(snapshot = false).resolveExplicitEpisodes(episodes).map { it.uuid })
        assertEquals(listOf("played"), playlist.copy(snapshot = false, finished = true).resolveExplicitEpisodes(episodes).map { it.uuid })
    }

    @Test
    fun `snapshot persists and removes completed episodes`() {
        val file = File.createTempFile("playlist-snapshot", ".tsv")
        try {
            val repository = PlaylistRepository(file)
            val snapshot = repository.createPlaylist("Mirror", listOf("episode"), snapshot = true)
            val normal = repository.createPlaylist("Normal", listOf("episode"), finished = true)
            repository.removeEpisodeFromAll("episode")
            val restored = PlaylistRepository(file).playlists.value.associateBy { it.id }
            assertTrue(restored.getValue(snapshot.id).snapshot)
            assertTrue(restored.getValue(snapshot.id).episodeUuids.isEmpty())
            assertTrue(restored.getValue(normal.id).finished)
            assertTrue(restored.getValue(normal.id).episodeUuids.isEmpty())
            repository.removeEpisode(snapshot.id, "episode")
            assertTrue(repository.playlists.value.first { it.id == snapshot.id }.episodeUuids.isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `legacy playlists default to normal filtering`() {
        val file = File.createTempFile("playlist-legacy", ".tsv")
        try {
            file.writeText("legacy\t1\tTGVnYWN5\tepisode\t\t0\t\t0\t0\t1")
            val playlist = PlaylistRepository(file).playlists.value.single()
            assertFalse(playlist.snapshot)
            assertTrue(playlist.finished)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `legacy smart playlist duplicates are removed while user playlists remain`() {
        val file = File.createTempFile("playlist-smart-cleanup", ".tsv")
        try {
            file.writeText(
                listOf(
                    "legacy-played\t1\tUGxheWVk\tepisode\t\t0\t\t0\t0\t1",
                    "legacy-unplayed\t2\tVW5wbGF5ZWQ=\tepisode2\t\t0\t\t0\t0\t0",
                    "custom\t3\tTW9ybmluZyBsaXN0\tepisode3\t\t0\t\t0\t0\t0",
                ).joinToString("\n"),
            )
            val repository = PlaylistRepository(file)
            assertEquals(listOf("Morning list"), repository.playlists.value.map { it.name })
            assertEquals(listOf("Morning list"), PlaylistRepository(file).playlists.value.map { it.name })
        } finally {
            file.delete()
        }
    }
}
