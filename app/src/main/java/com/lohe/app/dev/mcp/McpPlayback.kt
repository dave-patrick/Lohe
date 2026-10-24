package com.lohe.app.dev.mcp

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.resume

/**
 * Playback control for the dev surface.
 *
 * It talks to [PlaybackService] through a [MediaController] rather than
 * reaching for the ExoPlayer instance directly: the controller is the same
 * handle the UI uses, it starts the service when it is not running, and it
 * keeps the commands on the main thread where a player has to be touched.
 */
class McpPlayback(private val context: Context) {

    private var controller: MediaController? = null
    private var pending: ListenableFuture<MediaController>? = null

    /** Starts (or resumes) the given episode. */
    suspend fun play(episode: EpisodeEntity, show: PodcastEntity?) {
        val c = controller() ?: throw ToolException("Could not reach the playback service")
        val item = PlaybackService.mediaItemFor(
            episodeUuid = episode.uuid,
            uri = episode.playbackUri,
            title = episode.title,
            showTitle = show?.title,
            artworkUrl = episode.artworkUrl?.takeIf { it.isNotBlank() } ?: show?.artworkUrl,
        )
        withContext(Dispatchers.Main) {
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    /** Pauses whatever is playing. */
    suspend fun pause() {
        val c = controller() ?: return
        withContext(Dispatchers.Main) { c.pause() }
    }

    /** A snapshot of the transport, or null when nothing is loaded. */
    suspend fun state(): JsonObject? {
        val c = controller() ?: return null
        return withContext(Dispatchers.Main) {
            jsonObject(
                "isPlaying" to c.isPlaying,
                "title" to c.mediaMetadata.title?.toString(),
                "showTitle" to c.mediaMetadata.artist?.toString(),
                "episodeUuid" to c.currentMediaItem?.mediaId,
                "positionMs" to c.currentPosition.coerceAtLeast(0L),
                "durationMs" to (c.duration.takeIf { it > 0L } ?: 0L),
                "playbackSpeed" to c.playbackParameters.speed,
            )
        }
    }

    /**
     * The connected controller, connecting on first use.
     *
     * The connection is asynchronous, so the wait is expressed as a
     * continuation rather than a blocking get: the request thread stays free
     * while the session binds.
     */
    private suspend fun controller(): MediaController? {
        controller?.let { return it }
        return withContext(Dispatchers.Main) {
            controller?.let { return@withContext it }
            val future = pending ?: MediaController
                .Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
                .buildAsync()
                .also { pending = it }
            suspendCancellableCoroutine<MediaController?> { continuation ->
                future.addListener(
                    {
                        val built = runCatching { future.get() }.getOrNull()
                        if (built != null) controller = built
                        if (continuation.isActive) continuation.resume(built)
                    },
                    MoreExecutors.directExecutor(),
                )
            }
        }
    }
}
