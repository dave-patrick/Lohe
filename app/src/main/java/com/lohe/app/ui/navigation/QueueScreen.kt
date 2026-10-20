package com.lohe.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lohe.app.R
import com.lohe.app.data.DOWNLOADED_ID
import com.lohe.app.data.PLAYED_ID
import com.lohe.app.data.QueueEntry
import com.lohe.app.data.PlaylistSort
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.ResolvedPlaylist
import com.lohe.app.data.ServiceLocator
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.ElectricCyan
import com.lohe.app.ui.theme.GlassCard
import com.lohe.app.ui.theme.LoheDialog

private const val ALL_PLAYLIST_FOLDERS = "__ALL__"

/**
 * Dedicated "Up Next" tab screen (far right on the bottom bar).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    onPlay: (QueueEntry) -> Unit,
    onShowMessage: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val queue by viewModel.queue.collectAsState()
    val queuedSeconds by viewModel.queuedSeconds.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val autoDownloadUpNext by viewModel.autoDownloadUpNext.collectAsState()
    val now = remember { System.currentTimeMillis() }

    var confirmClearFrom by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var selectedUuids by remember { mutableStateOf(emptySet<String>()) }
    var actionEpisode by remember { mutableStateOf<QueueEntry?>(null) }
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // The header carries the queue's own actions and nothing
                    // else. Auto-download is a setting and lives in Settings;
                    // adding a whole playlist to the queue is rare enough to
                    // sit behind an overflow.
                    var menuOpen by remember { mutableStateOf(false) }

                    if (queue.isNotEmpty()) {
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

                    HeaderSettingsButton(onClick = onOpenSettings)
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

        // The list the screen actually draws. It is a copy of the queue so a
        // dragged row can be moved around without writing to the database on
        // every pixel of the drag; the new order is written once, on drop.
        var items by remember { mutableStateOf(queue) }
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
        LaunchedEffect(queue) {
            if (!dragState.isDragging) items = queue
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
                    entry = entry,
                    isHead = index == 0,
                    inSelectionMode = inSelectionMode,
                    isSelected = isSelected,
                    now = now,
                    dragHandle = {
                        DragHandle(state = dragState, index = index, key = entry.episode.uuid)
                    },
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
                            onPlay(entry)
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
                    onClearFrom = if (index == 0 && queue.size > 1) {
                        { confirmClearFrom = entry.episode.uuid }
                    } else {
                        null
                    },
                )
            }
        }
    }

    actionEpisode?.let { entry ->
        val index = queue.indexOfFirst { it.episode.uuid == entry.episode.uuid }
        QueueActionSheet(
            entry = entry,
            onDismiss = { actionEpisode = null },
            onPlay = {
                actionEpisode = null
                onPlay(entry)
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

    confirmClearFrom?.let { uuid ->
        LoheDialog(
            onDismiss = { confirmClearFrom = null },
            title = stringResource(R.string.queue_clear_title),
            icon = Icons.Filled.DeleteSweep,
            destructive = true,
            body = {
                Text(
                    text = stringResource(R.string.queue_clear_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmLabel = stringResource(R.string.queue_clear_confirm),
            onConfirm = {
                viewModel.clearFrom(uuid)
                confirmClearFrom = null
            },
            dismissLabel = stringResource(R.string.no),
        )
    }
}

/**
 * Redesigned "Playlists" tab screen:
 *  - Supports List / Grid toggle and Folder grouping for managing many playlists
 *  - Visual playlist cards with 2x2 episode artwork mosaics, reordering,
 *    auto-download toggle, and one-tap "Add to Up Next" / "Play"
 *  - Drilling into a playlist shows its episode list with reordering & removal
 */
@Composable
fun PlaylistsScreen(
    viewModel: QueueViewModel,
    onPlay: (QueueEntry) -> Unit,
    onShowMessage: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val now = remember { System.currentTimeMillis() }
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    var openedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val playlistSort by viewModel.playlistSort.collectAsState()
    var selectedFolder by rememberSaveable { mutableStateOf(ALL_PLAYLIST_FOLDERS) }
    var folderDialogTarget by remember { mutableStateOf<ResolvedPlaylist?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistFolder by remember { mutableStateOf("") }
    // The folders already in use, so a playlist can be made from one of them.
    val podcastFolders by viewModel.podcastFolders.collectAsState()
    val existingFolders = remember(podcastFolders) {
        podcastFolders.values.filter { it.isNotBlank() }.distinct().sorted()
    }
    var newPlaylistAutoDl by remember { mutableStateOf(true) }
    var actionEpisode by remember { mutableStateOf<QueueEntry?>(null) }
    var addToPlaylistEpisode by remember { mutableStateOf<QueueEntry?>(null) }

    /**
     * Episodes picked inside a playlist.
     *
     * The Played list in particular is thousands of episodes long, and
     * picking through them one at a time is not a thing anyone would do.
     */
    var selectedEpisodeUuids by remember { mutableStateOf<Set<String>>(emptySet()) }

    val availableFolders = remember(userPlaylists) {
        userPlaylists.filterNot { it.system }
            .map { it.folder }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    if (selectedFolder != ALL_PLAYLIST_FOLDERS && selectedFolder !in availableFolders) {
        selectedFolder = ALL_PLAYLIST_FOLDERS
    }

    val visiblePlaylists = remember(userPlaylists, selectedFolder, playlistSort) {
        val filtered = if (selectedFolder == ALL_PLAYLIST_FOLDERS) {
            userPlaylists
        } else {
            userPlaylists.filter { !it.system && it.folder == selectedFolder }
        }
        when (playlistSort) {
            // The listener arranged these; leave them where they put them.
            PlaylistSort.MANUAL -> filtered
            PlaylistSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            PlaylistSort.NEWEST -> filtered.sortedByDescending { it.id }
            PlaylistSort.MOST_EPISODES -> filtered.sortedByDescending { it.entries.size }
        }
    }

    val openedPlaylist: ResolvedPlaylist? = userPlaylists.firstOrNull { it.id == openedPlaylistId }
    if (openedPlaylistId != null && openedPlaylist == null) {
        openedPlaylistId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        if (openedPlaylist != null) {
            // --- Playlist Detail View ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { openedPlaylistId = null }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Playlists",
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = openedPlaylist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (openedPlaylist.folder.isNotBlank()) append("📁 ${openedPlaylist.folder} · ")
                            append("${openedPlaylist.entries.size} episodes · ${openedPlaylist.totalSeconds / 60L} min")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { folderDialogTarget = openedPlaylist },
                    enabled = !openedPlaylist.system,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Assign folder",
                        tint = if (openedPlaylist.folder.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (!openedPlaylist.system) {
                    IconButton(
                        onClick = {
                            val name = openedPlaylist.name
                            viewModel.deletePlaylist(openedPlaylist.id)
                            openedPlaylistId = null
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
            }

            GlassCard(
                contentPadding = 12.dp,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!openedPlaylist.system) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Auto-download all",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Switch(
                                checked = openedPlaylist.autoDownload,
                                onCheckedChange = {
                                    viewModel.setPlaylistAutoDownload(openedPlaylist.id, it)
                                },
                            )
                        }
                    } else {
                        Text(
                            text = when (openedPlaylist.id) {
                                PLAYED_ID -> "Episodes you have already listened to. They leave your other playlists as you finish them."
                                DOWNLOADED_ID -> "Episodes saved on this device for offline listening."
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (openedPlaylist.entries.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.addPlaylistToUpNext(openedPlaylist.id)
                                    onShowMessage("Added \"${openedPlaylist.name}\" (${openedPlaylist.entries.size} ep) to Up Next")
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
                                    viewModel.addPlaylistToUpNext(openedPlaylist.id)
                                    onPlay(openedPlaylist.entries.first())
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

            if (selectedEpisodeUuids.isNotEmpty()) {
                GlassCard(
                    accent = ElectricBlue,
                    contentPadding = 12.dp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { selectedEpisodeUuids = emptySet() },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Exit selection",
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${selectedEpisodeUuids.size} selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            TextButton(
                                onClick = {
                                    val all = openedPlaylist.entries.map { it.episode.uuid }
                                    selectedEpisodeUuids = if (selectedEpisodeUuids.size == all.size) {
                                        emptySet()
                                    } else {
                                        all.toSet()
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Filled.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (selectedEpisodeUuids.size == openedPlaylist.entries.size) {
                                        "Deselect All"
                                    } else {
                                        "Select All"
                                    },
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AssistChip(
                                onClick = {
                                    val count = selectedEpisodeUuids.size
                                    viewModel.markUnplayedBatch(selectedEpisodeUuids)
                                    selectedEpisodeUuids = emptySet()
                                    onShowMessage("Marked $count unlistened")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text("Mark unlistened") },
                            )

                            AssistChip(
                                onClick = {
                                    val count = selectedEpisodeUuids.size
                                    viewModel.markPlayedBatch(selectedEpisodeUuids)
                                    selectedEpisodeUuids = emptySet()
                                    onShowMessage("Marked $count listened")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text("Mark listened") },
                            )

                            if (!openedPlaylist.system) {
                                AssistChip(
                                    onClick = {
                                        val count = selectedEpisodeUuids.size
                                        viewModel.removeEpisodesFromPlaylist(
                                            openedPlaylist.id,
                                            selectedEpisodeUuids,
                                        )
                                        selectedEpisodeUuids = emptySet()
                                        onShowMessage("Removed $count from ${openedPlaylist.name}")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    label = { Text("Remove from playlist") },
                                )
                            }
                        }
                    }
                }
            }

            if (openedPlaylist.entries.isEmpty()) {
                EmptyQueue(
                    title = "\"${openedPlaylist.name}\" is empty",
                    hint = "Long-press episodes in any podcast and choose Add to Playlist.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // A copy of the playlist's episodes so a dragged row can be
                // moved around without rewriting the playlist on every pixel;
                // the new order is written once, on drop.
                var items by remember(openedPlaylist.id) {
                    mutableStateOf(openedPlaylist.entries)
                }
                val listState = rememberLazyListState()
                val dragState = rememberDragReorderState(
                    listState = listState,
                    onMove = { from, to ->
                        items = items.toMutableList().apply { add(to, removeAt(from)) }
                    },
                    onDrop = {
                        viewModel.reorderPlaylistEpisodes(
                            openedPlaylist.id,
                            items.map { it.episode.uuid },
                        )
                    },
                )
                LaunchedEffect(openedPlaylist.entries) {
                    if (!dragState.isDragging) items = openedPlaylist.entries
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        items,
                        key = { _, entry -> "${openedPlaylist.id}-${entry.episode.uuid}" },
                    ) { index, entry ->
                        val dragging = dragState.draggedIndex == index
                        val isSelected = entry.episode.uuid in selectedEpisodeUuids
                        QueueRow(
                            positionNumber = index + 1,
                            entry = entry,
                            isHead = index == 0,
                            inSelectionMode = selectedEpisodeUuids.isNotEmpty(),
                            isSelected = isSelected,
                            now = now,
                            dragHandle = {
                                DragHandle(state = dragState, index = index, key = entry.episode.uuid)
                            },
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
                            onPlay = { onPlay(entry) },
                            onLongPress = {
                                val uuid = entry.episode.uuid
                                selectedEpisodeUuids = if (uuid in selectedEpisodeUuids) {
                                    selectedEpisodeUuids - uuid
                                } else {
                                    selectedEpisodeUuids + uuid
                                }
                            },
                            onMoveUp = if (index > 0) {
                                {
                                    viewModel.moveEpisodeInPlaylist(
                                        openedPlaylist.id,
                                        entry.episode.uuid,
                                        -1,
                                    )
                                }
                            } else {
                                null
                            },
                            onMoveDown = if (index < items.lastIndex) {
                                {
                                    viewModel.moveEpisodeInPlaylist(
                                        openedPlaylist.id,
                                        entry.episode.uuid,
                                        +1,
                                    )
                                }
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
                            onRemove = {
                                viewModel.removeEpisodeFromPlaylist(
                                    openedPlaylist.id,
                                    entry.episode.uuid,
                                )
                            },
                            onClearFrom = null,
                        )
                    }
                }
            }
        } else {
            // --- Playlists Overview (Grid / List + Folders) ---
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box {
                        IconButton(
                            onClick = { sortMenuOpen = true },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sort,
                                contentDescription = "Sort playlists",
                                tint = if (playlistSort != PlaylistSort.MANUAL) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            PlaylistSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        if (option == playlistSort) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                    onClick = {
                                        sortMenuOpen = false
                                        viewModel.setPlaylistSort(option)
                                    },
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

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

                    HeaderSettingsButton(onClick = onOpenSettings)
                }
            }

            if (availableFolders.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selectedFolder == ALL_PLAYLIST_FOLDERS,
                        onClick = { selectedFolder = ALL_PLAYLIST_FOLDERS },
                        label = { Text("All (${userPlaylists.size})", maxLines = 1) },
                    )
                    availableFolders.forEach { folder ->
                        val count = userPlaylists.count { it.folder == folder }
                        FilterChip(
                            selected = selectedFolder == folder,
                            onClick = { selectedFolder = folder },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text("$folder ($count)", maxLines = 1) },
                        )
                    }
                }
            }

            if (visiblePlaylists.isEmpty()) {
                EmptyQueue(
                    title = "No playlists yet",
                    hint = "Tap \"New Playlist\" or long-press episodes in any podcast to create one.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(visiblePlaylists, key = { _, pl -> pl.id }) { index, pl ->
                        PlaylistGridCard(
                            playlist = pl,
                            canMoveUp = !pl.system && index > 0,
                            canMoveDown = !pl.system && index < visiblePlaylists.lastIndex,
                            onOpen = { openedPlaylistId = pl.id },
                            onPlayAll = {
                                if (pl.entries.isNotEmpty()) {
                                    viewModel.addPlaylistToUpNext(pl.id)
                                    onPlay(pl.entries.first())
                                }
                            },
                            onAddToUpNext = {
                                viewModel.addPlaylistToUpNext(pl.id)
                                onShowMessage("Added \"${pl.name}\" to Up Next")
                            },
                            onMoveUp = { viewModel.movePlaylist(pl.id, -1) },
                            onMoveDown = { viewModel.movePlaylist(pl.id, +1) },
                            onAssignFolder = { folderDialogTarget = pl },
                        )
                    }
                }
            } else {
                val dragEnabled = selectedFolder == ALL_PLAYLIST_FOLDERS && playlistSort == PlaylistSort.MANUAL
                var items by remember(visiblePlaylists) { mutableStateOf(visiblePlaylists) }
                val listState = rememberLazyListState()
                val dragState = rememberDragReorderState(
                    listState = listState,
                    onMove = { from, to ->
                        items = items.toMutableList().apply { add(to, removeAt(from)) }
                    },
                    onDrop = {
                        viewModel.reorderPlaylists(items.map { it.id })
                    },
                )
                LaunchedEffect(visiblePlaylists) {
                    if (!dragState.isDragging) items = visiblePlaylists
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(items, key = { _, pl -> pl.id }) { index, pl ->
                        val dragging = dragState.draggedIndex == index
                        PlaylistListCard(
                            playlist = pl,
                            canMoveUp = !pl.system && index > 0,
                            canMoveDown = !pl.system && index < items.lastIndex,
                            onOpen = { openedPlaylistId = pl.id },
                            onPlayAll = {
                                if (pl.entries.isNotEmpty()) {
                                    viewModel.addPlaylistToUpNext(pl.id)
                                    onPlay(pl.entries.first())
                                }
                            },
                            onAddToUpNext = {
                                viewModel.addPlaylistToUpNext(pl.id)
                                onShowMessage("Added \"${pl.name}\" to Up Next")
                            },
                            onMoveUp = { viewModel.movePlaylist(pl.id, -1) },
                            onMoveDown = { viewModel.movePlaylist(pl.id, +1) },
                            onAssignFolder = { folderDialogTarget = pl },
                            dragHandle = if (dragEnabled && !pl.system) {
                                {
                                    DragHandle(state = dragState, index = index, key = pl.id)
                                }
                            } else null,
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
                        )
                    }
                }
            }
        }
    }

    actionEpisode?.let { entry ->
        EpisodeActionSheet(
            episode = entry.episode,
            artworkUrl = entry.episode.artworkUrl,
            showTitle = entry.showTitle,
            onDismiss = { actionEpisode = null },
            onPlay = {
                actionEpisode = null
                onPlay(entry)
            },
            onAddToUpNext = {
                actionEpisode = null
                viewModel.enqueue(entry.episode.uuid)
                onShowMessage("Added to Up Next")
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
            onToggleDownload = {
                actionEpisode = null
                val dm = ServiceLocator.downloadManager()
                if (entry.episode.isDownloaded) {
                    onShowMessage("Removed download")
                } else {
                    dm.start(entry.episode)
                    onShowMessage("Downloading")
                }
            },
            onAddToPlaylist = {
                actionEpisode = null
                addToPlaylistEpisode = entry
            },
            onRemoveFromPlaylist = if (openedPlaylist != null && !openedPlaylist.system) {
                {
                    actionEpisode = null
                    viewModel.removeEpisodeFromPlaylist(
                        openedPlaylist.id,
                        entry.episode.uuid,
                    )
                    onShowMessage("Removed from ${openedPlaylist.name}")
                }
            } else {
                null
            },
            onDelete = {
                actionEpisode = null
                viewModel.deleteEpisode(entry.episode.uuid)
                onShowMessage("Deleted ${entry.episode.title}")
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

    folderDialogTarget?.let { target ->
        FolderAssignDialog(
            title = "Folder for \"${target.name}\"",
            currentFolder = target.folder,
            existingFolders = availableFolders,
            onSave = { folder ->
                viewModel.setPlaylistFolder(target.id, folder)
                folderDialogTarget = null
            },
            onDismiss = { folderDialogTarget = null },
        )
    }

    if (showCreateDialog) {
        LoheDialog(
            onDismiss = {
                showCreateDialog = false
                newPlaylistName = ""
                newPlaylistFolder = ""
            },
            title = "New Playlist",
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Folder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // A playlist tied to a folder follows it: putting a show
                    // in the folder adds it to the playlist, and taking it
                    // out takes it away. The folders already in use are
                    // offered, so a playlist can be made from one of them
                    // rather than by typing its name.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = newPlaylistFolder.isBlank(),
                            onClick = { newPlaylistFolder = "" },
                            label = { Text("No folder") },
                        )
                        existingFolders.forEach { folder ->
                            FilterChip(
                                selected = newPlaylistFolder == folder,
                                onClick = { newPlaylistFolder = folder },
                                label = { Text(folder, maxLines = 1) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newPlaylistFolder,
                        onValueChange = { newPlaylistFolder = it },
                        label = { Text("Or name a new folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Auto-download episodes", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = newPlaylistAutoDl,
                            onCheckedChange = { newPlaylistAutoDl = it },
                        )
                    }
                }
            },
            confirmLabel = "Create",
            onConfirm = {
                val created = viewModel.createPlaylist(
                    name = newPlaylistName,
                    folder = newPlaylistFolder,
                    autoDownload = newPlaylistAutoDl,
                )
                openedPlaylistId = created.id
                newPlaylistName = ""
                newPlaylistFolder = ""
                showCreateDialog = false
            },
            dismissLabel = "Cancel",
        )
    }
}

@Composable
private fun PlaylistArtworkMosaic(
    entries: List<QueueEntry>,
    modifier: Modifier = Modifier,
) {
    val urls = remember(entries) {
        entries.mapNotNull { it.episode.artworkUrl?.takeIf { u -> u.isNotBlank() } }.distinct().take(4)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(ElectricBlue.copy(alpha = 0.45f), ElectricCyan.copy(alpha = 0.25f))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            urls.isEmpty() -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(36.dp),
                )
            }
            urls.size < 4 -> {
                RemoteImage(
                    url = urls.first(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        RemoteImage(url = urls[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight())
                        RemoteImage(url = urls[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        RemoteImage(url = urls[2], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight())
                        RemoteImage(url = urls[3], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: ResolvedPlaylist,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
    onAddToUpNext: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAssignFolder: () -> Unit,
) {
    GlassCard(onClick = onOpen, contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                PlaylistArtworkMosaic(
                    entries = playlist.entries,
                    modifier = Modifier.fillMaxSize(),
                )

                if (playlist.folder.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "📁 ${playlist.folder}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color.White,
                        )
                    }
                }

                if (playlist.autoDownload && !playlist.system) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElectricBlue.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "AUTO-DL",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                if (playlist.system) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (playlist.id == PLAYED_ID) "PLAYED" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }

            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${playlist.entries.size} ep · ${playlist.totalSeconds / 60L} min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onAssignFolder, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (canMoveUp) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move playlist earlier",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (canMoveDown) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move playlist later",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = onAddToUpNext, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Add to Up Next",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onPlayAll, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play playlist",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistListCard(
    playlist: ResolvedPlaylist,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
    onAddToUpNext: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAssignFolder: () -> Unit,
    dragHandle: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GlassCard(onClick = onOpen, contentPadding = 12.dp, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaylistArtworkMosaic(
                entries = playlist.entries,
                modifier = Modifier.size(58.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.entries.size} episodes · ${playlist.totalSeconds / 60L} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (playlist.folder.isNotBlank()) {
                    Text(
                        text = "📁 ${playlist.folder}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (dragHandle != null) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    dragHandle()
                }
            }

            if (canMoveUp) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                }
            }
            if (canMoveDown) {
                IconButton(onClick = onMoveDown, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onAssignFolder, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Folder, contentDescription = "Folder", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onAddToUpNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Add to Up Next",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onPlayAll, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play playlist",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
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

    LoheDialog(
        onDismiss = onDismiss,
        title = "Add ${episodeUuids.size} Episode${if (episodeUuids.size == 1) "" else "s"} to…",
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        body = {
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

                // The built-in Played and Downloaded lists are derived from the
                // library, so there is nothing to add to them.
                playlists.filterNot { it.system }.forEach { pl ->
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
        confirmLabel = if (newName.isNotBlank()) "Create & Add" else null,
        onConfirm = if (newName.isNotBlank()) {
            {
                val created = queueViewModel.createPlaylist(newName, episodeUuids)
                onAdded("Created \"${created.name}\" (${episodeUuids.size} episodes)")
            }
        } else {
            null
        },
        dismissLabel = "Cancel",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueRow(
    positionNumber: Int,
    entry: QueueEntry,
    isHead: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    now: Long,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onTogglePlayed: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onClearFrom: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = null,
) {
    val ep = entry.episode

    // One line, not four. The runtime, the date and where the episode is up
    // to come first and the show name last: a long show title would
    // otherwise push out the number the listener is actually reading.
    val metaLine = remember(entry.showTitle, ep.playedUpToSeconds, ep.durationSeconds, ep.playingStatus, ep.published, now) {
        val state = EpisodeMeta.line(ep, now)
        val show = entry.showTitle.trim()
        when {
            state.isNotEmpty() && show.isNotEmpty() -> "$state · $show"
            state.isNotEmpty() -> state
            else -> show
        }
    }

    // Swiping left takes the episode out of the queue.
    //
    // It used to be a reveal: the swipe showed a Remove button and then
    // settled back, which meant the button could never be tapped — the row
    // was already on its way home. A swipe that does nothing is worse than
    // no swipe at all, so the swipe now does the removing itself.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // The swipe removes the episode itself.
                SwipeToDismissBoxValue.EndToStart -> onRemove != null
                // Swiping the other way rests the row open on its actions, so
                // they can actually be tapped; the old behaviour settled
                // straight back, which made them unreachable.
                SwipeToDismissBoxValue.StartToEnd -> true
                else -> false
            }
        },
    )

    val scope = rememberCoroutineScope()

    // Fired once the swipe has actually been accepted, rather than from
    // inside the confirm callback, so the removal is not racing the
    // animation that is still running.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onRemove?.invoke()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val background = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(background)
                    .padding(horizontal = 14.dp),
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (onMoveUp != null) {
                            SwipeActionButton(
                                icon = Icons.Filled.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.queue_move_up),
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    onMoveUp()
                                    scope.launch { dismissState.reset() }
                                },
                            )
                        }
                        if (onMoveDown != null) {
                            SwipeActionButton(
                                icon = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move down",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    onMoveDown()
                                    scope.launch { dismissState.reset() }
                                },
                            )
                        }
                        if (onTogglePlayed != null) {
                            SwipeActionButton(
                                icon = Icons.Filled.CheckCircle,
                                contentDescription = "Mark listened",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    onTogglePlayed()
                                    scope.launch { dismissState.reset() }
                                },
                            )
                        }
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart && onRemove != null) {
                    // A hint, not a button: the swipe itself removes the
                    // episode, so there is nothing to tap.
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.queue_remove),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 7.dp,
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
                    modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = "$positionNumber",
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
                        url = ep.artworkUrl,
                        contentDescription = null,
                        size = 34.dp,
                        cornerRadius = 9.dp,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ep.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (metaLine.isNotBlank()) {
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (ep.completionFraction > 0f) {
                            // Inside the text column, so it reads as a
                            // progress bar for the episode rather than a rule
                            // drawn across the card.
                            LinearProgressIndicator(
                                progress = { ep.completionFraction },
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth()
                                    .height(2.dp),
                            )
                        }
                    }

                    IconButton(onClick = onPlay, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.player_now_playing),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    if (dragHandle != null) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            dragHandle()
                        }
                    }
                }

                if (isHead && onClearFrom != null) {
                    TextButton(
                        onClick = onClearFrom,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.padding(top = 2.dp),
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
}

/** One of the actions revealed by swiping a row. */
@Composable
private fun SwipeActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(34.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}
