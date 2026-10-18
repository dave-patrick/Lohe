package com.lohe.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
    onToggleAppearance: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val transport by rememberTransportState(connection)
    val sleepTimer = Playback.sleepTimer
    val sleepTimerState by Playback.sleepState.collectAsState()
    var sleepRemainingMs by remember { mutableLongStateOf(0L) }
    var volumeBoostEnabled by rememberSaveable { mutableStateOf(volumeBoostDefault) }
    var sleepTimerOpen by remember { mutableStateOf(false) }

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
            ),
        )
    }

    LaunchedEffect(volumeBoostEnabled) {
        connection.setVolumeBoost(volumeBoostEnabled)
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
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeaderRow(
                appearance = appearance,
                onToggleAppearance = onToggleAppearance,
                onOpenSleepTimer = { sleepTimerOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))

            ArtworkTile(
                isLight = appearance == Appearance.LIGHT,
                artworkUrl = mediaArtworkUrl,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
            )

            Spacer(Modifier.height(28.dp))

            EpisodeTitle(
                isLight = appearance == Appearance.LIGHT,
                title = shownTitle,
                showTitle = mediaShowTitle,
            )

            Spacer(Modifier.height(20.dp))

            Scrubber(
                progress = transport.progress,
                positionMs = transport.positionMs,
                // The player's own duration wins once it exists; the feed's
                // length stands in until then.
                durationMs = transport.durationMs.takeIf { it > 0L }
                    ?: knownDurationMs
                    ?: 0L,
                onSeek = connection::seekToFraction,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            TransportRow(
                isPlaying = transport.isPlaying,
                enabled = transport.hasMedia,
                onTogglePlay = connection::togglePlayPause,
                onSkipBack = { connection.skipBackMs(skipBackMs) },
                onSkipForward = { connection.skipForwardMs(skipForwardMs) },
                onVolumeBoostToggle = { volumeBoostEnabled = !volumeBoostEnabled },
                volumeBoostEnabled = volumeBoostEnabled,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))
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
}
@Composable
private fun HeaderRow(
    appearance: Appearance,
    onToggleAppearance: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = appearance == Appearance.LIGHT
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.player_now_playing).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
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

            IconButton(
                onClick = onToggleAppearance,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .glassPanel(isLight = isLight, shape = RoundedCornerShape(20.dp)),
            ) {
                Icon(
                    imageVector = if (isLight) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                    contentDescription = stringResource(
                        if (isLight) R.string.theme_label_dark else R.string.theme_label_light,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
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
            .clip(RoundedCornerShape(22.dp))
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
private fun EpisodeTitle(isLight: Boolean, title: String, showTitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While dragging we show the finger, not the player: the position poller
    // keeps ticking underneath and would otherwise yank the thumb backwards.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val shownMs = if (dragging) (dragValue * durationMs).toLong() else positionMs

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
        ) {
            Text(
                text = formatTimestamp(shownMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTimestamp(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onVolumeBoostToggle: () -> Unit,
    volumeBoostEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSkipBack, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Replay10,
                contentDescription = stringResource(R.string.player_skip_back),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onVolumeBoostToggle,
            enabled = enabled,
            modifier = Modifier.size(28.dp),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.weight(0.5f))

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
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onSkipForward, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Forward30,
                contentDescription = stringResource(R.string.player_skip_forward),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
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
            onToggleAppearance = {},
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
            onToggleAppearance = {},
        )
    }
}
