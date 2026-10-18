package com.lohe.app.ui.navigation

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.data.EpisodeFilter
import com.lohe.app.data.EpisodesViewModel
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.GlassCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodesScreen(
    viewModel: EpisodesViewModel,
    queueViewModel: QueueViewModel,
    onBack: () -> Unit,
    onPlay: (EpisodeEntity) -> Unit,
    onQueue: (EpisodeEntity) -> Unit,
    onDownload: (EpisodeEntity) -> Unit,
    onRemoveDownloads: (Collection<EpisodeEntity>) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val podcast by viewModel.podcast.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val order by viewModel.order.collectAsState()
    var sortOpen by remember { mutableStateOf(false) }

    var selectedUuids by remember { mutableStateOf(emptySet<String>()) }
    val inSelectionMode = selectedUuids.isNotEmpty()
    var playlistPickerUuids by remember { mutableStateOf<List<String>?>(null) }

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
                                if (allPlayed) {
                                    viewModel.markUnplayedBatch(selectedUuids)
                                    onShowMessage("Marked ${selectedUuids.size} unplayed")
                                } else {
                                    viewModel.markPlayedBatch(selectedUuids)
                                    onShowMessage("Marked ${selectedUuids.size} listened")
                                }
                                selectedUuids = emptySet()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            }
        }

        Spacer(Modifier.size(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EpisodeFilter.Filter.entries.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { viewModel.setFilter(option) },
                    label = { Text(option.label) },
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
                    onTogglePlayed = {
                        viewModel.togglePlayed(episode.uuid, episode.isPlayed)
                    },
                    onToggleStar = { viewModel.setStarred(episode.uuid, !episode.starred) },
                )
            }
        }
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
    onTogglePlayed: () -> Unit,
    onToggleStar: () -> Unit,
) {
    GlassCard(
        accent = if (isSelected) ElectricBlue else null,
        onClick = onPlay,
        onLongClick = onLongPress,
        contentPadding = 14.dp,
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
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.size(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = EpisodeMeta.line(episode, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

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

                    IconButton(
                        onClick = onDownload,
                        enabled = !episode.isDownloaded && !episode.isDownloading,
                        modifier = Modifier.size(30.dp),
                    ) {
                        when {
                            episode.isDownloading -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )

                            else -> Icon(
                                imageVector = if (episode.isDownloaded) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.Download
                                },
                                contentDescription = stringResource(
                                    if (episode.isDownloaded) R.string.episodes_downloaded
                                    else R.string.episodes_download,
                                ),
                                tint = if (episode.isDownloaded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
