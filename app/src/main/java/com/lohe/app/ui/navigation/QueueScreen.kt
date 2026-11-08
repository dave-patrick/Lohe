package com.lohe.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import com.lohe.app.ui.theme.QueueIndicatorSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.data.QueueEntry
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.ResolvedPlaylist
import com.lohe.app.ui.player.EpisodeNotesSheet
import com.lohe.app.ui.formatDuration
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.GlassCard
import com.lohe.app.ui.theme.LoheDialog

/**
 * Dedicated "Up Next" tab screen (far right on the bottom bar).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    onPlay: (QueueEntry) -> Unit,
    currentEpisodeId: String? = null,
    onShowMessage: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    queueIndicator: QueueIndicatorSettings = QueueIndicatorSettings(),
    onArchiveEpisode: (String) -> Unit = { viewModel.archiveEpisode(it) },
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val autoDownloadUpNext by viewModel.autoDownloadUpNext.collectAsStateWithLifecycle()
    val upcomingQueue = remember(queue, currentEpisodeId) {
        queue.filterNot { it.episode.uuid == currentEpisodeId }
    }
    val upcomingSeconds = upcomingQueue.sumOf { (it.episode.durationSeconds ?: 0).toLong() }
    val now = remember { System.currentTimeMillis() }
    val scope = rememberCoroutineScope()

    var confirmClearAll by remember { mutableStateOf(false) }
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var playlistToAdd by remember { mutableStateOf<ResolvedPlaylist?>(null) }
    var selectedUuids by remember { mutableStateOf(emptySet<String>()) }
    var actionEpisode by remember { mutableStateOf<QueueEntry?>(null) }
    var notesEpisode by remember { mutableStateOf<QueueEntry?>(null) }
    var addToPlaylistEpisode by remember { mutableStateOf<QueueEntry?>(null) }
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
                                selectedUuids = if (selectedUuids.size == upcomingQueue.size) {
                                    emptySet()
                                } else {
                                    upcomingQueue.map { it.episode.uuid }.toSet()
                                }
                            },
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (selectedUuids.size == upcomingQueue.size) "Deselect All" else "Select All")
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
                                val selected = selectedUuids.toList()
                                scope.launch {
                                    viewModel.promoteBatch(selected).join()
                                    selectedUuids = emptySet()
                                    onShowMessage("Moved selection to top of Up Next")
                                }
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // The header carries the queue's own actions and nothing
                    // else. Auto-download is a setting and lives in Settings;
                    // adding a whole playlist to the queue is rare enough to
                    // sit behind an overflow.
                    var menuOpen by remember { mutableStateOf(false) }

                    if (upcomingQueue.isNotEmpty()) {
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear Up Next",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            if (userPlaylists.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Add Playlist to Up Next") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        showAddPlaylistDialog = true
                                    },
                                )
                            }
                        }
                    }

                    HeaderSettingsSpacer()
                }
            }
        }

        if (upcomingQueue.isNotEmpty()) {
            Text(
                text = "${upcomingQueue.size} episode${if (upcomingQueue.size == 1) "" else "s"} next · ${formatDuration(upcomingSeconds)} remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (upcomingQueue.isEmpty()) {
            EmptyQueue(
                title = if (currentEpisodeId.isNullOrBlank()) "Up Next is empty" else "Nothing queued next",
                hint = if (currentEpisodeId.isNullOrBlank()) {
                    "Add episodes from any podcast or add an entire Playlist to line them up."
                } else {
                    "Add episodes from any podcast or playlist to play after this one."
                },
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        // The list the screen actually draws. It is a copy of the queue so a
        // dragged row can be moved around without writing to the database on
        // every pixel of the drag; the new order is written once, on drop.
        var items by remember { mutableStateOf(upcomingQueue) }
        val listState = rememberLazyListState()
        val dragState = rememberDragReorderState(
            listState = listState,
            onMove = { from, to ->
                items = items.toMutableList().apply { add(to, removeAt(from)) }
            },
            onDrop = {
                viewModel.reorderQueue(items.map { it.episode.uuid })
            },
        )

        // Keep the copy in step with the queue, unless a drag is in progress
        // and the list is deliberately out of step with the database.
        LaunchedEffect(upcomingQueue) {
            if (!dragState.isDragging) items = upcomingQueue
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(items, key = { _, entry -> entry.episode.uuid }) { index, entry ->
                val isSelected = entry.episode.uuid in selectedUuids
                val dragging = dragState.draggedIndex == index
                QueueRow(
                    positionNumber = index + 1,
                    showPositionNumber = false,
                    entry = entry,
                    queueIndicator = queueIndicator,
                    isHead = index == 0,
                    inSelectionMode = inSelectionMode,
                    isSelected = isSelected,
                    now = now,
                    dragHandle = {
                        DragHandle(state = dragState, index = index, key = entry.episode.uuid)
                    },
                    dragHandleAtStart = true,
                    modifier = Modifier
                        .then(
                            if (dragging) {
                                Modifier.zIndex(1f)
                            } else {
                                Modifier.animateItem()
                            },
                        )
                        .graphicsLayer {
                            if (dragging) {
                                translationY = dragState.draggedOffset
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        },
                    onPlay = {
                        if (inSelectionMode) {
                            selectedUuids = if (isSelected) {
                                selectedUuids - entry.episode.uuid
                            } else {
                                selectedUuids + entry.episode.uuid
                            }
                        } else {
                            items = listOf(entry) + items.filterNot {
                                it.episode.uuid == entry.episode.uuid
                            }
                            scope.launch {
                                viewModel.reorderQueue(items.map { it.episode.uuid }).join()
                                onPlay(entry)
                            }
                        }
                    },
                    onOpen = {
                        if (inSelectionMode) {
                            selectedUuids = if (isSelected) {
                                selectedUuids - entry.episode.uuid
                            } else {
                                selectedUuids + entry.episode.uuid
                            }
                        } else {
                            notesEpisode = entry
                        }
                    },
                    onLongPress = {
                        if (inSelectionMode) {
                            selectedUuids = if (isSelected) {
                                selectedUuids - entry.episode.uuid
                            } else {
                                selectedUuids + entry.episode.uuid
                            }
                        } else {
                            actionEpisode = entry
                        }
                    },
                    onMoveUp = if (index > 0) {
                        { viewModel.moveInQueue(entry.episode.uuid, -1) }
                    } else {
                        null
                    },
                    onMoveDown = if (index < items.lastIndex) {
                        { viewModel.moveInQueue(entry.episode.uuid, +1) }
                    } else {
                        null
                    },
                    onTogglePlayed = {
                        if (entry.episode.isPlayed) {
                            viewModel.reopenEpisode(entry.episode.uuid)
                            onShowMessage("Marked unlistened")
                        } else {
                            viewModel.markPlayed(entry.episode.uuid)
                            onShowMessage("Marked listened")
                        }
                    },
                    onRemove = { viewModel.dequeue(entry.episode.uuid) },
                    onMoveToTop = if (index > 0) {
                        { viewModel.promote(entry.episode.uuid) }
                    } else null,
                    onMoveToEnd = if (index < items.lastIndex) {
                        { viewModel.moveToEndInQueue(entry.episode.uuid) }
                    } else null,
                )
            }
        }
    }

    notesEpisode?.let { entry ->
        var description by remember(entry.episode.uuid) { mutableStateOf<String?>(null) }
        LaunchedEffect(entry.episode.uuid) {
            description = viewModel.episodeDescription(entry.episode.uuid)
        }
        EpisodeNotesSheet(
            title = entry.episode.title,
            showTitle = entry.showTitle,
            artworkUrl = entry.episode.artworkUrl,
            description = description,
            durationSeconds = entry.episode.durationSeconds,
            published = entry.episode.published,
            sizeBytes = entry.episode.sizeBytes,
            onArchive = if (entry.episode.isArchived) null else ({ onArchiveEpisode(entry.episode.uuid) }),
            onPlay = {
                scope.launch {
                    viewModel.promote(entry.episode.uuid).join()
                    onPlay(entry)
                }
            },
            onDismiss = { notesEpisode = null },
        )
    }

    actionEpisode?.let { entry ->
        val index = queue.indexOfFirst { it.episode.uuid == entry.episode.uuid }
        QueueActionSheet(
            entry = entry,
            onDismiss = { actionEpisode = null },
            onPlay = {
                actionEpisode = null
                scope.launch {
                    viewModel.promote(entry.episode.uuid).join()
                    onPlay(entry)
                }
            },
            onMoveUp = if (index > 0) {
                {
                    actionEpisode = null
                    viewModel.moveInQueue(entry.episode.uuid, -1)
                }
            } else {
                null
            },
            onMoveDown = if (index in 0 until queue.lastIndex) {
                {
                    actionEpisode = null
                    viewModel.moveInQueue(entry.episode.uuid, +1)
                }
            } else {
                null
            },
            onTogglePlayed = {
                actionEpisode = null
                if (entry.episode.isPlayed) {
                    viewModel.reopenEpisode(entry.episode.uuid)
                    onShowMessage("Marked unlistened")
                } else {
                    viewModel.markPlayed(entry.episode.uuid)
                    onShowMessage("Marked listened")
                }
            },
            onRemove = {
                actionEpisode = null
                viewModel.dequeue(entry.episode.uuid)
                onShowMessage("Removed from Up Next")
            },
        )
    }

    addToPlaylistEpisode?.let { entry ->
        AddToPlaylistDialog(
            episodeUuids = listOf(entry.episode.uuid),
            queueViewModel = viewModel,
            onAdded = { msg ->
                addToPlaylistEpisode = null
                onShowMessage(msg)
            },
            onDismiss = { addToPlaylistEpisode = null },
        )
    }

    if (showAddPlaylistDialog) {
        LoheDialog(
            onDismiss = { showAddPlaylistDialog = false },
            title = "Add Playlist to Up Next",
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            dismissLabel = "Close",
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    userPlaylists.forEach { pl ->
                        GlassCard(
                            onClick = {
                                playlistToAdd = pl
                                showAddPlaylistDialog = false
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
        )
    }

    playlistToAdd?.let { pl ->
        LoheDialog(
            onDismiss = { playlistToAdd = null },
            title = "Add to Up Next",
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            dismissLabel = "Cancel",
            body = {
                Text(
                    text = "Add \"${pl.name}\" (${pl.entries.size} episodes) to the top or bottom of Up Next?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.addPlaylistToUpNext(pl.id, true)
                            playlistToAdd = null
                            onShowMessage("Added \"${pl.name}\" to top of Up Next")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.VerticalAlignTop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Top")
                    }
                    Button(
                        onClick = {
                            viewModel.addPlaylistToUpNext(pl.id, false)
                            playlistToAdd = null
                            onShowMessage("Added \"${pl.name}\" to bottom of Up Next")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.VerticalAlignBottom, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bottom")
                    }
                }
            },
        )
    }

    if (confirmClearAll) {
        LoheDialog(
            onDismiss = { confirmClearAll = false },
            title = "Clear Up Next?",
            icon = Icons.Filled.DeleteSweep,
            destructive = true,
            body = {
                Text(
                    text = "Removes all episodes from your Up Next queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmLabel = "Clear All",
            onConfirm = {
                viewModel.clearAllQueue()
                confirmClearAll = false
                onShowMessage("Cleared Up Next queue")
            },
            dismissLabel = stringResource(R.string.no),
        )
    }

}

@Composable
internal fun EmptyQueue(
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
