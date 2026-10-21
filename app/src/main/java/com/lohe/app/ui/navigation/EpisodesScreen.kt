package com.lohe.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lohe.app.R
import com.lohe.app.data.EpisodeFilter
import com.lohe.app.data.EpisodesViewModel
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.toSummary
import com.lohe.app.ui.player.EpisodeNotesSheet
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.GlassCard
import com.lohe.app.ui.theme.LoheDialog
import com.lohe.app.ui.theme.PlaybackSettings
import com.lohe.app.ui.theme.ShowPlaybackOverrides
import com.lohe.app.data.ServiceLocator

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EpisodesScreen(
    viewModel: EpisodesViewModel,
    queueViewModel: QueueViewModel,
    onBack: () -> Unit,
    onPlay: (EpisodeEntity) -> Unit,
    onQueue: (EpisodeEntity) -> Unit,
    onDownload: (EpisodeEntity) -> Unit,
    onRemoveDownloads: (Collection<EpisodeEntity>) -> Unit,
    onOpenSettings: () -> Unit = {},
    onShowMessage: (String) -> Unit,
) {
    val podcast by viewModel.podcast.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val order by viewModel.order.collectAsState()
    var sortOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    var selectedUuids by remember { mutableStateOf(emptySet<String>()) }
    val inSelectionMode = selectedUuids.isNotEmpty()
    var playlistPickerUuids by remember { mutableStateOf<List<String>?>(null) }
    var confirmRemoveDownloads by remember { mutableStateOf<Pair<Set<String>, Boolean>?>(null) }
    var notesEpisode by remember { mutableStateOf<EpisodeEntity?>(null) }
    val scope = rememberCoroutineScope()

    // Sampled once per composition pass rather than per row: "3d ago" would
    // otherwise be recomputed for every episode and could disagree mid-list.
    val now = remember { System.currentTimeMillis() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        if (inSelectionMode) {
            val selectedEpisodes = remember(episodes, selectedUuids) {
                episodes.filter { it.uuid in selectedUuids }
            }
            val allPlayed = selectedEpisodes.isNotEmpty() && selectedEpisodes.all { it.isPlayed }

            GlassCard(
                accent = ElectricBlue,
                contentPadding = 12.dp,
                modifier = Modifier.padding(top = 8.dp),
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
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Exit selection",
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${selectedUuids.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        TextButton(
                            onClick = {
                                selectedUuids = if (selectedUuids.size == episodes.size) {
                                    emptySet()
                                } else {
                                    episodes.map { it.uuid }.toSet()
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
                                if (selectedUuids.size == episodes.size) "Deselect All" else "Select All",
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
                                selectedEpisodes.forEach { onDownload(it) }
                                onShowMessage("Downloading ${selectedEpisodes.size} episode${if (selectedEpisodes.size == 1) "" else "s"}")
                                selectedUuids = emptySet()
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

                        AssistChip(
                            onClick = {
                                onRemoveDownloads(selectedEpisodes)
                                onShowMessage("Removed downloads for ${selectedEpisodes.size} episode${if (selectedEpisodes.size == 1) "" else "s"}")
                                selectedUuids = emptySet()
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

                        AssistChip(
                            onClick = {
                                playlistPickerUuids = selectedUuids.toList()
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

                        AssistChip(
                            onClick = {
                                confirmRemoveDownloads = Pair(selectedUuids, allPlayed)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text(if (allPlayed) "Mark Unplayed" else "Mark Listened") },
                        )

                        AssistChip(
                            onClick = {
                                val count = selectedUuids.size
                                viewModel.deleteBatch(selectedUuids)
                                onShowMessage("Deleted $count episode${if (count == 1) "" else "s"}")
                                selectedUuids = emptySet()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text("Delete") },
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    text = podcast?.title ?: stringResource(R.string.episodes_loading),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = stringResource(R.string.episodes_show_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { sortOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.SwapVert,
                            contentDescription = stringResource(R.string.episodes_sort),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = sortOpen,
                        onDismissRequest = { sortOpen = false },
                    ) {
                        EpisodeFilter.Order.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                leadingIcon = {
                                    if (option == order) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setOrder(option)
                                    sortOpen = false
                                },
                            )
                        }
                    }
                }
                // The same control, the same size and the same place as on
                // every other screen.
                HeaderSettingsButton(onClick = onOpenSettings)
            }
        }

        Spacer(Modifier.size(10.dp))

        // --- The show itself: artwork, name, and what it is about ---
        podcast?.let { show ->
            GlassCard(contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    RemoteImage(
                        url = show.artworkUrl,
                        contentDescription = null,
                        size = 92.dp,
                        fallbackLabel = show.title,
                        cornerRadius = 14.dp,
                        modifier = Modifier.size(92.dp),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = show.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!show.author.isNullOrBlank()) {
                            Text(
                                text = show.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val blurb = show.description
                        if (!blurb.isNullOrBlank()) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = blurb,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EpisodeFilter.Filter.entries.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { viewModel.setFilter(option) },
                    label = { Text(option.label, maxLines = 1) },
                )
            }
        }

        Spacer(Modifier.size(10.dp))

        if (episodes.isEmpty()) {
            Text(
                text = stringResource(R.string.episodes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(episodes, key = { it.uuid }) { episode ->
                val isSelected = episode.uuid in selectedUuids
                val handleTogglePlayed: () -> Unit = {
                    if (!episode.isPlayed && episode.isDownloaded) {
                        confirmRemoveDownloads = Pair(setOf(episode.uuid), false)
                    } else {
                        viewModel.togglePlayed(episode.uuid, episode.isPlayed)
                    }
                }
                val swipeState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        when (value) {
                            SwipeToDismissBoxValue.StartToEnd -> {
                                handleTogglePlayed()
                                false
                            }
                            SwipeToDismissBoxValue.EndToStart -> {
                                if (episode.isDownloaded) {
                                    val dm = ServiceLocator.downloadManager()
                                    scope.launch { dm.delete(episode.toSummary()) }
                                    onShowMessage("Removed download")
                                } else {
                                    onDownload(episode)
                                }
                                false
                            }
                            SwipeToDismissBoxValue.Settled -> false
                        }
                    },
                )

                SwipeToDismissBox(
                    state = swipeState,
                    backgroundContent = {
                        val direction = swipeState.dismissDirection
                        val bgColor = when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> ElectricBlue.copy(alpha = 0.28f)
                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            else -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(bgColor)
                                .padding(horizontal = 20.dp),
                        ) {
                            if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                Row(
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = if (episode.isPlayed) "Mark Unplayed" else "Mark Listened",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                                Row(
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = if (episode.isDownloaded) "Remove Download" else "Download",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Icon(
                                        imageVector = if (episode.isDownloaded) Icons.Filled.DeleteSweep else Icons.Filled.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    },
                ) {
                    EpisodeRow(
                        episode = episode,
                        now = now,
                        inSelectionMode = inSelectionMode,
                        isSelected = isSelected,
                        onPlay = {
                            if (inSelectionMode) {
                                selectedUuids = if (isSelected) {
                                    selectedUuids - episode.uuid
                                } else {
                                    selectedUuids + episode.uuid
                                }
                            } else {
                                // Always download when pressing play
                                if (!episode.isDownloaded && !episode.isDownloading) {
                                    ServiceLocator.downloadManager().start(episode.toSummary())
                                }
                                onPlay(episode)
                            }
                        },
                        onLongPress = {
                            selectedUuids = if (isSelected) {
                                selectedUuids - episode.uuid
                            } else {
                                selectedUuids + episode.uuid
                            }
                        },
                        onQueue = { onQueue(episode) },
                        onDownload = { onDownload(episode) },
                        onRemoveDownloads = {
                            val dm = ServiceLocator.downloadManager()
                            scope.launch {
                                dm.delete(episode.toSummary())
                            }
                            onShowMessage("Removed download")
                        },
                        onOpenNotes = { notesEpisode = episode },
                        onTogglePlayed = handleTogglePlayed,
                        onToggleStar = { viewModel.setStarred(episode.uuid, !episode.starred) },
                        onToggleArchived = {
                            viewModel.toggleArchived(episode.uuid, episode.isArchived)
                        },
                        onRefresh = {
                            scope.launch {
                                val show = podcast
                                if (show != null) {
                                    ServiceLocator.subscriptionRepository()
                                        .importFeed(show.feedUrl)
                                }
                                if (episode.isDownloaded) {
                                    val dm = ServiceLocator.downloadManager()
                                    dm.delete(episode.toSummary())
                                    dm.start(episode.toSummary())
                                }
                                onShowMessage("Refreshed")
                            }
                        },
                    )
                }
            }
        }
    }

    notesEpisode?.let { ep ->
        EpisodeNotesSheet(
            title = ep.title,
            showTitle = podcast?.title.orEmpty(),
            artworkUrl = ep.artworkUrl ?: podcast?.artworkUrl,
            description = ep.description ?: podcast?.description,
            durationSeconds = ep.durationSeconds,
            onPlay = { onPlay(ep) },
            onAddToUpNext = { onQueue(ep) },
            onDismiss = { notesEpisode = null },
        )
    }

    // The show's own playback settings, which fall back to the global ones.
    if (showSettings && podcast != null) {
        val store = remember { ServiceLocator.settingsStore() }
        val global by store.settings.collectAsState(initial = PlaybackSettings())
        val overrides by store.showPlaybackOverrides(podcast!!.uuid)
            .collectAsState(initial = ShowPlaybackOverrides())
        ShowSettingsSheet(
            showTitle = podcast!!.title,
            global = global,
            overrides = overrides,
            onSetVolumeBoost = { v -> scope.launch { store.setShowVolumeBoost(podcast!!.uuid, v) } },
            onSetSkipSilence = { v -> scope.launch { store.setShowSkipSilence(podcast!!.uuid, v) } },
            onSetSpeed = { v -> scope.launch { store.setShowPlaybackSpeed(podcast!!.uuid, v) } },
            onSetSmartSpeed = { v -> scope.launch { store.setShowSmartSpeed(podcast!!.uuid, v) } },
            onSetAutoTrim = { v -> scope.launch { store.setShowAutoTrim(podcast!!.uuid, v) } },
            onSetSkipIntro = { v -> scope.launch { store.setShowSkipIntroSeconds(podcast!!.uuid, v) } },
            onSetSkipOutro = { v -> scope.launch { store.setShowSkipOutroSeconds(podcast!!.uuid, v) } },
            onReset = { scope.launch { store.clearShowPlaybackSettings(podcast!!.uuid) } },
            onDismiss = { showSettings = false },
        )
    }

    playlistPickerUuids?.let { uuids ->
        AddToPlaylistDialog(
            episodeUuids = uuids,
            queueViewModel = queueViewModel,
            onAdded = { msg ->
                playlistPickerUuids = null
                selectedUuids = emptySet()
                onShowMessage(msg)
            },
            onDismiss = { playlistPickerUuids = null },
        )
    }

    confirmRemoveDownloads?.let { (uuids, allPlayed) ->
        LoheDialog(
            onDismiss = { confirmRemoveDownloads = null },
            title = if (allPlayed) "Mark all unplayed?" else "Mark all listened?",
            icon = if (allPlayed) Icons.Outlined.RadioButtonUnchecked else Icons.Filled.CheckCircle,
            body = {
                Text(
                    text = if (allPlayed) {
                        "Remove downloaded files for all ${uuids.size} episodes?"
                    } else {
                        "Remove downloaded files for all ${uuids.size} episodes?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmLabel = "Yes",
            onConfirm = {
                if (allPlayed) {
                    viewModel.markUnplayedBatch(uuids)
                } else {
                    viewModel.markPlayedBatch(uuids)
                }
                // Remove downloads for all selected episodes
                val dm = ServiceLocator.downloadManager()
                scope.launch {
                    uuids.forEach { uuid ->
                        val ep = episodes.firstOrNull { it.uuid == uuid }
                        if (ep?.isDownloaded == true) {
                            dm.delete(ep.toSummary())
                        }
                    }
                }
                onShowMessage(
                    if (allPlayed) {
                        "Marked ${uuids.size} unplayed"
                    } else {
                        "Marked ${uuids.size} listened"
                    },
                )
                confirmRemoveDownloads = null
                selectedUuids = emptySet()
            },
            dismissLabel = "No",
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    now: Long,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownloads: () -> Unit,
    onOpenNotes: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleArchived: () -> Unit,
    onRefresh: () -> Unit,
) {
    GlassCard(
        accent = if (isSelected) ElectricBlue else null,
        onClick = onPlay,
        onLongClick = onLongPress,
        contentPadding = 14.dp,
    ) {
        // Played episodes read as dimmed so unplayed items stand out.
        val rowAlpha = if (episode.isPlayed) 0.55f else 1f
        Column(
            modifier = Modifier.alpha(rowAlpha),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
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

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (episode.isPlayed) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (episode.isPlayed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (episode.isPlayed) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "LISTENED ✓",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.size(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = EpisodeMeta.line(episode.toSummary(), now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(onClick = onOpenNotes, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Show notes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(onClick = onToggleStar, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (episode.starred) {
                                Icons.Filled.Star
                            } else {
                                Icons.Filled.StarBorder
                            },
                            contentDescription = stringResource(R.string.episodes_star),
                            tint = if (episode.starred) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(onClick = onToggleArchived, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (episode.isArchived) {
                                Icons.Filled.Unarchive
                            } else {
                                Icons.Filled.Archive
                            },
                            contentDescription = stringResource(
                                if (episode.isArchived) {
                                    R.string.episodes_unarchive
                                } else {
                                    R.string.episodes_archive
                                },
                            ),
                            tint = if (episode.isArchived) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(onClick = onRefresh, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.episodes_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(
                        onClick = onTogglePlayed,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = if (episode.isPlayed) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Outlined.RadioButtonUnchecked
                            },
                            contentDescription = stringResource(
                                if (episode.isPlayed) R.string.episodes_mark_unplayed
                                else R.string.episodes_mark_played,
                            ),
                            tint = if (episode.isPlayed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }

                        when {
                            episode.isDownloading -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )

                            episode.isDownloaded -> IconButton(
                                onClick = onRemoveDownloads,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = stringResource(R.string.episodes_downloaded),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            else -> IconButton(
                                onClick = onDownload,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = stringResource(R.string.episodes_download),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        IconButton(
                            onClick = onQueue,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = if (episode.isQueued) {
                                    Icons.AutoMirrored.Filled.PlaylistAddCheck
                                } else {
                                    Icons.AutoMirrored.Filled.PlaylistAdd
                                },
                                contentDescription = stringResource(
                                    if (episode.isQueued) R.string.episodes_queued
                                    else R.string.episodes_add_to_queue,
                                ),
                                tint = if (episode.isQueued) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
