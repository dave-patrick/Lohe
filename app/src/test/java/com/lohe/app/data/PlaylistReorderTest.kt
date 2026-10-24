package com.lohe.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistReorderTest {
    @Test
    fun `grid order persists without changing playlist contents`() {
        val file = File.createTempFile("playlist-order", ".tsv")
        try {
            val repository = PlaylistRepository(file)
            val first = repository.createPlaylist("First", listOf("one"), snapshot = true)
            val second = repository.createPlaylist("Second", listOf("two"))
            val third = repository.createPlaylist("Third", listOf("three"))
            repository.reorderPlaylists(listOf(third.id, first.id, second.id))
            assertEquals(listOf(third, first, second), PlaylistRepository(file).playlists.value)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `reordering a filtered folder preserves other playlist slots`() {
        val file = File.createTempFile("playlist-folder-order", ".tsv")
        try {
            val repository = PlaylistRepository(file)
            val first = repository.createPlaylist("First", listOf("one"))
            val outside = repository.createPlaylist("Outside", listOf("two"))
            val last = repository.createPlaylist("Last", listOf("three"))
            repository.reorderPlaylists(listOf(last.id, first.id))
            assertEquals(listOf(last, outside, first), PlaylistRepository(file).playlists.value)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `pinned playlist state persists`() {
        val file = File.createTempFile("playlist-pinned", ".tsv")
        try {
            val repository = PlaylistRepository(file)
            val playlist = repository.createPlaylist("Pinned", listOf("episode"))
            repository.setPlaylistPinned(playlist.id, true)

            assertEquals(true, PlaylistRepository(file).playlists.value.single().pinned)
        } finally {
            file.delete()
        }
    }
}
