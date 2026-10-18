package com.lohe.app.ui.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.ui.image.RemoteImage

/**
 * Modal bottom sheet presenting an episode's show notes, artwork, and metadata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeNotesSheet(
    title: String,
    showTitle: String,
    artworkUrl: String?,
    description: String?,
    durationSeconds: Int? = null,
    onPlay: (() -> Unit)? = null,
    onAddToUpNext: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RemoteImage(
                    url = artworkUrl,
                    contentDescription = null,
                    size = 64.dp,
                    cornerRadius = 14.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "Episode Details" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showTitle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = showTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    durationSeconds?.takeIf { it > 0 }?.let { secs ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Duration · ${formatTimestamp(secs * 1_000L)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (onPlay != null || onAddToUpNext != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onPlay != null) {
                        Button(
                            onClick = {
                                onPlay()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Play")
                        }
                    }
                    if (onAddToUpNext != null) {
                        OutlinedButton(
                            onClick = {
                                onAddToUpNext()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Add to Up Next")
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            Text(
                text = "SHOW NOTES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = description?.takeIf { it.isNotBlank() }
                        ?: "No show notes available for this episode yet. Use \"Check for new episodes\" in My Podcasts to refresh feed descriptions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
