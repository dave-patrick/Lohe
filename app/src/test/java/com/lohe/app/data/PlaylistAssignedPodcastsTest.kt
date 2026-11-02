package com.lohe.app.data

import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.EpisodeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaylistAssignedPodcastsTest {
    private fun episode(id: String, show: String = "assigned", published: Long = 1L) = EpisodeSummary(
        uuid = id, podcastUuid = show, title = id, audioUrl = "https://example.com/$id", published = published,
    )

    @Test
    fun `assigned episodes include played and archived while explicit entries retain their filters`() {
        val playlist = UserPlaylist(
            "id", "Mix", listOf("manual", "manual-played", "manual-archived", "missing", "duplicate"),
            assignedPodcastUuids = listOf("assigned"), excludedUuids = listOf("excluded"),
        )
        val episodes = listOf(
            episode("manual", "other"),
            episode("manual-played", "other").copy(playingStatus = EpisodeSummary.PLAYED),
            episode("manual-archived", "other").copy(isArchived = true),
            episode("duplicate", published = 20),
            episode("played", published = 30).copy(playingStatus = EpisodeSummary.PLAYED),
            episode("archived", published = 30).copy(isArchived = true),
            episode("excluded", published = 50),
            episode("deleted", published = 60).copy(isDeleted = true),
            episode("other", "other", 100),
        )
        assertEquals(
            listOf("manual", "duplicate", "archived", "played"),
            playlist.resolveAssignedEpisodes(episodes).map { it.uuid },
        )
    }

    @Test
    fun `assignment survives reload and clearing preserves legacy rules and exclusions`() {
        val file = File.createTempFile("assigned-playlists", ".tsv")
        try {
            val repository = PlaylistRepository(file)
            val playlist = repository.createPlaylist("Mix", shows = listOf("legacy"))
            repository.removeEpisode(playlist.id, "hidden")
            repository.assignPodcastShows(playlist.id, listOf("show-b", "show-a", "show-b"))
            var restored = PlaylistRepository(file).playlists.value.single()
            assertEquals(listOf("show-b", "show-a"), restored.assignedPodcastUuids)
            assertEquals(listOf("legacy"), restored.shows)
            assertEquals(listOf("hidden"), restored.excludedUuids)

            val reloaded = PlaylistRepository(file)
            reloaded.assignPodcastShows(playlist.id, emptyList())
            restored = PlaylistRepository(file).playlists.value.single()
            assertTrue(restored.assignedPodcastUuids.isEmpty())
            assertEquals(listOf("legacy"), restored.shows)
            assertEquals(listOf("hidden"), restored.excludedUuids)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `legacy file has no assigned podcasts`() {
        val file = File.createTempFile("assigned-legacy", ".tsv")
        try {
            file.writeText("legacy\t1\tTGVnYWN5\tepisode\t\t0\told-show\t0\t0\t0")
            val playlist = PlaylistRepository(file).playlists.value.single()
            assertTrue(playlist.assignedPodcastUuids.isEmpty())
            assertEquals(listOf("old-show"), playlist.shows)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `assigned podcasts enable future auto downloads even for snapshots and respect exclusions and switch`() {
        val repository = PlaylistRepository()
        val playlist = repository.createPlaylist("Snapshot", snapshot = true)
        repository.assignPodcastShows(playlist.id, listOf("assigned"))
        val episode = EpisodeEntity("ep", "assigned", "Episode", "https://example.com/ep", 1L)
        assertTrue(repository.shouldAutoDownloadPodcast("assigned"))
        assertTrue(repository.shouldAutoDownloadEpisode("assigned", episode))
        assertFalse(repository.shouldAutoDownloadPodcast("other"))
        assertFalse(repository.shouldAutoDownloadEpisode("other", episode.copy(podcastUuid = "other")))
        repository.removeEpisode(playlist.id, episode.uuid)
        assertFalse(repository.shouldAutoDownloadEpisode("assigned", episode))
        repository.setPlaylistAutoDownload(playlist.id, false)
        assertFalse(repository.shouldAutoDownloadPodcast("assigned"))
        assertFalse(repository.shouldAutoDownloadEpisode("assigned", episode.copy(uuid = "new")))
    }

    @Test
    fun `user exclusion survives clearing both rules and reassigning after reload`() {
        val file = File.createTempFile("assigned-exclusion", ".tsv")
        try {
            val repository = PlaylistRepository(file)
            val playlist = repository.createPlaylist("Mix", shows = listOf("legacy"))
            repository.assignPodcastShows(playlist.id, listOf("assigned"))
            repository.removeEpisode(playlist.id, "hidden")
            repository.assignPodcastShows(playlist.id, emptyList())
            repository.setPlaylistShows(playlist.id, emptyList())

            val reloaded = PlaylistRepository(file)
            assertEquals(listOf("hidden"), reloaded.playlists.value.single().excludedUuids)
            reloaded.assignPodcastShows(playlist.id, listOf("assigned"))
            val restored = PlaylistRepository(file).playlists.value.single()
            assertTrue(restored.resolveAssignedEpisodes(listOf(episode("hidden"))).isEmpty())
            assertEquals(listOf("hidden"), restored.excludedUuids)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `automatic explicit cleanup does not exclude an assigned episode`() {
        val repository = PlaylistRepository()
        val playlist = repository.createPlaylist("Mix", listOf("episode"), shows = listOf("legacy"))
        repository.assignPodcastShows(playlist.id, listOf("assigned"))
        repository.removeExplicitEpisode(playlist.id, "episode")
        val updated = repository.playlists.value.single()
        assertTrue(updated.episodeUuids.isEmpty())
        assertTrue(updated.excludedUuids.isEmpty())
        assertEquals(listOf("episode"), updated.resolveAssignedEpisodes(listOf(episode("episode"))).map { it.uuid })
        assertEquals(listOf("legacy"), updated.shows)
    }

    @Test
    fun `assigned download eligibility excludes played archived and deleted episodes`() {
        val repository = PlaylistRepository()
        val playlist = repository.createPlaylist("Mix")
        repository.assignPodcastShows(playlist.id, listOf("assigned"))
        val candidate = EpisodeEntity("ep", "assigned", "Episode", "https://example.com/ep", 1L)
        assertTrue(repository.shouldAutoDownloadEpisode("assigned", candidate))
        assertFalse(repository.shouldAutoDownloadEpisode("assigned", candidate.copy(playingStatus = EpisodeEntity.PLAYED)))
        assertFalse(repository.shouldAutoDownloadEpisode("assigned", candidate.copy(isArchived = true)))
        assertFalse(repository.shouldAutoDownloadEpisode("assigned", candidate.copy(isDeleted = true)))
    }

    @Test
    fun `assignment does not backfill explicit entries or change legacy rule`() {
        val repository = PlaylistRepository()
        val playlist = repository.createPlaylist("Mix", listOf("manual"), shows = listOf("legacy"), maxDurationMinutes = 30)
        repository.assignPodcastShows(playlist.id, listOf("assigned"))
        val updated = repository.playlists.value.single()
        assertEquals(listOf("manual"), updated.episodeUuids)
        assertEquals(listOf("legacy"), updated.shows)
        assertEquals(30, updated.maxDurationMinutes)
        assertEquals(listOf("manual", "catalog"), updated.resolveAssignedEpisodes(
            listOf(episode("manual", "other"), episode("catalog")),
        ).map { it.uuid })
        assertEquals(listOf("manual"), repository.playlists.value.single().episodeUuids)
        repository.assignPodcastShows(playlist.id, emptyList())
        assertEquals(listOf("legacy"), repository.playlists.value.single().shows)
        assertTrue(repository.shouldAutoDownloadPodcast("legacy"))
    }
}
