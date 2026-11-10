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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.ui.theme.LoheSheet
import com.lohe.app.ui.formatDuration
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.navigation.formatBytes
import com.lohe.app.ui.navigation.EpisodeMeta

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
    published: Long? = null,
    sizeBytes: Long? = null,
    onPlay: (() -> Unit)? = null,
    onAddToUpNext: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val notes = description?.takeIf { it.isNotBlank() }
        ?: "No show notes available for this episode yet. Use \"Check for new episodes\" in My Podcasts to refresh feed descriptions."
    val linkColor = MaterialTheme.colorScheme.primary
    val renderedNotes = remember(notes, linkColor) {
        val html = when {
            Regex("<\\s*/?\\s*[a-zA-Z][^>]*>").containsMatchIn(notes) -> notes
            Regex("&(?:#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]+);").containsMatchIn(notes) ->
                notes.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
            else -> null
        }
        if (html != null) {
            AnnotatedString.fromHtml(
                htmlString = html,
                linkStyles = TextLinkStyles(
                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ),
            )
        } else {
            AnnotatedString(notes)
        }
    }
    LoheSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
                        .clip(MaterialTheme.shapes.large)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), MaterialTheme.shapes.large),
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

                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                published?.takeIf { it > 0 }?.let {
                    Text(
                        text = "Published · ${EpisodeMeta.fullPublishedDate(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                durationSeconds?.takeIf { it > 0 }?.let {
                    Text(
                        text = "Duration · ${formatDuration(it.toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sizeBytes?.takeIf { it > 0 }?.let {
                    Text(
                        text = "File size · ${formatBytes(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onPlay != null || onAddToUpNext != null || onArchive != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (onPlay != null) {
                        FilledIconButton(
                            onClick = {
                                onPlay()
                                onDismiss()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (onAddToUpNext != null) {
                        OutlinedIconButton(
                            onClick = {
                                onAddToUpNext()
                                onDismiss()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Add to Up Next",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (onArchive != null) {
                        OutlinedIconButton(
                            onClick = { onArchive(); onDismiss() },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Archive, contentDescription = "Archive",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
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
                    text = renderedNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
