package com.lohe.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import com.lohe.app.ui.theme.QueueIndicatorSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lohe.app.data.DOWNLOADED_ID
import com.lohe.app.data.IN_PROGRESS_ID
import com.lohe.app.data.NEW_RELEASES_ID
import com.lohe.app.data.NOT_DOWNLOADED_ID
import com.lohe.app.data.PLAYED_ID
import com.lohe.app.data.UNPLAYED_ID
import com.lohe.app.data.QueueEntry
import com.lohe.app.data.PlaylistSort
import com.lohe.app.data.PlaylistEpisodeSort
import com.lohe.app.data.PlaylistEpisodeSortDirection
import com.lohe.app.data.PlaylistEpisodeSortField
import com.lohe.app.data.sortPlaylistEpisodes
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.ResolvedPlaylist
import com.lohe.app.data.ServiceLocator
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.player.EpisodeNotesSheet
import com.lohe.app.ui.formatDuration
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.ElectricCyan
import com.lohe.app.ui.theme.GlassCard
import com.lohe.app.ui.theme.LoheDialog

private const val ALL_PLAYLIST_FOLDERS = "__ALL__"

/**
 * Redesigned "Playlists" tab screen:
 *  - Supports List / Grid toggle and Folder grouping for managing many playlists
 *  - Visual playlist cards with 2x2 episode artwork mosaics, reordering,
 *    auto-download toggle, and one-tap "Add to Up Next" / "Play"
 *  - Drilling into a playlist shows its episode list with reordering & removal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: QueueViewModel,
    onPlay: (QueueEntry) -> Unit,
    onShowMessage: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    queueIndicator: QueueIndicatorSettings = QueueIndicatorSettings(),
    onArchiveEpisode: (String) -> Unit = { viewModel.archiveEpisode(it) },
) {
    val now = remember { System.currentTimeMillis() }
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val subscribedShows by viewModel.subscribedShows.collectAsStateWithLifecycle()
    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val settingsStore = remember { ServiceLocator.settingsStore() }
    val playlistSettings by settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = com.lohe.app.ui.theme.PlaybackSettings(),
    )
    val playlistScope = rememberCoroutineScope()

    var openedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var notesEpisode by remember(openedPlaylistId) { mutableStateOf<QueueEntry?>(null) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var playlistOptionsOpen by remember { mutableStateOf(false) }
    var smartPlaylistDialogOpen by remember { mutableStateOf(false) }
    val playlistSort by viewModel.playlistSort.collectAsStateWithLifecycle()
    var selectedFolder by rememberSaveable { mutableStateOf(ALL_PLAYLIST_FOLDERS) }
    var folderDialogTarget by remember { mutableStateOf<ResolvedPlaylist?>(null) }
    var followDialogTarget by remember { mutableStateOf<ResolvedPlaylist?>(null) }
    var assignDialogTarget by remember { mutableStateOf<ResolvedPlaylist?>(null) }
    var detailMenuOpen by remember(openedPlaylistId) { mutableStateOf(false) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistFolder by remember { mutableStateOf("") }
    // The folders already in use, so a playlist can be made from one of them.
    val podcastFolders by viewModel.podcastFolders.collectAsStateWithLifecycle()
    val existingFolders = remember(podcastFolders) {
        podcastFolders.values.filter { it.isNotBlank() }.distinct().sorted()
    }
    var newPlaylistAutoDl by remember { mutableStateOf(true) }
    var actionEpisode by remember { mutableStateOf<QueueEntry?>(null) }
    var addToPlaylistEpisode by remember { mutableStateOf<QueueEntry?>(null) }
    var playlistToAdd by remember { mutableStateOf<ResolvedPlaylist?>(null) }

    /**
     * Episodes picked inside a playlist.
     *
     * The Played list in particular is thousands of episodes long, and
     * picking through them one at a time is not a thing anyone would do.
     */
    var selectedEpisodeUuids by remember(openedPlaylistId) { mutableStateOf<Set<String>>(emptySet()) }
    var playlistSelectionMode by remember(openedPlaylistId) { mutableStateOf(false) }
    var unplayedOnly by remember(openedPlaylistId) { mutableStateOf(true) }

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

    val smartVisibility = mapOf(
        PLAYED_ID to playlistSettings.showPlayedSmartPlaylist,
        IN_PROGRESS_ID to playlistSettings.showInProgressSmartPlaylist,
        UNPLAYED_ID to playlistSettings.showUnplayedSmartPlaylist,
        NOT_DOWNLOADED_ID to playlistSettings.showNotDownloadedSmartPlaylist,
        DOWNLOADED_ID to playlistSettings.showDownloadedSmartPlaylist,
        NEW_RELEASES_ID to playlistSettings.showNewReleasesSmartPlaylist,
    )
    val visiblePlaylists = remember(userPlaylists, selectedFolder, playlistSort, smartVisibility) {
        val enabledPlaylists = userPlaylists.filter { !it.system || smartVisibility[it.id] == true }
        val filtered = if (selectedFolder == ALL_PLAYLIST_FOLDERS) {
            enabledPlaylists
        } else {
            enabledPlaylists.filter { !it.system && it.folder == selectedFolder }
        }
        val sorted = when (playlistSort) {
            // The listener arranged these; leave them where they put them.
            PlaylistSort.MANUAL -> filtered
            PlaylistSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            PlaylistSort.NEWEST -> filtered.sortedByDescending { it.id }
            PlaylistSort.MOST_EPISODES -> filtered.sortedByDescending { it.entries.size }
        }
        sorted.sortedByDescending { it.pinned }
    }

    val openedPlaylist: ResolvedPlaylist? = userPlaylists.firstOrNull { it.id == openedPlaylistId }
    if (openedPlaylistId != null && openedPlaylist == null) {
        openedPlaylistId = null
    }

    val visibleSelectionIds = openedPlaylist?.entries.orEmpty().filter { !unplayedOnly || !it.episode.isPlayed }.map { it.episode.uuid }.toSet()
    val allVisibleSelected = visibleSelectionIds.isNotEmpty() && selectedEpisodeUuids.containsAll(visibleSelectionIds)
    val hiddenSelectionCount = (selectedEpisodeUuids - visibleSelectionIds).size
    LaunchedEffect(openedPlaylistId, openedPlaylist?.entries) {
        selectedEpisodeUuids = selectedEpisodeUuids.intersect(openedPlaylist?.entries.orEmpty().map { it.episode.uuid }.toSet())
    }
    BackHandler(enabled = playlistSelectionMode && assignDialogTarget == null && followDialogTarget == null && folderDialogTarget == null && playlistToAdd == null && actionEpisode == null && addToPlaylistEpisode == null) {
        playlistSelectionMode = false
        selectedEpisodeUuids = emptySet()
    }

    // Pulling down re-checks every feed: playlists are built from what the
    // feeds have published, so this is the gesture that brings new episodes in.
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        if (openedPlaylist != null) {
            val episodeSort = key(openedPlaylist.id) {
                val savedSort by remember(openedPlaylist.id) {
                    settingsStore.playlistEpisodeSort(openedPlaylist.id)
                }.collectAsStateWithLifecycle(initialValue = PlaylistEpisodeSort())
                savedSort
            }
            var episodeSortMenuOpen by remember(openedPlaylist.id) { mutableStateOf(false) }
            var items by remember(openedPlaylist.id) {
                mutableStateOf(openedPlaylist.entries)
            }
            val canReorder = openedPlaylist.assignedPodcastUuids.isEmpty() &&
                episodeSort.field == PlaylistEpisodeSortField.MANUAL
            val reorderAllowed by rememberUpdatedState(canReorder && !unplayedOnly && !playlistSelectionMode)
            val displayItems = remember(items, unplayedOnly, episodeSort) {
                val filtered = if (unplayedOnly) items.filter { !it.episode.isPlayed } else items
                sortPlaylistEpisodes(filtered, episodeSort)
            }
            val listState = key(openedPlaylist.id) { rememberLazyListState() }
            val dragState = rememberDragReorderState(
                listState = listState,
                firstDraggableIndex = 1,
                canMove = { from, to -> reorderAllowed && from - 1 in items.indices && to - 1 in items.indices },
                onMove = { from, to ->
                    val source = from - 1
                    val target = to - 1
                    if (reorderAllowed && source in items.indices && target in items.indices) {
                        items = items.toMutableList().apply { add(target, removeAt(source)) }
                    }
                },
                onDrop = {
                    if (reorderAllowed) {
                        viewModel.reorderPlaylistEpisodes(
                            openedPlaylist.id,
                            items.map { it.episode.uuid },
                        )
                    }
                },
            )
            LaunchedEffect(openedPlaylist.entries) {
                if (!dragState.isDragging) items = openedPlaylist.entries
            }

            if (playlistSelectionMode) {
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
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { playlistSelectionMode = false; selectedEpisodeUuids = emptySet() },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Exit selection",
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${selectedEpisodeUuids.size} selected" + if (hiddenSelectionCount > 0) " ($hiddenSelectionCount hidden)" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            TextButton(
                                enabled = visibleSelectionIds.isNotEmpty(),
                                onClick = {
                                    selectedEpisodeUuids = if (allVisibleSelected) selectedEpisodeUuids - visibleSelectionIds else selectedEpisodeUuids + visibleSelectionIds
                                },
                            ) {
                                Icon(
                                    Icons.Filled.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (allVisibleSelected) {
                                        "Deselect All"
                                    } else {
                                        "Select All"
                                    },
                                )
                            }
                            HeaderSettingsSpacer()
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AssistChip(
                                enabled = selectedEpisodeUuids.isNotEmpty(),
                                onClick = {
                                    val count = selectedEpisodeUuids.size
                                    viewModel.markUnplayedBatch(selectedEpisodeUuids)
                                    selectedEpisodeUuids = emptySet()
                                    playlistSelectionMode = false
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
                                enabled = selectedEpisodeUuids.isNotEmpty(),
                                onClick = {
                                    val count = selectedEpisodeUuids.size
                                    viewModel.markPlayedBatch(selectedEpisodeUuids)
                                    selectedEpisodeUuids = emptySet()
                                    playlistSelectionMode = false
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
                                    enabled = selectedEpisodeUuids.isNotEmpty(),
                                    onClick = {
                                        val count = selectedEpisodeUuids.size
                                        viewModel.removeEpisodesFromPlaylist(
                                            openedPlaylist.id,
                                            selectedEpisodeUuids,
                                        )
                                        selectedEpisodeUuids = emptySet()
                                        playlistSelectionMode = false
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

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "playlist-controls") {
                    Column {
                        if (!playlistSelectionMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { if (playlistSelectionMode) { playlistSelectionMode = false; selectedEpisodeUuids = emptySet() } else openedPlaylistId = null }) {
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
                                        text = buildAnnotatedString {
                                            if (openedPlaylist.folder.isNotBlank()) {
                                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                                    append("${openedPlaylist.folder} · ")
                                                }
                                            }
                                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                                append("${openedPlaylist.entries.size} episodes · ${formatDuration(openedPlaylist.totalSeconds)}")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!playlistSelectionMode) {
                                    Box {
                                        IconButton(onClick = { episodeSortMenuOpen = true }) {
                                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort playlist episodes")
                                        }
                                        DropdownMenu(
                                            expanded = episodeSortMenuOpen,
                                            onDismissRequest = { episodeSortMenuOpen = false },
                                        ) {
                                            PlaylistEpisodeSortField.entries.forEach { field ->
                                                DropdownMenuItem(
                                                    modifier = Modifier.semantics { selected = episodeSort.field == field },
                                                    text = { Text(field.displayLabel) },
                                                    trailingIcon = {
                                                        if (episodeSort.field == field) Icon(Icons.Filled.Check, contentDescription = null)
                                                    },
                                                    onClick = {
                                                        episodeSortMenuOpen = false
                                                        playlistScope.launch {
                                                            settingsStore.setPlaylistEpisodeSort(openedPlaylist.id, episodeSort.copy(field = field))
                                                        }
                                                    },
                                                )
                                            }
                                            HorizontalDivider()
                                            PlaylistEpisodeSortDirection.entries.forEach { direction ->
                                                DropdownMenuItem(
                                                    modifier = Modifier.semantics { selected = episodeSort.direction == direction },
                                                    text = { Text(direction.displayLabel) },
                                                    enabled = episodeSort.field != PlaylistEpisodeSortField.MANUAL,
                                                    trailingIcon = {
                                                        if (episodeSort.direction == direction) Icon(Icons.Filled.Check, contentDescription = null)
                                                    },
                                                    onClick = {
                                                        episodeSortMenuOpen = false
                                                        playlistScope.launch {
                                                            settingsStore.setPlaylistEpisodeSort(openedPlaylist.id, episodeSort.copy(direction = direction))
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    Box {
                                        IconButton(onClick = { detailMenuOpen = true }) {
                                            Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options")
                                        }
                                        DropdownMenu(expanded = detailMenuOpen, onDismissRequest = { detailMenuOpen = false }) {
                                            if (openedPlaylist.entries.isNotEmpty()) {
                                                DropdownMenuItem(text = { Text("Select episodes") }, onClick = {
                                                    detailMenuOpen = false
                                                    playlistSelectionMode = true
                                                })
                                            }
                                            if (!openedPlaylist.system) {
                                                DropdownMenuItem(
                                                    text = { Text("Auto-download") },
                                                    trailingIcon = { Checkbox(checked = openedPlaylist.autoDownload, onCheckedChange = null) },
                                                    onClick = {
                                                        detailMenuOpen = false
                                                        viewModel.setPlaylistAutoDownload(openedPlaylist.id, !openedPlaylist.autoDownload)
                                                    },
                                                )
                                                DropdownMenuItem(text = { Text("Assign folder") }, onClick = {
                                                    detailMenuOpen = false
                                                    folderDialogTarget = openedPlaylist
                                                })
                                                if (!openedPlaylist.snapshot) {
                                                    DropdownMenuItem(text = { Text("Show rules") }, onClick = {
                                                        detailMenuOpen = false
                                                        followDialogTarget = openedPlaylist
                                                    })
                                                }
                                                DropdownMenuItem(text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) }, onClick = {
                                                    detailMenuOpen = false
                                                    val name = openedPlaylist.name
                                                    viewModel.deletePlaylist(openedPlaylist.id)
                                                    openedPlaylistId = null
                                                    onShowMessage("Deleted playlist \"$name\"")
                                                })
                                            }
                                        }
                                    }
                                }
                                HeaderSettingsSpacer()
                            }

                        }

                        if (!playlistSelectionMode) {
                            if (!openedPlaylist.system) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                        .semantics { contentDescription = "Assign podcasts" }
                                        .clickable { assignDialogTarget = openedPlaylist }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Podcasts", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            if (openedPlaylist.assignedPodcastUuids.isEmpty()) "Add podcasts to this playlist" else "${openedPlaylist.assignedPodcastUuids.size} assigned",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                Text(
                                    text = when (openedPlaylist.id) {
                                        PLAYED_ID -> "Episodes you have listened to."
                                        IN_PROGRESS_ID -> "Episodes you have started but not finished."
                                        UNPLAYED_ID -> "Episodes you have not started or finished."
                                        NOT_DOWNLOADED_ID -> "Episodes without an offline copy."
                                        DOWNLOADED_ID -> "Episodes saved on this device."
                                        NEW_RELEASES_ID -> "Unplayed episodes from the last 7 days."
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                )
                            }
                            if (openedPlaylist.entries.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            viewModel.enqueueAll(displayItems.map { it.episode.uuid })
                                            displayItems.firstOrNull()?.let(onPlay)
                                        }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        enabled = displayItems.isNotEmpty(),
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play")
                                    }
                                    OutlinedButton(
                                        onClick = { playlistToAdd = openedPlaylist.copy(entries = displayItems) },
                                        enabled = displayItems.isNotEmpty(),
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Up Next")
                                    }
                                    IconToggleButton(
                                        checked = unplayedOnly,
                                        onCheckedChange = { unplayedOnly = it },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.FilterList,
                                            contentDescription = if (unplayedOnly) "Show all episodes" else "Show unplayed episodes",
                                            tint = if (unplayedOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                    }
                }
                if (displayItems.isEmpty()) {
                    item(key = "playlist-empty") {
                        EmptyQueue(
                            title = if (openedPlaylist.entries.isNotEmpty()) "No unplayed episodes" else "${openedPlaylist.name} is empty",
                            hint = if (openedPlaylist.entries.isNotEmpty()) "Use the filter icon above to show all episodes." else if (openedPlaylist.system) "No episodes match this playlist yet." else "Tap Podcasts above to add shows, or add individual episodes from a podcast.",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                } else {
                    itemsIndexed(
                        displayItems,
                        key = { _, entry -> "${openedPlaylist.id}-${entry.episode.uuid}" },
                    ) { index, entry ->
                        val dragging = canReorder && !unplayedOnly && dragState.draggedIndex == index + 1
                        val isSelected = entry.episode.uuid in selectedEpisodeUuids
                        QueueRow(
                            positionNumber = index + 1,
                            entry = entry,
                            queueIndicator = queueIndicator,
                            isHead = index == 0,
                            inSelectionMode = playlistSelectionMode,
                            isSelected = isSelected,
                            playlistSelection = playlistSelectionMode,
                            now = now,
                            dragHandle = if (!canReorder || unplayedOnly || playlistSelectionMode) null else ({
                                DragHandle(state = dragState, index = index + 1, key = entry.episode.uuid)
                            }),
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
                            onOpen = {
                                if (playlistSelectionMode) {
                                    val uuid = entry.episode.uuid
                                    selectedEpisodeUuids = if (uuid in selectedEpisodeUuids) selectedEpisodeUuids - uuid else selectedEpisodeUuids + uuid
                                } else notesEpisode = entry
                            },
                            onPlay = {
                                if (playlistSelectionMode) {
                                    val uuid = entry.episode.uuid
                                    selectedEpisodeUuids = if (uuid in selectedEpisodeUuids) selectedEpisodeUuids - uuid else selectedEpisodeUuids + uuid
                                } else onPlay(entry)
                            },
                            onLongPress = {
                                playlistSelectionMode = true
                                val uuid = entry.episode.uuid
                                selectedEpisodeUuids = if (uuid in selectedEpisodeUuids) {
                                    selectedEpisodeUuids - uuid
                                } else {
                                    selectedEpisodeUuids + uuid
                                }
                            },
                            onMoveUp = if (!canReorder || unplayedOnly || index <= 0) null else {
                                {
                                    viewModel.moveEpisodeInPlaylist(
                                        openedPlaylist.id,
                                        entry.episode.uuid,
                                        -1,
                                    )
                                }
                            },
                            onMoveDown = if (!canReorder || unplayedOnly || index >= displayItems.lastIndex) null else {
                                {
                                    viewModel.moveEpisodeInPlaylist(
                                        openedPlaylist.id,
                                        entry.episode.uuid,
                                        +1,
                                    )
                                }
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
                            onAddToUpNext = {
                                viewModel.enqueue(entry.episode.uuid)
                                onShowMessage("Added to Up Next")
                            },
                            onArchive = if (!entry.episode.isArchived) ({
                                viewModel.archiveEpisode(entry.episode.uuid)
                                onShowMessage("Archived")
                            }) else null,
                        )
                    }
                }
            }
        } else {
            // --- Playlists Overview (Grid / List + Folders) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = userPlaylists.count { !it.system || smartVisibility[it.id] == true }.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box {
                        TouchTargetIconButton(
                            onClick = { sortMenuOpen = true },
                            layoutSize = 38.dp,
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

                    Box {
                        TouchTargetIconButton(
                            onClick = { playlistOptionsOpen = true },
                            layoutSize = 38.dp,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Playlist options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = playlistOptionsOpen,
                            onDismissRequest = { playlistOptionsOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Smart playlists") },
                                onClick = {
                                    playlistOptionsOpen = false
                                    smartPlaylistDialogOpen = true
                                },
                            )
                        }
                    }

                    TouchTargetIconButton(
                        onClick = { isGridView = !isGridView },
                        layoutSize = 38.dp,
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TouchTargetIconButton(
                        onClick = { showCreateDialog = true },
                        layoutSize = 40.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Create playlist",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    HeaderSettingsSpacer()
                }
            }

            if (smartPlaylistDialogOpen) {
                val smartPlaylists = listOf(
                    Triple(PLAYED_ID, "Played", playlistSettings.showPlayedSmartPlaylist),
                    Triple(IN_PROGRESS_ID, "In Progress", playlistSettings.showInProgressSmartPlaylist),
                    Triple(UNPLAYED_ID, "Unplayed", playlistSettings.showUnplayedSmartPlaylist),
                    Triple(NOT_DOWNLOADED_ID, "Not Downloaded", playlistSettings.showNotDownloadedSmartPlaylist),
                    Triple(DOWNLOADED_ID, "Downloaded", playlistSettings.showDownloadedSmartPlaylist),
                    Triple(NEW_RELEASES_ID, "New Releases", playlistSettings.showNewReleasesSmartPlaylist),
                )
                LoheDialog(
                    onDismiss = { smartPlaylistDialogOpen = false },
                    title = "Smart playlists",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    confirmLabel = "Done",
                    onConfirm = { smartPlaylistDialogOpen = false },
                    dismissLabel = null,
                    body = {
                        smartPlaylists.forEach { (id, name, visible) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(name, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = visible,
                                    onCheckedChange = { enabled ->
                                        playlistScope.launch { settingsStore.setSmartPlaylistVisibility(id, enabled) }
                                    },
                                )
                            }
                        }
                    },
                )
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
                    hint = "Tap + or long-press episodes in any podcast to create a playlist",
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isGridView) {
                val dragEnabled = playlistSort == PlaylistSort.MANUAL
                var gridItems by remember(selectedFolder, playlistSort) { mutableStateOf(visiblePlaylists) }
                val gridState = rememberLazyGridState()
                val gridDragState = rememberGridDragReorderState(
                    gridState = gridState,
                    onMove = { from, to ->
                        val fromIndex = gridItems.indexOfFirst { it.id == from }
                        val toIndex = gridItems.indexOfFirst { it.id == to }
                        if (fromIndex >= 0 && toIndex >= 0 && !gridItems[toIndex].system &&
                            gridItems[fromIndex].pinned == gridItems[toIndex].pinned
                        ) {
                            gridItems = gridItems.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                        }
                    },
                    onDrop = { viewModel.reorderPlaylists(gridItems.filterNot { it.system }.map { it.id }) },
                )
                LaunchedEffect(visiblePlaylists) {
                    if (gridDragState.draggedKey == null) gridItems = visiblePlaylists
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(gridItems, key = { _, pl -> pl.id }) { index, pl ->
                        val dragging = gridDragState.draggedKey == pl.id
                        PlaylistGridCard(
                            playlist = pl,
                            onOpen = { openedPlaylistId = pl.id },
                            onPlayAll = {
                                if (pl.entries.isNotEmpty()) {
                                    viewModel.addPlaylistToUpNext(pl.id)
                                    onPlay(pl.entries.first())
                                }
                            },
                            onAddToUpNext = {
                                playlistToAdd = pl
                            },
                            onAssignFolder = { folderDialogTarget = pl },
                            onTogglePinned = { viewModel.setPlaylistPinned(pl.id, !pl.pinned) },
                            dragHandle = if (dragEnabled && !pl.system) {
                                {
                                    GridDragHandle(
                                        state = gridDragState,
                                        playlistId = pl.id,
                                        playlistName = pl.name,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            } else null,
                            modifier = Modifier
                                .then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem())
                                .graphicsLayer {
                                    if (dragging) {
                                        val offset = gridDragState.offset(pl.id)
                                        translationX = offset.x
                                        translationY = offset.y
                                        scaleX = 1.03f
                                        scaleY = 1.03f
                                    }
                                },
                        )
                    }
                }
            } else {
                val dragEnabled = selectedFolder == ALL_PLAYLIST_FOLDERS && playlistSort == PlaylistSort.MANUAL
                var items by remember(visiblePlaylists) { mutableStateOf(visiblePlaylists) }
                val listState = rememberLazyListState()
                val dragState = rememberDragReorderState(
                    listState = listState,
                    canMove = { from, to -> items[from].pinned == items[to].pinned },
                    onMove = { from, to ->
                        if (items[from].pinned == items[to].pinned) {
                            items = items.toMutableList().apply { add(to, removeAt(from)) }
                        }
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
                    contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(items, key = { _, pl -> pl.id }) { index, pl ->
                        val dragging = dragState.draggedIndex == index
                        PlaylistListCard(
                            playlist = pl,
                            canMoveUp = !pl.system && index > 0 &&
                                (pl.pinned || !items[index - 1].pinned),
                            canMoveDown = !pl.system && index < items.lastIndex &&
                                (!pl.pinned || items[index + 1].pinned),
                            onOpen = { openedPlaylistId = pl.id },
                            onPlayAll = {
                                if (pl.entries.isNotEmpty()) {
                                    viewModel.addPlaylistToUpNext(pl.id)
                                    onPlay(pl.entries.first())
                                }
                            },
                            onAddToUpNext = {
                                playlistToAdd = pl
                            },
                            onMoveUp = { viewModel.movePlaylist(pl.id, -1) },
                            onMoveDown = { viewModel.movePlaylist(pl.id, +1) },
                            onAssignFolder = { folderDialogTarget = pl },
                            onTogglePinned = { viewModel.setPlaylistPinned(pl.id, !pl.pinned) },
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
            onPlay = { onPlay(entry) },
            onArchive = if (entry.episode.isArchived) null else ({ onArchiveEpisode(entry.episode.uuid) }),
            onAddToUpNext = {
                viewModel.enqueue(entry.episode.uuid)
                onShowMessage("Added to Up Next")
            },
            onDismiss = { notesEpisode = null },
        )
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
                            viewModel.enqueueAll(pl.entries.map { it.episode.uuid }, true)
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
                            viewModel.enqueueAll(pl.entries.map { it.episode.uuid }, false)
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

    assignDialogTarget?.let { playlist ->
        AssignPodcastsDialog(
            playlist = playlist,
            shows = subscribedShows,
            onDismiss = { assignDialogTarget = null },
            onSave = { selected ->
                viewModel.assignPlaylistPodcasts(playlist.id, selected)
                assignDialogTarget = null
            },
        )
    }

    followDialogTarget?.let { playlist ->
        FollowShowsDialog(
            playlist = playlist,
            shows = subscribedShows,
            onDismiss = { followDialogTarget = null },
            onSave = { selected ->
                viewModel.setPlaylistShows(playlist.id, selected)
                followDialogTarget = null
            },
        )
    }
}

@Composable
private fun AssignPodcastsDialog(
    playlist: ResolvedPlaylist,
    shows: List<com.lohe.app.data.db.entity.PodcastEntity>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by remember(playlist.id) { mutableStateOf(playlist.assignedPodcastUuids.toSet()) }
    var query by remember(playlist.id) { mutableStateOf("") }
    val filteredShows = remember(shows, query) { shows.filter { it.title.contains(query.trim(), ignoreCase = true) } }
    val availableIds = remember(shows) { shows.map { it.uuid }.toSet() }
    val unavailable = selected.filterNot { it in availableIds }.sorted()
    LoheDialog(
        onDismiss = onDismiss,
        title = "Assign podcasts",
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        confirmLabel = "Save",
        onConfirm = { onSave(selected) },
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Include listened and archived episodes. New episodes appear after feed updates.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search podcasts") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${selected.size} selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (selected.isEmpty() && playlist.assignedPodcastUuids.isNotEmpty()) {
                    Text("Clearing restores the playlist’s previous rules.", style = MaterialTheme.typography.bodySmall)
                }
                if (shows.isEmpty() && unavailable.isEmpty()) {
                    Text("Subscribe to podcasts to assign them here.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        if (filteredShows.isEmpty() && shows.isNotEmpty()) {
                            item { Text("No podcasts found", modifier = Modifier.padding(vertical = 16.dp)) }
                        }
                        itemsIndexed(filteredShows, key = { _, show -> "show-${show.uuid}" }) { _, show ->
                            val checked = show.uuid in selected
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .heightIn(min = 64.dp).toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = { value -> selected = if (value) selected + show.uuid else selected - show.uuid },
                                ).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RemoteImage(
                                    url = show.artworkUrl,
                                    contentDescription = null,
                                    fallbackLabel = show.title,
                                    modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(show.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    show.author?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Checkbox(checked = checked, onCheckedChange = null)
                            }
                        }
                        itemsIndexed(unavailable, key = { _, id -> "unavailable-$id" }) { _, id ->
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                    value = true, role = Role.Checkbox, onValueChange = { selected = selected - id },
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = true, onCheckedChange = null)
                                Text("Unavailable podcast ($id)", maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun FollowShowsDialog(
    playlist: ResolvedPlaylist,
    shows: List<com.lohe.app.data.db.entity.PodcastEntity>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by remember(playlist.id) { mutableStateOf(playlist.shows.toSet()) }
    LoheDialog(
        onDismiss = onDismiss,
        title = "Choose shows",
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        confirmLabel = "Save",
        onConfirm = { onSave(selected) },
        dismissLabel = "Cancel",
        body = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(shows, key = { _, show -> show.uuid }) { _, show ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (show.uuid in selected) selected - show.uuid else selected + show.uuid
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = show.uuid in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + show.uuid else selected - show.uuid
                            },
                        )
                        Text(show.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
    )
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
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
    onAddToUpNext: () -> Unit,
    onAssignFolder: () -> Unit,
    onTogglePinned: () -> Unit,
    dragHandle: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GlassCard(onClick = onOpen, contentPadding = 8.dp, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 4:3 rather than a square: the mosaic crops nothing
                    // important and the shorter card puts another row of
                    // playlists on screen.
                    .aspectRatio(4f / 3f),
            ) {
                PlaylistArtworkMosaic(
                    entries = playlist.entries,
                    modifier = Modifier.fillMaxSize(),
                )

                val playlistUnplayed = playlist.entries.count { it.episode.playingStatus != 2 }
                if (playlistUnplayed > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .shadow(2.5.dp, RoundedCornerShape(50), clip = false)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = playlistUnplayed.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                        )
                    }
                } else if (playlist.entries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .shadow(2.5.dp, RoundedCornerShape(50), clip = false)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "All listened",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                if (playlist.autoDownload && !playlist.system) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(ElectricBlue.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "AUTO-DL",
                            style = MaterialTheme.typography.labelSmall,
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
                            .clip(MaterialTheme.shapes.small)
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = when (playlist.id) {
                                PLAYED_ID -> "PLAYED"
                                IN_PROGRESS_ID -> "IN PROGRESS"
                                UNPLAYED_ID -> "UNPLAYED"
                                NOT_DOWNLOADED_ID -> "NOT DOWNLOADED"
                                DOWNLOADED_ID -> "DOWNLOADED"
                                NEW_RELEASES_ID -> "NEW"
                                else -> "SMART"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

            }

            // Name and count sit tight under the artwork: they are one caption,
            // not two paragraphs, and the gap between them was reading as a
            // separate block.
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (playlist.folder.isNotBlank() && !playlist.folder.equals(playlist.name, ignoreCase = true)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = playlist.folder,
                            style = MaterialTheme.typography.labelSmall,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = ElectricBlue, fontWeight = FontWeight.Bold)) {
                            append(playlist.entries.size.toString())
                        }
                        append(" ep · ${formatDuration(playlist.totalSeconds)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (dragHandle != null) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        dragHandle()
                    }
                }
                if (!playlist.system) {
                    TouchTargetIconButton(onClick = onTogglePinned, layoutSize = 26.dp) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = if (playlist.pinned) "Unpin playlist" else "Pin playlist to top",
                            tint = if (playlist.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                TouchTargetIconButton(onClick = onAssignFolder, layoutSize = 26.dp) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                TouchTargetIconButton(onClick = onAddToUpNext, layoutSize = 26.dp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Add to Up Next",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                }
                TouchTargetIconButton(onClick = onPlayAll, layoutSize = 26.dp) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play playlist",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
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
    onTogglePinned: () -> Unit,
    dragHandle: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GlassCard(onClick = onOpen, contentPadding = 8.dp, modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (dragHandle != null) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    dragHandle()
                }
            }

            PlaylistArtworkMosaic(
                entries = playlist.entries,
                modifier = Modifier.size(40.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val folderLabel = playlist.folder
                    .takeIf { it.isNotBlank() && !it.equals(playlist.name, ignoreCase = true) }
                val unplayed = playlist.entries.count { it.episode.playingStatus != 2 }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (folderLabel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = folderLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val accent = MaterialTheme.colorScheme.primary
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = if (unplayed > 0) accent else muted, fontWeight = FontWeight.SemiBold)) {
                                append(if (unplayed > 0) "$unplayed unplayed" else "All listened")
                            }
                            withStyle(SpanStyle(color = muted)) {
                                append(" · ${playlist.entries.size} ep · ${formatDuration(playlist.totalSeconds)}")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

        }
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            // Dragging is the reorder control when it is available; the arrows
            // are only for the times it is not.
            if (dragHandle == null) {
                if (canMoveUp) {
                    TouchTargetIconButton(onClick = onMoveUp, layoutSize = 26.dp) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(17.dp))
                    }
                }
                if (canMoveDown) {
                    TouchTargetIconButton(onClick = onMoveDown, layoutSize = 26.dp) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(17.dp))
                    }
                }
            }
            if (!playlist.system) {
                TouchTargetIconButton(onClick = onTogglePinned, layoutSize = 26.dp) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = if (playlist.pinned) "Unpin playlist" else "Pin playlist to top",
                        tint = if (playlist.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            TouchTargetIconButton(onClick = onAssignFolder, layoutSize = 26.dp) {
                Icon(Icons.Filled.Folder, contentDescription = "Folder", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
            }
            TouchTargetIconButton(onClick = onAddToUpNext, layoutSize = 28.dp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Add to Up Next",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            TouchTargetIconButton(onClick = onPlayAll, layoutSize = 28.dp) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play playlist",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
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
    val playlists by queueViewModel.userPlaylists.collectAsStateWithLifecycle()
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
