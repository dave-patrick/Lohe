package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheUserSettings
import com.example.data.local.PodcastEntity
import com.example.data.repository.DiscoverCatalogItem
import com.example.playback.PlaybackUiState
import com.example.ui.components.HomeBrandingHeader
import com.example.ui.components.HomeFlyingKaniOverlay
import com.example.ui.components.ListeningActivity14DayCard
import com.example.ui.components.PodcastArtworkThumbnail
import com.example.ui.components.PrimaryTabTopBar
import com.example.ui.components.QueueIndicatorBadge
import com.example.ui.components.YourSignalCard
import com.example.ui.components.formatDurationCompactHuman
import com.example.ui.components.formatDurationMmSs
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheBrightBlueText
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatEpisodeDate(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
}

@Composable
fun HomeTabScreen(
    settings: LoheUserSettings,
    podcasts: List<PodcastEntity>,
    inProgressEpisodes: List<EpisodeEntity>,
    activeEpisodes: List<EpisodeEntity>,
    upNextEpisodes: List<EpisodeEntity>,
    playbackState: PlaybackUiState,
    hasPlayedHomeFlight: Boolean = false,
    onHomeFlightFinished: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToPodcasts: () -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onToggleUpNext: (EpisodeEntity, Boolean) -> Unit,
    onToggleStarred: (EpisodeEntity) -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    onImportOpmlOrJson: () -> Unit,
    onExportJsonBackup: () -> Unit
) {
    val totalEpisodes = activeEpisodes.size
    val playedCount = activeEpisodes.count { it.isPlayed }
    val waitingCount = activeEpisodes.count { !it.isPlayed }
    val playedPercent = if (totalEpisodes > 0) ((playedCount * 100f) / totalEpisodes).toInt().coerceIn(1, 100) else 0
    val timePlayedMs = activeEpisodes.sumOf { ep ->
        if (ep.isPlayed) ep.durationMs else ep.resumePositionMs
    }
    val totalDurationMs = activeEpisodes.sumOf { it.durationMs }.coerceAtLeast(1L)
    val stillToHearMs = activeEpisodes.sumOf { ep ->
        if (ep.isPlayed) 0L else (ep.durationMs - ep.resumePositionMs).coerceAtLeast(0L)
    }

    val showHeaderKani = podcasts.isNotEmpty() || activeEpisodes.isNotEmpty()
    val listState = rememberLazyListState()
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var kaniRestCenterPx by remember { mutableStateOf<Offset?>(null) }
    var kaniRestSizePx by remember { mutableFloatStateOf(0f) }
    var isFlightDrawableReady by remember { mutableStateOf(false) }
    val restCenter = kaniRestCenterPx
    val isFlightOverlayActive = showHeaderKani &&
        !settings.reducedMotion &&
        !hasPlayedHomeFlight &&
        restCenter != null &&
        listState.firstVisibleItemIndex == 0 &&
        restCenter.y > -40f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoords = it }
    ) {
        val isTwoColumn = maxWidth >= 600.dp
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("home_tab_screen"),
            contentPadding = PaddingValues(bottom = 172.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HomeBrandingHeader(
                    reducedMotion = settings.reducedMotion,
                    onOpenSettings = onOpenSettings,
                    onKaniClick = {
                        inProgressEpisodes.firstOrNull()?.let(onPlayEpisode) ?: onNavigateToPodcasts()
                    },
                    showKani = showHeaderKani,
                    showStaticKani = settings.reducedMotion || hasPlayedHomeFlight || !isFlightOverlayActive || !isFlightDrawableReady,
                    onKaniSlotPositioned = { slotCoords ->
                        val root = rootCoords
                        if (root != null && root.isAttached && slotCoords.isAttached) {
                            val topLeft = root.localPositionOf(slotCoords, Offset.Zero)
                            kaniRestSizePx = slotCoords.size.width.toFloat()
                            kaniRestCenterPx = Offset(
                                x = topLeft.x + slotCoords.size.width / 2f,
                                y = topLeft.y + slotCoords.size.height / 2f
                            )
                        }
                    }
                )
            }

        if (podcasts.isEmpty() && activeEpisodes.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp, bottom = 24.dp, start = 12.dp, end = 12.dp)
                        .testTag("home_empty_onboarding_card"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.kani),
                        contentDescription = "Kani dragonfly mascot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(136.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Nothing queued up",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Find a show to follow, or import audio\nalready on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Button(
                        onClick = onNavigateToDiscover,
                        colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Find a show", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onImportOpmlOrJson,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Import from this device", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        } else if (isTwoColumn) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_two_column_layout"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        YourSignalCard(
                            playedPercent = playedPercent,
                            playedCount = playedCount,
                            totalEpisodesCount = totalEpisodes,
                            inProgressCount = inProgressEpisodes.size,
                            timePlayedMs = timePlayedMs,
                            totalDurationMs = totalDurationMs,
                            timeSavedMs = settings.totalTimeSavedMs,
                            stillToHearMs = stillToHearMs,
                            waitingEpisodesCount = waitingCount,
                            onInProgressClick = {
                                inProgressEpisodes.firstOrNull()?.let(onPlayEpisode) ?: onNavigateToPodcasts()
                            }
                        )
                        ListeningActivity14DayCard(
                            dailyMinutes = settings.dailyListeningMinutes,
                            totalTimeSavedMs = settings.totalTimeSavedMs
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (inProgressEpisodes.isNotEmpty()) {
                            Text(
                                text = stringResource(id = R.string.continue_listening_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            inProgressEpisodes.forEach { episode ->
                                CompactEpisodeRowCard(
                                    episode = episode,
                                    settings = settings,
                                    isCurrentlyPlaying = playbackState.currentEpisode?.id == episode.id && playbackState.isPlaying,
                                    queuePosition = upNextEpisodes.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 }?.plus(1),
                                    onPlay = { onPlayEpisode(episode) },
                                    onToggleUpNext = { onToggleUpNext(episode, false) },
                                    onToggleStarred = { onToggleStarred(episode) },
                                    onOpenNotes = { onOpenNotes(episode) }
                                )
                            }
                        }
                        Text(
                            text = "Recent Dispatches",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = if (inProgressEpisodes.isNotEmpty()) 6.dp else 0.dp)
                        )
                        activeEpisodes.take(6).forEach { episode ->
                            CompactEpisodeRowCard(
                                episode = episode,
                                settings = settings,
                                isCurrentlyPlaying = playbackState.currentEpisode?.id == episode.id && playbackState.isPlaying,
                                queuePosition = upNextEpisodes.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 }?.plus(1),
                                onPlay = { onPlayEpisode(episode) },
                                onToggleUpNext = { onToggleUpNext(episode, false) },
                                onToggleStarred = { onToggleStarred(episode) },
                                onOpenNotes = { onOpenNotes(episode) }
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportOpmlOrJson,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_import_saf_button")
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, tint = LoheCyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(id = R.string.import_opml_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onExportJsonBackup,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_export_saf_button")
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = LoheElectricBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(id = R.string.export_backup_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            item {
                YourSignalCard(
                    playedPercent = playedPercent,
                    playedCount = playedCount,
                    totalEpisodesCount = totalEpisodes,
                    inProgressCount = inProgressEpisodes.size,
                    timePlayedMs = timePlayedMs,
                    totalDurationMs = totalDurationMs,
                    timeSavedMs = settings.totalTimeSavedMs,
                    stillToHearMs = stillToHearMs,
                    waitingEpisodesCount = waitingCount,
                    onInProgressClick = {
                        inProgressEpisodes.firstOrNull()?.let(onPlayEpisode) ?: onNavigateToPodcasts()
                    }
                )
            }

            item {
                ListeningActivity14DayCard(
                    dailyMinutes = settings.dailyListeningMinutes,
                    totalTimeSavedMs = settings.totalTimeSavedMs
                )
            }

            if (inProgressEpisodes.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.continue_listening_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(inProgressEpisodes, key = { "in_prog_${it.id}" }) { episode ->
                    CompactEpisodeRowCard(
                        episode = episode,
                        settings = settings,
                        isCurrentlyPlaying = playbackState.currentEpisode?.id == episode.id && playbackState.isPlaying,
                        queuePosition = upNextEpisodes.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 }?.plus(1),
                        onPlay = { onPlayEpisode(episode) },
                        onToggleUpNext = { onToggleUpNext(episode, false) },
                        onToggleStarred = { onToggleStarred(episode) },
                        onOpenNotes = { onOpenNotes(episode) }
                    )
                }
            }

            item {
                Text(
                    text = "Recent Dispatches",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(activeEpisodes.take(6), key = { "recent_${it.id}" }) { episode ->
                CompactEpisodeRowCard(
                    episode = episode,
                    settings = settings,
                    isCurrentlyPlaying = playbackState.currentEpisode?.id == episode.id && playbackState.isPlaying,
                    queuePosition = upNextEpisodes.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 }?.plus(1),
                    onPlay = { onPlayEpisode(episode) },
                    onToggleUpNext = { onToggleUpNext(episode, false) },
                    onToggleStarred = { onToggleStarred(episode) },
                    onOpenNotes = { onOpenNotes(episode) }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportOpmlOrJson,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_import_saf_button")
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, tint = LoheCyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(id = R.string.import_opml_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onExportJsonBackup,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_export_saf_button")
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = LoheElectricBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(id = R.string.export_backup_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        }

        if (isFlightOverlayActive) {
            restCenter?.let { center ->
                HomeFlyingKaniOverlay(
                    restCenterPx = center,
                    restSlotSizePx = kaniRestSizePx,
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                    reducedMotion = settings.reducedMotion,
                    onDrawableReady = { ready -> isFlightDrawableReady = ready },
                    onFlightFinished = onHomeFlightFinished
                )
            }
        }
    }
}

@Composable
fun CompactEpisodeRowCard(
    episode: EpisodeEntity,
    settings: LoheUserSettings,
    isCurrentlyPlaying: Boolean,
    queuePosition: Int?,
    onPlay: () -> Unit,
    onToggleUpNext: () -> Unit,
    onToggleStarred: () -> Unit,
    onOpenNotes: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardBg = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val border = if (isCurrentlyPlaying) LoheElectricBlue else if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
            .testTag("episode_card_${episode.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PodcastArtworkThumbnail(
                artworkUrl = episode.artworkUrl,
                title = episode.podcastTitle,
                cornerRadius = 10.dp,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (episode.inUpNext) {
                        QueueIndicatorBadge(settings = settings, queuePosition = queuePosition)
                    }
                    Text(
                        text = episode.podcastTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = LoheBrightBlueText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${formatEpisodeDate(episode.publishedAt)} • ${formatDurationMmSs(episode.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                if (episode.durationMs > 0L && episode.resumePositionMs > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (episode.resumePositionMs.toFloat() / episode.durationMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = LoheElectricBlue,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenNotes, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = "Show Notes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onToggleStarred, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (episode.isStarred) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (episode.isStarred) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onToggleUpNext, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (episode.inUpNext) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Up Next",
                        tint = if (episode.inUpNext) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(LoheElectricBlue)
                        .testTag("play_ep_${episode.id}")
                ) {
                    Icon(
                        imageVector = if (isCurrentlyPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverTabScreen(
    query: String,
    catalog: List<DiscoverCatalogItem>,
    isSearching: Boolean,
    subscribedPodcasts: List<PodcastEntity>,
    previewItem: DiscoverCatalogItem?,
    previewEpisodes: List<EpisodeEntity>,
    isPreviewLoading: Boolean,
    onSearchDiscover: (String) -> Unit,
    onBrowseCategory: (String) -> Unit = onSearchDiscover,
    onOpenPreview: (DiscoverCatalogItem?) -> Unit,
    onSubscribeCatalog: (DiscoverCatalogItem) -> Unit,
    onSubscribeRssUrl: (String) -> Unit,
    onPlayPreviewEpisode: (EpisodeEntity) -> Unit,
    onImportOpmlOrJson: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var searchText by remember(query) { mutableStateOf(query) }
    var showRssDialog by remember { mutableStateOf(false) }
    var rssInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("discover_tab_screen")
    ) {
        PrimaryTabTopBar(
            title = stringResource(id = R.string.tab_discover),
            subtitle = "Trending charts, genre browse & live Apple RSS lookup",
            trailingActions = {
                IconButton(
                    onClick = { showRssDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("discover_rss_button")
                ) {
                    Icon(Icons.Filled.RssFeed, contentDescription = "Add RSS URL", tint = LoheCyanAccent)
                }
                IconButton(
                    onClick = onImportOpmlOrJson,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = "Import OPML", tint = LoheElectricBlue)
                }
            },
            onOpenSettings = onOpenSettings
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search shows, hosts, or topics...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("discover_search_input")
            )
            Button(
                onClick = { onSearchDiscover(searchText) },
                colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("discover_search_button")
            ) {
                Text("Search", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
        val chipBg = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
        val chipBorder = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Trending", "All", "Technology", "Science", "Design", "History", "Philosophy", "Music").forEach { topic ->
                val selected = (searchText.isBlank() && topic == "All") || searchText.equals(topic, ignoreCase = true)
                val chipTag = if (topic == "Trending") "discover_trending_chip" else "discover_category_${topic.lowercase()}"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) LoheElectricBlue else chipBg)
                        .border(
                            width = 1.dp,
                            color = if (selected) LoheElectricBlue else chipBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            searchText = if (topic == "All") "" else topic
                            onBrowseCategory(topic)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag(chipTag)
                ) {
                    Text(
                        text = topic,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LoheElectricBlue)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 165.dp),
            contentPadding = PaddingValues(bottom = 172.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(catalog, key = { it.feedUrl + it.title }) { item ->
                val isSubscribed = subscribedPodcasts.any {
                    it.feedUrl == item.feedUrl || it.title.equals(item.title, ignoreCase = true)
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .clickable { onOpenPreview(item) }
                        .testTag("discover_card_${item.title}")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            PodcastArtworkThumbnail(
                                artworkUrl = item.artworkUrl,
                                title = item.title,
                                cornerRadius = 14.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                            if (isSubscribed) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .clip(CircleShape)
                                        .background(LoheElectricBlue)
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Subscribed",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.category,
                                fontSize = 11.sp,
                                color = LoheBrightBlueText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else LoheElectricBlue,
                                modifier = Modifier.clickable {
                                    if (isSubscribed) onOpenPreview(item) else onSubscribeCatalog(item)
                                }
                            ) {
                                Text(
                                    text = if (isSubscribed) "Open" else "+ Follow",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSubscribed) MaterialTheme.colorScheme.onSurface else Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (catalog.isEmpty() && !isSearching) {
                item(span = { GridItemSpan(2) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Explore, contentDescription = null, tint = LoheElectricBlue, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No matching shows", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showRssDialog) {
        AlertDialog(
            onDismissRequest = { showRssDialog = false },
            title = { Text("Subscribe by RSS Feed URL") },
            text = {
                OutlinedTextField(
                    value = rssInput,
                    onValueChange = { rssInput = it },
                    label = { Text("https://example.org/feed.xml") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (rssInput.isNotBlank()) {
                        onSubscribeRssUrl(rssInput)
                        rssInput = ""
                        showRssDialog = false
                    }
                }) { Text("Subscribe") }
            },
            dismissButton = {
                TextButton(onClick = { showRssDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (previewItem != null) {
        val alreadySubscribed = subscribedPodcasts.any {
            it.feedUrl == previewItem.feedUrl || it.title.equals(previewItem.title, ignoreCase = true)
        }
        Dialog(
            onDismissRequest = { onOpenPreview(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                    .testTag("discover_preview_dialog")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PodcastArtworkThumbnail(
                            artworkUrl = previewItem.artworkUrl,
                            title = previewItem.title,
                            cornerRadius = 14.dp,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = previewItem.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = previewItem.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = LoheBrightBlueText
                            )
                            Text(
                                text = previewItem.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onOpenPreview(null) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Preview")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = previewItem.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onSubscribeCatalog(previewItem) },
                        enabled = !alreadySubscribed,
                        colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("discover_preview_subscribe")
                    ) {
                        Icon(
                            if (alreadySubscribed) Icons.Filled.CheckCircle else Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (alreadySubscribed) "Subscribed" else "Subscribe to Show", color = Color.White)
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Recent Episodes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))

                    if (isPreviewLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = LoheElectricBlue)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(previewEpisodes, key = { it.id }) { ep ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ep.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${formatEpisodeDate(ep.publishedAt)} • ${formatDurationMmSs(ep.durationMs)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onPlayPreviewEpisode(ep) }) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = "Preview Play", tint = LoheElectricBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
