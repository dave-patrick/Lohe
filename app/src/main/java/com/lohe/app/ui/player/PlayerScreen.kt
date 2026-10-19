package com.lohe.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.playback.Playback
import com.lohe.app.playback.PlaybackService
import com.lohe.app.playback.PlayerConnection
import com.lohe.app.playback.rememberTransportState
import com.lohe.app.ui.theme.Appearance
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.ElectricCyan
import com.lohe.app.ui.theme.LoheTheme
import com.lohe.app.ui.theme.artworkBleed
import com.lohe.app.ui.theme.glassPanel
import com.lohe.app.ui.image.RemoteImage
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private val ArtTint = Color(0xFF1E4FD8)

/**
 * Player screen.
 *
 * The player itself lives in [PlaybackService]; this screen observes it through
 * a [PlayerConnection]. That split is why playback survives navigating away —
 * an ExoPlayer owned by a composable would be destroyed the moment the
 * composable leaves the composition.
 */
@Composable
fun PlayerScreen(
    /**
     * The session handle, shared with the rest of the nav host.
     *
     * Passed in rather than created here: a second `PlayerConnection` means a
     * second MediaController on the same session, so the now-playing bar and
     * this screen would observe independent transport state and disagree about
     * whether anything is playing.
     */
    connection: PlayerConnection,
    appearance: Appearance,
    onShrink: () -> Unit = {},
    onEnded: () -> Unit = {},
    /** Media to play. Null leaves whatever is already loaded alone. */
    mediaUri: String? = null,
    /** Library row this media belongs to, used to persist resume position. */
    mediaEpisodeId: String? = null,
    /** Artwork for the current episode. */
    mediaArtworkUrl: String? = null,
    /** Shown under the artwork; falls back to the loaded item's title. */
    mediaTitle: String = "",
    mediaShowTitle: String = "",
    mediaDescription: String? = null,
    /** Skip amounts, from user settings. */
    skipBackMs: Long = 10_000L,
    skipForwardMs: Long = 30_000L,
    /**
     * Length from the feed, used until the player reports its own.
     *
     * ExoPlayer leaves [androidx.media3.common.C.TIME_UNSET] in place while
     * media prepares, and the scrubber renders 00:00 for that window. The feed
     * already told us the length, so the timeline does not have to be blank.
     */
    knownDurationMs: Long? = null,
    /** Whether playback starts amplified. */
    volumeBoostDefault: Boolean = false,
    onPersistSpeed: (Float) -> Unit = {},
    onPersistSkipSilence: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val transport by rememberTransportState(connection)
    val sleepTimer = Playback.sleepTimer
    val sleepTimerState by Playback.sleepState.collectAsState()
    val playbackSpeed by Playback.playbackSpeed.collectAsState()
    val skipSilence by Playback.skipSilence.collectAsState()
    var sleepRemainingMs by remember { mutableLongStateOf(0L) }
    var volumeBoostEnabled by rememberSaveable { mutableStateOf(volumeBoostDefault) }
    var sleepTimerOpen by remember { mutableStateOf(false) }
    var speedSheetOpen by remember { mutableStateOf(false) }
    var notesSheetOpen by remember { mutableStateOf(false) }

    val resolvedArtworkUrl = mediaArtworkUrl?.takeIf { it.isNotBlank() }
        ?: transport.artworkUrl.takeIf { it.isNotBlank() }

    // The timer owns its deadline; this only re-reads the remaining time often
    // enough for the sheet's countdown to move.
    LaunchedEffect(sleepTimerOpen) {
        while (sleepTimerOpen) {
            sleepRemainingMs = sleepTimer.remainingMs()
            delay(1_000)
        }
    }

    // Loading a new item is a one-shot per URI; re-running it on recomposition
    // would restart the episode every time the transport poll ticks.
    LaunchedEffect(mediaUri) {
        val uri = mediaUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val itemId = mediaEpisodeId?.takeIf { it.isNotBlank() } ?: uri

        // Returning to this screen disposes and recreates it, which re-enters
        // this effect. Without this guard, coming back from Settings restarted
        // the episode from zero.
        val controller = connection.controller
        if (controller != null &&
            controller.mediaItemCount > 0 &&
            controller.currentMediaItem?.mediaId == itemId
        ) {
            return@LaunchedEffect
        }

        connection.play(
            PlaybackService.mediaItemFor(
                episodeUuid = itemId,
                uri = uri,
                title = mediaTitle.ifBlank { transport.title },
                showTitle = mediaShowTitle.takeIf { it.isNotBlank() },
                artworkUrl = resolvedArtworkUrl,
            ),
        )
    }

    LaunchedEffect(volumeBoostEnabled) {
        connection.setVolumeBoost(volumeBoostEnabled)
    }

    LaunchedEffect(playbackSpeed) {
        connection.setPlaybackSpeed(playbackSpeed)
    }

    val shownTitle = mediaTitle.ifBlank { transport.title }
        .ifBlank { stringResource(R.string.player_nothing_queued) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .artworkBleed(tint = ArtTint),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeaderRow(
                onShrink = onShrink,
                onOpenSleepTimer = { sleepTimerOpen = true },
                onOpenNotes = { notesSheetOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            )

            Spacer(Modifier.height(6.dp))

            // Artwork expands to fill almost the entire screen between the
            // header and the playback controls dock.
            ArtworkTile(
                isLight = appearance == Appearance.LIGHT,
                artworkUrl = resolvedArtworkUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(Modifier.height(12.dp))

            EpisodeTitle(
                isLight = appearance == Appearance.LIGHT,
                title = shownTitle,
                showTitle = mediaShowTitle.ifBlank { transport.showTitle },
                onClick = { notesSheetOpen = true },
            )

            Spacer(Modifier.height(6.dp))

            Scrubber(
                progress = transport.progress,
                positionMs = transport.positionMs,
                // The player's own duration wins once it exists; the feed's
                // length stands in until then.
                durationMs = transport.durationMs.takeIf { it > 0L }
                    ?: knownDurationMs
                    ?: 0L,
                playbackSpeed = playbackSpeed,
                onSeek = connection::seekToFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            )

            Spacer(Modifier.height(2.dp))

            TransportRow(
                isPlaying = transport.isPlaying,
                enabled = transport.hasMedia,
                playbackSpeed = playbackSpeed,
                onTogglePlay = connection::togglePlayPause,
                onSkipBack = { connection.skipBackMs(skipBackMs) },
                onSkipForward = { connection.skipForwardMs(skipForwardMs) },
                onVolumeBoostToggle = { volumeBoostEnabled = !volumeBoostEnabled },
                onOpenSpeedSheet = { speedSheetOpen = true },
                volumeBoostEnabled = volumeBoostEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    if (sleepTimerOpen) {
        SleepTimerSheet(
            current = sleepTimerState,
            remainingMs = sleepRemainingMs,
            onArm = sleepTimer::start,
            onCancel = sleepTimer::cancel,
            onDismiss = { sleepTimerOpen = false },
        )
    }

    if (speedSheetOpen) {
        PlaybackSpeedSheet(
            currentSpeed = playbackSpeed,
            skipSilenceEnabled = skipSilence,
            onSelectSpeed = { speed ->
                connection.setPlaybackSpeed(speed)
                onPersistSpeed(speed)
            },
            onToggleSkipSilence = { enabled ->
                connection.setSkipSilence(enabled)
                onPersistSkipSilence(enabled)
            },
            onDismiss = { speedSheetOpen = false },
        )
    }

    if (notesSheetOpen) {
        val totalDurSecs = ((transport.durationMs.takeIf { it > 0L } ?: knownDurationMs ?: 0L) / 1_000L).toInt()
        EpisodeNotesSheet(
            title = shownTitle,
            showTitle = mediaShowTitle.ifBlank { transport.showTitle },
            artworkUrl = resolvedArtworkUrl,
            description = mediaDescription,
            durationSeconds = totalDurSecs.takeIf { it > 0 },
            onDismiss = { notesSheetOpen = false },
        )
    }
}
@Composable
private fun HeaderRow(
    onShrink: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onShrink)
                .padding(end = 8.dp),
        ) {
            IconButton(
                onClick = onShrink,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Shrink player",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = stringResource(R.string.player_now_playing).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onOpenNotes,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Show notes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            IconButton(
                onClick = onOpenSleepTimer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Bedtime,
                    contentDescription = stringResource(R.string.sleep_timer_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtworkTile(
    isLight: Boolean,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .border(
                width = 1.dp,
                color = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(26.dp),
            )
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0B1230),
                        Color(0xFF14285C),
                        Color(0xFF1E4FD8),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(
            url = artworkUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EpisodeTitle(
    isLight: Boolean,
    title: String,
    showTitle: String,
    onClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = showTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Scrubber(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While dragging we show the finger, not the player: the position poller
    // keeps ticking underneath and would otherwise yank the thumb backwards.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    // Tapping either side of the timeline toggles between raw file time and
    // real wall-clock time adjusted for the current playback speed.
    var showElapsedAdjusted by rememberSaveable { mutableStateOf(false) }
    var showRemainingAdjusted by rememberSaveable { mutableStateOf(false) }

    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val shownMs = if (dragging) (dragValue * durationMs).toLong() else positionMs

    val leftText = if (showElapsedAdjusted) {
        val adj = adjustedElapsedMs(shownMs, playbackSpeed)
        "${formatTimestamp(adj)} (${formatSpeedLabel(playbackSpeed)})"
    } else {
        formatTimestamp(shownMs)
    }

    val rightText = if (showRemainingAdjusted) {
        val rem = adjustedRemainingMs(shownMs, durationMs, playbackSpeed)
        "-${formatTimestamp(rem)} (${formatSpeedLabel(playbackSpeed)})"
    } else {
        formatTimestamp(durationMs)
    }

    Column(modifier = modifier) {
        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek(dragValue)
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = ElectricBlue,
                activeTrackColor = ElectricBlue,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = leftText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (showElapsedAdjusted) FontWeight.SemiBold else FontWeight.Normal,
                color = if (showElapsedAdjusted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showElapsedAdjusted = !showElapsedAdjusted }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                text = rightText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (showRemainingAdjusted) FontWeight.SemiBold else FontWeight.Normal,
                color = if (showRemainingAdjusted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showRemainingAdjusted = !showRemainingAdjusted }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** Real wall-clock remaining time at [speed] (ms). */
internal fun adjustedRemainingMs(positionMs: Long, durationMs: Long, speed: Float): Long {
    val safeSpeed = speed.takeIf { it > 0f } ?: 1f
    val rawRemaining = (durationMs - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    return (rawRemaining / safeSpeed).toLong()
}

/** Real wall-clock elapsed time at [speed] (ms). */
internal fun adjustedElapsedMs(positionMs: Long, speed: Float): Long {
    val safeSpeed = speed.takeIf { it > 0f } ?: 1f
    return (positionMs.coerceAtLeast(0L) / safeSpeed).toLong()
}

/** Compact speed badge such as `1×`, `1.25×`, `1.5×`. */
internal fun formatSpeedLabel(speed: Float): String {
    val rounded = (speed * 100).roundToInt() / 100f
    return when {
        rounded % 1f == 0f -> String.format(Locale.US, "%.0f×", rounded)
        (rounded * 10f) % 1f == 0f -> String.format(Locale.US, "%.1f×", rounded)
        else -> String.format(Locale.US, "%.2f×", rounded)
    }
}

/** mm:ss, or h:mm:ss past an hour. Negative/zero renders as 00:00. */
internal fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    enabled: Boolean,
    playbackSpeed: Float,
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onVolumeBoostToggle: () -> Unit,
    onOpenSpeedSheet: () -> Unit,
    volumeBoostEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onVolumeBoostToggle,
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (volumeBoostEnabled) {
                    Icons.AutoMirrored.Filled.VolumeUp
                } else {
                    Icons.AutoMirrored.Filled.VolumeMute
                },
                contentDescription = stringResource(
                    if (volumeBoostEnabled) R.string.player_volume_boost_on
                    else R.string.player_volume_boost_off,
                ),
                tint = if (volumeBoostEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }

        // Center cluster: Replay10 right next to Play, and Forward30 right next to Play.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            IconButton(
                onClick = onSkipBack,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = stringResource(R.string.player_skip_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(
                        if (enabled) {
                            Brush.linearGradient(listOf(ElectricBlue, ElectricCyan))
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    enabled = enabled,
                    modifier = Modifier.size(68.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.player_now_playing),
                        tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            IconButton(
                onClick = onSkipForward,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward30,
                    contentDescription = stringResource(R.string.player_skip_forward),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (playbackSpeed != 1f) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                )
                .clickable { onOpenSpeedSheet() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatSpeedLabel(playbackSpeed),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (playbackSpeed != 1f) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private val SPEED_PRESETS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSpeedSheet(
    currentSpeed: Float,
    skipSilenceEnabled: Boolean,
    onSelectSpeed: (Float) -> Unit,
    onToggleSkipSilence: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Playback Speed · ${formatSpeedLabel(currentSpeed)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val next = ((currentSpeed - 0.05f) * 20).roundToInt() / 20f
                        onSelectSpeed(next.coerceIn(0.5f, 3.0f))
                    },
                ) {
                    Text("-0.05×")
                }

                Slider(
                    value = currentSpeed.coerceIn(0.5f, 3.0f),
                    onValueChange = { raw ->
                        val stepped = (raw * 20).roundToInt() / 20f
                        onSelectSpeed(stepped.coerceIn(0.5f, 3.0f))
                    },
                    valueRange = 0.5f..3.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue,
                    ),
                )

                OutlinedButton(
                    onClick = {
                        val next = ((currentSpeed + 0.05f) * 20).roundToInt() / 20f
                        onSelectSpeed(next.coerceIn(0.5f, 3.0f))
                    },
                ) {
                    Text("+0.05×")
                }
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SPEED_PRESETS.forEach { preset ->
                    val selected = kotlin.math.abs(currentSpeed - preset) < 0.01f
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSpeed(preset) },
                        label = { Text(formatSpeedLabel(preset)) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Skip Silence",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Automatically trim dead air and long pauses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = skipSilenceEnabled,
                    onCheckedChange = onToggleSkipSilence,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// --- Previews --------------------------------------------------------------
//
// These render the static layout only. PlayerScreen binds to PlaybackService,
// which does not exist in the preview renderer, so the transport reads as
// disconnected and the controls render disabled — which is also the honest
// preview of the empty state.

// A preview cannot bind a real MediaSession, so it gets a detached handle:
// controller stays null, the transport renders its empty snapshot, and nothing
// tries to play. Enough to check layout and type.
@Preview(name = "Player — dark", showBackground = true)
@Composable
private fun PlayerDarkPreview() {
    val context = LocalContext.current
    LoheTheme(appearance = Appearance.DARK) {
        PlayerScreen(
            connection = PlayerConnection.create(context),
            appearance = Appearance.DARK,
        )
    }
}

@Preview(name = "Player — light", showBackground = true)
@Composable
private fun PlayerLightPreview() {
    val context = LocalContext.current
    LoheTheme(appearance = Appearance.LIGHT) {
        PlayerScreen(
            connection = PlayerConnection.create(context),
            appearance = Appearance.LIGHT,
        )
    }
}
