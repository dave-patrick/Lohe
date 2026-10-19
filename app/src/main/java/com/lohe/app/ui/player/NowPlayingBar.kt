package com.lohe.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.playback.PlayerConnection
import com.lohe.app.playback.TransportState
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.LoheAccent
import com.lohe.app.ui.theme.artworkBleed

/**
 * Persistent mini Now Playing card shown above the bottom bar on every screen.
 *
 * Clicking anywhere on the card expands the full-screen [PlayerScreen]; the
 * full-screen player's collapse chevron shrinks it back down to this card.
 */
@Composable
fun NowPlayingBar(
    connection: PlayerConnection,
    transport: TransportState,
    artworkUrl: String?,
    fallbackTitle: String? = null,
    fallbackShowTitle: String? = null,
    fallbackProgress: Float = 0f,
    nextUpTitle: String? = null,
    nextUpShowTitle: String? = null,
    onPlayFallback: (() -> Unit)? = null,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Visible when something is playing, when there is something to resume,
    // or when the queue has an episode ready to play next.
    val hasActive = transport.hasMedia ||
        !fallbackTitle.isNullOrBlank() ||
        !nextUpTitle.isNullOrBlank()
    if (!hasActive) return

    val shownTitle = transport.title.ifBlank {
        fallbackTitle?.takeIf { it.isNotBlank() } ?: nextUpTitle.orEmpty()
    }
    val shownShow = transport.showTitle.ifBlank { fallbackShowTitle.orEmpty() }
    val shownProgress = if (transport.hasMedia) transport.progress else fallbackProgress

    // The "next up" line is only meaningful when something else is playing
    // now; when the queue head is the title itself, showing it twice is noise.
    val nextUpLabel = nextUpTitle?.takeIf { it.isNotBlank() && it != shownTitle }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(shape)
            .background(LoheAccent.glassFill)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(LoheAccent.glassBorder, Color.Transparent),
                ),
                shape = shape,
            )
            .clickable(onClick = onOpenPlayer),
    ) {
        LinearProgressIndicator(
            progress = { shownProgress.coerceIn(0f, 1f) },
            color = ElectricBlue,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(
                url = artworkUrl,
                contentDescription = null,
                size = 42.dp,
                cornerRadius = 10.dp,
                modifier = Modifier.artworkBleed(tint = ElectricBlue),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = shownTitle.ifBlank {
                        stringResource(R.string.player_nothing_queued)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (shownShow.isNotBlank()) {
                    Text(
                        text = shownShow,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The episode that plays once this one finishes, so the queue
                // is visible without a trip to the Up Next tab.
                if (!nextUpLabel.isNullOrBlank()) {
                    Text(
                        text = "Next up · $nextUpLabel",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(
                onClick = {
                    if (transport.hasMedia) {
                        connection.togglePlayPause()
                    } else {
                        onPlayFallback?.invoke() ?: onOpenPlayer()
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (transport.isPlaying) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = stringResource(R.string.player_now_playing),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.width(2.dp))

            IconButton(
                onClick = onOpenPlayer,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Expand player",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
