package com.lohe.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lohe.app.data.db.entity.EpisodeEntity

/**
 * Long-press action sheet for an episode.
 *
 * Offered wherever a single episode is listed — inside a playlist, in the Up
 * Next queue — so the same set of verbs is available wherever the episode is
 * touched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeActionSheet(
    episode: EpisodeEntity,
    onDismiss: () -> Unit,
    onPlay: () -> Unit = {},
    onAddToUpNext: () -> Unit = {},
    onTogglePlayed: () -> Unit = {},
    onToggleDownload: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SheetAction(
                icon = Icons.Filled.PlayArrow,
                label = "Play now",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onPlay,
            )
            SheetAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "Add to Up Next",
                onClick = onAddToUpNext,
            )
            SheetAction(
                icon = if (episode.isPlayed) Icons.Filled.CheckCircle else Icons.Filled.CheckCircle,
                label = if (episode.isPlayed) "Mark as unlistened" else "Mark as listened",
                onClick = onTogglePlayed,
            )
            SheetAction(
                icon = if (episode.isDownloaded) Icons.Filled.DeleteSweep else Icons.Filled.Download,
                label = if (episode.isDownloaded) "Remove download" else "Download",
                onClick = onToggleDownload,
            )
            SheetAction(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Add to another playlist",
                onClick = onAddToPlaylist,
            )
            if (onRemoveFromPlaylist != null) {
                SheetAction(
                    icon = Icons.Filled.RemoveCircleOutline,
                    label = "Remove from this playlist",
                    onClick = onRemoveFromPlaylist,
                )
            }
            if (onDelete != null) {
                SheetAction(
                    icon = Icons.Filled.DeleteOutline,
                    label = "Delete episode",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
            )
        }
    }
}
