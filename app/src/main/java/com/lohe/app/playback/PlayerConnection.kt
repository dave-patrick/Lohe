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
import androidx.media3.common.Player
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val artworkUrl: String = "",
    val playbackSpeed: Float = 1f,
    /** Media id of the currently loaded item, for "next up" matching. */
    val mediaId: String = "",
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

    private val _controllers = MutableStateFlow<MediaController?>(null)
    val controllers = _controllers.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
    }

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
                controller?.let {
                    it.addListener(listener)
                    _isPlaying.value = it.isPlaying
                    _controllers.value = it
                    drainPendingPlay(it)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    /** Applies the play request held during connect, then forgets it. */
    private fun drainPendingPlay(c: MediaController) {
        val request = pendingPlay ?: return
        pendingPlay = null
        replaceMediaItem(c, request.first, request.second)
    }

    fun detach() {
        pendingPlay = null
        controller?.removeListener(listener)
        _controllers.value = null
        _isPlaying.value = false
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
        replaceMediaItem(c, item, playWhenReady)
    }

    private fun replaceMediaItem(c: MediaController, item: MediaItem, playWhenReady: Boolean) {
        com.lohe.app.dev.DevDiagnostics.log("PLAYBACK_LOAD", "Replacing ${c.currentMediaItem?.mediaId} with ${item.mediaId}")
        c.pause()
        c.stop()
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
        // MediaController.setVolume() enforces 0..1. Amplification above
        // unity is applied by PlaybackService's session-bound audio effect.
        Playback.setVolumeBoost(enabled)
        controller?.volume = 1f
    }

    fun setVoiceEnhancement(enabled: Boolean) {
        Playback.setVoiceEnhancement(enabled)
    }

    fun setEnhanceVoices(enabled: Boolean) = setVoiceEnhancement(enabled)
    fun setVoiceBoost(enabled: Boolean) = setVoiceEnhancement(enabled)

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        Playback.setPlaybackSpeed(clamped)
        controller?.setPlaybackSpeed(clamped)
    }

    fun setSkipSilence(enabled: Boolean) {
        Playback.setSkipSilence(enabled)
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
        connection.controllers.collectLatest { c ->
            if (c == null) {
                value = TransportState()
                return@collectLatest
            }
            val events = Channel<Unit>(Channel.CONFLATED)
            fun update() {
                value = TransportState(
                    connected = true,
                    isPlaying = c.isPlaying,
                    hasMedia = c.mediaItemCount > 0,
                    positionMs = c.currentPosition.coerceAtLeast(0L),
                    durationMs = c.duration.takeIf { it > 0L } ?: 0L,
                    bufferedMs = c.bufferedPosition.coerceAtLeast(0L),
                    title = c.mediaMetadata.title?.toString().orEmpty(),
                    showTitle = c.mediaMetadata.artist?.toString().orEmpty(),
                    artworkUrl = c.mediaMetadata.artworkUri?.toString().orEmpty(),
                    playbackSpeed = c.playbackParameters.speed.takeIf { it > 0f } ?: 1f,
                    mediaId = c.currentMediaItem?.mediaId.orEmpty(),
                )
            }
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, changed: Player.Events) {
                    update()
                    events.trySend(Unit)
                }
            }
            c.addListener(listener)
            try {
                while (isActive) {
                    update()
                    if (c.isPlaying) delay(POLL_MS) else events.receive()
                }
            } finally {
                c.removeListener(listener)
                events.close()
            }
        }
    }

private const val POLL_MS = 200L
