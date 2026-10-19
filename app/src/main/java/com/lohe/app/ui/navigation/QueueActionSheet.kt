package com.lohe.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.data.QueueEntry
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.LoheSheet

/**
 * Long-press sheet for a queued episode.
 *
 * Reordering and removing used to be four little buttons on every row, which
 * cost each row about forty points of height and pushed the queue off the
 * bottom of the screen. They live here instead, where there is room to say
 * what they do.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueActionSheet(
    entry: QueueEntry,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onTogglePlayed: () -> Unit,
    onRemove: () -> Unit,
) {
    val episode = entry.episode

    LoheSheet(onDismiss = onDismiss) {
        Column {
            // --- Header: which episode these actions belong to ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (episode.artworkUrl != null) {
                    RemoteImage(
                        url = episode.artworkUrl,
                        contentDescription = null,
                        size = 44.dp,
                        cornerRadius = 12.dp,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.showTitle.isNotBlank()) {
                        Text(
                            text = entry.showTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.padding(top = 18.dp))

            val actions = buildList {
                add(
                    SheetAction(
                        icon = Icons.Filled.PlayArrow,
                        label = "Play now",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onPlay,
                    ),
                )
                if (onMoveUp != null) {
                    add(
                        SheetAction(
                            icon = Icons.Filled.KeyboardArrowUp,
                            label = "Move up",
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = onMoveUp,
                        ),
                    )
                }
                if (onMoveDown != null) {
                    add(
                        SheetAction(
                            icon = Icons.Filled.KeyboardArrowDown,
                            label = "Move down",
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = onMoveDown,
                        ),
                    )
                }
                add(
                    SheetAction(
                        icon = Icons.Filled.CheckCircle,
                        label = if (episode.isPlayed) "Mark as unlistened" else "Mark as listened",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onTogglePlayed,
                    ),
                )
            }

            SheetActionGroup(actions)

            // Removing from the queue is the one destructive thing here, so it
            // gets a group of its own rather than sitting among the rest.
            Spacer(Modifier.padding(top = 10.dp))
            SheetActionGroup(
                listOf(
                    SheetAction(
                        icon = Icons.Filled.RemoveCircleOutline,
                        label = "Remove from Up Next",
                        accent = MaterialTheme.colorScheme.error,
                        onClick = onRemove,
                    ),
                ),
            )
        }
    }
}
