package com.lohe.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Base64
import java.util.UUID

/**
 * A user-created named playlist storing an ordered list of episode UUIDs.
 */
data class UserPlaylist(
    val id: String,
    val name: String,
    val episodeUuids: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Durable store for custom user playlists, backed by a file when [file] is
 * provided or in-memory when `null` (unit tests).
 *
 * Uses a simple line-oriented format (with Base64-encoded names) rather than
 * `org.json.*` so it runs identically on Android and in JVM unit tests where
 * `android.jar` stubs out `org.json`.
 */
class PlaylistRepository(
    private val file: File? = null,
) {
    private val lock = Any()
    private val _playlists = MutableStateFlow(loadFromDisk())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    fun createPlaylist(
        name: String,
        initialEpisodes: Collection<String> = emptyList(),
    ): UserPlaylist {
        val cleanName = name.trim().ifBlank { "Untitled Playlist" }
        val created = UserPlaylist(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            episodeUuids = initialEpisodes.distinct(),
        )
        synchronized(lock) {
            val next = _playlists.value + created
            _playlists.value = next
            saveToDisk(next)
        }
        return created
    }

    fun deletePlaylist(playlistId: String) {
        synchronized(lock) {
            val next = _playlists.value.filterNot { it.id == playlistId }
            _playlists.value = next
            saveToDisk(next)
        }
    }

    fun addEpisodes(playlistId: String, episodeUuids: Collection<String>) {
        if (episodeUuids.isEmpty()) return
        synchronized(lock) {
            val next = _playlists.value.map { pl ->
                if (pl.id != playlistId) {
                    pl
                } else {
                    pl.copy(episodeUuids = (pl.episodeUuids + episodeUuids).distinct())
                }
            }
            _playlists.value = next
            saveToDisk(next)
        }
    }

    fun removeEpisode(playlistId: String, episodeUuid: String) {
        synchronized(lock) {
            val next = _playlists.value.map { pl ->
                if (pl.id != playlistId) {
                    pl
                } else {
                    pl.copy(episodeUuids = pl.episodeUuids.filterNot { it == episodeUuid })
                }
            }
            _playlists.value = next
            saveToDisk(next)
        }
    }

    private fun loadFromDisk(): List<UserPlaylist> {
        val target = file ?: return emptyList()
        if (!target.exists()) return emptyList()
        return runCatching {
            val encoder = Base64.getDecoder()
            target.readLines()
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 3) return@mapNotNull null
                    val id = parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val createdAt = parts[1].toLongOrNull() ?: 0L
                    val name = String(encoder.decode(parts[2]), Charsets.UTF_8)
                    val eps = if (parts.size > 3 && parts[3].isNotBlank()) {
                        parts[3].split(',').filter { it.isNotBlank() }
                    } else {
                        emptyList()
                    }
                    UserPlaylist(
                        id = id,
                        name = name,
                        episodeUuids = eps,
                        createdAt = createdAt,
                    )
                }
        }.getOrDefault(emptyList())
    }

    private fun saveToDisk(list: List<UserPlaylist>) {
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            val encoder = Base64.getEncoder()
            val text = list.joinToString("\n") { pl ->
                val encodedName = encoder.encodeToString(pl.name.toByteArray(Charsets.UTF_8))
                val eps = pl.episodeUuids.joinToString(",")
                "${pl.id}\t${pl.createdAt}\t$encodedName\t$eps"
            }
            target.writeText(text)
        }
    }
}
