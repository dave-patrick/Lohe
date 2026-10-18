package com.lohe.app.ui.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.data.QueueEntry
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.ResolvedPlaylist
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.GlassCard

private const val UP_NEXT_ID = "__UP_NEXT__"

@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    onPlay: (QueueEntry) -> Unit,
) {
    val queue by viewModel.queue.collectAsState()
    val queuedSeconds by viewModel.queuedSeconds.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    var selectedTabId by remember { mutableStateOf(UP_NEXT_ID) }
    var confirmClear by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val activePlaylist: ResolvedPlaylist? = userPlaylists.firstOrNull { it.id == selectedTabId }
    if (selectedTabId != UP_NEXT_ID && activePlaylist == null) {
        selectedTabId = UP_NEXT_ID
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            OutlinedButton(
                onClick = { showCreateDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("New Playlist", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Selector chips: Up Next Queue + every custom user playlist
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedTabId == UP_NEXT_ID,
                    onClick = { selectedTabId = UP_NEXT_ID },
                    label = { Text("Up Next (${queue.size})") },
                )
            }
            items(userPlaylists, key = { it.id }) { pl ->
                FilterChip(
                    selected = selectedTabId == pl.id,
                    onClick = { selectedTabId = pl.id },
                    label = { Text("${pl.name} (${pl.entries.size})") },
                )
            }
        }

        if (selectedTabId == UP_NEXT_ID) {
            if (queue.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.queue_summary, queuedSeconds / 60L),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (queue.isEmpty()) {
                EmptyQueue(modifier = Modifier.fillMaxSize())
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(queue, key = { _, entry -> entry.episode.uuid }) { index, entry ->
                    QueueRow(
                        entry = entry,
                        isHead = index == 0,
                        onPlay = { onPlay(entry) },
                        onPromote = { viewModel.promote(entry.episode.uuid) },
                        onRemove = { viewModel.dequeue(entry.episode.uuid) },
                        onClearFrom = { confirmClear = entry.episode.uuid },
                    )
                }
            }
        } else if (activePlaylist != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${activePlaylist.entries.size} episodes · ${activePlaylist.totalSeconds / 60L} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activePlaylist.entries.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.enqueueAll(activePlaylist.entries.map { it.episode.uuid })
                                onPlay(activePlaylist.entries.first())
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Play All", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    IconButton(
                        onClick = {
                            viewModel.deletePlaylist(activePlaylist.id)
                            selectedTabId = UP_NEXT_ID
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete playlist",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (activePlaylist.entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = "\"${activePlaylist.name}\" is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "Long-press episodes in any podcast to add them here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        activePlaylist.entries,
                        key = { _, entry -> "${activePlaylist.id}-${entry.episode.uuid}" },
                    ) { index, entry ->
                        QueueRow(
                            entry = entry,
                            isHead = index == 0,
                            onPlay = { onPlay(entry) },
                            onPromote = null,
                            onRemove = {
                                viewModel.removeEpisodeFromPlaylist(
                                    activePlaylist.id,
                                    entry.episode.uuid,
                                )
                            },
                            onClearFrom = null,
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val created = viewModel.createPlaylist(newPlaylistName)
                        selectedTabId = created.id
                        newPlaylistName = ""
                        showCreateDialog = false
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newPlaylistName = ""
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    confirmClear?.let { uuid ->
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text(stringResource(R.string.queue_clear_title)) },
            text = { Text(stringResource(R.string.queue_clear_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearFrom(uuid)
                        confirmClear = null
                    },
                ) { Text(stringResource(R.string.queue_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

/**
 * Reusable dialog that adds one or more episodes to the Up Next queue or any
 * custom named playlist (including creating a new playlist on the spot).
 */
@Composable
fun AddToPlaylistDialog(
    episodeUuids: Collection<String>,
    queueViewModel: QueueViewModel,
    onAdded: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val playlists by queueViewModel.userPlaylists.collectAsState()
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${episodeUuids.size} Episode${if (episodeUuids.size == 1) "" else "s"} to Playlist") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassCard(
                    onClick = {
                        queueViewModel.enqueueAll(episodeUuids)
                        onAdded("Added ${episodeUuids.size} to Up Next")
                    },
                    contentPadding = 12.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Up Next Queue",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                playlists.forEach { pl ->
                    GlassCard(
                        onClick = {
                            queueViewModel.addEpisodesToPlaylist(pl.id, episodeUuids)
                            onAdded("Added ${episodeUuids.size} to ${pl.name}")
                        },
                        contentPadding = 12.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Text(
                                text = "${pl.entries.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Or create new playlist…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (newName.isNotBlank()) {
                Button(
                    onClick = {
                        val created = queueViewModel.createPlaylist(newName, episodeUuids)
                        onAdded("Created \"${created.name}\" (${episodeUuids.size} episodes)")
                    },
                ) {
                    Text("Create & Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EmptyQueue(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.queue_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.queue_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    isHead: Boolean,
    onPlay: () -> Unit,
    onPromote: (() -> Unit)?,
    onRemove: () -> Unit,
    onClearFrom: (() -> Unit)?,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 14.dp,
        accent = if (isHead) ElectricBlue.copy(alpha = 0.5f) else null,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RemoteImage(
                    url = entry.episode.artworkUrl,
                    contentDescription = null,
                    size = 46.dp,
                    cornerRadius = 10.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.showTitle.isNotBlank()) {
                        Text(
                            text = entry.showTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (!isHead && onPromote != null) {
                    IconButton(onClick = onPromote, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.queue_move_up),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.queue_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.player_now_playing),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (entry.episode.completionFraction > 0f) {
                LinearProgressIndicator(
                    progress = { entry.episode.completionFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            if (isHead && onClearFrom != null) {
                TextButton(
                    onClick = onClearFrom,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.queue_clear_from_here),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
