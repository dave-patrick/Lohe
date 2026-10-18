package com.lohe.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Snapshot of the transport, for the player UI to render. */
data class TransportState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val hasMedia: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val title: String = "",
    val showTitle: String = "",
) {
    /** 0f..1f, or 0f when the duration is not known yet. */
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * A UI-side handle on [PlaybackService].
 *
 * Owns the [MediaController] and nothing else — every command here is a
 * forward to the service's player, so the audio keeps running whether or not
 * any screen is currently observing.
 */
class PlayerConnection internal constructor(
    private val appContext: Context,
) {

    private var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    /**
     * A play request that arrived before the session had bound.
     *
     * [attach] connects asynchronously, but callers issue commands from
     * `LaunchedEffect` on first composition — which is almost always sooner.
     * Dropping those requests is why tapping play did nothing: the command hit
     * a null controller and was discarded, with no retry and no error.
     */
    private var pendingPlay: Pair<MediaItem, Boolean>? = null

    /** Null until the session binds; commands issued before then are deferred. */
    var controller: MediaController? = null
        private set

    fun attach() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val pending = MediaController.Builder(appContext, token).buildAsync()
        future = pending
        pending.addListener(
            {
                controller = runCatching { pending.get() }.getOrNull()
                controller?.let(::drainPendingPlay)
            },
            MoreExecutors.directExecutor(),
        )
    }

    /** Applies the play request held during connect, then forgets it. */
    private fun drainPendingPlay(c: MediaController) {
        val request = pendingPlay ?: return
        pendingPlay = null
        c.setMediaItem(request.first)
        c.prepare()
        c.playWhenReady = request.second
    }

    fun detach() {
        pendingPlay = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    fun play(item: MediaItem, playWhenReady: Boolean = true) {
        val c = controller
        if (c == null) {
            pendingPlay = item to playWhenReady
            return
        }
        c.setMediaItem(item)
        c.prepare()
        c.playWhenReady = playWhenReady
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekToFraction(fraction: Float) {
        val c = controller ?: return
        val duration = c.duration
        if (duration > 0L) {
            c.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        }
    }

    fun skipForwardMs(ms: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + ms).coerceIn(0L, c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        c.seekTo(target)
    }

    fun skipBackMs(ms: Long) {
        controller?.seekTo((controller!!.currentPosition - ms).coerceAtLeast(0L))
    }

    fun setVolumeBoost(enabled: Boolean) {
        // MediaController.setVolume() enforces 0..1 and throws
        // IllegalArgumentException for values > 1f. Real amplification above
        // 0 dB is applied via LoudnessEnhancer inside PlaybackService.
        Playback.setVolumeBoost(enabled)
        controller?.volume = 1f
    }

    companion object {
        fun create(context: Context): PlayerConnection =
            PlayerConnection(context.applicationContext)
    }
}

/** Binds a connection to [PlaybackService] for the caller's lifetime. */
@Composable
fun rememberPlayerConnection(): PlayerConnection {
    val context = LocalContext.current
    val connection = remember { PlayerConnection.create(context) }

    DisposableEffect(connection) {
        connection.attach()
        onDispose { connection.detach() }
    }
    return connection
}

/** Polls [connection] and returns a transport snapshot the caller can read. */
@Composable
fun rememberTransportState(connection: PlayerConnection): State<TransportState> =
    produceState(initialValue = TransportState(), connection) {
        while (isActive) {
            val c = connection.controller
            value = if (c == null) {
                TransportState()
            } else {
                TransportState(
                    connected = true,
                    isPlaying = c.isPlaying,
                    hasMedia = c.mediaItemCount > 0,
                    positionMs = c.currentPosition.coerceAtLeast(0L),
                    durationMs = c.duration.takeIf { it > 0L } ?: 0L,
                    bufferedMs = c.bufferedPosition.coerceAtLeast(0L),
                    title = c.mediaMetadata.title?.toString().orEmpty(),
                    showTitle = c.mediaMetadata.artist?.toString().orEmpty(),
                )
            }
            delay(POLL_MS)
        }
    }

private const val POLL_MS = 200L
