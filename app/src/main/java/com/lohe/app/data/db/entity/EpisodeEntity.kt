package com.lohe.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

/**
 * An episode belonging to a podcast. Only the subset needed to drive the UI
 * and queue — playback stats attach later.
 *
 * Time fields are stored as epoch millis to keep Room mappings converter-free.
 */
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["podcastUuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["podcastUuid"]),
        Index(value = ["podcastUuid", "published"]),
        // Backs "continue listening" and the queue; both sort on these columns.
        Index(value = ["playingStatus", "lastPlayedAt"]),
        Index(value = ["queuePosition"]),
    ],
)
data class EpisodeEntity(
    @PrimaryKey val uuid: String,
    val podcastUuid: String,
    val title: String,
    val audioUrl: String,
    val published: Long,
    val durationSeconds: Int? = null,
    val sizeBytes: Long? = null,
    val fileType: String? = null,
    val author: String? = null,
    val artworkUrl: String? = null,
    val description: String? = null,
    /** 0 = unplayed, 1 = in progress, 2 = played. */
    val playingStatus: Int = 0,
    val playedUpToSeconds: Long = 0,
    /**
     * Actual time spent listening, accumulated across sessions.
     *
     * Separate from [playedUpToSeconds] because that field is a resume
     * position: it is zeroed when an episode is marked played, so it cannot
     * answer "how much did I actually listen to". Marking an episode listened
     * without playing it must not add its full runtime to the listening total.
     */
    val listenedSeconds: Long = 0,
    val starred: Boolean = false,
    val isDeleted: Boolean = false,
    /**
     * Set aside by the listener.
     *
     * Archived episodes are kept but out of the way: they are left out of the
     * episode lists and, more to the point, they stop counting towards the
     * listening figures, so an episode filed away is not still being credited
     * as heard.
     */
    val isArchived: Boolean = false,
    /**
     * When the episode was archived, or null if it is not.
     *
     * An archived episode is marked played and stays in the Played list, with
     * its download kept, for a configurable time from this moment. After that
     * the download is removed; the episode itself stays archived.
     */
    val archivedAt: Long? = null,
    /**
     * Position in the play queue; null when the episode is not queued.
     *
     * Lower sorts first. Enqueue assigns the next integer, so the queue is an
     * ordered list rather than a set.
     */
    val queuePosition: Int? = null,
    /** When the episode last sat at the head of the queue. Drives "continue". */
    val lastPlayedAt: Long? = null,
    /** Absolute path to a downloaded copy, or null when streaming only. */
    val localPath: String? = null,
    /** One of [DownloadState]. */
    val downloadState: Int = DownloadState.NONE,
    /**
     * Chapter markers as JSON: [{"title": "...", "startMs": 12345}, ...]
     * Used for chapter navigation in the player.
     */
    val chapters: String? = null,
) {
    val isPlayed: Boolean get() = playingStatus == PLAYED
    val isInProgress: Boolean get() = playingStatus == IN_PROGRESS
    val isQueued: Boolean get() = queuePosition != null

    /** True once a usable local copy exists on disk. */
    val isDownloaded: Boolean
        get() = downloadState == DownloadState.DONE && !localPath.isNullOrBlank()

    val isDownloading: Boolean get() = downloadState == DownloadState.RUNNING

    /**
     * Source the player should open.
     *
     * A downloaded copy is preferred: it starts immediately and does not depend
     * on the network. Falls back to the remote URL, which is also what makes a
     * partially-written file dangerous to use — only a committed path lands here.
     *
     * The local copy is only used if it is still there. Downloads can vanish
     * without the app being told — the user clears the app's storage, a
     * restore brings back a database whose files did not come with it — and
     * handing the player a path that no longer resolves turns a missing file
     * into an episode that will not play at all. Streaming is a much better
     * failure than silence.
     */
    val playbackUri: String
        get() {
            val local = localPath
            if (isDownloaded && !local.isNullOrBlank() && File(local).exists()) return local
            return audioUrl
        }

    /** Fraction listened, or 0 when the duration is unknown. */
    val completionFraction: Float
        get() = when {
            durationSeconds == null || durationSeconds <= 0 -> 0f
            isPlayed -> 1f
            else -> (playedUpToSeconds.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        }

    companion object {
        const val UNPLAYED = 0
        const val IN_PROGRESS = 1
        const val PLAYED = 2
    }
}

/**
 * Lightweight projection of [EpisodeEntity] for list views.
 *
 * Omits [description] and [chapters], which can be large text blobs that are
 * only needed on the episode detail screen. Loading them for every episode in
 * the library (thousands of rows) blows the heap. Full [EpisodeEntity] is
 * fetched by UUID when the detail screen or player needs them.
 */
data class EpisodeSummary(
    val uuid: String,
    val podcastUuid: String,
    val title: String,
    val audioUrl: String,
    val published: Long,
    val durationSeconds: Int? = null,
    val sizeBytes: Long? = null,
    val fileType: String? = null,
    val author: String? = null,
    val artworkUrl: String? = null,
    /** 0 = unplayed, 1 = in progress, 2 = played. */
    val playingStatus: Int = 0,
    val playedUpToSeconds: Long = 0,
    val listenedSeconds: Long = 0,
    val starred: Boolean = false,
    val isDeleted: Boolean = false,
    /** Set aside by the listener; see [EpisodeEntity.isArchived]. */
    val isArchived: Boolean = false,
    /** When it was archived; see [EpisodeEntity.archivedAt]. */
    val archivedAt: Long? = null,
    val queuePosition: Int? = null,
    val lastPlayedAt: Long? = null,
    val localPath: String? = null,
    /** One of [DownloadState]. */
    val downloadState: Int = DownloadState.NONE,
) {
    val isPlayed: Boolean get() = playingStatus == PLAYED
    val isInProgress: Boolean get() = playingStatus == IN_PROGRESS
    val isQueued: Boolean get() = queuePosition != null

    val isDownloaded: Boolean
        get() = downloadState == DownloadState.DONE && !localPath.isNullOrBlank()

    val isDownloading: Boolean get() = downloadState == DownloadState.RUNNING

    /** Source the player should open. Falls back to the remote URL. */
    val playbackUri: String
        get() {
            val local = localPath
            if (isDownloaded && !local.isNullOrBlank() && java.io.File(local).exists()) return local
            return audioUrl
        }

    /** Fraction listened, or 0 when the duration is unknown. */
    val completionFraction: Float
        get() = when {
            durationSeconds == null || durationSeconds <= 0 -> 0f
            isPlayed -> 1f
            else -> (playedUpToSeconds.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        }

    companion object {
        const val UNPLAYED = 0
        const val IN_PROGRESS = 1
        const val PLAYED = 2
    }
}

/** Converts a full entity to its lightweight summary projection. */
fun EpisodeEntity.toSummary(): EpisodeSummary = EpisodeSummary(
    uuid = uuid,
    podcastUuid = podcastUuid,
    title = title,
    audioUrl = audioUrl,
    published = published,
    durationSeconds = durationSeconds,
    sizeBytes = sizeBytes,
    fileType = fileType,
    author = author,
    artworkUrl = artworkUrl,
    playingStatus = playingStatus,
    playedUpToSeconds = playedUpToSeconds,
    listenedSeconds = listenedSeconds,
    starred = starred,
    isDeleted = isDeleted,
    isArchived = isArchived,
    archivedAt = archivedAt,
    queuePosition = queuePosition,
    lastPlayedAt = lastPlayedAt,
    localPath = localPath,
    downloadState = downloadState,
)

/** Progress of an offline download. */
object DownloadState {
    const val NONE = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3
}