package com.lohe.app.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import java.util.UUID

/**
 * Imports device-local audio into the library as a single synthetic
 * "Local Files" pseudo-podcast, so the rest of the app treats local and remote
 * episodes identically.
 */
class LocalFileManager(
    private val context: Context,
    private val repository: SubscriptionRepository,
) {

    /**
     * Import [fileUris] under a pseudo-podcast named after [folderName].
     *
     * Re-importing the same folder replaces that folder's episodes rather than
     * duplicating them: the podcast uuid is derived from the folder, so a second
     * import lands on the same row.
     *
     * The files are marked as downloaded, because that is what they are. The
     * listener dropped them here themselves, and marking them is what puts
     * them in the Downloaded playlist alongside everything the app fetched.
     *
     * @return the uuids of the episodes now in the folder
     */
    suspend fun importFolder(
        folderName: String,
        fileUris: List<Uri>,
    ): List<String> {
        if (fileUris.isEmpty()) return emptyList()

        val podcast = repository.getPodcast(podcastUuidFor(folderName))
            ?: PodcastEntity(
                uuid = podcastUuidFor(folderName),
                title = folderName,
                description = "Imported from this device",
                feedUrl = "",
                lastEpisodePublished = System.currentTimeMillis(),
            )
        repository.subscribe(podcast)

        val existing = repository.allEpisodesOnce(podcast.uuid).associateBy { it.audioUrl }
        val episodes = fileUris.mapNotNull { uri ->
            val prev = existing[uri.toString()]
            val name = displayName(uri) ?: return@mapNotNull null
            if (prev != null && prev.playedUpToSeconds > 0L) {
                prev // keep resume position across re-imports
            } else {
                EpisodeEntity(
                    uuid = prev?.uuid ?: UUID.randomUUID().toString(),
                    podcastUuid = podcast.uuid,
                    title = name.substringBeforeLast('.'),
                    audioUrl = uri.toString(),
                    published = System.currentTimeMillis(),
                    durationSeconds = null,
                    sizeBytes = sizeOf(uri),
                    fileType = mimeOf(uri),
                    author = null,
                    artworkUrl = null,
                    playingStatus = 0,
                    playedUpToSeconds = prev?.playedUpToSeconds ?: 0L,
                    starred = prev?.starred ?: false,
                    isDeleted = false,
                    // The file is already on the device, so it is a local copy
                    // and is in the Downloaded list.
                    localPath = uri.toString(),
                    downloadState = DownloadState.DONE,
                )
            }
        }

        repository.upsertEpisodes(episodes)
        return episodes.map { it.uuid }
    }

    private fun podcastUuidFor(folderName: String) = "local:$folderName"

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun mimeOf(uri: Uri): String? =
        context.contentResolver.getType(uri)
            ?: FileScanner.lowerExtensionOf(uri.lastPathSegment).takeIf { it.isNotEmpty() }
                ?.let { "audio/$it" }

    private fun sizeOf(uri: Uri): Long? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null, null, null,
        )?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.SIZE)
            if (i >= 0 && c.moveToFirst() && !c.isNull(i)) c.getLong(i) else null
        }
}
