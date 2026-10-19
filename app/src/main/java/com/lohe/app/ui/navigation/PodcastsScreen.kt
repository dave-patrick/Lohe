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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lohe.app.R
import com.lohe.app.data.FeedImportState
import com.lohe.app.data.OpmlImportState
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.RefreshState
import com.lohe.app.data.ServiceLocator
import com.lohe.app.data.SubscriptionsViewModel
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.GlassCard
import com.lohe.app.ui.theme.GlassRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ALL_FOLDERS = "__ALL__"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodcastsScreen(
    viewModel: SubscriptionsViewModel,
    queueViewModel: QueueViewModel,
    onOpenPodcast: (String) -> Unit,
    onAddLocalFolder: () -> Unit,
    onManageStorage: () -> Unit,
    onPickOpmlToImport: () -> Unit,
    onPickExportDestination: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val podcasts by viewModel.podcasts.collectAsState()
    val allEpisodes by viewModel.allEpisodes.collectAsState()
    val podcastFolders by queueViewModel.podcastFolders.collectAsState()
    val query by viewModel.searchQueryState.collectAsState()
    val import by viewModel.feedImport.collectAsState()
    val opml by viewModel.opmlImport.collectAsState()
    val refresh by viewModel.refresh.collectAsState()
    val refreshing = refresh is RefreshState.Running
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddFeed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    var isGridView by rememberSaveable { mutableStateOf(false) }
    var selectedFolder by rememberSaveable { mutableStateOf(ALL_FOLDERS) }
    var folderDialogPodcast by remember { mutableStateOf<PodcastEntity?>(null) }
    var confirmUnsubscribe by remember { mutableStateOf<PodcastEntity?>(null) }

    var selectedPodcastUuids by remember { mutableStateOf(emptySet<String>()) }
    val inSelectionMode = selectedPodcastUuids.isNotEmpty()
    var playlistPickerUuids by remember { mutableStateOf<List<String>?>(null) }

    val availableFolders = remember(podcastFolders) {
        podcastFolders.values.filter { it.isNotBlank() }.distinct().sorted()
    }
    if (selectedFolder != ALL_FOLDERS && selectedFolder !in availableFolders) {
        selectedFolder = ALL_FOLDERS
    }

    val visiblePodcasts = remember(podcasts, podcastFolders, selectedFolder) {
        if (selectedFolder == ALL_FOLDERS) {
            podcasts
        } else {
            podcasts.filter { podcastFolders[it.uuid] == selectedFolder }
        }
    }

    LaunchedEffect(import) {
        when (val state = import) {
            is FeedImportState.Succeeded -> {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.podcasts_feed_added, state.showTitle),
                )
                showAddFeed = false
                viewModel.consumeFeedImport()
            }

            is FeedImportState.Failed -> {
                snackbarHostState.showSnackbar(state.reason)
                viewModel.consumeFeedImport()
            }

            else -> Unit
        }
    }

    LaunchedEffect(opml) {
        when (val state = opml) {
            is OpmlImportState.Done -> {
                val r = state.result
                val summary = if (r.failed == 0) {
                    context.getString(R.string.opml_imported_clean, r.imported)
                } else {
                    context.getString(R.string.opml_imported_partial, r.imported, r.failed)
                }
                snackbarHostState.showSnackbar(summary)
                viewModel.consumeOpmlImport()
            }

            is OpmlImportState.Failed -> {
                snackbarHostState.showSnackbar(state.reason)
                viewModel.consumeOpmlImport()
            }

            else -> Unit
        }
    }

    LaunchedEffect(refresh) {
        when (val state = refresh) {
            is RefreshState.Done -> {
                val s = state.summary
                val summary = if (s.failed == 0) {
                    context.getString(R.string.refresh_clean, s.refreshed)
                } else {
                    context.getString(R.string.refresh_partial, s.refreshed, s.failed)
                }
                snackbarHostState.showSnackbar(summary)
                viewModel.consumeRefresh()
            }

            is RefreshState.Failed -> {
                snackbarHostState.showSnackbar(state.reason)
                viewModel.consumeRefresh()
            }

            else -> Unit
        }
    }

    if (showAddFeed) {
        AddFeedDialog(
            loading = import is FeedImportState.Loading,
            onSubmit = viewModel::importFeed,
            onDismiss = {
                showAddFeed = false
                viewModel.consumeFeedImport()
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (inSelectionMode) {
                val targetEpisodes = remember(allEpisodes, selectedPodcastUuids) {
                    allEpisodes.filter { it.podcastUuid in selectedPodcastUuids && !it.isDeleted }
                }

                GlassCard(
                    accent = ElectricBlue,
                    contentPadding = 12.dp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { selectedPodcastUuids = emptySet() },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Exit selection",
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${selectedPodcastUuids.size} show${if (selectedPodcastUuids.size == 1) "" else "s"} (${targetEpisodes.size} ep)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            TextButton(
                                onClick = {
                                    selectedPodcastUuids = if (selectedPodcastUuids.size == visiblePodcasts.size) {
                                        emptySet()
                                    } else {
                                        visiblePodcasts.map { it.uuid }.toSet()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (selectedPodcastUuids.size == visiblePodcasts.size) "Deselect All" else "Select All",
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val unplayed = targetEpisodes.filter { !it.isPlayed }
                                    val dm = ServiceLocator.downloadManager()
                                    unplayed.forEach { dm.start(it) }
                                    selectedPodcastUuids = emptySet()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Downloading ${unplayed.size} episodes")
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text("Download") },
                            )

                            FilterChip(
                                selected = false,
                                onClick = {
                                    val dm = ServiceLocator.downloadManager()
                                    val downloaded = targetEpisodes.filter { it.isDownloaded }
                                    selectedPodcastUuids = emptySet()
                                    scope.launch {
                                        downloaded.forEach { dm.delete(it) }
                                        snackbarHostState.showSnackbar("Removed ${downloaded.size} downloads")
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text("Remove Downloads") },
                            )

                            FilterChip(
                                selected = false,
                                onClick = {
                                    playlistPickerUuids = targetEpisodes
                                        .filter { !it.isPlayed }
                                        .ifEmpty { targetEpisodes }
                                        .map { it.uuid }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text("Add to Playlist") },
                            )

                            FilterChip(
                                selected = false,
                                onClick = {
                                    viewModel.markPodcastsPlayed(selectedPodcastUuids)
                                    val count = targetEpisodes.size
                                    selectedPodcastUuids = emptySet()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Marked $count episodes listened")
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text("Mark Listened") },
                            )

                            FilterChip(
                                selected = false,
                                onClick = {
                                    val count = selectedPodcastUuids.size
                                    viewModel.unsubscribeBatch(selectedPodcastUuids)
                                    selectedPodcastUuids = emptySet()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unsubscribed from $count show${if (count == 1) "" else "s"}")
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text("Remove Shows") },
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.podcasts_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = { showAddFeed = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.RssFeed,
                            contentDescription = stringResource(R.string.podcasts_add_feed),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = onAddLocalFolder, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = stringResource(R.string.podcasts_add_folder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.podcasts_more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (refreshing) R.string.refreshing else R.string.refresh_feeds,
                                        ),
                                    )
                                },
                                leadingIcon = {
                                    if (refreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = null)
                                    }
                                },
                                enabled = !refreshing && podcasts.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    viewModel.refreshFeeds()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.opml_import)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onPickOpmlToImport()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.opml_export)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                                },
                                enabled = podcasts.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val document = runCatching { viewModel.buildOpml() }
                                            .getOrNull()
                                        if (document == null) {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.opml_export_failed),
                                            )
                                        } else {
                                            onPickExportDestination(document)
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.storage_management)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Storage, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onManageStorage()
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChanged,
                label = { Text(stringResource(R.string.podcasts_search_label)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )

            // Folder filter bar (shown in both List and Grid view)
            if (availableFolders.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selectedFolder == ALL_FOLDERS,
                        onClick = { selectedFolder = ALL_FOLDERS },
                        label = { Text("All (${podcasts.size})", maxLines = 1) },
                    )
                    availableFolders.forEach { folder ->
                        val count = podcasts.count { podcastFolders[it.uuid] == folder }
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

            if (visiblePodcasts.isEmpty()) {
                EmptyPodcastsState(
                    onAddFeed = { showAddFeed = true },
                    onImportFolder = onAddLocalFolder,
                    query = query,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visiblePodcasts, key = { it.uuid }) { podcast ->
                        val isSelected = podcast.uuid in selectedPodcastUuids
                        SwipeablePodcastCard(
                            podcast = podcast,
                            folderName = podcastFolders[podcast.uuid].orEmpty(),
                            inSelectionMode = inSelectionMode,
                            isSelected = isSelected,
                            onOpen = {
                                if (inSelectionMode) {
                                    selectedPodcastUuids = if (isSelected) {
                                        selectedPodcastUuids - podcast.uuid
                                    } else {
                                        selectedPodcastUuids + podcast.uuid
                                    }
                                } else {
                                    onOpenPodcast(podcast.uuid)
                                }
                            },
                            onAssignFolder = { folderDialogPodcast = podcast },
                            onUnsubscribe = { confirmUnsubscribe = podcast },
                            onQueue = {
                                queueViewModel.enqueue(podcast.uuid)
                                scope.launch { snackbarHostState.showSnackbar("Added to Up Next") }
                            },
                            onTogglePlayed = {
                                viewModel.markPodcastsPlayed(setOf(podcast.uuid))
                                scope.launch { snackbarHostState.showSnackbar("Marked ${podcast.title} listened") }
                            },
                            scope = scope,
                            viewModel = viewModel,
                            onShowMessage = { scope.launch { snackbarHostState.showSnackbar(it) } },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visiblePodcasts, key = { it.uuid }) { podcast ->
                        val isSelected = podcast.uuid in selectedPodcastUuids
                        SwipeablePodcastCard(
                            podcast = podcast,
                            folderName = podcastFolders[podcast.uuid].orEmpty(),
                            inSelectionMode = inSelectionMode,
                            isSelected = isSelected,
                            onOpen = {
                                if (inSelectionMode) {
                                    selectedPodcastUuids = if (isSelected) {
                                        selectedPodcastUuids - podcast.uuid
                                    } else {
                                        selectedPodcastUuids + podcast.uuid
                                    }
                                } else {
                                    onOpenPodcast(podcast.uuid)
                                }
                            },
                            onAssignFolder = { folderDialogPodcast = podcast },
                            onUnsubscribe = { confirmUnsubscribe = podcast },
                            onQueue = {
                                queueViewModel.enqueue(podcast.uuid)
                                scope.launch { snackbarHostState.showSnackbar("Added to Up Next") }
                            },
                            onTogglePlayed = {
                                viewModel.markPodcastsPlayed(setOf(podcast.uuid))
                                scope.launch { snackbarHostState.showSnackbar("Marked ${podcast.title} listened") }
                            },
                            scope = scope,
                            viewModel = viewModel,
                            onShowMessage = { scope.launch { snackbarHostState.showSnackbar(it) } },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    folderDialogPodcast?.let { target ->
        FolderAssignDialog(
            title = "Folder for \"${target.title}\"",
            currentFolder = podcastFolders[target.uuid].orEmpty(),
            existingFolders = availableFolders,
            onSave = { folder ->
                queueViewModel.setPodcastFolder(target.uuid, folder)
                folderDialogPodcast = null
            },
            onDismiss = { folderDialogPodcast = null },
        )
    }

    confirmUnsubscribe?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = null },
            title = { Text("Remove \"${target.title}\"?") },
            text = { Text("Unsubscribes from this podcast and removes its episode list from your library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unsubscribe(target.uuid)
                        confirmUnsubscribe = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Removed ${target.title}")
                        }
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsubscribe = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    playlistPickerUuids?.let { uuids ->
        AddToPlaylistDialog(
            episodeUuids = uuids,
            queueViewModel = queueViewModel,
            onAdded = { msg ->
                playlistPickerUuids = null
                selectedPodcastUuids = emptySet()
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
            onDismiss = { playlistPickerUuids = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolderAssignDialog(
    title: String,
    currentFolder: String,
    existingFolders: List<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var folderText by remember(currentFolder) { mutableStateOf(currentFolder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = folderText,
                    onValueChange = { folderText = it },
                    label = { Text("Folder name (leave blank for none)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (existingFolders.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        existingFolders.forEach { f ->
                            FilterChip(
                                selected = folderText.trim() == f,
                                onClick = { folderText = f },
                                label = { Text(f) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(folderText.trim()) }) {
                Text("Save")
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
private fun AddFeedDialog(
    loading: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.podcasts_add_feed)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.podcasts_feed_url_label)) },
                placeholder = { Text(stringResource(R.string.podcasts_feed_url_hint)) },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(url) },
                enabled = url.isNotBlank() && !loading,
            ) {
                Text(
                    text = stringResource(
                        if (loading) R.string.podcasts_fetching else R.string.podcasts_add,
                    ),
                )
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.no))
            }
        },
    )
}

/**
 * Wraps a podcast row in a slide-to-act box.
 *
 * Slide right (StartToEnd) reveals: Add to Up Next, Mark Listened/Unplayed,
 * Assign Folder. Slide left (EndToStart) reveals: Download/Remove Download,
 * Remove Show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeablePodcastCard(
    podcast: PodcastEntity,
    folderName: String,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onAssignFolder: () -> Unit,
    onUnsubscribe: () -> Unit,
    onQueue: () -> Unit,
    onTogglePlayed: () -> Unit,
    scope: CoroutineScope,
    viewModel: SubscriptionsViewModel,
    onShowMessage: (String) -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value -> false }, // always settle back; actions are tapped in the background
    )

    val startBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val endBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            val bg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> startBg
                SwipeToDismissBoxValue.EndToStart -> endBg
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .padding(horizontal = 16.dp),
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(
                            onClick = {
                                onQueue()
                                onTogglePlayed()
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Add to Up Next",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = onTogglePlayed,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Mark listened",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch { viewModel.unsubscribe(podcast.uuid) }
                                onShowMessage("Unsubscribed from ${podcast.title}")
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Unsubscribe",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
    ) {
        PodcastCard(
            podcast = podcast,
            folderName = folderName,
            inSelectionMode = inSelectionMode,
            isSelected = isSelected,
            onClick = onOpen,
            onLongClick = {},
            onAssignFolder = onAssignFolder,
            onUnsubscribe = onUnsubscribe,
        )
    }
}

@Composable
private fun EmptyPodcastsState(
    onAddFeed: () -> Unit,
    onImportFolder: () -> Unit,
    query: String,
    modifier: Modifier = Modifier,
) {
    val searching = query.isNotBlank()
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.size(14.dp))

        Text(
            text = stringResource(
                if (searching) R.string.podcasts_empty_search else R.string.podcasts_empty_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(
                if (searching) R.string.podcasts_empty_search_hint
                else R.string.podcasts_empty_hint,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (!searching) {
            Spacer(Modifier.size(24.dp))
            Button(onClick = onAddFeed, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.podcasts_add_feed))
            }
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = onImportFolder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.podcasts_add_folder))
            }
        }
    }
}

@Composable
private fun PodcastCard(
    podcast: PodcastEntity,
    folderName: String,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAssignFolder: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    GlassRow(
        accent = if (isSelected) ElectricBlue else null,
        onClick = onClick,
        onLongClick = onLongClick,
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
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(22.dp),
            )
        }

        RemoteImage(
            url = podcast.artworkUrl,
            contentDescription = null,
            size = 56.dp,
            cornerRadius = 10.dp,
        )

        Spacer(Modifier.size(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            podcast.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (folderName.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "📁 $folderName",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (podcast.uuid.startsWith("local:")) {
                    Text(
                        text = stringResource(R.string.podcasts_local_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        IconButton(onClick = onAssignFolder, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Assign folder",
                tint = if (folderName.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }

        IconButton(onClick = onUnsubscribe, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = "Unsubscribe",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PodcastGridCard(
    podcast: PodcastEntity,
    folderName: String,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAssignFolder: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    GlassCard(
        accent = if (isSelected) ElectricBlue else null,
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = 12.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                RemoteImage(
                    url = podcast.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )

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
                            Color.White
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(22.dp),
                    )
                }

                if (folderName.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "📁 $folderName",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color.White,
                        )
                    }
                }
            }

            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = podcast.author.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                IconButton(onClick = onAssignFolder, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Assign folder",
                        tint = if (folderName.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp),
                    )
                }

                IconButton(onClick = onUnsubscribe, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Unsubscribe",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
