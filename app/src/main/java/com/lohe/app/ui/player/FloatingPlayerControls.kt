package com.lohe.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lohe.app.playback.PlayerConnection
import com.lohe.app.playback.TransportState
import com.lohe.app.ui.theme.LoheAccent

/**
 * A floating playback control that stays on top of every screen.
 *
 * Collapsed it is a single accent play/pause button at the bottom right. Tapping
 * it expands an accent row — skip back · play/pause · skip forward — centred above
 * it, with a collapse chevron, so the most-used transport actions are always
 * one tap away without opening the full player.
 */
@Composable
fun FloatingPlayerControls(
    connection: PlayerConnection,
    transport: TransportState,
    modifier: Modifier = Modifier,
) {
    // Nothing to control yet.
    if (!transport.hasMedia) return

    var expanded by remember { mutableStateOf(false) }
    val isPlaying = transport.isPlaying

    val controlAccent = MaterialTheme.colorScheme.primary
    val collapsedSize = 56.dp
    val buttonSize = 44.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp, end = 20.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Expanded: skip back | play/pause · skip forward | collapse chevron
        if (expanded) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable(onClick = { expanded = false }),
                shape = RoundedCornerShape(28.dp),
                color = LoheAccent.glassFill,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { connection.skipBackMs(15_000) },
                        modifier = Modifier.size(buttonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Skip back 15 seconds",
                            tint = controlAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { connection.togglePlayPause() },
                        modifier = Modifier.size(buttonSize),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = controlAccent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(
                        onClick = { connection.skipForwardMs(30_000) },
                        modifier = Modifier.size(buttonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Skip forward 30 seconds",
                            tint = controlAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { expanded = false },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Collapse controls",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Collapsed: a single floating play/pause button at the bottom right.
        if (!expanded) {
            Surface(
                color = controlAccent,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 14.dp, end = 4.dp)
                    .size(collapsedSize)
                    .clickable(onClick = { expanded = true }),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Expand playback controls",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}
