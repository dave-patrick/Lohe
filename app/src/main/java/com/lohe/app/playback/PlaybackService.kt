package com.lohe.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lohe.app.MainActivity
import com.lohe.app.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the single ExoPlayer instance for the whole app.
 *
 * The player lives here rather than in a composable so playback survives
 * navigation, screen rotation, and backgrounding — a player that dies when you
 * check another tab is not a podcast player. The MediaSession gives the system
 * (and any connected UI) a handle, and lets the platform notification drive
 * playback without this class knowing about Compose.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    /** Shared with the UI so the countdown and the stop action agree. */
    private val sleepTimer: SleepTimer get() = Playback.sleepTimer

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause rather than keep playing into a silent headset unplug.
            .setHandleAudioBecomingNoisy(true)
            .build()

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()

        player.addListener(EndOfEpisodeWatcher())
        startVolumeBoostObserver()
        startSleepTimerPolling()
        startProgressReporting()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should not leave audio playing with no way to
        // reach it.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        player.removeListener(EndOfEpisodeWatcher())
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Ends playback when a [SleepTimer.Mode.EndOfEpisode] timer is armed and
     * this episode finishes, restores a saved position, and rolls on to the next
     * queued episode when the queue is not empty.
     */
    private inner class EndOfEpisodeWatcher : Player.Listener {
        private var resumedForMediaId: String? = null

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                applyVolumeBoost(Playback.volumeBoost.value)
            }
            if (playbackState != Player.STATE_ENDED) {
                if (playbackState == Player.STATE_READY) resumeOnce()
                return
            }

            // The sleep timer outranks the queue: the listener asked to stop,
            // so finishing the episode is the end of playback, not a transition.
            if (sleepTimer.state.value.stopsAtEpisodeEnd) {
                sleepTimer.cancel()
                markCurrentPlayed()
                player.pause()
                return
            }

            markCurrentPlayed()
            advanceQueue()
        }

        private fun markCurrentPlayed() {
            val id = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
            scope.launch(Dispatchers.IO) {
                runCatching { ServiceLocator.subscriptionRepository() }
                    .getOrNull()
                    ?.markPlayed(id)
            }
        }

        /**
         * Plays whatever is at the head of the queue, or stops if it is empty.
         *
         * The finished episode is dequeued first, so failing to load the next
         * one cannot leave it stuck at the head and wedge the queue.
         */
        private fun advanceQueue() {
            val finishedId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
            scope.launch(Dispatchers.IO) {
                val repository = runCatching { ServiceLocator.subscriptionRepository() }
                    .getOrNull() ?: return@launch

                finishedId?.let { repository.dequeue(it) }

                val next = repository.queueSnapshot().firstOrNull()
                if (next == null) {
                    withContext(Dispatchers.Main) { player.stop() }
                    return@launch
                }

                val show = runCatching {
                    repository.getPodcast(next.podcastUuid)
                }.getOrNull()
                val showTitle = show?.title
                val artworkUrl = next.artworkUrl?.takeIf { it.isNotBlank() } ?: show?.artworkUrl

                withContext(Dispatchers.Main) {
                    player.setMediaItem(
                        mediaItemFor(
                            episodeUuid = next.uuid,
                            uri = next.playbackUri,
                            title = next.title,
                            showTitle = showTitle,
                            artworkUrl = artworkUrl,
                        ),
                    )
                    player.prepare()
                    player.play()
                }
            }
        }

        /**
         * Seeks to the stored position the first time an item becomes ready.
         *
         * Guarded by [resumedForMediaId] because STATE_READY fires repeatedly —
         * on every buffering recovery, not just the first load — and re-seeking
         * each time would yank the listener back to the resume point.
         */
        private fun resumeOnce() {
            val item = player.currentMediaItem ?: return
            val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: return
            if (resumedForMediaId == mediaId) return
            resumedForMediaId = mediaId

            scope.launch(Dispatchers.IO) {
                val repository = runCatching { ServiceLocator.subscriptionRepository() }
                    .getOrNull() ?: return@launch
                val seconds = runCatching { repository.playedPosition(mediaId) }
                    .getOrDefault(0L)
                if (seconds <= 0L) return@launch

                val durationMs = player.duration.takeIf { it > 0L } ?: 0L
                val intoTail = durationMs > 0L &&
                    durationMs / 1_000L - seconds < PLAYED_TAIL_SECONDS
                if (intoTail) return@launch

                withContext(Dispatchers.Main) {
                    player.seekTo(seconds * 1_000L)
                }
            }
        }
    }

    private fun startVolumeBoostObserver() {
        scope.launch {
            Playback.volumeBoost.collect { enabled ->
                applyVolumeBoost(enabled)
            }
        }
        scope.launch {
            Playback.playbackSpeed.collect { speed ->
                player.setPlaybackSpeed(speed.coerceIn(0.5f, 3.0f))
            }
        }
        scope.launch {
            Playback.skipSilence.collect { enabled ->
                player.skipSilenceEnabled = enabled
            }
        }
    }

    private fun applyVolumeBoost(enabled: Boolean) {
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return

        runCatching {
            if (loudnessEnhancer == null || loudnessSessionId != sessionId) {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(sessionId)
                loudnessSessionId = sessionId
            }
            loudnessEnhancer?.apply {
                setTargetGain(if (enabled) VOLUME_BOOST_MILLIBELS else 0)
                setEnabled(enabled)
            }
        }
    }

    /**
     * Polls the sleep timer.
     *
     * The timer itself holds no coroutine so its rules stay testable; this loop
     * is the only place that turns a deadline into an action.
     */
    private fun startSleepTimerPolling() {
        scope.launch {
            while (isActive) {
                if (sleepTimer.isDue()) {
                    sleepTimer.cancel()
                    player.pause()
                }
                delay(TIMER_POLL_MS)
            }
        }
    }

    /**
     * Writes the current position back to the library while playing.
     *
     * Debounced to one write per [PROGRESS_SAVE_INTERVAL_MS]: persisting on
     * every position callback would hammer the database for no benefit, since a
     * resume only needs second-level accuracy.
     */
    private fun startProgressReporting() {
        scope.launch {
            var lastSaved = -1L
            while (isActive) {
                if (player.isPlaying) {
                    val position = player.currentPosition
                    if (position != lastSaved) {
                        persistProgress(position, player.duration)
                        lastSaved = position
                    }
                }
                delay(PROGRESS_SAVE_INTERVAL_MS)
            }
        }
    }

    private fun persistProgress(positionMs: Long, durationMs: Long) {
        val episodeId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        val durationSeconds = durationMs.takeIf { it > 0L }?.div(1_000L) ?: 0L

        scope.launch(Dispatchers.IO) {
            val repository = runCatching { ServiceLocator.subscriptionRepository() }.getOrNull()
                ?: return@launch

            val finished = durationMs > 0L &&
                (positionMs.toDouble() / durationMs >= PLAYED_THRESHOLD ||
                    durationMs - positionMs <= PLAYED_TAIL_SECONDS * 1_000L)

            if (finished) {
                repository.markPlayed(episodeId)
            } else {
                repository.saveProgress(episodeId, positionMs / 1_000L, durationSeconds)
            }
        }
    }

    companion object {
        private const val TIMER_POLL_MS = 1_000L
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

        /**
         * Amplification applied via [LoudnessEnhancer] when volume boost is on.
         * 500 mB = +5 dB (~1.78x linear amplitude).
         */
        const val VOLUME_BOOST_MILLIBELS = 500
        const val VOLUME_BOOST_GAIN = 1.8f

        /**
         * Builds an item for [episodeUuid].
         *
         * The uuid is carried as the media id so progress can be written back
         * against the right row: the service has no other handle on which
         * episode it is playing, and matching on the URL would break the moment
         * two shows share an enclosure host.
         */
        fun mediaItemFor(
            episodeUuid: String,
            uri: String,
            title: String,
            showTitle: String? = null,
            artworkUrl: String? = null,
        ): MediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(episodeUuid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(showTitle)
                    .apply {
                        artworkUrl?.takeIf { it.isNotBlank() }?.let {
                            setArtworkUri(Uri.parse(it))
                        }
                    }
                    .build(),
            )
            .build()

        /** Past this fraction an episode counts as finished, not part-finished. */
        const val PLAYED_THRESHOLD = 0.95

        /** Never auto-skip the final few seconds; treat the tail as played. */
        const val PLAYED_TAIL_SECONDS = 10L
    }
}
