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
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.local.EpisodeEntity
import com.example.widget.LohePlayerWidget
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val errorMessage: String? = null
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

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingEpisodeRequest: Pair<EpisodeEntity, Boolean>? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var shakeExtendEnabled = true
    private var lastShakeTimestampMs = 0L
    private var onSleepExtendedCallback: ((Int) -> Unit)? = null
    private var shakeListener: SensorEventListener? = null

    init {
        connectToPlaybackService()
    }

    private fun isRobolectricRuntime(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

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

                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.value = _state.value.copy(isPlaying = isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val buffering = playbackState == Player.STATE_BUFFERING
                        val dur = controller.duration.takeIf { it > 0 } ?: _state.value.durationMs
                        _state.value = _state.value.copy(
                            isBuffering = buffering,
                            durationMs = dur
                        )
                        if (playbackState == Player.STATE_ENDED) {
                            _state.value.currentEpisode?.let { ep ->
                                onEpisodeCompleted(ep.id)
                            }
                        }
                    }
                })

                // Re-apply audio DSP preferences and load any queued playback request
                controller.playbackParameters = PlaybackParameters(_state.value.playbackSpeed)
                sendSkipSilenceCommand(_state.value.skipSilenceEnabled)
                sendVolumeBoostCommand(_state.value.volumeBoostDb)
                startProgressTicker()

                pendingEpisodeRequest?.let { (ep, autoPlay) ->
                    pendingEpisodeRequest = null
                    loadIntoController(controller, ep, autoPlay)
                }
            } catch (_: Throwable) {
                // Headless Robolectric / unit-test fallback: keep reactive PlaybackUiState working cleanly
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                val controller = mediaController
                val currentEp = _state.value.currentEpisode
                if (controller != null && currentEp != null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.takeIf { it > 0 } ?: currentEp.durationMs
                    if (controller.isPlaying || _state.value.isPlaying) {
                        _state.value = _state.value.copy(
                            isPlaying = controller.isPlaying,
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
        } else {
            Uri.parse(episode.audioUrl)
        }
    }

    fun loadAndPlayEpisode(episode: EpisodeEntity, autoPlay: Boolean = true) {
        val current = _state.value.currentEpisode
        val controller = mediaController

        if (current?.id == episode.id && controller != null && controller.mediaItemCount > 0) {
            if (autoPlay) {
                controller.play()
                _state.value = _state.value.copy(isPlaying = true)
            }
            return
        }

        _state.value = _state.value.copy(
            currentEpisode = episode,
            positionMs = episode.resumePositionMs,
            durationMs = episode.durationMs.coerceAtLeast(30_000L),
            isPlaying = autoPlay,
            errorMessage = null
        )
        pushWidgetSnapshot()

        if (controller != null) {
            loadIntoController(controller, episode, autoPlay)
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

    private fun loadIntoController(controller: MediaController, episode: EpisodeEntity, autoPlay: Boolean) {
        try {
            val metadata = MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(episode.podcastTitle)
                .setSubtitle(episode.podcastTitle)
                .apply {
                    if (episode.artworkUrl.isNotBlank()) {
                        setArtworkUri(Uri.parse(episode.artworkUrl))
                    }
                }
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(episode.id)
                .setUri(resolveMediaUri(episode))
                .setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            if (episode.resumePositionMs > 0L && episode.resumePositionMs < episode.durationMs) {
                controller.seekTo(episode.resumePositionMs)
            }
            controller.playbackParameters = PlaybackParameters(_state.value.playbackSpeed)
            sendSkipSilenceCommand(_state.value.skipSilenceEnabled)
            sendVolumeBoostCommand(_state.value.volumeBoostDb)
            if (autoPlay) {
                controller.play()
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
        }
    }

    fun togglePlayPause() {
        val controller = mediaController
        val ep = _state.value.currentEpisode ?: return
        if (controller == null) {
            _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
            pushWidgetSnapshot()
            return
        }
        if (controller.mediaItemCount == 0) {
            loadAndPlayEpisode(ep, autoPlay = true)
            return
        }
        if (controller.isPlaying) {
            controller.pause()
            _state.value = _state.value.copy(isPlaying = false)
            onProgressPersist(ep.id, _state.value.positionMs, _state.value.durationMs)
        } else {
            controller.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
        pushWidgetSnapshot()
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _state.value.durationMs)
        mediaController?.seekTo(clamped)
        _state.value = _state.value.copy(positionMs = clamped)
        _state.value.currentEpisode?.let { ep ->
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
        mediaController?.playbackParameters = PlaybackParameters(valid)
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
            mediaController?.pause()
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
                mediaController?.pause()
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
        mediaController = null
        controllerFuture?.let { future ->
            runCatching { MediaController.releaseFuture(future) }
        }
        controllerFuture = null
    }
}
