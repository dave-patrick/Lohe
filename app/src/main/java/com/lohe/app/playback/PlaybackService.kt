package com.lohe.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lohe.app.MainActivity
import com.lohe.app.data.ServiceLocator
import com.lohe.app.dev.DevDiagnostics
import com.lohe.app.ui.theme.ShowPlaybackSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
 *
 * It is a [MediaLibraryService] rather than a plain session service so that
 * Android Auto has something to browse. A session on its own is a player with
 * no library: the car can play what the app hands it, but there is no way to
 * choose what that is. The browse tree is in [LibraryCatalog].
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
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

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openAppIntent())
            .build()

        // Expose the platform token so the Android Automotive OS car app can
        // register media playback with its MediaPlaybackManager.
        platformToken = session?.platformToken

        player.addListener(EndOfEpisodeWatcher())
        startVolumeBoostObserver()
        startSleepTimerPolling()
        startProgressReporting()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

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
        platformToken = null
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
     * The browse tree Android Auto walks.
     *
     * Every method here is called by a browser — Android Auto, Car Assistant,
     * a smartwatch, the system notification shade — and none of them may block
     * the main thread, so the work happens off it and the answer arrives as a
     * future.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            val root = catalog()?.rootItem()
            if (root == null) {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            } else {
                LibraryResult.ofItem(root, params)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val all = catalog()?.children(parentId).orEmpty()
            val from = page * pageSize
            val slice = if (from >= all.size) {
                emptyList()
            } else {
                all.subList(from, minOf(from + pageSize, all.size))
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(slice), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            val item = catalog()?.item(mediaId)
                ?: return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            LibraryResult.ofItem(item, null)
        }

        /**
         * Turns the ids a browser sends back into playable episodes.
         *
         * A browser holds only what it was given — an id, a title and
         * whatever artwork it cached — and the URI is deliberately not part of
         * it. So the episode is looked up again here and rebuilt with the
         * address, the show and the artwork the player needs.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = future {
            val catalog = catalog() ?: return@future mutableListOf()
            val shows = catalog.showsByUuid()
            val resolved = mediaItems.mapNotNull { item ->
                catalog.playable(item.mediaId, shows)
            }
            resolved.toMutableList()
        }
    }

    /**
     * The catalogue, built on demand.
     *
     * The service can be started by the system before anything in the app has
     * run, so the repository is resolved lazily and a failure to resolve it
     * leaves the car with an empty library rather than a crashed service.
     */
    private fun catalog(): LibraryCatalog? {
        val repository = runCatching { ServiceLocator.subscriptionRepository() }
            .getOrNull() ?: return null
        val playlists = runCatching { ServiceLocator.playlistRepository() }
            .getOrNull() ?: return null
        return LibraryCatalog(repository, playlists)
    }

    /**
     * Runs a piece of work off the main thread and hands the answer back as
     * a future, which is what a browse callback has to return.
     */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch(Dispatchers.IO) {
            try {
                future.set(block())
            } catch (e: Throwable) {
                future.setException(e)
            }
        }
        return future
    }

    /**
     * Ends playback when a [SleepTimer.Mode.EndOfEpisode] timer is armed and
     * this episode finishes, restores a saved position, and rolls on to the next
     * queued episode when the queue is not empty.
     */
    private inner class EndOfEpisodeWatcher : Player.Listener {
        private var resumedForMediaId: String? = null

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN($playbackState)"
            }
            val summary = "state=$stateName playWhenReady=${player.playWhenReady} mediaId=${player.currentMediaItem?.mediaId} pos=${player.currentPosition}ms dur=${player.duration}ms"
            DevDiagnostics.playbackStateSummary = summary
            DevDiagnostics.log("EXOPLAYER", summary)

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

        override fun onPlayerError(error: PlaybackException) {
            val summary = "ERROR code=${error.errorCodeName} mediaId=${player.currentMediaItem?.mediaId} msg=${error.message}"
            DevDiagnostics.playbackStateSummary = summary
            DevDiagnostics.log("EXOPLAYER_ERR", summary, error)
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
                    applyShowSettings(next.podcastUuid)
                    applyCrossfade(next.uuid)
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

            // player.duration must be read on the main thread (ExoPlayer requirement).
            // resumeOnce() is called from onPlaybackStateChanged which runs on Main.
            val durationMs = player.duration.takeIf { it > 0L } ?: 0L

            scope.launch(Dispatchers.IO) {
                val repository = runCatching { ServiceLocator.subscriptionRepository() }
                    .getOrNull() ?: return@launch
                val saved = runCatching { repository.playedPosition(mediaId) }
                    .getOrDefault(0L)

                val intoTail = durationMs > 0L &&
                    durationMs / 1_000L - saved < PLAYED_TAIL_SECONDS
                val resumeSeconds = if (saved <= 0L || intoTail) 0L else saved

                // Settings hang off the show, not the episode. Asking for the
                // episode uuid found nothing, so every per-show setting — trim
                // included — was silently falling back to the defaults.
                val showUuid = runCatching { repository.podcastUuidFor(mediaId) }
                    .getOrNull()

                withContext(Dispatchers.Main) {
                    if (resumeSeconds > 0L) player.seekTo(resumeSeconds * 1_000L)
                    if (showUuid != null) applyShowSettings(showUuid)
                    onEpisodeStarted(mediaId, startingAtTheTop = resumeSeconds <= 0L)
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

        /** The ExoPlayer, exposed so the Android Automotive OS car app can drive it. */
        var player: ExoPlayer? = null
            private set

        /** The platform token, exposed so the car app can register playback. */
        var platformToken: android.media.session.MediaSession.Token? = null
            private set

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

        /**
         * Episodes shorter than this are left alone: the head scan alone would
         * eat a noticeable share of them.
         */
        private const val TRIM_MIN_DURATION_MS = 60_000L
    }

    /**
     * Applies per-show playback settings to the player.
     *
     * Reads the per-show settings from [ServiceLocator.settingsStore] and
     * applies them to the player: volume boost, skip silence, playback speed,
     * smart speed, crossfade, and auto-trim.
     */
    private suspend fun applyShowSettings(podcastUuid: String) {
        val settings = ServiceLocator.settingsStore()
            .showPlaybackSettings(podcastUuid)
            .first()
            ?: ShowPlaybackSettings.EMPTY

        // Apply playback speed
        player.setPlaybackSpeed(settings.playbackSpeed.coerceIn(0.5f, 3.0f))

        // Apply skip silence
        player.skipSilenceEnabled = settings.skipSilence || settings.smartSpeed

        // Apply volume boost
        applyVolumeBoost(settings.volumeBoost)

        // Store crossfade and auto-trim for use on track transitions
        _currentCrossfadeMs = settings.crossfadeMs
        _currentAutoTrim = settings.autoTrim
        _currentSmartSpeed = settings.smartSpeed
    }

    /** Currently active crossfade duration (ms), set from per-show settings. */
    private var _currentCrossfadeMs = 0
    /** Currently active auto-trim flag, set from per-show settings. */
    private var _currentAutoTrim = false
    /** Currently active smart speed flag, set from per-show settings. */
    private var _currentSmartSpeed = false

    /**
     * Crossfade between episodes: fades out the current episode over
     * [crossfadeMs] milliseconds while fading in the next. Called when a
     * new episode starts playing.
     */
    private fun applyCrossfade(nextEpisodeUuid: String) {
        val crossfadeMs = _currentCrossfadeMs
        if (crossfadeMs <= 0) return

        // Fade out current volume over crossfadeMs milliseconds.
        val steps = 20
        val stepMs = crossfadeMs / steps
        scope.launch(Dispatchers.Main) {
            for (i in 0 until steps) {
                val progress = i.toFloat() / steps
                val volume = 1.0f - progress
                player.setVolume(volume)
                delay(stepMs.toLong())
            }
            // New episode is already playing; fade in complete
            player.setVolume(1.0f)
        }
    }

    /**
     * Auto-trim: drops the dead air at the top of an episode that starts from
     * the beginning.
     *
     * The head is measured, not assumed. Trimming means skipping the silence
     * before the talking starts, which differs from show to show and is often
     * only a second or two; a flat thirty-second cut chopped real content
     * whenever the cold open was short. [SilenceScanner] decodes the opening
     * and reports where the speech begins.
     *
     * It only makes sense to cut the head off a fresh start. Running it on a
     * resume threw the listener past wherever they had left off, which is why
     * it looked like trimming "did not work at the beginning": the only path
     * that reached it was the resume path.
     */
    private fun applyAutoTrim(startingAtTheTop: Boolean) {
        if (!_currentAutoTrim || !startingAtTheTop) return
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: return
        val uri = item.localConfiguration?.uri?.toString() ?: return
        // A clip barely longer than the scan has nothing worth cutting.
        val durationMs = player.duration
        if (durationMs in 1..TRIM_MIN_DURATION_MS) return

        scope.launch {
            val silenceMs = SilenceScanner.leadingSilenceMs(this@PlaybackService, mediaId, uri)
                ?: return@launch
            if (silenceMs <= 0L) return@launch

            withContext(Dispatchers.Main) {
                // The listener may have moved on, or scrubbed ahead while the
                // scan was running. Their position wins over the trim.
                if (player.currentMediaItem?.mediaId != mediaId) return@withContext
                if (player.currentPosition >= silenceMs) return@withContext
                player.seekTo(silenceMs)
            }
        }
    }

    /**
     * Called when a new episode starts playing. Applies crossfade, and the
     * auto-trim when the episode is starting from the beginning.
     */
    private fun onEpisodeStarted(episodeUuid: String, startingAtTheTop: Boolean = false) {
        applyCrossfade(episodeUuid)
        applyAutoTrim(startingAtTheTop)
    }
}
