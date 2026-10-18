package com.lohe.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    /** 0 = unplayed, 1 = in progress, 2 = played. */
    val playingStatus: Int = 0,
    val playedUpToSeconds: Long = 0,
    val starred: Boolean = false,
    val isDeleted: Boolean = false,
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
     */
    val playbackUri: String
        get() = if (isDownloaded) localPath!! else audioUrl

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

/** Progress of an offline download. */
object DownloadState {
    const val NONE = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3
}