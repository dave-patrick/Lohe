package com.lohe.app.ui.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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

/**
 * Dedicated "Up Next" tab screen (far right on the bottom bar).
 *
 * Supports full queue reordering (Move Up, Move Down, Move to Top), removing
 * single or multi-selected episodes, clearing from an item or clearing the
 * entire queue, and appending any named Playlist directly into Up Next.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    onPlay: (QueueEntry) -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val queue by viewModel.queue.collectAsState()
    val queuedSeconds by viewModel.queuedSeconds.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    var confirmClearFrom by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var selectedUuids by remember { mutableStateOf(emptySet<String>()) }
    val inSelectionMode = selectedUuids.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        if (inSelectionMode) {
            GlassCard(
                accent = ElectricBlue,
                contentPadding = 12.dp,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedUuids = emptySet() },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Exit selection")
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${selectedUuids.size} selected in Up Next",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        TextButton(
                            onClick = {
                                selectedUuids = if (selectedUuids.size == queue.size) {
                                    emptySet()
                                } else {
                                    queue.map { it.episode.uuid }.toSet()
                                }
                            },
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (selectedUuids.size == queue.size) "Deselect All" else "Select All")
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(
                            onClick = {
                                val count = selectedUuids.size
                                viewModel.dequeueBatch(selectedUuids)
                                selectedUuids = emptySet()
                                onShowMessage("Removed $count from Up Next")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text("Remove from Up Next") },
                        )

                        AssistChip(
                            onClick = {
                                selectedUuids.reversed().forEach { viewModel.promote(it) }
                                selectedUuids = emptySet()
                                onShowMessage("Moved selection to top of Up Next")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.VerticalAlignTop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text("Move to Top") },
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (userPlaylists.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showAddPlaylistDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Add Playlist", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear Up Next",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (queue.isNotEmpty()) {
            Text(
                text = "${queue.size} episode${if (queue.size == 1) "" else "s"} · ${queuedSeconds / 60L} min remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (queue.isEmpty()) {
            EmptyQueue(
                title = "Up Next is empty",
                hint = "Add episodes from any podcast or add an entire Playlist to line them up.",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(queue, key = { _, entry -> entry.episode.uuid }) { index, entry ->
                val isSelected = entry.episode.uuid in selectedUuids
                QueueRow(
                    positionNumber = index + 1,
                    entry = entry,
                    isHead = index == 0,
                    isTail = index == queue.lastIndex,
                    inSelectionMode = inSelectionMode,
                    isSelected = isSelected,
                    onPlay = {
                        if (inSelectionMode) {
                            selectedUuids = if (isSelected) {
                                selectedUuids - entry.episode.uuid
                            } else {
                                selectedUuids + entry.episode.uuid
                            }
                        } else {
                            onPlay(entry)
                        }
                    },
                    onLongPress = {
                        selectedUuids = if (isSelected) {
                            selectedUuids - entry.episode.uuid
                        } else {
                            selectedUuids + entry.episode.uuid
                        }
                    },
                    onMoveUp = if (index > 0) {
                        { viewModel.moveInQueue(entry.episode.uuid, -1) }
                    } else {
                        null
                    },
                    onMoveDown = if (index < queue.lastIndex) {
                        { viewModel.moveInQueue(entry.episode.uuid, +1) }
                    } else {
                        null
                    },
                    onPromote = if (index > 0) {
                        { viewModel.promote(entry.episode.uuid) }
                    } else {
                        null
                    },
                    onRemove = { viewModel.dequeue(entry.episode.uuid) },
                    onClearFrom = if (index == 0 && queue.size > 1) {
                        { confirmClearFrom = entry.episode.uuid }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (showAddPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddPlaylistDialog = false },
            title = { Text("Add Playlist to Up Next") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    userPlaylists.forEach { pl ->
                        GlassCard(
                            onClick = {
                                viewModel.addPlaylistToUpNext(pl.id)
                                showAddPlaylistDialog = false
                                onShowMessage("Added \"${pl.name}\" (${pl.entries.size} episodes) to Up Next")
                            },
                            contentPadding = 12.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${pl.entries.size} ep",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddPlaylistDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear Up Next?") },
            text = { Text("Removes all episodes from your Up Next queue.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllQueue()
                        confirmClearAll = false
                        onShowMessage("Cleared Up Next queue")
                    },
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    confirmClearFrom?.let { uuid ->
        AlertDialog(
            onDismissRequest = { confirmClearFrom = null },
            title = { Text(stringResource(R.string.queue_clear_title)) },
            text = { Text(stringResource(R.string.queue_clear_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearFrom(uuid)
                        confirmClearFrom = null
                    },
                ) { Text(stringResource(R.string.queue_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearFrom = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

/**
 * Dedicated "Playlists" tab screen for creating, editing, rearranging, playing,
 * and adding custom named playlists to the Up Next queue.
 */
@Composable
fun PlaylistsScreen(
    viewModel: QueueViewModel,
    onPlay: (QueueEntry) -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val activePlaylist: ResolvedPlaylist? = userPlaylists.firstOrNull { it.id == selectedPlaylistId }
        ?: userPlaylists.firstOrNull()

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

        if (userPlaylists.isEmpty()) {
            EmptyQueue(
                title = "No playlists yet",
                hint = "Tap \"New Playlist\" or long-press episodes in any podcast to create one.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(userPlaylists, key = { it.id }) { pl ->
                    FilterChip(
                        selected = activePlaylist?.id == pl.id,
                        onClick = { selectedPlaylistId = pl.id },
                        label = { Text("${pl.name} (${pl.entries.size})") },
                    )
                }
            }

            if (activePlaylist != null) {
                GlassCard(
                    contentPadding = 12.dp,
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activePlaylist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${activePlaylist.entries.size} episodes · ${activePlaylist.totalSeconds / 60L} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            IconButton(
                                onClick = {
                                    val name = activePlaylist.name
                                    viewModel.deletePlaylist(activePlaylist.id)
                                    selectedPlaylistId = null
                                    onShowMessage("Deleted playlist \"$name\"")
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete playlist",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        if (activePlaylist.entries.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.addPlaylistToUpNext(activePlaylist.id)
                                        onShowMessage("Added \"${activePlaylist.name}\" (${activePlaylist.entries.size} ep) to Up Next")
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add to Up Next", style = MaterialTheme.typography.labelMedium)
                                }

                                Button(
                                    onClick = {
                                        viewModel.addPlaylistToUpNext(activePlaylist.id)
                                        onPlay(activePlaylist.entries.first())
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play Playlist", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                if (activePlaylist.entries.isEmpty()) {
                    EmptyQueue(
                        title = "\"${activePlaylist.name}\" is empty",
                        hint = "Long-press episodes in any podcast and choose Add to Playlist.",
                        modifier = Modifier.fillMaxSize(),
                    )
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
                                positionNumber = index + 1,
                                entry = entry,
                                isHead = index == 0,
                                isTail = index == activePlaylist.entries.lastIndex,
                                inSelectionMode = false,
                                isSelected = false,
                                onPlay = { onPlay(entry) },
                                onLongPress = {},
                                onMoveUp = if (index > 0) {
                                    {
                                        viewModel.moveEpisodeInPlaylist(
                                            activePlaylist.id,
                                            entry.episode.uuid,
                                            -1,
                                        )
                                    }
                                } else {
                                    null
                                },
                                onMoveDown = if (index < activePlaylist.entries.lastIndex) {
                                    {
                                        viewModel.moveEpisodeInPlaylist(
                                            activePlaylist.id,
                                            entry.episode.uuid,
                                            +1,
                                        )
                                    }
                                } else {
                                    null
                                },
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
                        selectedPlaylistId = created.id
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
        title = { Text("Add ${episodeUuids.size} Episode${if (episodeUuids.size == 1) "" else "s"} to…") },
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
private fun EmptyQueue(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
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
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun QueueRow(
    positionNumber: Int,
    entry: QueueEntry,
    isHead: Boolean,
    isTail: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onPromote: (() -> Unit)?,
    onRemove: () -> Unit,
    onClearFrom: (() -> Unit)?,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 12.dp,
        accent = when {
            isSelected -> ElectricBlue
            isHead -> ElectricBlue.copy(alpha = 0.5f)
            else -> null
        },
        onClick = onPlay,
        onLongClick = onLongPress,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (inSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = "#$positionNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isHead) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                RemoteImage(
                    url = entry.episode.artworkUrl,
                    contentDescription = null,
                    size = 44.dp,
                    cornerRadius = 10.dp,
                    modifier = Modifier
                        .size(44.dp)
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

                if (onPromote != null) {
                    IconButton(onClick = onPromote, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Filled.VerticalAlignTop,
                            contentDescription = "Move to top",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.queue_move_up),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.queue_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onPlay, modifier = Modifier.size(32.dp)) {
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
