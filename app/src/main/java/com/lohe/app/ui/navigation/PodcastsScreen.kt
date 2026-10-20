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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.LinearProgressIndicator
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
import com.lohe.app.data.FeedReplacementState
import com.lohe.app.data.OpmlImportFailure
import com.lohe.app.data.OpmlImportState
import com.lohe.app.data.PodcastSort
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.RefreshState
import com.lohe.app.data.ServiceLocator
import com.lohe.app.data.SubscriptionsViewModel
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.GlassCard
import com.lohe.app.ui.theme.GlassRow
import com.lohe.app.ui.theme.LoheDialog
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
    onOpenSettings: () -> Unit = {},
    localFolderName: String? = null,
    onChangeLocalFolder: () -> Unit = {},
    onClearLocalFolder: () -> Unit = {},
    onManageStorage: () -> Unit,
    onPickOpmlToImport: () -> Unit,
    onPickExportDestination: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val podcasts by viewModel.podcasts.collectAsState()
    val totalPodcastCount by viewModel.totalPodcastCount.collectAsState()
    val allEpisodes by viewModel.allEpisodes.collectAsState()
    val podcastFolders by queueViewModel.podcastFolders.collectAsState()
    val query by viewModel.searchQueryState.collectAsState()
    val import by viewModel.feedImport.collectAsState()
    val opml by viewModel.opmlImport.collectAsState()
    val replacement by viewModel.replacement.collectAsState()
    val refresh by viewModel.refresh.collectAsState()
    val refreshing = refresh is RefreshState.Running
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddFeed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val podcastSort by queueViewModel.podcastSort.collectAsState()

    var isGridView by rememberSaveable { mutableStateOf(false) }
    var selectedFolder by rememberSaveable { mutableStateOf(ALL_FOLDERS) }
    var folderDialogPodcast by remember { mutableStateOf<PodcastEntity?>(null) }
    var confirmUnsubscribe by remember { mutableStateOf<PodcastEntity?>(null) }
    var showOpmlFailures by remember { mutableStateOf(false) }
    var showLocalFolderDialog by remember { mutableStateOf(false) }
    var showSettingsFor by remember { mutableStateOf<PodcastEntity?>(null) }
    var lastOpmlFailures by remember { mutableStateOf<List<OpmlImportFailure>>(emptyList()) }

    var selectedPodcastUuids by remember { mutableStateOf(emptySet<String>()) }
    val inSelectionMode = selectedPodcastUuids.isNotEmpty()
    var playlistPickerUuids by remember { mutableStateOf<List<String>?>(null) }

    /**
     * Marks a show listened, or reopens it when it already is.
     *
     * The action is a real toggle. It used to only ever mark, and a feed
     * refresh deliberately preserves listener state, so a show marked by
     * accident stayed marked with no way back.
     */
    fun toggleShowPlayed(podcast: PodcastEntity) {
        val target = allEpisodes.filter {
            it.podcastUuid == podcast.uuid && !it.isDeleted
        }
        if (target.isEmpty()) return
        if (target.all { it.isPlayed }) {
            viewModel.unmarkPodcastsPlayed(setOf(podcast.uuid))
            scope.launch {
                snackbarHostState.showSnackbar("Marked ${podcast.title} unlistened")
            }
        } else {
            viewModel.markPodcastsPlayed(setOf(podcast.uuid))
            scope.launch {
                snackbarHostState.showSnackbar("Marked ${podcast.title} listened")
            }
        }
    }

    val availableFolders = remember(podcastFolders) {
        podcastFolders.values.filter { it.isNotBlank() }.distinct().sorted()
    }
    if (selectedFolder != ALL_FOLDERS && selectedFolder !in availableFolders) {
        selectedFolder = ALL_FOLDERS
    }

    // Counts and freshness per show, for the two orders that need them.
    val showStats = remember(allEpisodes) {
        allEpisodes.groupBy { it.podcastUuid }
            .mapValues { (_, episodes) ->
                episodes.size to episodes.maxOf { it.published }
            }
    }

    val visiblePodcasts = remember(podcasts, podcastFolders, selectedFolder, podcastSort, showStats) {
        val filtered = if (selectedFolder == ALL_FOLDERS) {
            podcasts
        } else {
            podcasts.filter { podcastFolders[it.uuid] == selectedFolder }
        }
        when (podcastSort) {
            PodcastSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            PodcastSort.MOST_EPISODES -> filtered.sortedByDescending {
                showStats[it.uuid]?.first ?: 0
            }
            PodcastSort.RECENT -> filtered.sortedByDescending {
                showStats[it.uuid]?.second ?: 0L
            }
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
                if (r.failed == 0) {
                    // Nothing went wrong, so the summary is all that is needed.
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.opml_imported_clean, r.imported),
                        duration = SnackbarDuration.Long,
                    )
                } else {
                    // The dialog carries the same information in full, so a
                    // snackbar as well would just be a second box flashing up
                    // before the real one replaces it.
                    lastOpmlFailures = r.failures
                    showOpmlFailures = true
                }
                viewModel.consumeOpmlImport()
            }

            is OpmlImportState.Failed -> {
                snackbarHostState.showSnackbar(state.reason)
                viewModel.consumeOpmlImport()
            }

            else -> Unit
        }
    }

    // A replacement feed that imported cleanly closes the failure list.
    LaunchedEffect(replacement) {
        if (replacement is FeedReplacementState.Imported) {
            showOpmlFailures = false
            snackbarHostState.showSnackbar("Feed imported")
            viewModel.consumeReplacement()
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
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.podcasts_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(10.dp))
                    // How much the library holds, not how many rows the
                    // search box happens to have left on screen.
                    Text(
                        text = totalPodcastCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))

                    // Sort. The library is a pile, and which order it reads
                    // best in depends on what the listener is looking for.
                    Box {
                        IconButton(
                            onClick = { sortMenuOpen = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sort,
                                contentDescription = "Sort shows",
                                tint = if (podcastSort != PodcastSort.TITLE) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            PodcastSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        if (option == podcastSort) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                    onClick = {
                                        sortMenuOpen = false
                                        queueViewModel.setPodcastSort(option)
                                    },
                                )
                            }
                        }
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
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        localFolderName?.let { "Local folder: $it" }
                                            ?: "Set a local folder",
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    showLocalFolderDialog = true
                                },
                            )
                        }
                    }

                    // The settings control is the last thing in the header on
                    // every screen, so it is always in the same place.
                    HeaderSettingsButton(onClick = onOpenSettings)
                }
            }

            val opmlState = opml
            if (opmlState is OpmlImportState.Running) {
                val completed = opmlState.completed
                val total = opmlState.total
                val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
                GlassCard(
                    accent = ElectricBlue,
                    contentPadding = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (total > 0) {
                                "Importing $completed of $total show${if (total == 1) "" else "s"}…"
                            } else {
                                "Reading OPML file…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // The search box, with the add-feed button beside it. The header
            // above has enough to carry.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { showAddFeed = true }) {
                    Icon(
                        imageVector = Icons.Filled.RssFeed,
                        contentDescription = stringResource(R.string.podcasts_add_feed),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // The folder filters, with the view toggle on the same line. The
            // row is always there so the toggle does not move about.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (availableFolders.isNotEmpty()) {
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

                // List or grid, as one button that shows the other one.
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        imageVector = if (isGridView) {
                            Icons.AutoMirrored.Filled.ViewList
                        } else {
                            Icons.Filled.GridView
                        },
                        contentDescription = if (isGridView) {
                            "Switch to list view"
                        } else {
                            "Switch to grid view"
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
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
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            onShowSettings = { showSettingsFor = podcast },
                            onQueue = {
                                queueViewModel.enqueue(podcast.uuid)
                                scope.launch { snackbarHostState.showSnackbar("Added to Up Next") }
                            },
                            onTogglePlayed = {
                                toggleShowPlayed(podcast)
                            },
                            onAddToPlaylist = {
                                val episodeUuids = allEpisodes
                                    .filter { it.podcastUuid == podcast.uuid && !it.isDeleted }
                                    .map { it.uuid }
                                if (episodeUuids.isNotEmpty()) {
                                    playlistPickerUuids = episodeUuids
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar("No episodes to add") }
                                }
                            },
                            scope = scope,
                            viewModel = viewModel,
                            onShowMessage = { scope.launch { snackbarHostState.showSnackbar(it) } },
                        ) { onOpen ->
                            PodcastGridCard(
                                podcast = podcast,
                                folderName = podcastFolders[podcast.uuid].orEmpty(),
                                inSelectionMode = inSelectionMode,
                                isSelected = isSelected,
                                onClick = { onOpen() },
                                onLongClick = {
                                    selectedPodcastUuids = if (isSelected) {
                                        selectedPodcastUuids - podcast.uuid
                                    } else {
                                        selectedPodcastUuids + podcast.uuid
                                    }
                                },
                                onAssignFolder = { folderDialogPodcast = podcast },
                                onUnsubscribe = { confirmUnsubscribe = podcast },
                                onShowSettings = { showSettingsFor = podcast },
                            )
                        }
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
                            onShowSettings = { showSettingsFor = podcast },
                            onQueue = {
                                queueViewModel.enqueue(podcast.uuid)
                                scope.launch { snackbarHostState.showSnackbar("Added to Up Next") }
                            },
                            onTogglePlayed = {
                                toggleShowPlayed(podcast)
                            },
                            onAddToPlaylist = {
                                val episodeUuids = allEpisodes
                                    .filter { it.podcastUuid == podcast.uuid && !it.isDeleted }
                                    .map { it.uuid }
                                if (episodeUuids.isNotEmpty()) {
                                    playlistPickerUuids = episodeUuids
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar("No episodes to add") }
                                }
                            },
                            scope = scope,
                            viewModel = viewModel,
                            onShowMessage = { scope.launch { snackbarHostState.showSnackbar(it) } },
                        ) { onOpen ->
                            PodcastCard(
                                podcast = podcast,
                                folderName = podcastFolders[podcast.uuid].orEmpty(),
                                inSelectionMode = inSelectionMode,
                                isSelected = isSelected,
                                onClick = { onOpen() },
                                onLongClick = {
                                    selectedPodcastUuids = if (isSelected) {
                                        selectedPodcastUuids - podcast.uuid
                                    } else {
                                        selectedPodcastUuids + podcast.uuid
                                    }
                                },
                                onAssignFolder = { folderDialogPodcast = podcast },
                                onUnsubscribe = { confirmUnsubscribe = podcast },
                                onShowSettings = { showSettingsFor = podcast },
                            )
                        }
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
        LoheDialog(
            onDismiss = { confirmUnsubscribe = null },
            title = "Remove \"${target.title}\"?",
            icon = Icons.Filled.DeleteOutline,
            destructive = true,
            body = {
                Text(
                    text = "Unsubscribes from this podcast and removes its episode list from your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.unsubscribe(target.uuid)
                confirmUnsubscribe = null
                scope.launch {
                    snackbarHostState.showSnackbar("Removed ${target.title}")
                }
            },
            dismissLabel = stringResource(R.string.no),
        )
    }

    showSettingsFor?.let { show ->
        ShowSettingsSheetFor(
            podcastUuid = show.uuid,
            showTitle = show.title,
            onDismiss = { showSettingsFor = null },
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

    if (showLocalFolderDialog) {
        LoheDialog(
            onDismiss = { showLocalFolderDialog = false },
            title = if (localFolderName != null) {
                "Local folder"
            } else {
                "Set a local folder"
            },
            icon = Icons.Filled.CreateNewFolder,
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (localFolderName != null) {
                            "Files dropped into \"$localFolderName\" are picked up on every " +
                                "launch and land in your Downloaded playlist."
                        } else {
                            "Pick a folder on this device. Any podcast files you drop " +
                                "into it are picked up on every launch and land in your " +
                                "Downloaded playlist."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmLabel = if (localFolderName != null) "Change folder" else "Choose folder",
            onConfirm = {
                showLocalFolderDialog = false
                onChangeLocalFolder()
            },
            dismissLabel = if (localFolderName != null) "Stop using this folder" else "Cancel",
            onDismissAction = if (localFolderName != null) {
                {
                    onClearLocalFolder()
                }
            } else {
                null
            },
        )
    }

    if (showOpmlFailures) {
        LoheDialog(
            onDismiss = { showOpmlFailures = false },
            title = "${lastOpmlFailures.size} feed(s) could not be imported",
            body = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "A feed that has moved can be looked up in the catalog by name.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    lastOpmlFailures.forEach { f ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = f.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = f.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            when (val r = replacement) {
                                is FeedReplacementState.Searching ->
                                    if (r.title == f.title) {
                                        Text(
                                            text = "Looking in the catalog...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                is FeedReplacementState.Found ->
                                    if (r.wantedTitle == f.title) {
                                        Text(
                                            text = "Found: ${r.showTitle}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        TextButton(
                                            onClick = { viewModel.importReplacement(r.feedUrl) },
                                        ) {
                                            Text("Import this feed")
                                        }
                                    }

                                is FeedReplacementState.NotFound ->
                                    if (r.title == f.title) {
                                        Text(
                                            text = "No catalog match for that name.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                is FeedReplacementState.Failed ->
                                    if (r.title == f.title || r.title.isEmpty()) {
                                        Text(
                                            text = r.reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }

                                FeedReplacementState.Importing ->
                                    Text(
                                        text = "Importing...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )

                                FeedReplacementState.Imported -> Unit

                                else -> TextButton(
                                    onClick = { viewModel.findReplacementFeed(f.title) },
                                ) {
                                    Text("Find a replacement feed")
                                }
                            }
                        }
                        Divider()
                    }
                }
            },
            dismissLabel = "OK",
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

    LoheDialog(
        onDismiss = onDismiss,
        title = title,
        icon = Icons.Filled.Folder,
        body = {
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
        confirmLabel = "Save",
        onConfirm = { onSave(folderText.trim()) },
        dismissLabel = "Cancel",
    )
}

@Composable
private fun AddFeedDialog(
    loading: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    LoheDialog(
        onDismiss = { if (!loading) onDismiss() },
        title = stringResource(R.string.podcasts_add_feed),
        icon = Icons.Filled.RssFeed,
        body = {
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
        confirmLabel = stringResource(
            if (loading) R.string.podcasts_fetching else R.string.podcasts_add,
        ),
        onConfirm = { onSubmit(url) },
        confirmEnabled = url.isNotBlank() && !loading,
        dismissLabel = stringResource(R.string.no),
    )
}

/**
 * Wraps a podcast row in a slide-to-act box.
 *
 * Slide right (StartToEnd) reveals: Add to Up Next, Add to Playlist,
 * Mark Listened/Unplayed, Assign Folder. Slide left (EndToStart) reveals:
 * Download/Remove Download, Remove Show.
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
    onShowSettings: () -> Unit,
    onQueue: () -> Unit,
    onTogglePlayed: () -> Unit,
    onAddToPlaylist: () -> Unit,
    scope: CoroutineScope,
    viewModel: SubscriptionsViewModel,
    onShowMessage: (String) -> Unit,
    content: @Composable (() -> Unit) -> Unit,
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
                            onClick = onQueue,
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
                            onClick = onAddToPlaylist,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to Playlist",
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
                                contentDescription = "Toggle listened",
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
        content(onOpen)
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
    onShowSettings: () -> Unit,
) {
    GlassRow(
        accent = if (isSelected) ElectricBlue else null,
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = 8.dp,
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
                    .padding(end = 10.dp)
                    .size(20.dp),
            )
        }

        RemoteImage(
            url = podcast.artworkUrl,
            contentDescription = null,
            size = 40.dp,
            cornerRadius = 8.dp,
            fallbackLabel = podcast.title,
        )

        Spacer(Modifier.size(10.dp))

        // The title is on one line and the column is at least as tall as the
        // artwork, so every tile is the same height. A two-line title made the
        // list ragged and took more room than the show needed.
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The author and the folder sit on one line, so every tile is two
            // lines tall whatever else it carries. A folder on a line of its
            // own made the rows ragged.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                podcast.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
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
                            maxLines = 1,
                        )
                    }
                }
                if (podcast.uuid.startsWith("local:")) {
                    Text(
                        text = stringResource(R.string.podcasts_local_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
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

        // The show's own playback settings, reachable from the library so
        // they do not have to be found on the show's page.
        IconButton(onClick = onShowSettings, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = "Show settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onShowSettings: () -> Unit,
) {
    GlassCard(
        accent = if (isSelected) ElectricBlue else null,
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = 8.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                RemoteImage(
                    url = podcast.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )

                IconButton(
                    onClick = onShowSettings,
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Show settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }

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
                            .padding(6.dp)
                            .size(20.dp),
                    )
                }

                // The folder and unsubscribe actions live on the artwork rather
                // than on a line of their own: at three columns there is no
                // room for a third row of buttons under the title.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(9.dp))
                        // A scrim behind them: white icons vanish against
                        // white artwork.
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = onAssignFolder, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Assign folder",
                            tint = if (folderName.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White
                            },
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    IconButton(onClick = onUnsubscribe, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Unsubscribe",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                if (folderName.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "📁 $folderName",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Text(
                text = podcast.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = podcast.author.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
