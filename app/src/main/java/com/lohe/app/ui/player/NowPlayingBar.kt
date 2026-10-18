package com.lohe.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
 * Persistent transport strip above the bottom bar.
 *
 * A podcast player that hides playback the moment you navigate away makes you
 * stop what you are doing every time you want to check another tab, so the
 * current episode stays reachable from anywhere in the app.
 *
 * Renders nothing when there is no media, rather than an empty bar.
 */
@Composable
fun NowPlayingBar(
    connection: PlayerConnection,
    transport: TransportState,
    artworkUrl: String?,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!transport.hasMedia) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LoheAccent.glassFill)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(LoheAccent.glassBorder, Color.Transparent),
                ),
                shape = RoundedCornerShape(0.dp),
            )
            .clickable(onClick = onOpenPlayer),
    ) {
        LinearProgressIndicator(
            progress = { transport.progress },
            color = ElectricBlue,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(
                url = artworkUrl,
                contentDescription = null,
                size = 40.dp,
                cornerRadius = 8.dp,
                modifier = Modifier.artworkBleed(tint = ElectricBlue),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = transport.title.ifBlank {
                        stringResource(R.string.player_nothing_queued)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = transport.showTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = connection::togglePlayPause) {
                Icon(
                    imageVector = if (transport.isPlaying) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = stringResource(R.string.player_now_playing),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
