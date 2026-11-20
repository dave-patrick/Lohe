package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.concurrent.futures.await
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDatabase
import com.example.widget.LohePlayerWidget
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

data class PlaybackUiState(
    val currentEpisode: EpisodeEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 60_000L,
    val playbackSpeed: Float = 1.0f,
    val skipSilenceEnabled: Boolean = false,
    val volumeBoostDb: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0,
    val sleepAtEndOfEpisode: Boolean = false,
    val errorMessage: String? = null,
    val queueEpisodeIds: List<String> = emptyList()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

@OptIn(UnstableApi::class)
class LohePlayerController(
    context: Context,
    private val scope: CoroutineScope,
    private val onProgressPersist: (episodeId: String, positionMs: Long, durationMs: Long) -> Unit,
    private val onEpisodeCompleted: (episodeId: String) -> Unit
) {
    private val appContext = context.applicationContext
    private val loheDao = LoheDatabase.getInstance(appContext).loheDao()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var observedServicePlayer: Player? = null
    private var pendingEpisodeRequest: Pair<EpisodeEntity, Boolean>? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var shakeExtendEnabled = true
    private var lastShakeTimestampMs = 0L
    private var onSleepExtendedCallback: ((Int) -> Unit)? = null
    private var shakeListener: SensorEventListener? = null

    private val sharedPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val activePlayer = activeTransportPlayer()
            val pos = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: _state.value.positionMs
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                positionMs = pos
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val activePlayer = activeTransportPlayer() ?: return
            syncStateFromActivePlayer(activePlayer)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val activePlayer = activeTransportPlayer() ?: return
            syncStateFromActivePlayer(activePlayer)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val pos = newPosition.positionMs.coerceAtLeast(0L)
            _state.value = _state.value.copy(positionMs = pos)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _state.value = _state.value.copy(playbackSpeed = playbackParameters.speed)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val activePlayer = activeTransportPlayer()
            val buffering = playbackState == Player.STATE_BUFFERING
            val dur = activePlayer?.duration?.takeIf { it > 0 } ?: _state.value.durationMs
            val pos = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: _state.value.positionMs
            _state.value = _state.value.copy(
                isBuffering = buffering,
                positionMs = pos,
                durationMs = dur
            )
            if (playbackState == Player.STATE_ENDED) {
                _state.value.currentEpisode?.let { ep ->
                    onEpisodeCompleted(ep.id)
                }
            }
        }
    }

    init {
        observeInProcessService()
        observeSharedQueueFlow()
        connectToPlaybackService()
    }

    private fun isRobolectricRuntime(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    private fun activeTransportPlayer(): Player? =
        mediaController ?: LohePlaybackService.activeServiceFlow.value?.getPlayer()

    private fun observeSharedQueueFlow() {
        scope.launch {
            LohePlaybackService.sharedQueueIdsFlow.collectLatest { queueIds ->
                if (queueIds.isNotEmpty()) {
                    _state.value = _state.value.copy(queueEpisodeIds = queueIds)
                }
            }
        }
    }

    private fun observeInProcessService() {
        scope.launch {
            LohePlaybackService.activeServiceFlow.collectLatest { service ->
                val prev = observedServicePlayer
                val next = service?.getPlayer()
                if (prev !== next) {
                    prev?.removeListener(sharedPlayerListener)
                    observedServicePlayer = next
                    if (next != null && mediaController == null) {
                        next.addListener(sharedPlayerListener)
                        syncStateFromActivePlayer(next)
                        startProgressTicker()
                        pendingEpisodeRequest?.let { (ep, autoPlay) ->
                            pendingEpisodeRequest = null
                            if (next.mediaItemCount == 0 || autoPlay) {
                                loadIntoPlayer(next, ep, autoPlay)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun connectToPlaybackService() {
        if (isRobolectricRuntime() || controllerFuture != null || mediaController != null) return
        scope.launch {
            try {
                val sessionToken = SessionToken(
                    appContext,
                    ComponentName(appContext, LohePlaybackService::class.java)
                )
                val future = MediaController.Builder(appContext, sessionToken).buildAsync()
                controllerFuture = future
                val controller = future.await()
                mediaController = controller

                observedServicePlayer?.removeListener(sharedPlayerListener)
                controller.addListener(sharedPlayerListener)

                if (controller.mediaItemCount > 0) {
                    syncStateFromActivePlayer(controller)
                }

                // Re-apply audio DSP preferences and load any queued playback request
                controller.playbackParameters = PlaybackParameters(_state.value.playbackSpeed)
                sendSkipSilenceCommand(_state.value.skipSilenceEnabled)
                sendVolumeBoostCommand(_state.value.volumeBoostDb)
                startProgressTicker()

                pendingEpisodeRequest?.let { (ep, autoPlay) ->
                    pendingEpisodeRequest = null
                    if (controller.mediaItemCount == 0 || autoPlay) {
                        loadIntoPlayer(controller, ep, autoPlay)
                    }
                }
            } catch (_: Throwable) {
                // Headless Robolectric / unit-test fallback: uses in-process activeServiceFlow or local state
            }
        }
    }

    private fun syncStateFromActivePlayer(player: Player) {
        val currentIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val currentItem = player.currentMediaItem
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.takeIf { it > 0L } ?: _state.value.durationMs
        val playing = player.isPlaying

        if (currentItem == null) {
            _state.value = _state.value.copy(
                isPlaying = playing,
                queueEpisodeIds = currentIds
            )
            return
        }

        val mediaId = currentItem.mediaId
        if (_state.value.currentEpisode?.id == mediaId) {
            _state.value = _state.value.copy(
                isPlaying = playing,
                positionMs = pos,
                durationMs = dur,
                queueEpisodeIds = currentIds
            )
            return
        }

        scope.launch {
            val resolvedEp = loheDao.getEpisodeById(mediaId)
            if (resolvedEp != null) {
                val effectiveDur = player.duration.takeIf { it > 0L } ?: resolvedEp.durationMs.coerceAtLeast(30_000L)
                _state.value = _state.value.copy(
                    currentEpisode = resolvedEp,
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = effectiveDur,
                    queueEpisodeIds = currentIds
                )
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                val player = activeTransportPlayer()
                val currentEp = _state.value.currentEpisode
                if (player != null && currentEp != null) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it > 0 } ?: currentEp.durationMs
                    if (player.isPlaying || _state.value.isPlaying) {
                        _state.value = _state.value.copy(
                            isPlaying = player.isPlaying,
                            positionMs = pos,
                            durationMs = dur
                        )
                        tickCount++
                        if (tickCount % 4 == 0) {
                            onProgressPersist(currentEp.id, pos, dur)
                        }
                    }
                }
                delay(500L)
            }
        }
    }

    private fun resolveMediaUri(episode: EpisodeEntity): Uri {
        val localFile = episode.localFilePath.takeIf { it.isNotBlank() }?.let(::File)
        return if (localFile != null && localFile.exists() && localFile.length() > 0L) {
            Uri.fromFile(localFile)
        } else if (episode.localFilePath.startsWith("file://") || episode.localFilePath.startsWith("content://")) {
            Uri.parse(episode.localFilePath)
        } else {
            Uri.parse(episode.audioUrl)
        }
    }

    private fun buildPlayableMediaItem(episode: EpisodeEntity): MediaItem {
        val resumePosMs = LohePlaybackService.validResumePositionMs(episode)
        LohePlaybackService.cacheResumePositionMs(episode.id, resumePosMs)

        val extras = Bundle().apply {
            putLong(LohePlaybackService.EXTRA_RESUME_POSITION_MS, resumePosMs)
            putLong(LohePlaybackService.EXTRA_DURATION_MS, episode.durationMs)
            putString(LohePlaybackService.EXTRA_PODCAST_ID, episode.podcastId)
            putBoolean(LohePlaybackService.EXTRA_IS_DOWNLOADED, episode.isDownloaded)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastTitle)
            .setSubtitle(episode.podcastTitle)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setExtras(extras)
            .apply {
                if (episode.artworkUrl.isNotBlank()) {
                    setArtworkUri(Uri.parse(episode.artworkUrl))
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(resolveMediaUri(episode))
            .setMediaMetadata(metadata)
            .build()
    }

    fun loadAndPlayEpisode(episode: EpisodeEntity, autoPlay: Boolean = true) {
        val player = activeTransportPlayer()
        val current = _state.value.currentEpisode

        // If autoPlay is false and the shared service player already has an active item (e.g. from Android Auto),
        // adopt the service player's state rather than overwriting it.
        if (!autoPlay && player != null && player.mediaItemCount > 0) {
            syncStateFromActivePlayer(player)
            return
        }

        if (current?.id == episode.id && player != null && player.mediaItemCount > 0 &&
            player.currentMediaItem?.mediaId == episode.id
        ) {
            if (autoPlay) {
                player.prepare()
                player.play()
                _state.value = _state.value.copy(isPlaying = true)
            }
            return
        }

        val startPosMs = LohePlaybackService.validResumePositionMs(episode)
        LohePlaybackService.cacheResumePositionMs(episode.id, startPosMs)

        _state.value = _state.value.copy(
            currentEpisode = episode,
            positionMs = startPosMs,
            durationMs = episode.durationMs.coerceAtLeast(30_000L),
            isPlaying = autoPlay,
            errorMessage = null
        )
        pushWidgetSnapshot()

        if (player != null) {
            loadIntoPlayer(player, episode, autoPlay)
        } else {
            pendingEpisodeRequest = episode to autoPlay
            connectToPlaybackService()
        }
    }

    private fun pushWidgetSnapshot() {
        val snapshot = _state.value
        val ep = snapshot.currentEpisode ?: return
        scope.launch {
            LohePlayerWidget.syncSnapshotAndUpdate(
                context = appContext,
                episodeId = ep.id,
                episodeTitle = ep.title,
                podcastTitle = ep.podcastTitle,
                isPlaying = snapshot.isPlaying,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs
            )
        }
    }

    private fun loadIntoPlayer(player: Player, episode: EpisodeEntity, autoPlay: Boolean) {
        scope.launch {
            try {
                val upNext = runCatching { loheDao.getUpNextEpisodes().first() }.getOrDefault(emptyList())
                val queueEpisodes = if (upNext.any { it.id == episode.id }) {
                    upNext.map { if (it.id == episode.id) episode else it }
                } else {
                    listOf(episode) + upNext
                }
                val queueItems = queueEpisodes.map(::buildPlayableMediaItem)
                val startIndex = queueEpisodes.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
                val startPositionMs = LohePlaybackService.validResumePositionMs(episode)

                // Supply the saved resume position directly in setMediaItems BEFORE prepare/play,
                // never by seeking after STATE_READY.
                player.setMediaItems(queueItems, startIndex, startPositionMs)
                player.playbackParameters = PlaybackParameters(_state.value.playbackSpeed)
                sendSkipSilenceCommand(_state.value.skipSilenceEnabled)
                sendVolumeBoostCommand(_state.value.volumeBoostDb)
                _state.value = _state.value.copy(
                    positionMs = startPositionMs,
                    queueEpisodeIds = queueEpisodes.map { it.id }
                )

                if (autoPlay) {
                    player.prepare()
                    player.play()
                } else {
                    player.playWhenReady = false
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    fun togglePlayPause() {
        val player = activeTransportPlayer()
        val ep = _state.value.currentEpisode ?: return
        if (player == null) {
            _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
            pushWidgetSnapshot()
            return
        }
        if (player.mediaItemCount == 0) {
            loadAndPlayEpisode(ep, autoPlay = true)
            return
        }
        if (player.isPlaying) {
            player.pause()
            _state.value = _state.value.copy(isPlaying = false)
            onProgressPersist(ep.id, _state.value.positionMs, _state.value.durationMs)
        } else {
            player.prepare()
            player.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
        pushWidgetSnapshot()
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _state.value.durationMs)
        activeTransportPlayer()?.seekTo(clamped)
        _state.value = _state.value.copy(positionMs = clamped)
        _state.value.currentEpisode?.let { ep ->
            LohePlaybackService.cacheResumePositionMs(ep.id, clamped)
            onProgressPersist(ep.id, clamped, _state.value.durationMs)
        }
    }

    fun skipBySeconds(deltaSeconds: Int) {
        val target = (_state.value.positionMs + deltaSeconds * 1000L)
            .coerceIn(0L, _state.value.durationMs)
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        val valid = speed.coerceIn(0.5f, 3.0f)
        activeTransportPlayer()?.playbackParameters = PlaybackParameters(valid)
        _state.value = _state.value.copy(playbackSpeed = valid)
    }

    fun setSkipSilence(enabled: Boolean) {
        _state.value = _state.value.copy(skipSilenceEnabled = enabled)
        sendSkipSilenceCommand(enabled)
    }

    fun setVolumeBoostDb(db: Int) {
        _state.value = _state.value.copy(volumeBoostDb = db)
        sendVolumeBoostCommand(db)
    }

    private fun sendSkipSilenceCommand(enabled: Boolean) {
        LohePlaybackService.activeServiceFlow.value?.setSkipSilenceDirect(enabled)
        val controller = mediaController ?: return
        val args = Bundle().apply { putBoolean(LohePlaybackService.EXTRA_ENABLED, enabled) }
        runCatching {
            controller.sendCustomCommand(
                SessionCommand(LohePlaybackService.ACTION_SET_SKIP_SILENCE, Bundle.EMPTY),
                args
            )
        }
    }

    private fun sendVolumeBoostCommand(db: Int) {
        LohePlaybackService.activeServiceFlow.value?.setVolumeBoostDirect(db)
        val controller = mediaController ?: return
        val args = Bundle().apply { putInt(LohePlaybackService.EXTRA_BOOST_DB, db) }
        runCatching {
            controller.sendCustomCommand(
                SessionCommand(LohePlaybackService.ACTION_SET_VOLUME_BOOST, Bundle.EMPTY),
                args
            )
        }
    }

    fun configureShakeToExtend(enabled: Boolean, onExtended: ((Int) -> Unit)? = null) {
        shakeExtendEnabled = enabled
        onSleepExtendedCallback = onExtended
        if (!enabled || _state.value.sleepTimerRemainingSeconds <= 0) {
            unregisterShakeSensor()
        } else if (enabled && _state.value.sleepTimerRemainingSeconds > 0) {
            registerShakeSensor()
        }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _state.value = _state.value.copy(
                sleepTimerRemainingSeconds = 0,
                sleepAtEndOfEpisode = false
            )
            unregisterShakeSensor()
            return
        }
        val totalSeconds = minutes * 60
        _state.value = _state.value.copy(
            sleepTimerRemainingSeconds = totalSeconds,
            sleepAtEndOfEpisode = false
        )
        startSleepCountdownJob()
        if (shakeExtendEnabled) registerShakeSensor()
    }

    fun setSleepAtEndOfEpisode(enabled: Boolean) {
        sleepTimerJob?.cancel()
        unregisterShakeSensor()
        _state.value = _state.value.copy(
            sleepTimerRemainingSeconds = 0,
            sleepAtEndOfEpisode = enabled
        )
    }

    fun consumeSleepAtEndOfEpisode(): Boolean {
        val armed = _state.value.sleepAtEndOfEpisode
        if (armed) {
            _state.value = _state.value.copy(sleepAtEndOfEpisode = false, isPlaying = false)
            activeTransportPlayer()?.pause()
            pushWidgetSnapshot()
        }
        return armed
    }

    fun extendSleepTimerMinutes(extraMinutes: Int = 5) {
        val current = _state.value.sleepTimerRemainingSeconds
        if (current <= 0 && !_state.value.sleepAtEndOfEpisode) return
        val updated = (current + extraMinutes * 60).coerceAtLeast(extraMinutes * 60)
        sleepTimerJob?.cancel()
        _state.value = _state.value.copy(
            sleepTimerRemainingSeconds = updated,
            sleepAtEndOfEpisode = false
        )
        startSleepCountdownJob()
        onSleepExtendedCallback?.invoke(updated)
    }

    private fun startSleepCountdownJob() {
        sleepTimerJob = scope.launch {
            while (_state.value.sleepTimerRemainingSeconds > 0 && isActive) {
                delay(1000L)
                val next = (_state.value.sleepTimerRemainingSeconds - 1).coerceAtLeast(0)
                _state.value = _state.value.copy(sleepTimerRemainingSeconds = next)
            }
            if (_state.value.sleepTimerRemainingSeconds <= 0) {
                unregisterShakeSensor()
                activeTransportPlayer()?.pause()
                _state.value = _state.value.copy(isPlaying = false, sleepTimerRemainingSeconds = 0)
                pushWidgetSnapshot()
            }
        }
    }

    private fun registerShakeSensor() {
        if (isRobolectricRuntime() || shakeListener != null) return
        val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!shakeExtendEnabled || _state.value.sleepTimerRemainingSeconds <= 0) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (gForce > 1.85 && now - lastShakeTimestampMs > 3000L) {
                    lastShakeTimestampMs = now
                    extendSleepTimerMinutes(5)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        shakeListener = listener
        runCatching {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterShakeSensor() {
        val listener = shakeListener ?: return
        shakeListener = null
        val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        runCatching { sm.unregisterListener(listener) }
    }

    fun release() {
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        unregisterShakeSensor()
        observedServicePlayer?.removeListener(sharedPlayerListener)
        observedServicePlayer = null
        mediaController?.removeListener(sharedPlayerListener)
        mediaController = null
        controllerFuture?.let { future ->
            runCatching { MediaController.releaseFuture(future) }
        }
        controllerFuture = null
    }
}
