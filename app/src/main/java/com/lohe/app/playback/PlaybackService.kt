package com.lohe.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
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
import kotlinx.coroutines.flow.collectLatest
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
    private var voiceEqualizer: Equalizer? = null
    private var voiceSessionId: Int = C.AUDIO_SESSION_ID_UNSET

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

        // The static handle, so that anything outside the service — the
        // debug control surface, the car app — can reach the same player
        // rather than a second one.
        PlaybackService.player = player

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openAppIntent())
            .build()

        // Expose the platform token so the Android Automotive OS car app can
        // register media playback with its MediaPlaybackManager.
        platformToken = session?.platformToken

        player.addListener(EndOfEpisodeWatcher())
        startVolumeBoostObserver()
        startVoiceEnhancementObserver()
        startSleepTimerPolling()
        startProgressReporting()
        startOutroSkipping()
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
        runCatching { voiceEqualizer?.release() }
        voiceEqualizer = null
        session?.release()
        session = null
        platformToken = null
        PlaybackService.player = null
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
                LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
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
            val from = page.toLong() * pageSize
            val slice = if (page < 0 || pageSize <= 0 || from >= all.size) {
                emptyList()
            } else {
                all.drop(from.toInt()).take(pageSize)
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(slice), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            val item = catalog()?.item(mediaId)
                ?: return@future LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
            LibraryResult.ofItem(item, null)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            val results = catalog()?.search(query).orEmpty()
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid(params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val all = catalog()?.search(query).orEmpty()
            val from = page.toLong() * pageSize
            val slice = if (page < 0 || pageSize <= 0 || from >= all.size) {
                emptyList()
            } else {
                all.drop(from.toInt()).take(pageSize)
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(slice), params)
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
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val catalog = catalog() ?: return@future mutableListOf()
            val resolved = mediaItems.mapNotNull { item ->
                catalog.playable(item.mediaId)
            }
            DevDiagnostics.log(
                "PLAYBACK_LOAD",
                "Resolved ${resolved.size} item(s) in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
            )
            resolved.toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val catalog = catalog()
            val resolved = mediaItems.mapNotNull { catalog?.playable(it.mediaId) }
            if (resolved.isEmpty()) {
                return@future MediaSession.MediaItemsWithStartPosition(
                    resolved, C.INDEX_UNSET, C.TIME_UNSET,
                )
            }
            val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(resolved.indices)
            val selected = resolved[index]
            val position = if (startIndex != C.INDEX_UNSET && startPositionMs > 0L) {
                startPositionMs
            } else {
                val saved = ServiceLocator.subscriptionRepository().playedPosition(selected.mediaId)
                playbackStartPositionMs(saved, selected.mediaMetadata.durationMs ?: 0L)
            }
            DevDiagnostics.log("PLAYBACK_RESUME", "Prepared ${selected.mediaId} at ${position}ms before playback")
            MediaSession.MediaItemsWithStartPosition(resolved, index, position)
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
        private var observedMediaId: String? = null
        private var completingMediaId: String? = null

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
            ) {
                persistProgress(player.currentPosition, player.duration)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val nextId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
            val previousId = observedMediaId
            observedMediaId = nextId
            if (nextId != previousId) {
                completingMediaId = null
                resumedForMediaId = null
            }

            // A multi-item MediaSession queue transitions directly between
            // episodes without reporting STATE_ENDED for each one.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                previousId?.takeIf { it != nextId }?.let {
                    markPlayed(it, "media-item-transition", removeFromQueue = true)
                }
            }
        }

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
                if (playbackState == Player.STATE_READY) configureEpisodeOnce()
                return
            }

            // The sleep timer outranks the queue: the listener asked to stop,
            // so finishing the episode is the end of playback, not a transition.
            if (sleepTimer.state.value.stopsAtEpisodeEnd) {
                sleepTimer.cancel()
                completeEpisode(stopAfterCompletion = true)
                return
            }

            completeEpisode(stopAfterCompletion = false)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            val boostEnabled = Playback.volumeBoost.value
            DevDiagnostics.log(
                "VOLUME_BOOST",
                "audio session changed to $audioSessionId; reapplying boost=$boostEnabled",
            )
            applyVolumeBoost(boostEnabled)
        }

        override fun onPlayerError(error: PlaybackException) {
            val summary = "ERROR code=${error.errorCodeName} mediaId=${player.currentMediaItem?.mediaId} msg=${error.message}"
            DevDiagnostics.playbackStateSummary = summary
            DevDiagnostics.log("EXOPLAYER_ERR", summary, error)
        }

        private fun completeEpisode(stopAfterCompletion: Boolean) {
            val finishedId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
            if (finishedId != null && completingMediaId == finishedId) return
            completingMediaId = finishedId
            scope.launch(Dispatchers.IO) {
                val repository = runCatching { ServiceLocator.subscriptionRepository() }
                    .onFailure { DevDiagnostics.log("PLAYBACK_COMPLETE", "Could not open the episode repository", it) }
                    .getOrNull()

                if (repository != null && finishedId != null) {
                    runCatching { repository.markPlayed(finishedId) }
                        .onSuccess { DevDiagnostics.log("PLAYBACK_COMPLETE", "Marked $finishedId played at episode end") }
                        .onFailure { DevDiagnostics.log("PLAYBACK_COMPLETE", "Failed to mark $finishedId played", it) }
                    runCatching { repository.dequeue(finishedId) }
                        .onFailure { DevDiagnostics.log("PLAYBACK_COMPLETE", "Failed to remove $finishedId from Up Next", it) }
                }

                if (stopAfterCompletion) {
                    withContext(Dispatchers.Main) { player.pause() }
                    return@launch
                }

                if (repository != null) advanceQueue(repository)
            }
        }

        /**
         * Plays whatever is at the head of the queue, or stops if it is empty.
         *
         * The finished episode is dequeued first, so failing to load the next
         * one cannot leave it stuck at the head and wedge the queue.
         */
        private suspend fun advanceQueue(repository: com.lohe.app.data.SubscriptionRepository) {
            val next = repository.queueSnapshot().firstOrNull()
            if (next == null) {
                withContext(Dispatchers.Main) { player.stop() }
                return
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
                    playbackStartPositionMs(next.playedUpToSeconds, (next.durationSeconds?.toLong() ?: 0L) * 1_000L),
                )
                applyShowSettings(next.podcastUuid)
                applyCrossfade(next.uuid)
                player.prepare()
                player.play()
            }
        }

        private fun markPlayed(id: String, source: String, removeFromQueue: Boolean = false) {
            scope.launch(Dispatchers.IO) {
                val repository = runCatching { ServiceLocator.subscriptionRepository() }
                    .onFailure { DevDiagnostics.log("PLAYBACK_COMPLETE", "Could not open repository for $id ($source)", it) }
                    .getOrNull() ?: return@launch
                runCatching {
                    repository.markPlayed(id)
                    if (removeFromQueue) repository.dequeue(id)
                }
                    .onSuccess { DevDiagnostics.log("PLAYBACK_COMPLETE", "Marked $id played ($source)") }
                    .onFailure { DevDiagnostics.log("PLAYBACK_COMPLETE", "Failed to mark $id played ($source)", it) }
            }
        }

        /**
         * Applies show settings once after preparation. The initial resume
         * position is supplied before preparation, never sought after audio starts.
         *
         * Guarded by [resumedForMediaId] because STATE_READY fires repeatedly
         * on buffering recovery, and settings/intro handling should run once.
         */
        private fun configureEpisodeOnce() {
            val item = player.currentMediaItem ?: return
            val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: return
            observedMediaId = mediaId
            if (resumedForMediaId == mediaId) return
            resumedForMediaId = mediaId

            val initialPositionMs = player.currentPosition

            scope.launch(Dispatchers.IO) {
                val repository = runCatching { ServiceLocator.subscriptionRepository() }
                    .getOrNull() ?: return@launch
                // Settings hang off the show, not the episode. Asking for the
                // episode uuid found nothing, so every per-show setting — trim
                // included — was silently falling back to the defaults.
                val showUuid = runCatching { repository.podcastUuidFor(mediaId) }
                    .getOrNull()

                withContext(Dispatchers.Main) {
                    if (player.currentMediaItem?.mediaId != mediaId) return@withContext
                    if (showUuid != null) {
                        applyShowSettings(showUuid)
                    } else {
                        // No show to ask, so nothing may carry over from the
                        // previous episode's settings.
                        _currentAutoTrim = false
                        _currentSkipIntroMs = 0L
                        _currentSkipOutroMs = 0L
                    }

                    // A saved position inside this show's intro is no use: the
                    // listener was still in the bit the show asks to skip, so
                    // start from the top and let the head cut do its work.
                    if (player.currentMediaItem?.mediaId != mediaId) return@withContext
                    val insideIntro = _currentSkipIntroMs > 0L && initialPositionMs < _currentSkipIntroMs
                    onEpisodeStarted(mediaId, startingAtTheTop = initialPositionMs <= 0L || insideIntro)
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
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) {
            DevDiagnostics.log("VOLUME_BOOST", "cannot apply: invalid audio session $sessionId")
            return
        }

        val result = runCatching {
            if (loudnessEnhancer == null || loudnessSessionId != sessionId) {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(sessionId)
                loudnessSessionId = sessionId
            }
            loudnessEnhancer?.also {
                it.setTargetGain(if (enabled) VOLUME_BOOST_MILLIBELS else 0)
                it.enabled = enabled
            }
        }
        result.onSuccess { effect ->
            if (effect == null) {
                DevDiagnostics.log("VOLUME_BOOST", "no enhancer instance for session=$sessionId")
            } else {
                DevDiagnostics.log(
                    "VOLUME_BOOST",
                    "session=$sessionId requested=$enabled actual=${effect.enabled} " +
                        "gain=${effect.targetGain}mB control=${effect.hasControl()}",
                )
            }
        }.onFailure { error ->
            DevDiagnostics.log(
                "VOLUME_BOOST",
                "failed session=$sessionId requested=$enabled: ${error.javaClass.simpleName}: ${error.message}",
            )
        }
    }

    private fun startVoiceEnhancementObserver() {
        scope.launch {
            Playback.voiceEnhancement.collect { enabled ->
                applyVoiceEnhancement(enabled)
            }
        }
    }

    private fun applyVoiceEnhancement(enabled: Boolean) {
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return

        runCatching {
            if (voiceEqualizer == null || voiceSessionId != sessionId) {
                voiceEqualizer?.release()
                voiceEqualizer = Equalizer(0, sessionId)
                voiceSessionId = sessionId
            }
            val eq = voiceEqualizer ?: return@runCatching
            if (enabled) {
                val boostMillibels = 400
                for (i in 0 until eq.numberOfBands) {
                    val center = eq.getCenterFreq(i.toShort()) / 1000
                    if (center in 1000..4000) {
                        eq.setBandLevel(i.toShort(), boostMillibels.toShort())
                    } else {
                        eq.setBandLevel(i.toShort(), 0.toShort())
                    }
                }
                eq.enabled = true
            } else {
                for (i in 0 until eq.numberOfBands) {
                    eq.setBandLevel(i.toShort(), 0.toShort())
                }
                eq.enabled = false
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
            sleepTimer.state.collectLatest { state ->
                if (state.deadlineMs != null) {
                    while (!sleepTimer.isDue()) delay(sleepTimer.remainingMs().coerceAtLeast(1L))
                    sleepTimer.cancel()
                    player.pause()
                }
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

    /**
     * Cuts the last few seconds off an episode when the show asks for it.
     *
     * Outros are a fixed property of a show — a credits roll, a sponsor read —
     * so this is a plain countdown rather than a scan: when the playhead is
     * within [_currentSkipOutroMs] of the end, the episode is finished. The
     * end-of-episode listener then marks it played and moves the queue on, so
     * the cut lands in exactly the same place as a natural ending.
     *
     * Polled at half-second resolution: checking once per progress save (five
     * seconds) would overshoot a short outro on a fast playback speed.
     */
    private fun startOutroSkipping() {
        scope.launch {
            while (isActive) {
                val outroMs = _currentSkipOutroMs
                if (outroMs > 0 && player.isPlaying) {
                    val duration = player.duration
                    if (duration > outroMs && duration - player.currentPosition <= outroMs) {
                        player.seekTo(duration)
                    }
                }
                delay(500)
            }
        }
    }

    /** Currently active per-show head cut (ms), set from per-show settings. */
    private var _currentSkipIntroMs = 0L

    /** Currently active per-show tail cut (ms), set from per-show settings. */
    private var _currentSkipOutroMs = 0L

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
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

        /** Maximum amplification applied by LoudnessEnhancer when volume boost is on. */
        const val VOLUME_BOOST_MILLIBELS = 1_000
        const val VOLUME_BOOST_GAIN = 3.16f

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

        // Apply voice enhancement
        applyVoiceEnhancement(settings.voiceEnhancement)

        // Store crossfade and auto-trim for use on track transitions
        _currentCrossfadeMs = settings.crossfadeMs
        _currentAutoTrim = settings.autoTrim
        _currentSmartSpeed = settings.smartSpeed
        _currentSkipIntroMs = settings.skipIntroSeconds * 1_000L
        _currentSkipOutroMs = settings.skipOutroSeconds * 1_000L
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
        if (!startingAtTheTop) return
        val introMs = _currentSkipIntroMs
        val scanForSilence = _currentAutoTrim
        if (!scanForSilence && introMs <= 0L) return

        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: return
        val uri = item.localConfiguration?.uri?.toString() ?: return
        // A clip barely longer than the scan has nothing worth cutting.
        val durationMs = player.duration
        if (durationMs in 1..TRIM_MIN_DURATION_MS) return

        scope.launch {
            val silenceMs = if (scanForSilence) {
                SilenceScanner.leadingSilenceMs(this@PlaybackService, mediaId, uri) ?: 0L
            } else {
                0L
            }
            // A show with a fixed intro asks for at least that much; the scan
            // can only push the start later, never earlier.
            val targetMs = maxOf(silenceMs, introMs)
            if (targetMs <= 0L) return@launch

            withContext(Dispatchers.Main) {
                // The listener may have moved on, or scrubbed ahead while the
                // scan was running. Their position wins over the trim.
                if (player.currentMediaItem?.mediaId != mediaId) return@withContext
                if (player.currentPosition >= targetMs) return@withContext
                player.seekTo(targetMs)
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
