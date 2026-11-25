package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import java.text.NumberFormat
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeEntity
import com.example.data.local.EpisodeSwipeAction
import com.example.data.local.LoheUserSettings
import com.example.data.local.PodcastEntity
import com.example.data.local.PodcastFilterOption
import com.example.data.local.PodcastSortOption
import com.example.edition.Edition
import com.example.playback.PlaybackUiState
import com.example.ui.components.EpisodeBatchAction
import com.example.ui.components.EpisodeMultiSelectActionBar
import com.example.ui.components.EpisodeMultiSelectTopBar
import com.example.ui.components.PodcastArtworkThumbnail
import com.example.ui.components.PodcastBatchAction
import com.example.ui.components.PodcastMultiSelectActionBar
import com.example.ui.components.PrimaryTabTopBar
import com.example.ui.components.QueueIndicatorBadge
import com.example.ui.components.SwipeableEpisodeRow
import com.example.ui.components.UniformCountPillBadge
import com.example.ui.components.UpNextQueueActionButton
import com.example.ui.components.formatDurationCompactHuman
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheBrightBlueText
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark
import com.example.ui.theme.LoheSurfaceDark

fun filterAndSortPodcasts(
    podcasts: List<PodcastEntity>,
    episodes: List<EpisodeEntity>,
    filter: PodcastFilterOption,
    sort: PodcastSortOption,
    searchQuery: String = ""
): List<PodcastEntity> {
    val episodesByPodcast = episodes.groupBy { it.podcastId }
    val cleanQuery = searchQuery.trim()

    val filtered = podcasts.filter { podcast ->
        val matchesQuery = cleanQuery.isBlank() ||
            podcast.title.contains(cleanQuery, ignoreCase = true) ||
            podcast.author.contains(cleanQuery, ignoreCase = true) ||
            podcast.category.contains(cleanQuery, ignoreCase = true)
        if (!matchesQuery) return@filter false

        val showEpisodes = episodesByPodcast[podcast.id].orEmpty()
        when (filter) {
            PodcastFilterOption.ALL -> true
            PodcastFilterOption.UNPLAYED -> showEpisodes.any { !it.isPlayed }
            PodcastFilterOption.IN_PROGRESS -> showEpisodes.any { !it.isPlayed && it.resumePositionMs > 0L }
            PodcastFilterOption.DOWNLOADED -> showEpisodes.any { it.isDownloaded }
            PodcastFilterOption.STARRED -> showEpisodes.any { it.isStarred }
            PodcastFilterOption.AUTO_QUEUE -> podcast.autoQueueMode != AutoQueueMode.OFF.name
            PodcastFilterOption.CUSTOM_AUDIO -> podcast.useCustomAudio
            PodcastFilterOption.CAUGHT_UP -> showEpisodes.none { !it.isPlayed }
        }
    }

    val comparator: Comparator<PodcastEntity> = when (sort) {
        PodcastSortOption.TITLE_ASC -> compareBy { it.title.lowercase() }
        PodcastSortOption.TITLE_DESC -> compareByDescending { it.title.lowercase() }
        PodcastSortOption.LATEST_EPISODE -> compareByDescending<PodcastEntity> { podcast ->
            episodesByPodcast[podcast.id]?.maxOfOrNull { it.publishedAt } ?: 0L
        }.thenBy { it.title.lowercase() }
        PodcastSortOption.MOST_UNPLAYED -> compareByDescending<PodcastEntity> { podcast ->
            episodesByPodcast[podcast.id]?.count { !it.isPlayed } ?: 0
        }.thenBy { it.title.lowercase() }
        PodcastSortOption.LEAST_UNPLAYED -> compareBy<PodcastEntity> { podcast ->
            episodesByPodcast[podcast.id]?.count { !it.isPlayed } ?: 0
        }.thenBy { it.title.lowercase() }
        PodcastSortOption.MOST_EPISODES -> compareByDescending<PodcastEntity> { podcast ->
            episodesByPodcast[podcast.id]?.size ?: 0
        }.thenBy { it.title.lowercase() }
        PodcastSortOption.MOST_LISTENED -> compareByDescending<PodcastEntity> { podcast ->
            episodesByPodcast[podcast.id]?.sumOf { ep ->
                if (ep.isPlayed) ep.durationMs.coerceAtLeast(ep.resumePositionMs) else ep.resumePositionMs
            } ?: 0L
        }.thenBy { it.title.lowercase() }
        PodcastSortOption.RECENTLY_UPDATED -> compareByDescending<PodcastEntity> { it.lastSyncedAt }
            .thenBy { it.title.lowercase() }
        PodcastSortOption.CATEGORY_ASC -> compareBy<PodcastEntity> {
            it.category.ifBlank { "zzz" }.lowercase()
        }.thenBy { it.title.lowercase() }
    }

    return filtered.sortedWith(comparator)
}

fun countPodcastsByFilter(
    podcasts: List<PodcastEntity>,
    episodes: List<EpisodeEntity>
): Map<PodcastFilterOption, Int> {
    val episodesByPodcast = episodes.groupBy { it.podcastId }
    return PodcastFilterOption.entries.associateWith { filter ->
        podcasts.count { podcast ->
            val showEpisodes = episodesByPodcast[podcast.id].orEmpty()
            when (filter) {
                PodcastFilterOption.ALL -> true
                PodcastFilterOption.UNPLAYED -> showEpisodes.any { !it.isPlayed }
                PodcastFilterOption.IN_PROGRESS -> showEpisodes.any { !it.isPlayed && it.resumePositionMs > 0L }
                PodcastFilterOption.DOWNLOADED -> showEpisodes.any { it.isDownloaded }
                PodcastFilterOption.STARRED -> showEpisodes.any { it.isStarred }
                PodcastFilterOption.AUTO_QUEUE -> podcast.autoQueueMode != AutoQueueMode.OFF.name
                PodcastFilterOption.CUSTOM_AUDIO -> podcast.useCustomAudio
                PodcastFilterOption.CAUGHT_UP -> showEpisodes.none { !it.isPlayed }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onUpdateSkips: (PodcastEntity, Int, Int) -> Unit = { _, _, _ -> },
    onPlayNext: (EpisodeEntity) -> Unit = { onToggleUpNext(it, true) },
    onToggleNotifications: (PodcastEntity, Boolean) -> Unit = { _, _ -> },
    onAddToUpNext: (EpisodeEntity, Boolean) -> Unit = { ep, beg -> onToggleUpNext(ep, beg) },
    onSwipeEpisodeAction: (EpisodeEntity, EpisodeSwipeAction) -> Unit = { _, _ -> },
    onOpenProUpgradeModal: () -> Unit = {},
    onSortChange: (PodcastSortOption) -> Unit = {},
    onFilterChange: (PodcastFilterOption) -> Unit = {},
    onBatchEpisodeAction: (List<EpisodeEntity>, EpisodeBatchAction) -> Unit = { _, _ -> },
    onBatchPodcastAction: (List<PodcastEntity>, PodcastBatchAction) -> Unit = { _, _ -> }
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
                onAddToUpNext = onAddToUpNext,
                onSwipeEpisodeAction = onSwipeEpisodeAction,
                onBatchEpisodeAction = onBatchEpisodeAction,
                onPlayNext = onPlayNext,
                onToggleStarred = onToggleStarred,
                onToggleDownloaded = onToggleDownloaded,
                onMarkEpisodePlayed = onMarkEpisodePlayed,
                onResetEpisodeProgress = onResetEpisodeProgress,
                onOpenNotes = onOpenNotes,
                onUpdateAudioOverrides = { useCustom, spd, skipSil, boost ->
                    onUpdateAudioOverrides(activeShow, useCustom, spd, skipSil, boost)
                },
                onUpdateSkips = { intro, outro -> onUpdateSkips(activeShow, intro, outro) },
                onToggleNotifications = { enabled -> onToggleNotifications(activeShow, enabled) }
            )
            return@BoxWithConstraints
        }

        var isGridView by remember { mutableStateOf(true) }
        var showAddRssDialog by remember { mutableStateOf(false) }
        var showSortMenu by remember { mutableStateOf(false) }
        var showSortPillMenu by remember { mutableStateOf(false) }
        var showSearchBar by remember { mutableStateOf(false) }
        var librarySearchQuery by remember { mutableStateOf("") }
        var rssInput by remember { mutableStateOf("") }
        var isPodcastMultiSelectMode by remember { mutableStateOf(false) }
        var selectedPodcastIds by remember { mutableStateOf(setOf<String>()) }
        var pendingUnsubscribePodcasts by remember { mutableStateOf<List<PodcastEntity>?>(null) }
        val haptic = LocalHapticFeedback.current

        val displayedPodcasts = remember(podcasts, episodes, settings.podcastFilterOption, settings.podcastSortOption, librarySearchQuery) {
            filterAndSortPodcasts(
                podcasts = podcasts,
                episodes = episodes,
                filter = settings.podcastFilterOption,
                sort = settings.podcastSortOption,
                searchQuery = librarySearchQuery
            )
        }
        val activeSelectedPodcastIds = remember(selectedPodcastIds, displayedPodcasts) {
            val valid = displayedPodcasts.map { it.id }.toSet()
            selectedPodcastIds.filterTo(LinkedHashSet()) { it in valid }
        }
        val selectedPodcasts = remember(activeSelectedPodcastIds, displayedPodcasts) {
            displayedPodcasts.filter { it.id in activeSelectedPodcastIds }
        }

        BackHandler(enabled = isPodcastMultiSelectMode) {
            isPodcastMultiSelectMode = false
            selectedPodcastIds = emptySet()
        }

        val filterCounts = remember(podcasts, episodes) {
            countPodcastsByFilter(podcasts, episodes)
        }
        val paneDetailShow = if (isTwoPane) activeShow ?: displayedPodcasts.firstOrNull() ?: podcasts.firstOrNull() else null

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .then(if (isTwoPane) Modifier.weight(0.42f) else Modifier.fillMaxSize())
                    .padding(horizontal = 16.dp)
                    .testTag("podcasts_tab_screen")
            ) {
                if (isPodcastMultiSelectMode) {
                    EpisodeMultiSelectTopBar(
                        selectedCount = activeSelectedPodcastIds.size,
                        totalCount = displayedPodcasts.size,
                        onClose = {
                            isPodcastMultiSelectMode = false
                            selectedPodcastIds = emptySet()
                        },
                        onToggleSelectAll = {
                            selectedPodcastIds = if (activeSelectedPodcastIds.size >= displayedPodcasts.size) {
                                emptySet()
                            } else {
                                displayedPodcasts.map { it.id }.toSet()
                            }
                        }
                    )
                } else {
                    PrimaryTabTopBar(
                        title = stringResource(id = R.string.tab_podcasts),
                        trailingActions = {
                            if (displayedPodcasts.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        isPodcastMultiSelectMode = true
                                        selectedPodcastIds = emptySet()
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("podcasts_multiselect_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Checklist,
                                        contentDescription = "Select Multiple Podcasts",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onOpenSettings = onOpenSettings
                    )
                }

                val isDarkTheme = MaterialTheme.colorScheme.background == LoheBackgroundDark
                val formattedDisplayedCount = remember(displayedPodcasts.size) {
                    NumberFormat.getIntegerInstance().format(displayedPodcasts.size)
                }
                val formattedTotalCount = remember(podcasts.size) {
                    NumberFormat.getIntegerInstance().format(podcasts.size)
                }
                val isFiltered = settings.podcastFilterOption != PodcastFilterOption.ALL
                val showLabel = if (isFiltered) {
                    "of $formattedTotalCount"
                } else {
                    if (podcasts.size == 1) "Show" else "Shows"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Spacious, single-line count pill + active sort indicator
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = LoheElectricBlue.copy(alpha = if (isDarkTheme) 0.16f else 0.10f),
                        modifier = Modifier
                            .height(34.dp)
                            .border(
                                1.dp,
                                LoheElectricBlue.copy(alpha = if (isDarkTheme) 0.36f else 0.28f),
                                RoundedCornerShape(50)
                            )
                            .testTag("podcasts_count_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = formattedDisplayedCount,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = LoheElectricBlue,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = showLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (settings.podcastSortOption != PodcastSortOption.TITLE_ASC) {
                                Text(
                                    text = "• ${settings.podcastSortOption.shortLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LoheCyanAccent,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Compact grouped library actions pill (Sort, Refresh, Add RSS, Grid/List)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isDarkTheme) LoheSurfaceDark else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .height(36.dp)
                            .border(
                                1.dp,
                                if (isDarkTheme) LoheBorderDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("podcasts_sort_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapVert,
                                        contentDescription = "Sort Podcasts",
                                        tint = if (settings.podcastSortOption != PodcastSortOption.TITLE_ASC) LoheElectricBlue else LoheCyanAccent,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.testTag("podcasts_sort_menu")
                                ) {
                                    PodcastSortOption.entries.forEach { option ->
                                        val selected = settings.podcastSortOption == option
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (selected) LoheElectricBlue else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            trailingIcon = if (selected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Selected",
                                                        tint = LoheElectricBlue,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else null,
                                            onClick = {
                                                onSortChange(option)
                                                showSortMenu = false
                                            },
                                            modifier = Modifier.testTag("podcast_sort_${option.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = onRefreshFeeds,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("podcasts_refresh_feeds_button")
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh Feeds",
                                    tint = LoheCyanAccent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            val rssLocked = Edition.HAS_PRO && !settings.isProUnlocked
                            Box {
                                IconButton(
                                    onClick = {
                                        if (rssLocked) onOpenProUpgradeModal() else showAddRssDialog = true
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("podcasts_add_rss_button")
                                ) {
                                    Icon(
                                        Icons.Filled.RssFeed,
                                        contentDescription = if (rssLocked) "Add RSS (Pro)" else "Add RSS",
                                        tint = if (rssLocked) MaterialTheme.colorScheme.onSurfaceVariant else LoheCyanAccent,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                if (rssLocked) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 4.dp, end = 4.dp)
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .background(LoheElectricBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = "Pro feature",
                                            tint = Color.White,
                                            modifier = Modifier.size(8.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { isGridView = !isGridView },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("podcasts_view_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = "Toggle View",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                // Dedicated Sort & Library Filter bar (labeled Sort dropdown + quick library search + filter chips)
                if (podcasts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDarkTheme) LoheSurfaceDark else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .border(
                                        1.dp,
                                        if (settings.podcastSortOption != PodcastSortOption.TITLE_ASC) LoheElectricBlue.copy(alpha = 0.65f)
                                        else if (isDarkTheme) LoheBorderDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { showSortPillMenu = true }
                                    .testTag("podcasts_sort_pill_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapVert,
                                        contentDescription = null,
                                        tint = LoheElectricBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Sort: ${settings.podcastSortOption.shortLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Choose sort order",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showSortPillMenu,
                                onDismissRequest = { showSortPillMenu = false },
                                modifier = Modifier.testTag("podcasts_sort_pill_menu")
                            ) {
                                PodcastSortOption.entries.forEach { option ->
                                    val selected = settings.podcastSortOption == option
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.label,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (selected) LoheElectricBlue else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        trailingIcon = if (selected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Selected",
                                                    tint = LoheElectricBlue,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            onSortChange(option)
                                            showSortPillMenu = false
                                        },
                                        modifier = Modifier.testTag("podcast_sort_pill_${option.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (showSearchBar || librarySearchQuery.isNotBlank()) {
                                LoheElectricBlue.copy(alpha = 0.16f)
                            } else if (isDarkTheme) LoheSurfaceDark else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .border(
                                    1.dp,
                                    if (showSearchBar || librarySearchQuery.isNotBlank()) LoheElectricBlue.copy(alpha = 0.6f)
                                    else if (isDarkTheme) LoheBorderDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { showSearchBar = !showSearchBar }
                                .testTag("podcasts_search_toggle_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Filter Shows by Name",
                                    tint = if (showSearchBar || librarySearchQuery.isNotBlank()) LoheElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (librarySearchQuery.isBlank()) "Find" else "\"$librarySearchQuery\"",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (showSearchBar || librarySearchQuery.isNotBlank()) LoheElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isFiltered || librarySearchQuery.isNotBlank() || settings.podcastSortOption != PodcastSortOption.TITLE_ASC) {
                            TextButton(
                                onClick = {
                                    librarySearchQuery = ""
                                    showSearchBar = false
                                    onFilterChange(PodcastFilterOption.ALL)
                                    onSortChange(PodcastSortOption.TITLE_ASC)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("podcasts_reset_filters_button")
                            ) {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LoheBrightBlueText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (showSearchBar) {
                        OutlinedTextField(
                            value = librarySearchQuery,
                            onValueChange = { librarySearchQuery = it },
                            placeholder = { Text("Filter shows by name, host, or category...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = if (librarySearchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { librarySearchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear filter text", modifier = Modifier.size(18.dp))
                                    }
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("podcasts_library_search_input")
                        )
                    }

                    // Filter chip strip
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("podcasts_filter_row")
                    ) {
                        items(PodcastFilterOption.entries) { filterOption ->
                            val selected = settings.podcastFilterOption == filterOption
                            val count = filterCounts[filterOption] ?: 0
                            FilterChip(
                                selected = selected,
                                onClick = { onFilterChange(filterOption) },
                                label = {
                                    Text(
                                        text = "${filterOption.label} ($count)",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LoheElectricBlue.copy(alpha = 0.20f),
                                    selectedLabelColor = LoheElectricBlue
                                ),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag("podcast_filter_${filterOption.name.lowercase()}")
                            )
                        }
                    }
                }

                if (podcasts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No podcasts yet. Import OPML or explore Discover.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (displayedPodcasts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No podcasts match \"${settings.podcastFilterOption.label}\".",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try another filter or show your full library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { onFilterChange(PodcastFilterOption.ALL) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("podcasts_clear_filter_button")
                        ) {
                            Text("Show All Podcasts")
                        }
                    }
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = if (isPodcastMultiSelectMode) 12.dp else 24.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(displayedPodcasts, key = { it.id }) { podcast ->
                            val showEpisodes = episodes.filter { it.podcastId == podcast.id }
                            val unplayedCount = showEpisodes.count { !it.isPlayed }
                            val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
                            val isSelectedPane = isTwoPane && paneDetailShow?.id == podcast.id
                            val isSelected = podcast.id in activeSelectedPodcastIds
                            val borderClr = if (isSelected || isSelectedPane) LoheElectricBlue else if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isSelected) LoheElectricBlue.copy(alpha = if (isDark) 0.16f else 0.10f)
                                else if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        if (isSelected || isSelectedPane) 1.5.dp else 1.dp,
                                        borderClr,
                                        RoundedCornerShape(18.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (isPodcastMultiSelectMode) {
                                                selectedPodcastIds = if (isSelected) {
                                                    selectedPodcastIds - podcast.id
                                                } else {
                                                    selectedPodcastIds + podcast.id
                                                }
                                            } else {
                                                onSelectPodcast(podcast.id)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isPodcastMultiSelectMode = true
                                            selectedPodcastIds = selectedPodcastIds + podcast.id
                                        }
                                    )
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
                                        if (isPodcastMultiSelectMode) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) LoheElectricBlue else Color(0xCC0A101D))
                                                    .border(1.5.dp, Color.White, CircleShape)
                                                    .testTag("podcast_select_icon_${podcast.id}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (unplayedCount > 0) {
                                            UniformCountPillBadge(
                                                count = unplayedCount,
                                                borderColor = LoheBackgroundDark.copy(alpha = 0.65f),
                                                borderWidth = 1.dp,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .testTag("podcast_grid_unplayed_badge_${podcast.id}")
                                            )
                                        }
                                        if (podcast.autoQueueMode != AutoQueueMode.OFF.name) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(6.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xD90A101D))
                                                    .border(
                                                        0.8.dp,
                                                        LoheCyanAccent.copy(alpha = 0.55f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "AUTO-Q",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = LoheCyanAccent,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = podcast.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        minLines = 2,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = podcast.author.ifBlank { "Podcast" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        minLines = 1,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = if (isPodcastMultiSelectMode) 12.dp else 24.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(displayedPodcasts, key = { it.id }) { podcast ->
                            val showEpisodes = episodes.filter { it.podcastId == podcast.id }
                            val unplayedCount = showEpisodes.count { !it.isPlayed }
                            val isSelectedPane = isTwoPane && paneDetailShow?.id == podcast.id
                            val isSelected = podcast.id in activeSelectedPodcastIds
                            val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) LoheElectricBlue.copy(alpha = if (isDark) 0.16f else 0.10f)
                                else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        if (isSelected || isSelectedPane) 1.5.dp else 1.dp,
                                        if (isSelected || isSelectedPane) LoheElectricBlue else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (isPodcastMultiSelectMode) {
                                                selectedPodcastIds = if (isSelected) {
                                                    selectedPodcastIds - podcast.id
                                                } else {
                                                    selectedPodcastIds + podcast.id
                                                }
                                            } else {
                                                onSelectPodcast(podcast.id)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isPodcastMultiSelectMode = true
                                            selectedPodcastIds = selectedPodcastIds + podcast.id
                                        }
                                    )
                                    .testTag("podcast_card_${podcast.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isPodcastMultiSelectMode) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                            contentDescription = if (isSelected) "Selected" else "Not selected",
                                            tint = if (isSelected) LoheElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .testTag("podcast_select_icon_${podcast.id}")
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
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
                                            text = podcast.author.ifBlank { "Podcast" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (unplayedCount > 0) {
                                        Spacer(Modifier.width(10.dp))
                                        UniformCountPillBadge(
                                            count = unplayedCount,
                                            borderColor = LoheBackgroundDark.copy(alpha = 0.65f),
                                            borderWidth = 1.dp,
                                            modifier = Modifier.testTag("podcast_list_unplayed_badge_${podcast.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isPodcastMultiSelectMode) {
                    PodcastMultiSelectActionBar(
                        selectedPodcasts = selectedPodcasts,
                        onBatchAction = { action ->
                            if (selectedPodcasts.isEmpty()) return@PodcastMultiSelectActionBar
                            if (action == PodcastBatchAction.UNSUBSCRIBE) {
                                pendingUnsubscribePodcasts = selectedPodcasts
                            } else {
                                onBatchPodcastAction(selectedPodcasts, action)
                                isPodcastMultiSelectMode = false
                                selectedPodcastIds = emptySet()
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
                            onAddToUpNext = onAddToUpNext,
                            onSwipeEpisodeAction = onSwipeEpisodeAction,
                            onBatchEpisodeAction = onBatchEpisodeAction,
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
                            onUpdateSkips = { intro, outro -> onUpdateSkips(paneDetailShow, intro, outro) },
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

        pendingUnsubscribePodcasts?.takeIf { it.isNotEmpty() }?.let { toRemove ->
            AlertDialog(
                onDismissRequest = { pendingUnsubscribePodcasts = null },
                title = { Text("Unsubscribe from ${toRemove.size} Show${if (toRemove.size == 1) "" else "s"}?") },
                text = { Text("This removes the selected podcasts and their cached downloads from your library.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onBatchPodcastAction(toRemove, PodcastBatchAction.UNSUBSCRIBE)
                            pendingUnsubscribePodcasts = null
                            isPodcastMultiSelectMode = false
                            selectedPodcastIds = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.testTag("podcast_multiselect_unsubscribe_confirm")
                    ) {
                        Text("Unsubscribe", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingUnsubscribePodcasts = null }) { Text("Cancel") }
                }
            )
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    onAddToUpNext: (EpisodeEntity, Boolean) -> Unit = { ep, beg -> onToggleUpNext(ep, beg) },
    onSwipeEpisodeAction: (EpisodeEntity, EpisodeSwipeAction) -> Unit = { _, _ -> },
    onBatchEpisodeAction: (List<EpisodeEntity>, EpisodeBatchAction) -> Unit = { _, _ -> },
    onPlayNext: (EpisodeEntity) -> Unit = { onToggleUpNext(it, true) },
    onToggleStarred: (EpisodeEntity) -> Unit,
    onToggleDownloaded: (EpisodeEntity) -> Unit,
    onMarkEpisodePlayed: (EpisodeEntity, Boolean) -> Unit,
    onResetEpisodeProgress: (EpisodeEntity) -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    isEmbeddedPane: Boolean = false,
    onUpdateAudioOverrides: (Boolean, Float, Boolean, Int) -> Unit = { _, _, _, _ -> },
    onUpdateSkips: (Int, Int) -> Unit = { _, _ -> },
    onToggleNotifications: (Boolean) -> Unit = {}
) {
    var activeFilter by remember { mutableStateOf("All") }
    var sortNewestFirst by remember { mutableStateOf(true) }
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }
    var showPodcastSettingsModal by remember(podcast.id) { mutableStateOf(false) }
    var isMultiSelectMode by remember(podcast.id) { mutableStateOf(false) }
    var selectedEpisodeIds by remember(podcast.id) { mutableStateOf(setOf<String>()) }
    val haptic = LocalHapticFeedback.current

    val filteredEpisodes = remember(episodes, activeFilter, sortNewestFirst) {
        val base = when (activeFilter) {
            "Unplayed" -> episodes.filter { !it.isPlayed }
            "Listening" -> episodes.filter { !it.isPlayed && it.resumePositionMs > 0L }
            "Played" -> episodes.filter { it.isPlayed }
            else -> episodes
        }
        if (sortNewestFirst) base.sortedByDescending { it.publishedAt } else base.sortedBy { it.publishedAt }
    }
    val activeSelectedIds = remember(selectedEpisodeIds, filteredEpisodes) {
        val valid = filteredEpisodes.map { it.id }.toSet()
        selectedEpisodeIds.filterTo(LinkedHashSet()) { it in valid }
    }
    val selectedEpisodes = remember(activeSelectedIds, filteredEpisodes) {
        filteredEpisodes.filter { it.id in activeSelectedIds }
    }

    BackHandler(enabled = isMultiSelectMode || !isEmbeddedPane) {
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            selectedEpisodeIds = emptySet()
        } else {
            onBack()
        }
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
        if (isMultiSelectMode) {
            EpisodeMultiSelectTopBar(
                selectedCount = activeSelectedIds.size,
                totalCount = filteredEpisodes.size,
                onClose = {
                    isMultiSelectMode = false
                    selectedEpisodeIds = emptySet()
                },
                onToggleSelectAll = {
                    selectedEpisodeIds = if (activeSelectedIds.size >= filteredEpisodes.size && filteredEpisodes.isNotEmpty()) {
                        emptySet()
                    } else {
                        filteredEpisodes.map { it.id }.toSet()
                    }
                }
            )
        } else {
            // Top Header with title, Multi-Select, GraphicEq sync/waveform, Sort & single Settings gear
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
                    if (filteredEpisodes.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                isMultiSelectMode = true
                                selectedEpisodeIds = emptySet()
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("podcast_multiselect_toggle")
                        ) {
                            Icon(
                                Icons.Filled.Checklist,
                                contentDescription = "Select Episodes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = if (isMultiSelectMode) 12.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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

                            val hasCustomShowSettings = isAutoQueueEnabled ||
                                podcast.useCustomAudio ||
                                podcast.skipIntroSec > 0 ||
                                podcast.skipOutroSec > 0
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (hasCustomShowSettings) LoheElectricBlue.copy(alpha = 0.16f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (hasCustomShowSettings) LoheElectricBlue else borderColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { showPodcastSettingsModal = true }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                    .testTag("podcast_settings_button")
                            ) {
                                Icon(
                                    Icons.Filled.Tune,
                                    contentDescription = "Podcast Settings",
                                    tint = if (hasCustomShowSettings) LoheCyanAccent else LoheBrightBlueText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Show Settings",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (hasCustomShowSettings) LoheCyanAccent else MaterialTheme.colorScheme.onSurface
                                )
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

            // Episode Rows with Configurable Swipe Gestures, Multi-Select, Queue Indicator & Up Next chooser
            items(filteredEpisodes, key = { it.id }) { ep ->
                val isPlayingEp = playbackState.currentEpisode?.id == ep.id && playbackState.isPlaying
                val isSelected = ep.id in activeSelectedIds
                val qPos = upNextEpisodes.indexOfFirst { it.id == ep.id }.takeIf { it >= 0 }?.plus(1)
                SwipeableEpisodeRow(
                    episode = ep,
                    settings = settings,
                    onSwipeAction = onSwipeEpisodeAction,
                    cornerRadius = 18.dp,
                    enabled = !isMultiSelectMode
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) LoheElectricBlue.copy(alpha = if (isDark) 0.16f else 0.10f) else cardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected || isPlayingEp) LoheElectricBlue else borderColor,
                                RoundedCornerShape(18.dp)
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
                            .testTag("podcast_ep_row_${ep.id}")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isMultiSelectMode) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = if (isSelected) "Selected" else "Not selected",
                                        tint = if (isSelected) LoheElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .testTag("podcast_ep_select_icon_${ep.id}")
                                    )
                                    Spacer(Modifier.width(10.dp))
                                } else if (ep.inUpNext) {
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
                                if (!isMultiSelectMode) {
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
                                if (!isMultiSelectMode) {
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
                                        UpNextQueueActionButton(
                                            inUpNext = ep.inUpNext,
                                            onAddToBeginning = { onAddToUpNext(ep, true) },
                                            onAddToEnd = { onAddToUpNext(ep, false) },
                                            onRemoveFromUpNext = if (ep.inUpNext) ({ onToggleUpNext(ep, false) }) else null,
                                            buttonSize = 34.dp,
                                            iconSize = 19.dp,
                                            tint = if (ep.inUpNext) LoheBrightBlueText else MaterialTheme.colorScheme.onSurfaceVariant,
                                            testTagPrefix = "podcast_up_next_${ep.id}"
                                        )
                                    }
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

    if (showPodcastSettingsModal) {
        Dialog(
            onDismissRequest = { showPodcastSettingsModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 680.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                    .testTag("podcast_settings_modal")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = podcast.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { showPodcastSettingsModal = false },
                            modifier = Modifier.testTag("podcast_settings_close")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Show Settings")
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // New Episode Alerts
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = if (podcast.notifyNewEpisodes) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "New Episode Alerts",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Notify when new episodes are published.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Switch(
                                    checked = podcast.notifyNewEpisodes,
                                    onCheckedChange = onToggleNotifications,
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LoheElectricBlue),
                                    modifier = Modifier.testTag("podcast_notify_switch")
                                )
                            }
                        }

                        // Auto-Queue Card
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

                        // Per-Show Audio Overrides Card
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
                                    com.example.ui.components.VariableSpeedSliderBar(
                                        speed = podcast.customSpeed,
                                        onSpeedChange = { spd ->
                                            onUpdateAudioOverrides(
                                                true,
                                                spd,
                                                podcast.customSkipSilence,
                                                podcast.customVolumeBoostDb
                                            )
                                        },
                                        title = "Show Speed",
                                        keySpeedTagPrefix = "podcast_speed",
                                        onDarkSurface = isDark
                                    )

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

                        // Skip Intro & Outro Card
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                                .testTag("podcast_skip_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Skip Intro & Outro",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (podcast.skipIntroSec > 0 || podcast.skipOutroSec > 0) {
                                        "Skipping first ${podcast.skipIntroSec}s • last ${podcast.skipOutroSec}s of every episode"
                                    } else {
                                        "Jump past recurring intros and trailing credits on every episode"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (podcast.skipIntroSec > 0 || podcast.skipOutroSec > 0) LoheCyanAccent
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                SkipSecondsStepper(
                                    label = "Skip from start",
                                    seconds = podcast.skipIntroSec,
                                    tagPrefix = "podcast_skip_intro",
                                    borderColor = borderColor,
                                    onChange = { onUpdateSkips(it, podcast.skipOutroSec) }
                                )
                                Spacer(Modifier.height(10.dp))
                                SkipSecondsStepper(
                                    label = "Skip from end",
                                    seconds = podcast.skipOutroSec,
                                    tagPrefix = "podcast_skip_outro",
                                    borderColor = borderColor,
                                    onChange = { onUpdateSkips(podcast.skipIntroSec, it) }
                                )
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

@Composable
private fun SkipSecondsStepper(
    label: String,
    seconds: Int,
    tagPrefix: String,
    borderColor: Color,
    onChange: (Int) -> Unit
) {
    val step = 5
    val maxSeconds = 600
    val valueLabel = when {
        seconds <= 0 -> "Off"
        seconds >= 60 -> "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        else -> "${seconds}s"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, borderColor, CircleShape)
                    .clickable(enabled = seconds > 0) { onChange((seconds - step).coerceAtLeast(0)) }
                    .semantics { contentDescription = "$label: decrease by $step seconds" }
                    .testTag("${tagPrefix}_minus")
            ) {
                Text(
                    "−",
                    fontSize = 20.sp,
                    color = if (seconds > 0) LoheCyanAccent else MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (seconds > 0) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(56.dp)
                    .testTag("${tagPrefix}_value")
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, borderColor, CircleShape)
                    .clickable(enabled = seconds < maxSeconds) { onChange((seconds + step).coerceAtMost(maxSeconds)) }
                    .semantics { contentDescription = "$label: increase by $step seconds" }
                    .testTag("${tagPrefix}_plus")
            ) {
                Text(
                    "+",
                    fontSize = 20.sp,
                    color = if (seconds < maxSeconds) LoheCyanAccent else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
