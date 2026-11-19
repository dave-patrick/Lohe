package com.lohe.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lohe.app.R
import com.lohe.app.data.local.EpisodeEntity
import com.lohe.app.data.local.LoheUserSettings
import com.lohe.app.data.local.PlaylistEntity
import com.lohe.app.data.local.PodcastEntity
import com.lohe.app.playback.PlaybackUiState
import com.lohe.app.ui.components.PodcastArtworkThumbnail
import com.lohe.app.ui.components.PrimaryTabTopBar
import com.lohe.app.ui.components.QueueIndicatorBadge
import com.lohe.app.ui.components.formatDurationCompactHuman
import com.lohe.app.ui.components.formatDurationMmSs
import com.lohe.app.ui.theme.LoheBackgroundDark
import com.lohe.app.ui.theme.LoheBorderDark
import com.lohe.app.ui.theme.LoheBrightBlueText
import com.lohe.app.ui.theme.LoheCyanAccent
import com.lohe.app.ui.theme.LoheElectricBlue
import com.lohe.app.ui.theme.LoheSurfaceCardDark

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
    onOpenSettings: () -> Unit
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

        val folders = remember(playlists) {
            listOf("All") + playlists.map { it.folderName }.distinct().sorted()
        }

        val displayedPlaylists = remember(playlists, selectedFolder, sortNameAsc) {
            val base = playlists.filter { selectedFolder == "All" || it.folderName == selectedFolder }
            if (sortNameAsc) base.sortedBy { it.name.lowercase() } else base
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
        PrimaryTabTopBar(
            title = stringResource(id = R.string.tab_playlists),
            badgeCount = playlists.size,
            trailingActions = {
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
                contentPadding = PaddingValues(bottom = 172.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedPlaylists, key = { it.id }) { pl ->
                    val plEps = resolvePlaylistEpisodes(pl, episodes)
                    val totalDurMs = plEps.sumOf { it.durationMs }
                    val isSelectedPane = isTwoPane && paneDetailPlaylist?.id == pl.id
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (isSelectedPane) 1.5.dp else 1.dp,
                                if (isSelectedPane) LoheElectricBlue else borderColor,
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { onSelectPlaylist(pl.id) }
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
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(CircleShape)
                                        .background(LoheElectricBlue)
                                        .border(1.5.dp, Color.White, CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("${plEps.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                if (pl.autoDownload) {
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
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (pl.folderName != "General") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = LoheBrightBlueText,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = pl.folderName,
                                        fontSize = 11.sp,
                                        color = LoheBrightBlueText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = LoheBrightBlueText, fontWeight = FontWeight.SemiBold)) {
                                        append("${plEps.size}")
                                    }
                                    append(" ep • ${formatDurationCompactHuman(totalDurMs)}")
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

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
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 172.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(displayedPlaylists, key = { _, p -> p.id }) { _, pl ->
                    val plEps = resolvePlaylistEpisodes(pl, episodes)
                    val totalDurMs = plEps.sumOf { it.durationMs }
                    val isSelectedPane = isTwoPane && paneDetailPlaylist?.id == pl.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (isSelectedPane) 1.5.dp else 1.dp,
                                if (isSelectedPane) LoheElectricBlue else borderColor,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelectPlaylist(pl.id) }
                            .testTag("playlist_card_${pl.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                PlaylistCollageArtwork(episodes = plEps, fallbackTitle = pl.name)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pl.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    text = "${pl.folderName} • ${plEps.size} episodes • ${formatDurationCompactHuman(totalDurMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { plEps.firstOrNull()?.let(onPlayEpisode) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = LoheElectricBlue)
                            }
                        }
                    }
                }
            }
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
    onToggleStarred: (EpisodeEntity) -> Unit,
    onToggleDownloaded: (EpisodeEntity) -> Unit,
    onMarkPlayed: (EpisodeEntity, Boolean) -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    onOpenSettings: () -> Unit,
    isEmbeddedPane: Boolean = false
) {
    BackHandler(enabled = !isEmbeddedPane) { onBack() }

    var filterUnplayed by remember { mutableStateOf(false) }
    var sortNewest by remember { mutableStateOf(true) }
    var showEditPodcastsModal by remember { mutableStateOf(false) }
    var showDetailMenu by remember { mutableStateOf(false) }
    var expandedEpId by remember { mutableStateOf<String?>(null) }

    val displayed = remember(playlistEpisodes, filterUnplayed, sortNewest) {
        val base = if (filterUnplayed) playlistEpisodes.filter { !it.isPlayed } else playlistEpisodes
        if (sortNewest) base.sortedByDescending { it.publishedAt } else base.sortedBy { it.publishedAt }
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

        // Assigned Podcasts Row + Edit button (for custom playlists)
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

        LazyColumn(
            contentPadding = PaddingValues(bottom = 172.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(displayed, key = { _, e -> e.id }) { idx, ep ->
                val isPlayingEp = playbackState.currentEpisode?.id == ep.id && playbackState.isPlaying
                val qPos = upNextEpisodes.indexOfFirst { it.id == ep.id }.takeIf { it >= 0 }?.plus(1)
                val isExpanded = expandedEpId == ep.id
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isPlayingEp) LoheElectricBlue else borderColor, RoundedCornerShape(16.dp))
                        .clickable { expandedEpId = if (isExpanded) null else ep.id }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(22.dp)
                            )
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
                            IconButton(onClick = { onPlayEpisode(ep) }) {
                                Icon(
                                    if (isPlayingEp) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = LoheElectricBlue
                                )
                            }
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LoheElectricBlue.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = { expandedEpId = null }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onToggleUpNext(ep, false) }, modifier = Modifier.size(38.dp)) {
                                    Icon(
                                        if (ep.inUpNext) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = "Up Next",
                                        tint = LoheCyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onToggleStarred(ep) }, modifier = Modifier.size(38.dp)) {
                                    Icon(
                                        if (ep.isStarred) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = "Star",
                                        tint = LoheCyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onMarkPlayed(ep, !ep.isPlayed) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Filled.Check, contentDescription = "Played", tint = LoheBrightBlueText, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onToggleDownloaded(ep) }, modifier = Modifier.size(38.dp)) {
                                    Icon(
                                        if (ep.isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                        contentDescription = "Download",
                                        tint = LoheBrightBlueText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onOpenNotes(ep) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Filled.Description, contentDescription = "Notes", tint = LoheBrightBlueText, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
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
