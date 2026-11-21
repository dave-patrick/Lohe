package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.EpisodeEntity
import com.example.data.local.EpisodeSwipeAction
import com.example.data.local.LoheUserSettings
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.playback.PlaybackUiState
import com.example.ui.components.EpisodeBatchAction
import com.example.ui.components.EpisodeMultiSelectActionBar
import com.example.ui.components.EpisodeMultiSelectTopBar
import com.example.ui.components.PlaylistBatchAction
import com.example.ui.components.PlaylistMultiSelectActionBar
import com.example.ui.components.PodcastArtworkThumbnail
import com.example.ui.components.PrimaryTabTopBar
import com.example.ui.components.QueueIndicatorBadge
import com.example.ui.components.SwipeableEpisodeRow
import com.example.ui.components.UniformCountPillBadge
import com.example.ui.components.UpNextQueueActionButton
import com.example.ui.components.formatDurationCompactHuman
import com.example.ui.components.formatDurationMmSs
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheBrightBlueText
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsTabScreen(
    settings: LoheUserSettings,
    playlists: List<PlaylistEntity>,
    podcasts: List<PodcastEntity>,
    episodes: List<EpisodeEntity>,
    upNextEpisodes: List<EpisodeEntity>,
    selectedPlaylistId: String?,
    selectedFolder: String,
    playbackState: PlaybackUiState,
    resolvePlaylistEpisodes: (PlaylistEntity, List<EpisodeEntity>) -> List<EpisodeEntity>,
    onSelectPlaylist: (String?) -> Unit,
    onSelectFolder: (String) -> Unit,
    onToggleGridView: (Boolean) -> Unit,
    onCreatePlaylist: (String, String, List<String>) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onTogglePinned: (PlaylistEntity) -> Unit,
    onUpdateFolder: (PlaylistEntity, String) -> Unit,
    onUpdateAssignedPodcasts: (PlaylistEntity, List<String>) -> Unit,
    onQueueEpisodes: (List<EpisodeEntity>) -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onToggleUpNext: (EpisodeEntity, Boolean) -> Unit,
    onToggleStarred: (EpisodeEntity) -> Unit,
    onToggleDownloaded: (EpisodeEntity) -> Unit,
    onMarkPlayed: (EpisodeEntity, Boolean) -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    onOpenSettings: () -> Unit,
    onAddToUpNext: (EpisodeEntity, Boolean) -> Unit = { ep, beg -> onToggleUpNext(ep, beg) },
    onSwipeEpisodeAction: (EpisodeEntity, EpisodeSwipeAction) -> Unit = { _, _ -> },
    onBatchEpisodeAction: (List<EpisodeEntity>, EpisodeBatchAction) -> Unit = { _, _ -> },
    onBatchPlaylistAction: (List<PlaylistEntity>, PlaylistBatchAction) -> Unit = { _, _ -> }
) {
    val activePlaylist = remember(playlists, selectedPlaylistId) {
        playlists.find { it.id == selectedPlaylistId }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp

        if (!isTwoPane && activePlaylist != null) {
            val resolved = remember(activePlaylist, episodes) {
                resolvePlaylistEpisodes(activePlaylist, episodes)
            }
            PlaylistDetailScreen(
                playlist = activePlaylist,
                settings = settings,
                podcasts = podcasts,
                playlistEpisodes = resolved,
                upNextEpisodes = upNextEpisodes,
                playbackState = playbackState,
                onBack = { onSelectPlaylist(null) },
                onDeletePlaylist = { onDeletePlaylist(activePlaylist.id) },
                onUpdateAssignedPodcasts = { ids -> onUpdateAssignedPodcasts(activePlaylist, ids) },
                onQueueAll = { eps -> onQueueEpisodes(eps) },
                onPlayEpisode = onPlayEpisode,
                onToggleUpNext = onToggleUpNext,
                onAddToUpNext = onAddToUpNext,
                onSwipeEpisodeAction = onSwipeEpisodeAction,
                onBatchEpisodeAction = onBatchEpisodeAction,
                onToggleStarred = onToggleStarred,
                onToggleDownloaded = onToggleDownloaded,
                onMarkPlayed = onMarkPlayed,
                onOpenNotes = onOpenNotes,
                onOpenSettings = onOpenSettings
            )
            return@BoxWithConstraints
        }

        var sortNameAsc by remember { mutableStateOf(false) }
        var showCreateDialog by remember { mutableStateOf(false) }
        var folderDialogTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
        var isPlaylistMultiSelectMode by remember { mutableStateOf(false) }
        var selectedPlaylistIds by remember { mutableStateOf(setOf<String>()) }
        var pendingDeletePlaylists by remember { mutableStateOf<List<PlaylistEntity>?>(null) }
        val haptic = LocalHapticFeedback.current

        val folders = remember(playlists) {
            listOf("All") + playlists.map { it.folderName }.distinct().sorted()
        }

        val displayedPlaylists = remember(playlists, selectedFolder, sortNameAsc) {
            val base = playlists.filter { selectedFolder == "All" || it.folderName == selectedFolder }
            if (sortNameAsc) base.sortedBy { it.name.lowercase() } else base
        }
        val activeSelectedPlaylistIds = remember(selectedPlaylistIds, displayedPlaylists) {
            val valid = displayedPlaylists.map { it.id }.toSet()
            selectedPlaylistIds.filterTo(LinkedHashSet()) { it in valid }
        }
        val selectedPlaylists = remember(activeSelectedPlaylistIds, displayedPlaylists) {
            displayedPlaylists.filter { it.id in activeSelectedPlaylistIds }
        }

        BackHandler(enabled = isPlaylistMultiSelectMode) {
            isPlaylistMultiSelectMode = false
            selectedPlaylistIds = emptySet()
        }

        val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
        val cardBg = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
        val borderColor = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
        val paneDetailPlaylist = if (isTwoPane) activePlaylist ?: displayedPlaylists.firstOrNull() else null

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .then(if (isTwoPane) Modifier.weight(0.42f) else Modifier.fillMaxSize())
                    .padding(horizontal = 16.dp)
                    .testTag("playlists_tab_screen")
            ) {
        if (isPlaylistMultiSelectMode) {
            EpisodeMultiSelectTopBar(
                selectedCount = activeSelectedPlaylistIds.size,
                totalCount = displayedPlaylists.size,
                onClose = {
                    isPlaylistMultiSelectMode = false
                    selectedPlaylistIds = emptySet()
                },
                onToggleSelectAll = {
                    selectedPlaylistIds = if (activeSelectedPlaylistIds.size >= displayedPlaylists.size) {
                        emptySet()
                    } else {
                        displayedPlaylists.map { it.id }.toSet()
                    }
                }
            )
        } else {
            PrimaryTabTopBar(
                title = stringResource(id = R.string.tab_playlists),
                badgeCount = playlists.size,
                trailingActions = {
                    if (displayedPlaylists.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                isPlaylistMultiSelectMode = true
                                selectedPlaylistIds = emptySet()
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("playlists_multiselect_toggle")
                        ) {
                            Icon(Icons.Filled.Checklist, contentDescription = "Select Multiple Playlists", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { sortNameAsc = !sortNameAsc }, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Sort Playlists", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onToggleGridView(!settings.playlistsGridView) },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            if (settings.playlistsGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "Toggle Layout",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("create_playlist_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New Playlist", tint = LoheBrightBlueText)
                    }
                },
                onOpenSettings = onOpenSettings
            )
        }

        // Folder Filter Strip matching duration-playlists_c5e80cda.png
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            folders.forEach { folder ->
                val count = if (folder == "All") playlists.size else playlists.count { it.folderName == folder }
                val selected = selectedFolder == folder
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) LoheElectricBlue else cardBg)
                        .border(
                            width = 1.dp,
                            color = if (selected) LoheElectricBlue else borderColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectFolder(folder) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    if (folder != "All") {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = if (selected) Color.White else LoheBrightBlueText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = "$folder ($count)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (settings.playlistsGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = if (isPlaylistMultiSelectMode) 12.dp else 24.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(displayedPlaylists, key = { it.id }) { pl ->
                    val plEps = resolvePlaylistEpisodes(pl, episodes)
                    val totalDurMs = plEps.sumOf { it.durationMs }
                    val isSelectedPane = isTwoPane && paneDetailPlaylist?.id == pl.id
                    val isSelected = pl.id in activeSelectedPlaylistIds
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) LoheElectricBlue.copy(alpha = if (isDark) 0.16f else 0.10f) else cardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (isSelected || isSelectedPane) 1.5.dp else 1.dp,
                                if (isSelected || isSelectedPane) LoheElectricBlue else borderColor,
                                RoundedCornerShape(18.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isPlaylistMultiSelectMode) {
                                        selectedPlaylistIds = if (isSelected) {
                                            selectedPlaylistIds - pl.id
                                        } else {
                                            selectedPlaylistIds + pl.id
                                        }
                                    } else {
                                        onSelectPlaylist(pl.id)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isPlaylistMultiSelectMode = true
                                    selectedPlaylistIds = selectedPlaylistIds + pl.id
                                }
                            )
                            .testTag("playlist_card_${pl.id}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(114.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                PlaylistCollageArtwork(episodes = plEps, fallbackTitle = pl.name)
                                UniformCountPillBadge(
                                    count = plEps.size,
                                    borderColor = Color.White,
                                    borderWidth = 1.5.dp,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .testTag("playlist_count_pill_${pl.id}")
                                )
                                if (isPlaylistMultiSelectMode) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) LoheElectricBlue else Color(0xCC0A101D))
                                            .border(1.5.dp, Color.White, CircleShape)
                                            .testTag("playlist_select_icon_${pl.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                                            contentDescription = if (isSelected) "Selected" else "Not selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else if (pl.autoDownload) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(LoheElectricBlue)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("AUTO-DL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                if (pl.folderName != "General") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xD90A101D))
                                            .border(0.8.dp, LoheCyanAccent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = LoheCyanAccent,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = pl.folderName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                minLines = 1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = LoheBrightBlueText, fontWeight = FontWeight.SemiBold)) {
                                        append("${plEps.size}")
                                    }
                                    append(" ep • ${formatDurationCompactHuman(totalDurMs)}")
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            if (!isPlaylistMultiSelectMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { onTogglePinned(pl) }, modifier = Modifier.size(34.dp)) {
                                        Icon(
                                            if (pl.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Pin",
                                            tint = if (pl.isPinned) LoheCyanAccent else LoheBrightBlueText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(onClick = { folderDialogTarget = pl }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Filled.Folder, contentDescription = "Move Folder", tint = LoheBrightBlueText, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { if (plEps.isNotEmpty()) onQueueEpisodes(plEps) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Queue Playlist", tint = LoheBrightBlueText, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { plEps.firstOrNull()?.let(onPlayEpisode) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play Playlist", tint = LoheElectricBlue, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = if (isPlaylistMultiSelectMode) 12.dp else 24.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(displayedPlaylists, key = { _, p -> p.id }) { _, pl ->
                    val plEps = resolvePlaylistEpisodes(pl, episodes)
                    val totalDurMs = plEps.sumOf { it.durationMs }
                    val isSelectedPane = isTwoPane && paneDetailPlaylist?.id == pl.id
                    val isSelected = pl.id in activeSelectedPlaylistIds
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) LoheElectricBlue.copy(alpha = if (isDark) 0.16f else 0.10f) else cardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (isSelected || isSelectedPane) 1.5.dp else 1.dp,
                                if (isSelected || isSelectedPane) LoheElectricBlue else borderColor,
                                RoundedCornerShape(16.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isPlaylistMultiSelectMode) {
                                        selectedPlaylistIds = if (isSelected) {
                                            selectedPlaylistIds - pl.id
                                        } else {
                                            selectedPlaylistIds + pl.id
                                        }
                                    } else {
                                        onSelectPlaylist(pl.id)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isPlaylistMultiSelectMode = true
                                    selectedPlaylistIds = selectedPlaylistIds + pl.id
                                }
                            )
                            .testTag("playlist_card_${pl.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPlaylistMultiSelectMode) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                    tint = if (isSelected) LoheElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .testTag("playlist_select_icon_${pl.id}")
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                PlaylistCollageArtwork(episodes = plEps, fallbackTitle = pl.name)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pl.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${pl.folderName} • ${formatDurationCompactHuman(totalDurMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            UniformCountPillBadge(
                                count = plEps.size,
                                borderColor = Color.White,
                                borderWidth = 1.5.dp,
                                modifier = Modifier.testTag("playlist_count_pill_${pl.id}")
                            )
                            if (!isPlaylistMultiSelectMode) {
                                IconButton(onClick = { plEps.firstOrNull()?.let(onPlayEpisode) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = LoheElectricBlue)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isPlaylistMultiSelectMode) {
            PlaylistMultiSelectActionBar(
                selectedPlaylists = selectedPlaylists,
                onBatchAction = { action ->
                    if (selectedPlaylists.isEmpty()) return@PlaylistMultiSelectActionBar
                    if (action == PlaylistBatchAction.DELETE) {
                        pendingDeletePlaylists = selectedPlaylists.filter { it.smartRuleType.isEmpty() }
                    } else {
                        onBatchPlaylistAction(selectedPlaylists, action)
                        isPlaylistMultiSelectMode = false
                        selectedPlaylistIds = emptySet()
                    }
                }
            )
        }
    }

            if (isTwoPane) {
                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Box(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxSize()
                        .testTag("playlists_two_pane_layout")
                ) {
                    if (paneDetailPlaylist != null) {
                        val resolved = remember(paneDetailPlaylist, episodes) {
                            resolvePlaylistEpisodes(paneDetailPlaylist, episodes)
                        }
                        PlaylistDetailScreen(
                            playlist = paneDetailPlaylist,
                            settings = settings,
                            podcasts = podcasts,
                            playlistEpisodes = resolved,
                            upNextEpisodes = upNextEpisodes,
                            playbackState = playbackState,
                            onBack = { onSelectPlaylist(null) },
                            onDeletePlaylist = { onDeletePlaylist(paneDetailPlaylist.id) },
                            onUpdateAssignedPodcasts = { ids -> onUpdateAssignedPodcasts(paneDetailPlaylist, ids) },
                            onQueueAll = { eps -> onQueueEpisodes(eps) },
                            onPlayEpisode = onPlayEpisode,
                            onToggleUpNext = onToggleUpNext,
                            onAddToUpNext = onAddToUpNext,
                            onSwipeEpisodeAction = onSwipeEpisodeAction,
                            onBatchEpisodeAction = onBatchEpisodeAction,
                            onToggleStarred = onToggleStarred,
                            onToggleDownloaded = onToggleDownloaded,
                            onMarkPlayed = onMarkPlayed,
                            onOpenNotes = onOpenNotes,
                            onOpenSettings = onOpenSettings,
                            isEmbeddedPane = true
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Select a playlist to inspect episodes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

    pendingDeletePlaylists?.takeIf { it.isNotEmpty() }?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeletePlaylists = null },
            title = { Text("Delete ${toDelete.size} Playlist${if (toDelete.size == 1) "" else "s"}?") },
            text = { Text("Selected custom playlists will be permanently deleted. Your episodes and podcasts will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        onBatchPlaylistAction(toDelete, PlaylistBatchAction.DELETE)
                        pendingDeletePlaylists = null
                        isPlaylistMultiSelectMode = false
                        selectedPlaylistIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.testTag("playlist_multiselect_delete_confirm")
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePlaylists = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        var folderInput by remember { mutableStateOf("General") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Unlimited Custom Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (nameInput.isNotBlank()) {
                        onCreatePlaylist(nameInput.trim(), folderInput.trim().ifBlank { "General" }, emptyList())
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    folderDialogTarget?.let { target ->
        var folderText by remember(target.id) { mutableStateOf(target.folderName) }
        AlertDialog(
            onDismissRequest = { folderDialogTarget = null },
            title = { Text("Move \"${target.name}\" to Folder") },
            text = {
                OutlinedTextField(
                    value = folderText,
                    onValueChange = { folderText = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateFolder(target, folderText)
                    folderDialogTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { folderDialogTarget = null }) { Text("Cancel") }
            }
        )
    }
    }
}

@Composable
private fun PlaylistCollageArtwork(episodes: List<EpisodeEntity>, fallbackTitle: String) {
    val distinctShows = remember(episodes) {
        episodes.distinctBy { it.podcastId }.take(4)
    }
    if (distinctShows.size >= 4) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PodcastArtworkThumbnail(distinctShows[0].artworkUrl, distinctShows[0].podcastTitle, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
                PodcastArtworkThumbnail(distinctShows[1].artworkUrl, distinctShows[1].podcastTitle, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PodcastArtworkThumbnail(distinctShows[2].artworkUrl, distinctShows[2].podcastTitle, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
                PodcastArtworkThumbnail(distinctShows[3].artworkUrl, distinctShows[3].podcastTitle, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
            }
        }
    } else {
        val first = distinctShows.firstOrNull()
        PodcastArtworkThumbnail(
            artworkUrl = first?.artworkUrl ?: "",
            title = first?.podcastTitle ?: fallbackTitle,
            cornerRadius = 0.dp,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    settings: LoheUserSettings,
    podcasts: List<PodcastEntity>,
    playlistEpisodes: List<EpisodeEntity>,
    upNextEpisodes: List<EpisodeEntity>,
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onUpdateAssignedPodcasts: (List<String>) -> Unit,
    onQueueAll: (List<EpisodeEntity>) -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onToggleUpNext: (EpisodeEntity, Boolean) -> Unit,
    onAddToUpNext: (EpisodeEntity, Boolean) -> Unit = { ep, beg -> onToggleUpNext(ep, beg) },
    onSwipeEpisodeAction: (EpisodeEntity, EpisodeSwipeAction) -> Unit = { _, _ -> },
    onBatchEpisodeAction: (List<EpisodeEntity>, EpisodeBatchAction) -> Unit = { _, _ -> },
    onToggleStarred: (EpisodeEntity) -> Unit,
    onToggleDownloaded: (EpisodeEntity) -> Unit,
    onMarkPlayed: (EpisodeEntity, Boolean) -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    onOpenSettings: () -> Unit,
    isEmbeddedPane: Boolean = false
) {
    var filterUnplayed by remember { mutableStateOf(false) }
    var sortNewest by remember { mutableStateOf(true) }
    var showEditPodcastsModal by remember { mutableStateOf(false) }
    var showDetailMenu by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember(playlist.id) { mutableStateOf(false) }
    var selectedEpisodeIds by remember(playlist.id) { mutableStateOf(setOf<String>()) }
    val haptic = LocalHapticFeedback.current

    // Played episodes are always automatically hidden from view in playlists
    val displayed = remember(playlistEpisodes, filterUnplayed, sortNewest) {
        val unplayedOnly = playlistEpisodes.filter { !it.isPlayed }
        val base = if (filterUnplayed) unplayedOnly.filter { it.resumePositionMs == 0L } else unplayedOnly
        if (sortNewest) base.sortedByDescending { it.publishedAt } else base.sortedBy { it.publishedAt }
    }
    val activeSelectedIds = remember(selectedEpisodeIds, displayed) {
        val valid = displayed.map { it.id }.toSet()
        selectedEpisodeIds.filterTo(LinkedHashSet()) { it in valid }
    }
    val selectedEpisodes = remember(activeSelectedIds, displayed) {
        displayed.filter { it.id in activeSelectedIds }
    }

    BackHandler(enabled = isMultiSelectMode || !isEmbeddedPane) {
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            selectedEpisodeIds = emptySet()
        } else {
            onBack()
        }
    }

    val totalDurMs = displayed.sumOf { it.durationMs }
    val assignedCount = if (playlist.smartRuleType.isNotEmpty()) podcasts.size else playlist.assignedPodcastIdList().size
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardBg = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("playlist_detail_screen")
    ) {
        if (isMultiSelectMode) {
            EpisodeMultiSelectTopBar(
                selectedCount = activeSelectedIds.size,
                totalCount = displayed.size,
                onClose = {
                    isMultiSelectMode = false
                    selectedEpisodeIds = emptySet()
                },
                onToggleSelectAll = {
                    selectedEpisodeIds = if (activeSelectedIds.size >= displayed.size && displayed.isNotEmpty()) {
                        emptySet()
                    } else {
                        displayed.map { it.id }.toSet()
                    }
                }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (!isEmbeddedPane) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("playlist_detail_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Column {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${displayed.size} episodes • ${formatDurationCompactHuman(totalDurMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (displayed.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                isMultiSelectMode = true
                                selectedEpisodeIds = emptySet()
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("playlist_multiselect_toggle")
                        ) {
                            Icon(
                                Icons.Filled.Checklist,
                                contentDescription = "Select Episodes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { sortNewest = !sortNewest }, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { showDetailMenu = true }, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Playlist Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(
                            expanded = showDetailMenu,
                            onDismissRequest = { showDetailMenu = false }
                        ) {
                            if (displayed.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Select Multiple Episodes") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Checklist, contentDescription = null)
                                    },
                                    onClick = {
                                        showDetailMenu = false
                                        isMultiSelectMode = true
                                        selectedEpisodeIds = emptySet()
                                    }
                                )
                            }
                            if (playlist.smartRuleType.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Assign Shows") },
                                    onClick = {
                                        showDetailMenu = false
                                        showEditPodcastsModal = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Playlist", color = Color(0xFFEF4444)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                                    },
                                    onClick = {
                                        showDetailMenu = false
                                        onDeletePlaylist()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Queue All Episodes") },
                                    onClick = {
                                        showDetailMenu = false
                                        if (displayed.isNotEmpty()) onQueueAll(displayed)
                                    }
                                )
                            }
                        }
                    }
                    if (!isEmbeddedPane) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("top_settings_button")
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Assigned Podcasts Row + Edit button (for custom playlists)
        if (!isMultiSelectMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Podcasts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (playlist.smartRuleType.isNotEmpty()) "Smart Rule • $assignedCount shows" else "$assignedCount assigned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (playlist.smartRuleType.isEmpty()) {
                    TextButton(onClick = { showEditPodcastsModal = true }) {
                        Text("Edit", color = LoheElectricBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Dual Action Pills: Play + Up Next + Unplayed Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { displayed.firstOrNull()?.let(onPlayEpisode) },
                    enabled = displayed.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Play", color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { if (displayed.isNotEmpty()) onQueueAll(displayed) },
                    enabled = displayed.isNotEmpty(),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = LoheBrightBlueText)
                    Spacer(Modifier.width(4.dp))
                    Text("Up Next")
                }

                IconButton(
                    onClick = { filterUnplayed = !filterUnplayed },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (filterUnplayed) LoheElectricBlue.copy(alpha = 0.2f) else Color.Transparent)
                ) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = "Filter Unplayed",
                        tint = if (filterUnplayed) LoheCyanAccent else LoheBrightBlueText
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = if (isMultiSelectMode) 12.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(displayed, key = { _, e -> e.id }) { idx, ep ->
                val isPlayingEp = playbackState.currentEpisode?.id == ep.id && playbackState.isPlaying
                val isSelected = ep.id in activeSelectedIds
                val qPos = upNextEpisodes.indexOfFirst { it.id == ep.id }.takeIf { it >= 0 }?.plus(1)
                SwipeableEpisodeRow(
                    episode = ep,
                    settings = settings,
                    onSwipeAction = onSwipeEpisodeAction,
                    cornerRadius = 16.dp,
                    enabled = !isMultiSelectMode
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) LoheElectricBlue.copy(alpha = if (isDark) 0.16f else 0.10f) else cardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected || isPlayingEp) LoheElectricBlue else borderColor,
                                RoundedCornerShape(16.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelectMode) {
                                        selectedEpisodeIds = if (isSelected) {
                                            selectedEpisodeIds - ep.id
                                        } else {
                                            selectedEpisodeIds + ep.id
                                        }
                                    } else {
                                        onOpenNotes(ep)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isMultiSelectMode = true
                                    selectedEpisodeIds = selectedEpisodeIds + ep.id
                                }
                            )
                            .testTag("playlist_ep_row_${ep.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMultiSelectMode) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                    tint = if (isSelected) LoheElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .testTag("playlist_ep_select_icon_${ep.id}")
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(22.dp)
                                )
                            }
                            PodcastArtworkThumbnail(
                                artworkUrl = ep.artworkUrl,
                                title = ep.podcastTitle,
                                cornerRadius = 10.dp,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (ep.inUpNext) {
                                        QueueIndicatorBadge(settings = settings, queuePosition = qPos)
                                    }
                                    Text(
                                        text = ep.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = buildAnnotatedString {
                                        append("${formatEpisodeDate(ep.publishedAt)} • ")
                                        withStyle(SpanStyle(color = LoheBrightBlueText, fontWeight = FontWeight.SemiBold)) {
                                            append(formatDurationCompactHuman(ep.durationMs))
                                        }
                                        append(" • ${ep.podcastTitle}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (ep.durationMs > 0L && ep.resumePositionMs > 0L) {
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { (ep.resumePositionMs.toFloat() / ep.durationMs.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.5.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = LoheElectricBlue
                                    )
                                }
                            }
                            if (!isMultiSelectMode) {
                                UpNextQueueActionButton(
                                    inUpNext = ep.inUpNext,
                                    onAddToBeginning = { onAddToUpNext(ep, true) },
                                    onAddToEnd = { onAddToUpNext(ep, false) },
                                    onRemoveFromUpNext = if (ep.inUpNext) ({ onToggleUpNext(ep, false) }) else null,
                                    buttonSize = 36.dp,
                                    iconSize = 19.dp,
                                    testTagPrefix = "playlist_up_next_${ep.id}"
                                )
                                IconButton(
                                    onClick = { onPlayEpisode(ep) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        if (isPlayingEp) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = LoheElectricBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isMultiSelectMode) {
            EpisodeMultiSelectActionBar(
                selectedEpisodes = selectedEpisodes,
                onBatchAction = { action ->
                    if (selectedEpisodes.isNotEmpty()) {
                        onBatchEpisodeAction(selectedEpisodes, action)
                        isMultiSelectMode = false
                        selectedEpisodeIds = emptySet()
                    }
                }
            )
        }
    }

    if (showEditPodcastsModal) {
        var selectedIds by remember(playlist.id) { mutableStateOf(playlist.assignedPodcastIdList().toSet()) }
        AlertDialog(
            onDismissRequest = { showEditPodcastsModal = false },
            title = { Text("Assign Shows to \"${playlist.name}\"") },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    itemsIndexed(podcasts, key = { _, p -> p.id }) { _, pod ->
                        val checked = pod.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (checked) selectedIds - pod.id else selectedIds + pod.id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedIds = if (checked) selectedIds - pod.id else selectedIds + pod.id
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(pod.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateAssignedPodcasts(selectedIds.toList())
                    showEditPodcastsModal = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditPodcastsModal = false }) { Text("Cancel") }
            }
        )
    }
}
