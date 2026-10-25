package com.lohe.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
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
import com.lohe.app.ui.theme.backgroundIsLight

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

    val isLight = backgroundIsLight()
    val shape = RoundedCornerShape(36.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Draw the accent separately behind the capsule so the glow is visible
        // outside its edge and never mottles the frosted surface.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 2.dp, vertical = 3.dp)
                .blur(18.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(ElectricBlue.copy(alpha = 0.68f), shape),
        )
        Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .background(
                if (isLight) LoheAccent.barGlassFillLight else LoheAccent.barGlassFill,
            )
            // A restrained, even satin sheen keeps the panel glassy without
            // the patchy colored wash of the previous horizontal gradient.
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.045f), Color.Transparent),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        (if (isLight) LoheAccent.barGlassEdgeLight else LoheAccent.barGlassEdge),
                        Color.Transparent,
                    ),
                ),
                shape = shape,
            )
            .clickable(onClick = onOpenPlayer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(
                url = artworkUrl,
                contentDescription = null,
                size = 40.dp,
                cornerRadius = 12.dp,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
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
                modifier = Modifier
                    .size(40.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(27.dp)
                        .shadow(
                            elevation = 7.dp,
                            shape = CircleShape,
                            ambientColor = ElectricBlue.copy(alpha = 0.58f),
                            spotColor = ElectricBlue.copy(alpha = 0.82f),
                        )
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF68C9FF), ElectricBlue, Color(0xFF1742A5)),
                            ),
                            shape = CircleShape,
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.68f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (transport.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.player_now_playing),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            IconButton(
                onClick = onOpenPlayer,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Expand player",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The progress line lives along the bottom edge of the bar.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        ) {
            val barHeight = size.height
            drawRoundRect(
                color = ElectricBlue,
                size = Size(size.width * shownProgress.coerceIn(0f, 1f), barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
            )
        }
        }
    }
}
