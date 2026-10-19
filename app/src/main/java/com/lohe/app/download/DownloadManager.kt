package com.lohe.app.download

import android.content.Context
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Downloads episode audio for offline playback.
 *
 * Downloads land in the app's own files directory rather than shared storage:
 * no permission is needed, and the files disappear with the app, which is the
 * right default for a cache of something the user can re-fetch.
 *
 * Each file is written to a `.part` sibling and renamed only on completion, so
 * an interrupted download can never be mistaken for a finished one — the player
 * would otherwise open a truncated file and fail mid-episode.
 */
class DownloadManager(
    private val context: Context,
    private val repository: SubscriptionRepository,
    private val client: OkHttpClient = OkHttpClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    /** Downloaded episodes, newest first. */
    fun downloads() = repository.observeDownloads()

    fun start(episode: EpisodeEntity) {
        scope.launch {
            runDownload(episode)
        }
    }

    /** Removes the file and forgets the episode. */
    suspend fun delete(episode: EpisodeEntity) = withContext(io) {
        episode.localPath?.let { path ->
            runCatching { File(path).delete() }
        }
        repository.clearDownload(episode.uuid)
    }

    /** Total bytes currently held on disk by completed downloads. */
    suspend fun bytesOnDisk(): Long = withContext(io) {
        downloadsBlocking().sumOf { episode ->
            episode.localPath?.let { path ->
                File(path).takeIf { it.exists() }?.length() ?: 0L
            } ?: 0L
        }
    }

    /** Removes every downloaded file and clears the bookkeeping. */
    suspend fun deleteAll() = withContext(io) {
        downloadsBlocking().forEach { delete(it) }
    }

    /**
     * Forgets downloads whose file has gone missing.
     *
     * A download can disappear without the app being told: the user clears the
     * app's storage, a restore brings back a database whose files did not come
     * with it, or the system reclaims the space. The row keeps claiming the
     * episode is available offline, the "Downloaded" list keeps showing it,
     * and the storage screen keeps counting bytes that are not there.
     *
     * Returns how many rows were repaired.
     */
    suspend fun repairMissingDownloads(): Int = withContext(io) {
        var repaired = 0
        downloadsBlocking().forEach { episode ->
            val path = episode.localPath
            if (path.isNullOrBlank() || !File(path).exists()) {
                repository.clearDownload(episode.uuid)
                repaired++
            }
        }
        repaired
    }

    private suspend fun downloadsBlocking(): List<EpisodeEntity> =
        repository.observeDownloads().first()

    private suspend fun runDownload(episode: EpisodeEntity) {
        if (episode.isDownloaded || episode.isDownloading) return

        repository.setDownloadState(episode.uuid, DownloadState.RUNNING, null)
        val target = File(episodeDir(), fileNameFor(episode))
        val partial = File(target.absolutePath + PART_SUFFIX)

        try {
            withContext(io) {
                partial.parentFile?.mkdirs()

                val request = Request.Builder()
                    .url(episode.audioUrl)
                    .header("User-Agent", "Lohe/0.1 (+podcast reader)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("empty body")

                    // Copy through a buffer so a large episode is never held in
                    // memory, and check for cancellation between chunks.
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }

                if (partial.length() == 0L) throw IOException("downloaded an empty file")

                // Rename only now: an incomplete .part is never handed to the
                // player because playbackUri keys off the committed path.
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) throw IOException("could not finalise download")
            }

            repository.setDownloadState(
                episode.uuid,
                DownloadState.DONE,
                target.absolutePath,
            )
        } catch (e: Exception) {
            withContext(io) { runCatching { partial.delete() } }
            repository.setDownloadState(episode.uuid, DownloadState.FAILED, null)
        }
    }

    private fun episodeDir(): File =
        File(context.filesDir, "episodes").apply { mkdirs() }

    /**
     * Names the file after the episode, but never trusts the title.
     *
     * Feed titles routinely contain `/`, `:`, and other characters that either
     * fail on some filesystems or escape the intended directory. The uuid keeps
     * the name unique and safe.
     */
    private fun fileNameFor(episode: EpisodeEntity): String = DownloadNaming.fileName(
        title = episode.title,
        uuid = episode.uuid,
        mimeType = episode.fileType,
    )

    companion object {
        private const val PART_SUFFIX = ".part"
        private const val BUFFER_BYTES = 64 * 1024
    }
}

/**
 * Builds on-disk filenames for downloaded episodes.
 *
 * Extracted from [DownloadManager] so the sanitisation rules can be tested
 * directly: episode titles come from remote feeds and are attacker-influenced,
 * and a filename that escapes the episodes directory is a real, if narrow,
 * vulnerability.
 */
object DownloadNaming {

    private const val FALLBACK_EXTENSION = "mp3"
    private const val MAX_TITLE_CHARS = 60
    private const val UUID_PREFIX_CHARS = 8

    private val SAFE_EXTENSION = Regex("[A-Za-z0-9]{1,5}")
    private val SIMPLE_MIME = Regex("[a-z0-9.+-]+/[a-z0-9.+-]+")

    /**
     * MIME type -> file extension.
     *
     * A lookup rather than `substringAfterLast('/')`: the subtype is frequently
     * not the extension (`audio/mpeg` -> mpeg, not mp3), and it is feed-supplied,
     * so taking its last path segment would let `audio/../../etc` name the file.
     */
    private val EXTENSION_BY_MIME = mapOf(
        "audio/mpeg" to "mp3",
        "audio/mp3" to "mp3",
        "audio/x-mpeg" to "mp3",
        "audio/mpeg3" to "mp3",
        "audio/mp4" to "m4a",
        "audio/m4a" to "m4a",
        "audio/x-m4a" to "m4a",
        "audio/mp4a-latm" to "m4a",
        "audio/aac" to "aac",
        "audio/aacp" to "aac",
        "audio/x-aac" to "aac",
        "audio/ogg" to "ogg",
        "audio/vorbis" to "ogg",
        "audio/opus" to "opus",
        "audio/x-opus+ogg" to "opus",
        "audio/flac" to "flac",
        "audio/x-flac" to "flac",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
        "audio/wave" to "wav",
        "audio/webm" to "webm",
    )

    fun fileName(title: String, uuid: String, mimeType: String?): String =
        "${sanitize(title)}-${uuid.take(UUID_PREFIX_CHARS)}.${extensionFor(mimeType)}"

    /**
     * Extension for [mimeType], falling back to `mp3`.
     *
     * An unrecognised but plainly-safe subtype is honoured, so a format this
     * table has not seen still gets the right extension. A value that is not
     * even shaped like `type/subtype` is rejected outright: taking the last
     * path segment of `audio/../../etc` would name the file after it.
     */
    internal fun extensionFor(mimeType: String?): String {
        val mime = mimeType?.trim()?.lowercase() ?: return FALLBACK_EXTENSION
        EXTENSION_BY_MIME[mime]?.let { return it }

        // Exactly one separator: a well-formed MIME type has no path in it.
        if (!SIMPLE_MIME.matches(mime)) return FALLBACK_EXTENSION

        val subtype = mime.substringAfterLast('/')
        return if (subtype.matches(SAFE_EXTENSION)) subtype else FALLBACK_EXTENSION
    }

    /**
     * Replaces anything outside `[A-Za-z0-9_-]` with a single underscore.
     *
     * Runs of disallowed characters collapse to one underscore so a punctuated
     * title does not spend its whole length budget on separators.
     */
    fun sanitize(raw: String): String = buildString {
        var pendingSeparator = false
        for (c in raw) {
            if (c.isLetterOrDigit() || c == '-' || c == '_') {
                if (pendingSeparator && isNotEmpty()) append('_')
                append(c)
                pendingSeparator = false
            } else {
                pendingSeparator = true
            }
        }
    }
        .take(MAX_TITLE_CHARS)
        .trim('_')
        .ifEmpty { "episode" }
}
