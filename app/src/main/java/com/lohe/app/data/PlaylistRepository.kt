package com.lohe.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Base64
import java.util.UUID

/**
 * A user-created named playlist storing an ordered list of episode UUIDs,
 * an optional folder name, and whether items added to it should auto-download.
 */
data class UserPlaylist(
    val id: String,
    val name: String,
    val episodeUuids: List<String>,
    val folder: String = "",
    val autoDownload: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Durable store for custom user playlists and podcast folder assignments,
 * backed by a file when [file] is provided or in-memory when `null` (unit tests).
 */
class PlaylistRepository(
    private val file: File? = null,
) {
    private val lock = Any()
    private val _playlists = MutableStateFlow(loadFromDisk())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    private val _podcastFolders = MutableStateFlow(loadPodcastFoldersFromDisk())
    val podcastFolders: StateFlow<Map<String, String>> = _podcastFolders.asStateFlow()

    fun createPlaylist(
        name: String,
        initialEpisodes: Collection<String> = emptyList(),
        folder: String = "",
        autoDownload: Boolean = true,
    ): UserPlaylist {
        val cleanName = name.trim().ifBlank { "Untitled Playlist" }
        val created = UserPlaylist(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            episodeUuids = initialEpisodes.distinct(),
            folder = folder.trim(),
            autoDownload = autoDownload,
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

    fun movePlaylist(playlistId: String, delta: Int) {
        synchronized(lock) {
            val current = _playlists.value.toMutableList()
            val idx = current.indexOfFirst { it.id == playlistId }
            if (idx < 0) return
            val target = (idx + delta).coerceIn(0, current.lastIndex)
            if (target == idx) return
            val item = current.removeAt(idx)
            current.add(target, item)
            _playlists.value = current
            saveToDisk(current)
        }
    }

    fun setPlaylistFolder(playlistId: String, folder: String) {
        synchronized(lock) {
            val next = _playlists.value.map { pl ->
                if (pl.id == playlistId) pl.copy(folder = folder.trim()) else pl
            }
            _playlists.value = next
            saveToDisk(next)
        }
    }

    fun setPlaylistAutoDownload(playlistId: String, enabled: Boolean) {
        synchronized(lock) {
            val next = _playlists.value.map { pl ->
                if (pl.id == playlistId) pl.copy(autoDownload = enabled) else pl
            }
            _playlists.value = next
            saveToDisk(next)
        }
    }

    fun setPodcastFolder(podcastUuid: String, folder: String) {
        synchronized(lock) {
            val next = _podcastFolders.value.toMutableMap()
            val clean = folder.trim()
            if (clean.isEmpty()) {
                next.remove(podcastUuid)
            } else {
                next[podcastUuid] = clean
            }
            _podcastFolders.value = next
            savePodcastFoldersToDisk(next)
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

    /**
     * Removes [episodeUuid] from every playlist.
     *
     * Used when an episode is marked listened: it leaves the playlists it was
     * added to and shows up in the Played list instead.
     */
    fun removeEpisodeFromAll(episodeUuid: String) {
        synchronized(lock) {
            val current = _playlists.value
            if (current.none { pl -> episodeUuid in pl.episodeUuids }) return
            val next = current.map { pl ->
                if (episodeUuid in pl.episodeUuids) {
                    pl.copy(episodeUuids = pl.episodeUuids.filterNot { it == episodeUuid })
                } else {
                    pl
                }
            }
            _playlists.value = next
            saveToDisk(next)
        }
    }

    fun moveEpisode(playlistId: String, episodeUuid: String, delta: Int) {
        synchronized(lock) {
            val next = _playlists.value.map { pl ->
                if (pl.id != playlistId) {
                    pl
                } else {
                    val list = pl.episodeUuids.toMutableList()
                    val idx = list.indexOf(episodeUuid)
                    if (idx < 0) {
                        pl
                    } else {
                        val target = (idx + delta).coerceIn(0, list.lastIndex)
                        if (target == idx) {
                            pl
                        } else {
                            val item = list.removeAt(idx)
                            list.add(target, item)
                            pl.copy(episodeUuids = list)
                        }
                    }
                }
            }
            _playlists.value = next
            saveToDisk(next)
        }
    }

    /**
     * Rewrites a playlist's episode order wholesale.
     *
     * Used when a row is dragged to a new position: the order is written once,
     * when the row is dropped, rather than nudged one step at a time.
     */
    fun reorderEpisodes(playlistId: String, episodeUuids: List<String>) {
        synchronized(lock) {
            val next = _playlists.value.map { pl ->
                if (pl.id == playlistId) pl.copy(episodeUuids = episodeUuids) else pl
            }
            _playlists.value = next
            saveToDisk(next)
        }
    }

    private fun loadFromDisk(): List<UserPlaylist> {
        val target = file ?: return emptyList()
        if (!target.exists()) return emptyList()
        return runCatching {
            val decoder = Base64.getDecoder()
            target.readLines()
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 3) return@mapNotNull null
                    val id = parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val createdAt = parts[1].toLongOrNull() ?: 0L
                    val name = String(decoder.decode(parts[2]), Charsets.UTF_8)
                    val eps = if (parts.size > 3 && parts[3].isNotBlank()) {
                        parts[3].split(',').filter { it.isNotBlank() }
                    } else {
                        emptyList()
                    }
                    val folder = if (parts.size > 4 && parts[4].isNotBlank()) {
                        String(decoder.decode(parts[4]), Charsets.UTF_8)
                    } else {
                        ""
                    }
                    val autoDownload = if (parts.size > 5) {
                        parts[5] != "0"
                    } else {
                        true
                    }
                    UserPlaylist(
                        id = id,
                        name = name,
                        episodeUuids = eps,
                        folder = folder,
                        autoDownload = autoDownload,
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
                val encodedFolder = encoder.encodeToString(pl.folder.toByteArray(Charsets.UTF_8))
                val eps = pl.episodeUuids.joinToString(",")
                val autoDl = if (pl.autoDownload) "1" else "0"
                "${pl.id}\t${pl.createdAt}\t$encodedName\t$eps\t$encodedFolder\t$autoDl"
            }
            target.writeText(text)
        }
    }

    private fun podcastFoldersFile(): File? =
        file?.parentFile?.let { File(it, "podcast-folders.tsv") }

    private fun loadPodcastFoldersFromDisk(): Map<String, String> {
        val target = podcastFoldersFile() ?: return emptyMap()
        if (!target.exists()) return emptyMap()
        return runCatching {
            val decoder = Base64.getDecoder()
            target.readLines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 2) return@mapNotNull null
                val uuid = parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val folder = String(decoder.decode(parts[1]), Charsets.UTF_8).trim()
                if (folder.isEmpty()) null else uuid to folder
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun savePodcastFoldersToDisk(map: Map<String, String>) {
        val target = podcastFoldersFile() ?: return
        runCatching {
            target.parentFile?.mkdirs()
            val encoder = Base64.getEncoder()
            val text = map.entries.joinToString("\n") { (uuid, folder) ->
                "$uuid\t${encoder.encodeToString(folder.toByteArray(Charsets.UTF_8))}"
            }
            target.writeText(text)
        }
    }
}
