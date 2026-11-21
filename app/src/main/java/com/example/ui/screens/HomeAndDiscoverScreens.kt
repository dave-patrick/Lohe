package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.data.local.EpisodeSwipeAction
import com.example.data.local.HomeCardType
import com.example.data.local.LoheUserSettings
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.repository.DiscoverCatalogItem
import com.example.edition.Edition
import com.example.playback.PlaybackUiState
import com.example.ui.components.HomeBrandingHeader
import com.example.ui.components.HomeFlyingKaniOverlay
import com.example.ui.components.LibraryDetailsCard
import com.example.ui.components.ListeningActivity14DayCard
import com.example.ui.components.MostEpisodesCard
import com.example.ui.components.MostListenedPodcastsCard
import com.example.ui.components.PodcastArtworkThumbnail
import com.example.ui.components.PrimaryTabTopBar
import com.example.ui.components.QueueIndicatorBadge
import com.example.ui.components.SwipeableEpisodeRow
import com.example.ui.components.UpNextQueueActionButton
import com.example.ui.components.UpNextSpotlightCard
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
    playlists: List<PlaylistEntity> = emptyList(),
    inProgressEpisodes: List<EpisodeEntity>,
    activeEpisodes: List<EpisodeEntity>,
    allEpisodes: List<EpisodeEntity> = activeEpisodes,
    upNextEpisodes: List<EpisodeEntity>,
    playbackState: PlaybackUiState,
    hasPlayedHomeFlight: Boolean = false,
    onHomeFlightFinished: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToPodcasts: () -> Unit,
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToUpNext: () -> Unit = {},
    onSelectPodcast: (String) -> Unit = {},
    onCustomizeHomeCards: () -> Unit = onOpenSettings,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onToggleUpNext: (EpisodeEntity, Boolean) -> Unit,
    onAddToUpNext: (EpisodeEntity, Boolean) -> Unit = { ep, beg -> onToggleUpNext(ep, beg) },
    onSwipeEpisodeAction: (EpisodeEntity, EpisodeSwipeAction) -> Unit = { _, _ -> },
    onToggleStarred: (EpisodeEntity) -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    onImportOpmlOrJson: () -> Unit,
    onExportJsonBackup: () -> Unit
) {
    val statsEpisodes = if (allEpisodes.isNotEmpty()) allEpisodes else activeEpisodes
    val totalEpisodes = statsEpisodes.size
    val playedCount = statsEpisodes.count { it.isPlayed }
    val waitingCount = activeEpisodes.count { !it.isPlayed }
    val playedPercent = when {
        totalEpisodes <= 0 || playedCount <= 0 -> 0
        else -> ((playedCount * 100f) / totalEpisodes).toInt().coerceIn(1, 100)
    }
    val timePlayedMs = statsEpisodes.sumOf { ep ->
        if (ep.isPlayed) ep.durationMs.coerceAtLeast(ep.resumePositionMs) else ep.resumePositionMs.coerceAtLeast(0L)
    }
    val totalDurationMs = statsEpisodes.sumOf { it.durationMs.coerceAtLeast(it.resumePositionMs) }.coerceAtLeast(1L)
    val stillToHearMs = activeEpisodes.sumOf { ep ->
        if (ep.isPlayed) 0L else (ep.durationMs - ep.resumePositionMs).coerceAtLeast(0L)
    }

    val visibleCards = remember(settings.enabledHomeCards, settings.isProUnlocked) {
        HomeCardType.entries.filter { card ->
            card in settings.enabledHomeCards && (!card.isPro || (Edition.HAS_PRO && settings.isProUnlocked))
        }
    }

    val showHeaderKani = podcasts.isNotEmpty() || statsEpisodes.isNotEmpty()
    val listState = rememberLazyListState()
    val onInProgressAction: () -> Unit = {
        inProgressEpisodes.firstOrNull()?.let(onPlayEpisode) ?: onNavigateToPodcasts()
    }

    @Composable
    fun RenderHomeCard(card: HomeCardType) {
        when (card) {
            HomeCardType.YOUR_SIGNAL -> YourSignalCard(
                playedPercent = playedPercent,
                playedCount = playedCount,
                totalEpisodesCount = totalEpisodes,
                inProgressCount = inProgressEpisodes.size,
                timePlayedMs = timePlayedMs,
                totalDurationMs = totalDurationMs,
                timeSavedMs = settings.totalTimeSavedMs,
                stillToHearMs = stillToHearMs,
                waitingEpisodesCount = waitingCount,
                isPlaying = playbackState.isPlaying,
                reducedMotion = settings.reducedMotion,
                onInProgressClick = onInProgressAction
            )

            HomeCardType.LISTENING_ACTIVITY -> ListeningActivity14DayCard(
                dailyMinutes = settings.dailyListeningMinutes,
                episodes = statsEpisodes,
                totalTimeSavedMs = settings.totalTimeSavedMs,
                historyRange = settings.listeningHistoryRange
            )

            HomeCardType.LIBRARY_DETAILS -> LibraryDetailsCard(
                totalEpisodes = totalEpisodes,
                playedCount = playedCount,
                inProgressCount = inProgressEpisodes.size,
                waitingCount = waitingCount,
                subscriptionsCount = podcasts.size,
                playlistsCount = playlists.size,
                onNavigateToPodcasts = onNavigateToPodcasts,
                onNavigateToPlaylists = onNavigateToPlaylists,
                onInProgressClick = onInProgressAction
            )

            HomeCardType.MOST_EPISODES -> MostEpisodesCard(
                podcasts = podcasts,
                episodes = statsEpisodes,
                onSelectPodcast = onSelectPodcast
            )

            HomeCardType.MOST_LISTENED -> MostListenedPodcastsCard(
                podcasts = podcasts,
                episodes = statsEpisodes,
                onSelectPodcast = onSelectPodcast
            )

            HomeCardType.UP_NEXT_SPOTLIGHT -> UpNextSpotlightCard(
                upNextEpisodes = upNextEpisodes,
                playbackState = playbackState,
                onPlayEpisode = onPlayEpisode,
                onNavigateToUpNext = onNavigateToUpNext
            )

            HomeCardType.CONTINUE_LISTENING -> {
                if (inProgressEpisodes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(id = R.string.continue_listening_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        inProgressEpisodes.forEach { episode ->
                            SwipeableEpisodeRow(
                                episode = episode,
                                settings = settings,
                                onSwipeAction = onSwipeEpisodeAction,
                                cornerRadius = 16.dp
                            ) {
                                CompactEpisodeRowCard(
                                    episode = episode,
                                    settings = settings,
                                    isCurrentlyPlaying = playbackState.currentEpisode?.id == episode.id && playbackState.isPlaying,
                                    queuePosition = upNextEpisodes.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 }?.plus(1),
                                    onPlay = { onPlayEpisode(episode) },
                                    onToggleUpNext = { onToggleUpNext(episode, false) },
                                    onAddToUpNext = { beg -> onAddToUpNext(episode, beg) },
                                    onToggleStarred = { onToggleStarred(episode) },
                                    onOpenNotes = { onOpenNotes(episode) }
                                )
                            }
                        }
                    }
                }
            }

            HomeCardType.RECENT_EPISODES -> {
                if (activeEpisodes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Recent Dispatches",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        activeEpisodes.take(6).forEach { episode ->
                            SwipeableEpisodeRow(
                                episode = episode,
                                settings = settings,
                                onSwipeAction = onSwipeEpisodeAction,
                                cornerRadius = 16.dp
                            ) {
                                CompactEpisodeRowCard(
                                    episode = episode,
                                    settings = settings,
                                    isCurrentlyPlaying = playbackState.currentEpisode?.id == episode.id && playbackState.isPlaying,
                                    queuePosition = upNextEpisodes.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 }?.plus(1),
                                    onPlay = { onPlayEpisode(episode) },
                                    onToggleUpNext = { onToggleUpNext(episode, false) },
                                    onAddToUpNext = { beg -> onAddToUpNext(episode, beg) },
                                    onToggleStarred = { onToggleStarred(episode) },
                                    onOpenNotes = { onOpenNotes(episode) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val isTwoColumn = maxWidth >= 600.dp

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("home_tab_screen"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HomeBrandingHeader(
                    reducedMotion = settings.reducedMotion,
                    onOpenSettings = onOpenSettings,
                    onKaniClick = onInProgressAction,
                    showKani = showHeaderKani,
                    showStaticKani = true
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
            } else if (visibleCards.isEmpty()) {
                item {
                    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .testTag("home_no_cards_enabled_state")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DashboardCustomize,
                                contentDescription = null,
                                tint = LoheCyanAccent,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Home cards selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Choose which stats, rankings, and listening cards appear on your Home screen in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = onCustomizeHomeCards,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("home_customize_cards_button")
                            ) {
                                Text("Open Home Screen Settings")
                            }
                        }
                    }
                }
            } else if (isTwoColumn) {
                item {
                    val leftPreferred = setOf(
                        HomeCardType.YOUR_SIGNAL,
                        HomeCardType.LISTENING_ACTIVITY,
                        HomeCardType.LIBRARY_DETAILS
                    )
                    val initialLeft = visibleCards.filter { it in leftPreferred }
                    val initialRight = visibleCards.filter { it !in leftPreferred }
                    val (leftCards, rightCards) = when {
                        initialLeft.isNotEmpty() && initialRight.isNotEmpty() -> initialLeft to initialRight
                        else -> visibleCards.withIndex().partition { it.index % 2 == 0 }
                            .let { (l, r) -> l.map { it.value } to r.map { it.value } }
                    }

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
                            leftCards.forEach { card -> RenderHomeCard(card) }
                        }
                        if (rightCards.isNotEmpty()) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rightCards.forEach { card -> RenderHomeCard(card) }
                            }
                        }
                    }
                }
            } else {
                items(visibleCards, key = { "home_card_${it.name}" }) { card ->
                    RenderHomeCard(card)
                }
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
    onAddToUpNext: (Boolean) -> Unit = { onToggleUpNext() },
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
                cornerRadius = 12.dp,
                modifier = Modifier.size(68.dp)
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
                UpNextQueueActionButton(
                    inUpNext = episode.inUpNext,
                    onAddToBeginning = { onAddToUpNext(true) },
                    onAddToEnd = { onAddToUpNext(false) },
                    onRemoveFromUpNext = if (episode.inUpNext) onToggleUpNext else null,
                    buttonSize = 40.dp,
                    iconSize = 20.dp,
                    testTagPrefix = "home_up_next_${episode.id}"
                )
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

private data class DiscoverGenreStyle(
    val topic: String,
    val icon: ImageVector,
    val startColor: Color,
    val endColor: Color,
    val accentColor: Color
)

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
    onOpenSettings: () -> Unit,
    isProUnlocked: Boolean = false,
    onOpenProUpgradeModal: () -> Unit = {}
) {
    var searchText by remember(query) { mutableStateOf(query) }
    var showRssDialog by remember { mutableStateOf(false) }
    var rssInput by remember { mutableStateOf("") }
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardBg = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val cardBorder = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline

    val genreStyles = remember {
        listOf(
            DiscoverGenreStyle("Trending", Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF0062FF), Color(0xFF2B4C7E), Color(0xFFB8C4D4)),
            DiscoverGenreStyle("All", Icons.Filled.Apps, Color(0xFF1F2D4A), Color(0xFF2E436E), Color(0xFF8CB4FF)),
            DiscoverGenreStyle("Technology", Icons.Filled.Memory, Color(0xFF103783), Color(0xFF2E6BF0), Color(0xFFB4C2D6)),
            DiscoverGenreStyle("Science", Icons.Filled.Science, Color(0xFF0B525B), Color(0xFF14919B), Color(0xFF56F0D8)),
            DiscoverGenreStyle("Design", Icons.Filled.Palette, Color(0xFF6D28D9), Color(0xFFEC4899), Color(0xFFF9A8D4)),
            DiscoverGenreStyle("History", Icons.Filled.AccountBalance, Color(0xFF7C2D12), Color(0xFFD97706), Color(0xFFFDE68A)),
            DiscoverGenreStyle("Philosophy", Icons.Filled.Psychology, Color(0xFF312E81), Color(0xFF6366F1), Color(0xFFA5B4FC)),
            DiscoverGenreStyle("Music", Icons.Filled.MusicNote, Color(0xFF065F46), Color(0xFF10B981), Color(0xFF6EE7B7))
        )
    }

    val heroItem = catalog.firstOrNull()
    val gridItems = if (catalog.size > 1) catalog.drop(1) else emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("discover_tab_screen")
    ) {
        val rssLocked = Edition.HAS_PRO && !isProUnlocked
        PrimaryTabTopBar(
            title = stringResource(id = R.string.tab_discover),
            subtitle = "Curated shows, visual genres & live podcast lookup",
            trailingActions = {
                Box {
                    IconButton(
                        onClick = {
                            if (rssLocked) onOpenProUpgradeModal() else showRssDialog = true
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("discover_rss_button")
                    ) {
                        Icon(
                            Icons.Filled.RssFeed,
                            contentDescription = if (rssLocked) "Add RSS URL (Pro)" else "Add RSS URL",
                            tint = if (rssLocked) MaterialTheme.colorScheme.onSurfaceVariant else LoheCyanAccent
                        )
                    }
                    if (rssLocked) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(LoheElectricBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Pro feature",
                                tint = Color.White,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }
            },
            onOpenSettings = onOpenSettings
        )

        // Sleek integrated Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search shows, hosts, or topics...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = LoheCyanAccent) },
                trailingIcon = {
                    if (searchText.isNotBlank()) {
                        IconButton(onClick = {
                            searchText = ""
                            onBrowseCategory("All")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
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
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("discover_search_button")
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Go", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 24.dp, top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Graphical Genre Carousel (full-span)
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXPLORE GENRES",
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
                            fontWeight = FontWeight.Bold,
                            color = LoheBrightBlueText
                        )
                        Text(
                            text = "Swipe →",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(genreStyles, key = { it.topic }) { genre ->
                            val selected = (searchText.isBlank() && genre.topic == "All") ||
                                searchText.equals(genre.topic, ignoreCase = true)
                            val chipTag = if (genre.topic == "Trending") {
                                "discover_trending_chip"
                            } else {
                                "discover_category_${genre.topic.lowercase()}"
                            }
                            Box(
                                modifier = Modifier
                                    .width(122.dp)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(genre.startColor, genre.endColor)
                                        )
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color.White else genre.accentColor.copy(alpha = 0.32f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        searchText = if (genre.topic == "All") "" else genre.topic
                                        onBrowseCategory(genre.topic)
                                    }
                                    .testTag(chipTag)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = genre.accentColor.copy(alpha = 0.22f),
                                        radius = size.height * 0.62f,
                                        center = Offset(size.width * 0.92f, size.height * 0.12f)
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.08f),
                                        radius = size.height * 0.42f,
                                        center = Offset(size.width * 0.12f, size.height * 0.95f)
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = genre.icon,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (selected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                        }
                                    }
                                    Text(
                                        text = genre.topic,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isSearching) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LoheElectricBlue)
                    }
                }
            }

            // Editorial Featured Spotlight Hero Card
            if (heroItem != null && !isSearching) {
                item(span = { GridItemSpan(2) }) {
                    val isHeroSubscribed = subscribedPodcasts.any {
                        it.feedUrl == heroItem.feedUrl || it.title.equals(heroItem.title, ignoreCase = true)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = if (isDark) {
                                        listOf(Color(0xFF0E1D3A), Color(0xFF142D5C), Color(0xFF0B1528))
                                    } else {
                                        listOf(Color(0xFFEAF3FF), Color(0xFFDCEBFF), Color(0xFFF3F8FF))
                                    }
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        LoheCyanAccent.copy(alpha = 0.55f),
                                        LoheElectricBlue.copy(alpha = 0.35f),
                                        cardBorder
                                    )
                                ),
                                shape = RoundedCornerShape(22.dp)
                            )
                            .clickable { onOpenPreview(heroItem) }
                            .testTag("discover_card_${heroItem.title}")
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        LoheElectricBlue.copy(alpha = if (isDark) 0.24f else 0.14f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.88f, size.height * 0.15f),
                                    radius = size.width * 0.55f
                                ),
                                radius = size.width * 0.55f,
                                center = Offset(size.width * 0.88f, size.height * 0.15f)
                            )
                        }
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = LoheCyanAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (searchText.isBlank()) "FEATURED SPOTLIGHT" else "TOP MATCH",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp,
                                    color = if (isDark) LoheCyanAccent else LoheElectricBlue
                                )
                                Spacer(Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(LoheElectricBlue.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = heroItem.category,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) LoheCyanAccent else LoheElectricBlue
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PodcastArtworkThumbnail(
                                    artworkUrl = heroItem.artworkUrl,
                                    title = heroItem.title,
                                    cornerRadius = 16.dp,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = heroItem.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = heroItem.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LoheBrightBlueText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = heroItem.description.ifBlank { "Tap to preview episodes or subscribe instantly." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isHeroSubscribed) onOpenPreview(heroItem) else onSubscribeCatalog(heroItem)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isHeroSubscribed) MaterialTheme.colorScheme.surfaceVariant else LoheElectricBlue
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isHeroSubscribed) Icons.Filled.CheckCircle else Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = if (isHeroSubscribed) LoheCyanAccent else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isHeroSubscribed) "Subscribed" else "Follow Show",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isHeroSubscribed) MaterialTheme.colorScheme.onSurface else Color.White
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onOpenPreview(heroItem) },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Preview", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            if (gridItems.isNotEmpty() && !isSearching) {
                item(span = { GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchText.isBlank()) "CURATED SHOWS" else "MORE RESULTS",
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
                            fontWeight = FontWeight.Bold,
                            color = LoheBrightBlueText
                        )
                        Text(
                            text = "${catalog.size} shows",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Uniform 2-Column Graphical Show Cards
            itemsIndexed(
                items = gridItems,
                key = { idx, item -> "${item.feedUrl.trim().lowercase()}|${item.title.trim().lowercase()}|$idx" }
            ) { _, item ->
                val isSubscribed = subscribedPodcasts.any {
                    it.feedUrl == item.feedUrl || it.title.equals(item.title, ignoreCase = true)
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                        .clickable { onOpenPreview(item) }
                        .testTag("discover_card_${item.title}")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            PodcastArtworkThumbnail(
                                artworkUrl = item.artworkUrl,
                                title = item.title,
                                cornerRadius = 14.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xD90A101D))
                                    .border(0.8.dp, LoheCyanAccent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.category,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LoheCyanAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.author.ifBlank { "Podcast" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else LoheElectricBlue
                                )
                                .clickable {
                                    if (isSubscribed) onOpenPreview(item) else onSubscribeCatalog(item)
                                }
                                .padding(vertical = 7.dp)
                        ) {
                            Text(
                                text = if (isSubscribed) "Open Show" else "+ Follow",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSubscribed) MaterialTheme.colorScheme.onSurface else Color.White,
                                maxLines = 1
                            )
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
                            itemsIndexed(
                                items = previewEpisodes,
                                key = { idx, ep -> "${ep.id}|$idx" }
                            ) { _, ep ->
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
