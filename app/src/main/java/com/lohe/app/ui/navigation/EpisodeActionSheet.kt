package com.lohe.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.ui.theme.LoheSheet
import com.lohe.app.data.db.entity.EpisodeSummary
import com.lohe.app.ui.image.RemoteImage

/**
 * Long-press action sheet for an episode.
 *
 * Offered wherever a single episode is listed — inside a playlist, in the Up
 * Next queue — so the same set of verbs is available wherever the episode is
 * touched.
 *
 * The layout is deliberately quiet: one icon tile per action, the label
 * sitting right next to it, and the destructive action separated at the
 * bottom so it cannot be hit by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeActionSheet(
    episode: EpisodeSummary,
    artworkUrl: String? = null,
    showTitle: String = "",
    onDismiss: () -> Unit,
    onPlay: () -> Unit = {},
    onAddToUpNext: () -> Unit = {},
    onTogglePlayed: () -> Unit = {},
    onToggleDownload: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState()

    LoheSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
    ) {
        Column {
            // --- Header: which episode these actions belong to ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (artworkUrl != null) {
                    RemoteImage(
                        url = artworkUrl,
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
                    if (showTitle.isNotBlank()) {
                        Text(
                            text = showTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.padding(top = 18.dp))

            // --- Actions, grouped in a single quiet surface ---
            val actions = buildList {
                add(
                    SheetAction(
                        icon = Icons.Filled.PlayArrow,
                        label = "Play now",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onPlay,
                    ),
                )
                add(
                    SheetAction(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        label = "Add to Up Next",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onAddToUpNext,
                    ),
                )
                add(
                    SheetAction(
                        icon = Icons.Filled.CheckCircle,
                        label = if (episode.isPlayed) "Mark as unlistened" else "Mark as listened",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onTogglePlayed,
                    ),
                )
                add(
                    SheetAction(
                        icon = if (episode.isDownloaded) Icons.Filled.DeleteSweep else Icons.Filled.Download,
                        label = if (episode.isDownloaded) "Remove download" else "Download",
                        accent = MaterialTheme.colorScheme.onSurface,
                        onClick = onToggleDownload,
                    ),
                )
                add(
                    SheetAction(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        label = "Add to another playlist",
                        accent = MaterialTheme.colorScheme.onSurface,
                        onClick = onAddToPlaylist,
                    ),
                )
                if (onRemoveFromPlaylist != null) {
                    add(
                        SheetAction(
                            icon = Icons.Filled.RemoveCircleOutline,
                            label = "Remove from this playlist",
                            accent = MaterialTheme.colorScheme.onSurface,
                            onClick = onRemoveFromPlaylist,
                        ),
                    )
                }
            }

            SheetActionGroup(actions)

            if (onDelete != null) {
                Spacer(Modifier.padding(top = 10.dp))
                SheetActionGroup(
                    listOf(
                        SheetAction(
                            icon = Icons.Filled.DeleteOutline,
                            label = "Delete episode",
                            accent = MaterialTheme.colorScheme.error,
                            onClick = onDelete,
                        ),
                    ),
                )
            }
        }
    }
}
