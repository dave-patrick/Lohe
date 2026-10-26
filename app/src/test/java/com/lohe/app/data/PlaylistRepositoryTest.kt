package com.lohe.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaylistRepositoryTest {

    @Test
    fun followedShowsSurviveARestart() {
        val dir = createTempDir()
        val file = File(dir, "playlists.json")
        val repo = PlaylistRepository(file)
        val playlist = repo.createPlaylist("Morning")
        repo.setPlaylistPodcasts(playlist.id, listOf("show-a", "show-b", "show-a"))

        val reloaded = PlaylistRepository(file).playlists.value.single()
        assertEquals(listOf("show-a", "show-b"), reloaded.podcastUuids)
        dir.deleteRecursively()
    }

    @Test
    fun removingAnEpisodeOfAFollowedShowKeepsItOut() {
        val repo = PlaylistRepository()
        val playlist = repo.createPlaylist("Morning")
        repo.setPlaylistPodcasts(playlist.id, listOf("show-a"))
        repo.removeEpisode(playlist.id, "ep-1")

        assertEquals(listOf("ep-1"), repo.playlists.value.single().excludedUuids)

        repo.addEpisodes(playlist.id, listOf("ep-1"))
        assertTrue(repo.playlists.value.single().excludedUuids.isEmpty())
    }

    @Test
    fun unfollowingEverythingForgetsHandRemovedEpisodes() {
        val repo = PlaylistRepository()
        val playlist = repo.createPlaylist("Morning")
        repo.setPlaylistPodcasts(playlist.id, listOf("show-a"))
        repo.removeEpisode(playlist.id, "ep-1")
        repo.setPlaylistPodcasts(playlist.id, emptyList())

        assertTrue(repo.playlists.value.single().excludedUuids.isEmpty())
    }
}
