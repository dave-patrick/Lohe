package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheUserSettings
import com.example.data.local.PodcastEntity
import com.example.playback.PlaybackUiState
import com.example.ui.components.PodcastArtworkThumbnail
import com.example.ui.components.PrimaryTabTopBar
import com.example.ui.components.QueueIndicatorBadge
import com.example.ui.components.formatDurationCompactHuman
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheBrightBlueText
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark

@Composable
fun PodcastsTabScreen(
    settings: LoheUserSettings,
    podcasts: List<PodcastEntity>,
    episodes: List<EpisodeEntity>,
    upNextEpisodes: List<EpisodeEntity>,
    selectedPodcastId: String?,
    playbackState: PlaybackUiState,
    onSelectPodcast: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateAutoQueue: (PodcastEntity, AutoQueueMode) -> Unit,
    onUnsubscribePodcast: (String) -> Unit,
    onImportOpml: () -> Unit,
    onAddRssUrl: (String) -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onToggleUpNext: (EpisodeEntity, Boolean) -> Unit,
    onToggleStarred: (EpisodeEntity) -> Unit,
    onToggleDownloaded: (EpisodeEntity) -> Unit,
    onMarkEpisodePlayed: (EpisodeEntity, Boolean) -> Unit,
    onResetEpisodeProgress: (EpisodeEntity) -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    onRefreshFeeds: () -> Unit = {},
    onUpdateAudioOverrides: (PodcastEntity, Boolean, Float, Boolean, Int) -> Unit = { _, _, _, _, _ -> },
    onPlayNext: (EpisodeEntity) -> Unit = { onToggleUpNext(it, true) },
    onToggleNotifications: (PodcastEntity, Boolean) -> Unit = { _, _ -> }
) {
    val activeShow = remember(podcasts, selectedPodcastId) {
        podcasts.find { it.id == selectedPodcastId }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp

        if (!isTwoPane && activeShow != null) {
            PodcastDetailScreen(
                podcast = activeShow,
                settings = settings,
                episodes = episodes.filter { it.podcastId == activeShow.id },
                upNextEpisodes = upNextEpisodes,
                playbackState = playbackState,
                onBack = { onSelectPodcast(null) },
                onOpenSettings = onOpenSettings,
                onUpdateAutoQueue = { mode -> onUpdateAutoQueue(activeShow, mode) },
                onUnsubscribe = { onUnsubscribePodcast(activeShow.id) },
                onPlayEpisode = onPlayEpisode,
                onToggleUpNext = onToggleUpNext,
                onPlayNext = onPlayNext,
                onToggleStarred = onToggleStarred,
                onToggleDownloaded = onToggleDownloaded,
                onMarkEpisodePlayed = onMarkEpisodePlayed,
                onResetEpisodeProgress = onResetEpisodeProgress,
                onOpenNotes = onOpenNotes,
                onUpdateAudioOverrides = { useCustom, spd, skipSil, boost ->
                    onUpdateAudioOverrides(activeShow, useCustom, spd, skipSil, boost)
                },
                onToggleNotifications = { enabled -> onToggleNotifications(activeShow, enabled) }
            )
            return@BoxWithConstraints
        }

        var isGridView by remember { mutableStateOf(true) }
        var showAddRssDialog by remember { mutableStateOf(false) }
        var rssInput by remember { mutableStateOf("") }
        val paneDetailShow = if (isTwoPane) activeShow ?: podcasts.firstOrNull() else null

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .then(if (isTwoPane) Modifier.weight(0.42f) else Modifier.fillMaxSize())
                    .padding(horizontal = 16.dp)
                    .testTag("podcasts_tab_screen")
            ) {
                PrimaryTabTopBar(
                    title = stringResource(id = R.string.tab_podcasts),
                    badgeCount = podcasts.size,
                    trailingActions = {
                        IconButton(
                            onClick = onRefreshFeeds,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("podcasts_refresh_feeds_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Feeds", tint = LoheCyanAccent)
                        }
                        IconButton(onClick = { showAddRssDialog = true }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Filled.RssFeed, contentDescription = "Add RSS", tint = LoheCyanAccent)
                        }
                        IconButton(onClick = onImportOpml, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Filled.FileOpen, contentDescription = "Import OPML", tint = LoheElectricBlue)
                        }
                        IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                contentDescription = "Toggle View",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onOpenSettings = onOpenSettings
                )

                if (podcasts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No podcasts yet. Import OPML or explore Discover.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 172.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(podcasts, key = { it.id }) { podcast ->
                            val showEpisodes = episodes.filter { it.podcastId == podcast.id }
                            val unplayedCount = showEpisodes.count { !it.isPlayed }
                            val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
                            val isSelectedPane = isTwoPane && paneDetailShow?.id == podcast.id
                            val borderClr = if (isSelectedPane) LoheElectricBlue else if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        if (isSelectedPane) 1.5.dp else 1.dp,
                                        borderClr,
                                        RoundedCornerShape(18.dp)
                                    )
                                    .clickable { onSelectPodcast(podcast.id) }
                                    .testTag("podcast_card_${podcast.id}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        PodcastArtworkThumbnail(
                                            artworkUrl = podcast.artworkUrl,
                                            title = podcast.title,
                                            cornerRadius = 14.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(136.dp)
                                        )
                                        if (unplayedCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .clip(CircleShape)
                                                    .background(LoheElectricBlue)
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "$unplayedCount",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = podcast.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = podcast.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (podcast.autoQueueMode != AutoQueueMode.OFF.name) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Auto-Queue: ${podcast.autoQueueMode}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = LoheCyanAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 172.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(podcasts, key = { it.id }) { podcast ->
                            val showEpisodes = episodes.filter { it.podcastId == podcast.id }
                            val unplayedCount = showEpisodes.count { !it.isPlayed }
                            val isSelectedPane = isTwoPane && paneDetailShow?.id == podcast.id
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        if (isSelectedPane) 1.5.dp else 1.dp,
                                        if (isSelectedPane) LoheElectricBlue else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onSelectPodcast(podcast.id) }
                                    .testTag("podcast_card_${podcast.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PodcastArtworkThumbnail(
                                        artworkUrl = podcast.artworkUrl,
                                        title = podcast.title,
                                        cornerRadius = 12.dp,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = podcast.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${podcast.author} • $unplayedCount unplayed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
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
                        .testTag("podcasts_two_pane_layout")
                ) {
                    if (paneDetailShow != null) {
                        PodcastDetailScreen(
                            podcast = paneDetailShow,
                            settings = settings,
                            episodes = episodes.filter { it.podcastId == paneDetailShow.id },
                            upNextEpisodes = upNextEpisodes,
                            playbackState = playbackState,
                            onBack = { onSelectPodcast(null) },
                            onOpenSettings = onOpenSettings,
                            onUpdateAutoQueue = { mode -> onUpdateAutoQueue(paneDetailShow, mode) },
                            onUnsubscribe = { onUnsubscribePodcast(paneDetailShow.id) },
                            onPlayEpisode = onPlayEpisode,
                            onToggleUpNext = onToggleUpNext,
                            onPlayNext = onPlayNext,
                            onToggleStarred = onToggleStarred,
                            onToggleDownloaded = onToggleDownloaded,
                            onMarkEpisodePlayed = onMarkEpisodePlayed,
                            onResetEpisodeProgress = onResetEpisodeProgress,
                            onOpenNotes = onOpenNotes,
                            isEmbeddedPane = true,
                            onUpdateAudioOverrides = { useCustom, spd, skipSil, boost ->
                                onUpdateAudioOverrides(paneDetailShow, useCustom, spd, skipSil, boost)
                            },
                            onToggleNotifications = { enabled -> onToggleNotifications(paneDetailShow, enabled) }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Select a podcast to inspect episodes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (showAddRssDialog) {
        AlertDialog(
            onDismissRequest = { showAddRssDialog = false },
            title = { Text("Add Podcast RSS Feed") },
            text = {
                OutlinedTextField(
                    value = rssInput,
                    onValueChange = { rssInput = it },
                    label = { Text("https://example.org/podcast.xml") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (rssInput.isNotBlank()) {
                        onAddRssUrl(rssInput)
                        rssInput = ""
                        showAddRssDialog = false
                    }
                }) { Text("Subscribe") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRssDialog = false }) { Text("Cancel") }
            }
        )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodcastDetailScreen(
    podcast: PodcastEntity,
    settings: LoheUserSettings,
    episodes: List<EpisodeEntity>,
    upNextEpisodes: List<EpisodeEntity>,
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateAutoQueue: (AutoQueueMode) -> Unit,
    onUnsubscribe: () -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onToggleUpNext: (EpisodeEntity, Boolean) -> Unit,
    onPlayNext: (EpisodeEntity) -> Unit = { onToggleUpNext(it, true) },
    onToggleStarred: (EpisodeEntity) -> Unit,
    onToggleDownloaded: (EpisodeEntity) -> Unit,
    onMarkEpisodePlayed: (EpisodeEntity, Boolean) -> Unit,
    onResetEpisodeProgress: (EpisodeEntity) -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    isEmbeddedPane: Boolean = false,
    onUpdateAudioOverrides: (Boolean, Float, Boolean, Int) -> Unit = { _, _, _, _ -> },
    onToggleNotifications: (Boolean) -> Unit = {}
) {
    BackHandler(enabled = !isEmbeddedPane) { onBack() }

    var activeFilter by remember { mutableStateOf("All") }
    var sortNewestFirst by remember { mutableStateOf(true) }
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }

    val filteredEpisodes = remember(episodes, activeFilter, sortNewestFirst) {
        val base = when (activeFilter) {
            "Unplayed" -> episodes.filter { !it.isPlayed }
            "Listening" -> episodes.filter { !it.isPlayed && it.resumePositionMs > 0L }
            "Played" -> episodes.filter { it.isPlayed }
            else -> episodes
        }
        if (sortNewestFirst) base.sortedByDescending { it.publishedAt } else base.sortedBy { it.publishedAt }
    }

    val isAutoQueueEnabled = podcast.autoQueueMode != AutoQueueMode.OFF.name
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardBg = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("podcast_detail_screen")
    ) {
        // Top Header with title, GraphicEq sync/waveform, Sort & single Settings gear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!isEmbeddedPane) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("podcast_detail_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { activeFilter = if (activeFilter == "Listening") "All" else "Listening" },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = "Listening Filter",
                        tint = if (activeFilter == "Listening") LoheElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { sortNewestFirst = !sortNewestFirst }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Sort Episodes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isEmbeddedPane) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("top_settings_button")
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(id = R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 172.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("podcast_detail_list")
        ) {
            // Show Summary Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            PodcastArtworkThumbnail(
                                artworkUrl = podcast.artworkUrl,
                                title = podcast.title,
                                cornerRadius = 14.dp,
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = podcast.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = podcast.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (podcast.description.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = podcast.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showUnsubscribeConfirm = true }
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Unsubscribe", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (podcast.notifyNewEpisodes) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("New Ep Alerts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = podcast.notifyNewEpisodes,
                                    onCheckedChange = onToggleNotifications,
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LoheElectricBlue),
                                    modifier = Modifier.testTag("podcast_notify_switch")
                                )
                            }
                        }
                    }
                }
            }

            // Auto-Queue Card (matching indicator-podcast-final_dcfc6890.png)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                        .testTag("podcast_auto_queue_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatically add to Up Next",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Adds existing unplayed episodes now and new episodes on refresh.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = isAutoQueueEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateAutoQueue(if (enabled) AutoQueueMode.END else AutoQueueMode.OFF)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LoheElectricBlue),
                                modifier = Modifier.testTag("auto_queue_switch")
                            )
                        }

                        if (isAutoQueueEnabled) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Add episodes at",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(AutoQueueMode.BEGINNING, AutoQueueMode.END).forEach { mode ->
                                    val active = podcast.autoQueueMode == mode.name
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (active) LoheElectricBlue else cardBg)
                                            .border(
                                                width = 1.dp,
                                                color = if (active) LoheElectricBlue else borderColor,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onUpdateAutoQueue(mode) }
                                            .padding(horizontal = 18.dp, vertical = 9.dp)
                                            .testTag("auto_queue_${mode.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = mode.label,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Per-Show Audio Overrides Card (Speed, Skip Silence, Volume Boost)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                        .testTag("podcast_custom_audio_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show Audio Overrides",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (podcast.useCustomAudio) {
                                        "Custom ${podcast.customSpeed}x • Skip Silence ${if (podcast.customSkipSilence) "ON" else "OFF"} • Boost +${podcast.customVolumeBoostDb}dB"
                                    } else {
                                        "Using global Playback Settings (${settings.playbackSpeed}x)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (podcast.useCustomAudio) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = podcast.useCustomAudio,
                                onCheckedChange = { enabled ->
                                    onUpdateAudioOverrides(
                                        enabled,
                                        if (podcast.useCustomAudio) podcast.customSpeed else settings.playbackSpeed,
                                        if (podcast.useCustomAudio) podcast.customSkipSilence else settings.skipSilenceEnabled,
                                        if (podcast.useCustomAudio) podcast.customVolumeBoostDb else settings.volumeBoostDb
                                    )
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LoheElectricBlue),
                                modifier = Modifier.testTag("podcast_custom_audio_switch")
                            )
                        }

                        if (podcast.useCustomAudio) {
                            Spacer(Modifier.height(10.dp))
                            Text("Show Speed", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { spd ->
                                    val selected = kotlin.math.abs(podcast.customSpeed - spd) < 0.03f
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) LoheElectricBlue else cardBg)
                                            .border(
                                                1.dp,
                                                if (selected) LoheElectricBlue else borderColor,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                onUpdateAudioOverrides(
                                                    true,
                                                    spd,
                                                    podcast.customSkipSilence,
                                                    podcast.customVolumeBoostDb
                                                )
                                            }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                            .testTag("podcast_speed_${spd}")
                                    ) {
                                        Text(
                                            text = "${spd}x",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Trim silence for this show", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = podcast.customSkipSilence,
                                    onCheckedChange = { skip ->
                                        onUpdateAudioOverrides(
                                            true,
                                            podcast.customSpeed,
                                            skip,
                                            podcast.customVolumeBoostDb
                                        )
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LoheElectricBlue)
                                )
                            }

                            Spacer(Modifier.height(6.dp))
                            Text("Voice Boost", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Off", 3 to "+3dB", 6 to "+6dB", 10 to "+10dB").forEach { (db, lbl) ->
                                    val activeBoost = podcast.customVolumeBoostDb == db
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (activeBoost) LoheElectricBlue else cardBg)
                                            .border(
                                                1.dp,
                                                if (activeBoost) LoheElectricBlue else borderColor,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                onUpdateAudioOverrides(
                                                    true,
                                                    podcast.customSpeed,
                                                    podcast.customSkipSilence,
                                                    db
                                                )
                                            }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = lbl,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (activeBoost) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Segmented Filter Pills (All, Played, Unplayed, Listening matching screenshot)
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Played", "Unplayed", "Listening").forEach { filter ->
                        val active = activeFilter == filter
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) LoheElectricBlue else cardBg)
                                .border(
                                    width = 1.dp,
                                    color = if (active) LoheElectricBlue else borderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { activeFilter = filter }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = filter,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Episode Rows with Queue Indicator & 6 Action Icons matching indicator-podcast-final
            items(filteredEpisodes, key = { it.id }) { ep ->
                val isPlayingEp = playbackState.currentEpisode?.id == ep.id && playbackState.isPlaying
                val qPos = upNextEpisodes.indexOfFirst { it.id == ep.id }.takeIf { it >= 0 }?.plus(1)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isPlayingEp) LoheElectricBlue else borderColor,
                            RoundedCornerShape(18.dp)
                        )
                        .testTag("podcast_ep_row_${ep.id}")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (ep.inUpNext) {
                                QueueIndicatorBadge(settings = settings, queuePosition = qPos)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = ep.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (ep.isPlayed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
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

                        if (ep.durationMs > 0L && ep.resumePositionMs > 0L) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (ep.resumePositionMs.toFloat() / ep.durationMs.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = LoheElectricBlue
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatEpisodeDate(ep.publishedAt)} • ${formatDurationCompactHuman(ep.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { onOpenNotes(ep) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Outlined.Info, contentDescription = "Notes", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onToggleStarred(ep) }, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        if (ep.isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Star",
                                        tint = if (ep.isStarred) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onToggleDownloaded(ep) }, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        if (ep.isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                        contentDescription = "Download",
                                        tint = if (ep.isDownloaded) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onResetEpisodeProgress(ep) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Filled.RestartAlt, contentDescription = "Reset Progress", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onMarkEpisodePlayed(ep, !ep.isPlayed) }, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        if (ep.isPlayed) Icons.Filled.CheckCircle else Icons.Filled.CheckCircleOutline,
                                        contentDescription = "Mark Played",
                                        tint = if (ep.isPlayed) LoheElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onPlayNext(ep) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("play_next_ep_${ep.id}")
                                ) {
                                    Icon(
                                        Icons.Filled.QueuePlayNext,
                                        contentDescription = "Play Next",
                                        tint = LoheCyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onToggleUpNext(ep, false) }, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        if (ep.inUpNext) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = "Toggle Up Next",
                                        tint = if (ep.inUpNext) LoheBrightBlueText else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUnsubscribeConfirm) {
        AlertDialog(
            onDismissRequest = { showUnsubscribeConfirm = false },
            title = { Text("Unsubscribe from ${podcast.title}?") },
            text = { Text("This removes the show and its unplayed episodes from your device library.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsubscribeConfirm = false
                        onUnsubscribe()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Unsubscribe", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showUnsubscribeConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
