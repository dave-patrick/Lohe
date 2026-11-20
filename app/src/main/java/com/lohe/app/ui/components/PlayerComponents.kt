package com.lohe.app.ui.components

import android.content.Intent
import android.graphics.BlurMaskFilter
import android.net.Uri
import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lohe.app.data.local.EpisodeBookmarkEntity
import com.lohe.app.data.local.EpisodeEntity
import com.lohe.app.data.local.LoheUserSettings
import com.lohe.app.data.network.EpisodeChapter
import com.lohe.app.data.network.PodcastingChaptersAndTranscripts
import com.lohe.app.data.network.TranscriptCue
import com.lohe.app.playback.PlaybackUiState
import com.lohe.app.ui.PrimaryTab
import com.lohe.app.ui.theme.LoheBackgroundDark
import com.lohe.app.ui.theme.LoheBorderDark
import com.lohe.app.ui.theme.LoheBrightBlueText
import com.lohe.app.ui.theme.LoheCyanAccent
import com.lohe.app.ui.theme.LoheElectricBlue
import com.lohe.app.ui.theme.LoheGlassNavBorderDark
import com.lohe.app.ui.theme.LoheGlassNavDark
import com.lohe.app.ui.theme.LoheMiniPlayerBorderDark
import com.lohe.app.ui.theme.LoheMiniPlayerEndDark
import com.lohe.app.ui.theme.LoheMiniPlayerStartDark
import com.lohe.app.ui.theme.LoheSurfaceDark
import com.lohe.app.ui.theme.LoheSurfaceElevatedDark
import com.lohe.app.ui.theme.LoheTextMutedDark
import com.lohe.app.ui.theme.LoheTextPrimaryDark
import kotlin.math.roundToInt

fun formatDurationMmSs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

/**
 * Floating Pill Bottom Navigation Bar matching compact-nav81-ready_5458b234.png & indicator-light68_5c756cbf.png:
 * - Rounded floating pill with horizontal margin & navigationBarsPadding()
 * - Exact tab order: Home, Podcasts, Discover, Playlists, Up Next
 * - Active tab in Electric Blue with tiny glowing blue dot below the active label
 */
@Composable
fun LoheFloatingBottomNavBar(
    selectedTab: PrimaryTab,
    isSettingsOpen: Boolean,
    upNextCount: Int,
    onSelectTab: (PrimaryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val containerColor = if (isDark) LoheGlassNavDark.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f)
    val borderColor = if (isDark) LoheGlassNavBorderDark else Color(0xFFD8DEE8)
    val mutedColor = if (isDark) LoheTextMutedDark else Color(0xFF6B7280)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .navigationBarsPadding()
            .padding(bottom = 6.dp)
            .border(1.dp, borderColor, RoundedCornerShape(28.dp))
            .testTag("bottom_navigation_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryTab.entries.forEach { tab ->
                val isSelected = !isSettingsOpen && selectedTab == tab
                val tintColor = if (isSelected) LoheElectricBlue else mutedColor
                val iconVec = when (tab) {
                    PrimaryTab.HOME -> if (isSelected) Icons.Filled.Home else Icons.Outlined.Home
                    PrimaryTab.PODCASTS -> if (isSelected) Icons.Filled.Podcasts else Icons.Outlined.Podcasts
                    PrimaryTab.DISCOVER -> if (isSelected) Icons.Filled.Explore else Icons.Outlined.Explore
                    PrimaryTab.PLAYLISTS -> if (isSelected) Icons.AutoMirrored.Filled.PlaylistPlay else Icons.AutoMirrored.Outlined.PlaylistPlay
                    PrimaryTab.UP_NEXT -> if (isSelected) Icons.AutoMirrored.Filled.QueueMusic else Icons.AutoMirrored.Outlined.QueueMusic
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 4.dp)
                        .testTag(tab.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = iconVec,
                            contentDescription = tab.label,
                            tint = tintColor,
                            modifier = Modifier.size(22.dp)
                        )
                        if (tab == PrimaryTab.UP_NEXT && upNextCount > 0) {
                            Box(
                                modifier = Modifier
                                    .offset(x = 6.dp, y = (-2).dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(LoheCyanAccent)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) LoheElectricBlue else Color.Transparent)
                    )
                }
            }
        }
    }
}

/**
 * Adaptive NavigationRail for Medium/Expanded screens (tablets & unfolded foldables >= 600dp).
 */
@Composable
fun LoheNavigationRail(
    selectedTab: PrimaryTab,
    isSettingsOpen: Boolean,
    upNextCount: Int,
    onSelectTab: (PrimaryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val containerColor = if (isDark) LoheGlassNavDark else Color.White
    val borderColor = if (isDark) LoheGlassNavBorderDark else Color(0xFFD8DEE8)
    val mutedColor = if (isDark) LoheTextMutedDark else Color(0xFF6B7280)

    Row(
        modifier = modifier
            .fillMaxHeight()
            .testTag("tablet_navigation_rail")
    ) {
        NavigationRail(
            containerColor = containerColor,
            modifier = Modifier
                .fillMaxHeight()
                .width(84.dp)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryTab.entries.forEach { tab ->
                val isSelected = !isSettingsOpen && selectedTab == tab
                val iconVec = when (tab) {
                    PrimaryTab.HOME -> if (isSelected) Icons.Filled.Home else Icons.Outlined.Home
                    PrimaryTab.PODCASTS -> if (isSelected) Icons.Filled.Podcasts else Icons.Outlined.Podcasts
                    PrimaryTab.DISCOVER -> if (isSelected) Icons.Filled.Explore else Icons.Outlined.Explore
                    PrimaryTab.PLAYLISTS -> if (isSelected) Icons.AutoMirrored.Filled.PlaylistPlay else Icons.AutoMirrored.Outlined.PlaylistPlay
                    PrimaryTab.UP_NEXT -> if (isSelected) Icons.AutoMirrored.Filled.QueueMusic else Icons.AutoMirrored.Outlined.QueueMusic
                }

                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = iconVec,
                                contentDescription = tab.label,
                                modifier = Modifier.size(22.dp)
                            )
                            if (tab == PrimaryTab.UP_NEXT && upNextCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 5.dp, y = (-2).dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(LoheCyanAccent)
                                )
                            }
                        }
                    },
                    label = {
                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = LoheElectricBlue,
                        selectedTextColor = LoheElectricBlue,
                        indicatorColor = LoheElectricBlue.copy(alpha = 0.14f),
                        unselectedIconColor = mutedColor,
                        unselectedTextColor = mutedColor
                    ),
                    modifier = Modifier.testTag(tab.testTag)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        VerticalDivider(color = borderColor)
    }
}

/**
 * Floating Glass Pill Mini-Player directly above the floating bottom nav pill
 * matching compact-nav81-ready_5458b234.png & indicator-light68_5c756cbf.png.
 */
@Composable
fun MiniPlayerBar(
    playbackState: PlaybackUiState,
    nextUpEpisode: EpisodeEntity?,
    settings: LoheUserSettings,
    onTogglePlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val episode = playbackState.currentEpisode ?: return
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark

    val containerBrush = if (isDark) {
        Brush.horizontalGradient(listOf(LoheMiniPlayerStartDark, LoheMiniPlayerEndDark))
    } else {
        Brush.horizontalGradient(listOf(Color.White, Color(0xFFF3F7FC)))
    }
    val borderBrush = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                LoheElectricBlue.copy(alpha = 0.72f),
                LoheCyanAccent.copy(alpha = 0.82f),
                LoheElectricBlue.copy(alpha = 0.72f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                LoheElectricBlue.copy(alpha = 0.55f),
                LoheCyanAccent.copy(alpha = 0.65f),
                LoheElectricBlue.copy(alpha = 0.55f)
            )
        )
    }
    val trackColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .drawBehind {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        asFrameworkPaint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(
                                if (isDark) 135 else 85,
                                0, 142, 255
                            )
                            maskFilter = BlurMaskFilter(18.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                        }
                    }
                    val radius = 28.dp.toPx()
                    canvas.drawRoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        radiusX = radius,
                        radiusY = radius,
                        paint = paint
                    )
                }
            }
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = LoheElectricBlue,
                spotColor = LoheCyanAccent
            )
            .clip(RoundedCornerShape(28.dp))
            .background(containerBrush)
            .border(1.25.dp, borderBrush, RoundedCornerShape(28.dp))
            .clickable(onClick = onOpenFullPlayer)
            .testTag("mini_player_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PodcastArtworkThumbnail(
                    artworkUrl = episode.artworkUrl,
                    title = episode.podcastTitle,
                    cornerRadius = 12.dp,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = episode.podcastTitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (nextUpEpisode != null) {
                        Text(
                            text = "Next up • ${nextUpEpisode.title}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = LoheBrightBlueText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "${formatDurationMmSs(playbackState.positionMs)} / ${formatDurationMmSs(playbackState.durationMs)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = LoheBrightBlueText,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Circular glowing blue Play/Pause button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(LoheElectricBlue.copy(alpha = 0.22f))
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(LoheElectricBlue)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("mini_player_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onOpenFullPlayer,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mini_player_expand_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Open Full Player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { playbackState.progressFraction.coerceIn(0.02f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = LoheElectricBlue,
                trackColor = trackColor
            )
        }
    }
}

/**
 * Draggable Floating Mini-Player Pill (Pro Bundle feature)
 */
@Composable
fun FloatingMiniPlayerPill(
    playbackState: PlaybackUiState,
    sizeDp: Int,
    transparencyPct: Int,
    onTogglePlayPause: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val episode = playbackState.currentEpisode ?: return
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val alpha = (1f - transparencyPct / 100f).coerceIn(0.25f, 1f)

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .drawBehind {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        asFrameworkPaint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(130, 0, 142, 255)
                            maskFilter = BlurMaskFilter(14.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                        }
                    }
                    val radius = size.height / 2f
                    canvas.drawRoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        radiusX = radius,
                        radiusY = radius,
                        paint = paint
                    )
                }
            }
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(50),
                ambientColor = LoheElectricBlue,
                spotColor = LoheCyanAccent
            )
            .clip(RoundedCornerShape(50))
            .border(1.5.dp, LoheCyanAccent, RoundedCornerShape(50))
            .clickable(onClick = onOpenFullPlayer)
            .testTag("floating_mini_player_pill"),
        color = LoheSurfaceDark.copy(alpha = alpha),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = "Floating Signal Player",
                tint = LoheCyanAccent,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.width(140.dp)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = LoheTextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatDurationMmSs(playbackState.positionMs)} • PRO FLOAT",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoheCyanAccent
                )
            }
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size((sizeDp * 0.65f).coerceIn(34f, 52f).dp)
                    .clip(CircleShape)
                    .background(LoheElectricBlue)
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullPlayerModalDialog(
    playbackState: PlaybackUiState,
    settings: LoheUserSettings,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVolumeBoostChange: (Int) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onSleepTimerSelect: (Int) -> Unit,
    onToggleStarred: (EpisodeEntity) -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit,
    activeChapters: List<EpisodeChapter> = emptyList(),
    activeTranscriptCues: List<TranscriptCue> = emptyList(),
    activeBookmarks: List<EpisodeBookmarkEntity> = emptyList(),
    sleepShakeToExtend: Boolean = settings.sleepShakeToExtendEnabled,
    onSetSleepEndOfEpisode: (Boolean) -> Unit = {},
    onToggleSleepShakeExtend: (Boolean) -> Unit = {},
    onSaveBookmark: (String) -> Unit = {},
    onDeleteBookmark: (String) -> Unit = {}
) {
    val episode = playbackState.currentEpisode ?: return
    val context = LocalContext.current
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNoteInput by remember { mutableStateOf("") }

    var showSpeedPicker by remember { mutableStateOf(false) }
    var showVolumeBoostPicker by remember { mutableStateOf(false) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    var showChapterPicker by remember(episode.id) { mutableStateOf(false) }
    var inlineContentTab by remember(episode.id) { mutableIntStateOf(0) } // 0 = Notes, 1 = Transcript
    var inlineTranscriptQuery by remember(episode.id) { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val plainRichNotes = remember(episode.descriptionHtml) {
        Html.fromHtml(episode.descriptionHtml, Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    val activeChapterIdx = remember(activeChapters, playbackState.positionMs) {
        if (activeChapters.isEmpty()) -1
        else activeChapters.indexOfLast { it.startTimeMs <= playbackState.positionMs }.coerceAtLeast(0)
    }
    val currentChapter = activeChapters.getOrNull(activeChapterIdx)
    val effectiveArtworkUrl = currentChapter?.imageUrl?.takeIf { it.isNotBlank() } ?: episode.artworkUrl

    BackHandler(onBack = onDismiss)

    Surface(
        color = LoheBackgroundDark,
        modifier = Modifier
            .fillMaxSize()
            .background(LoheBackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoheBackgroundDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("full_player_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = LoheTextPrimaryDark
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoheCyanAccent
                    )
                    Text(
                        text = episode.podcastTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = LoheTextMutedDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val stamp = PodcastingChaptersAndTranscripts.formatTimestamp(playbackState.positionMs)
                            val shareText = "Listening to \"${episode.title}\" (${episode.podcastTitle}) at $stamp on Lohe\n${episode.audioUrl}"
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "${episode.podcastTitle} — ${episode.title}")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(sendIntent, "Share Episode Moment").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("full_player_share_snippet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share Episode Moment",
                            tint = LoheTextPrimaryDark
                        )
                    }

                    val isBookmarked = episode.isStarred || activeBookmarks.isNotEmpty()
                    IconButton(
                        onClick = {
                            bookmarkNoteInput = currentChapter?.title ?: ""
                            showBookmarkDialog = !showBookmarkDialog
                            if (showBookmarkDialog) {
                                showSpeedPicker = false
                                showVolumeBoostPicker = false
                                showSleepTimerPicker = false
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("full_player_save_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isBookmarked || showBookmarkDialog) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (activeBookmarks.isNotEmpty()) "Bookmark (${activeBookmarks.size})" else "Bookmark Episode",
                            tint = if (isBookmarked || showBookmarkDialog) LoheCyanAccent else LoheTextPrimaryDark
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val maxArtworkSide = minOf(maxWidth, (maxHeight * 0.72f).coerceAtLeast(320.dp))
                val isFullBleed = maxArtworkSide >= maxWidth - 2.dp
                val artCornerRadius = if (isFullBleed) 0.dp else 20.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .testTag("full_player_modal")
                ) {
                    Box(
                        modifier = if (isFullBleed) {
                            Modifier
                                .fillMaxWidth()
                                .height(maxArtworkSide)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            LoheElectricBlue.copy(alpha = 0.35f),
                                            LoheSurfaceDark,
                                            LoheBackgroundDark
                                        )
                                    )
                                )
                                .testTag("full_player_artwork")
                        } else {
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(maxArtworkSide)
                                .clip(RoundedCornerShape(artCornerRadius))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            LoheElectricBlue.copy(alpha = 0.35f),
                                            LoheSurfaceDark,
                                            LoheBackgroundDark
                                        )
                                    )
                                )
                                .border(1.dp, LoheBorderDark, RoundedCornerShape(artCornerRadius))
                                .testTag("full_player_artwork")
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        if (effectiveArtworkUrl.isNotBlank()) {
                            PodcastArtworkThumbnail(
                                artworkUrl = effectiveArtworkUrl,
                                title = currentChapter?.title ?: episode.podcastTitle,
                                cornerRadius = artCornerRadius,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = LoheCyanAccent,
                                    modifier = Modifier.size(84.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (playbackState.isPlaying) "MEDIA3 EXOPLAYER ACTIVE" else "PAUSED • READY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LoheCyanAccent
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = episode.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = LoheTextPrimaryDark,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = episode.podcastTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LoheElectricBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

            Slider(
                value = playbackState.positionMs.toFloat(),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..playbackState.durationMs.coerceAtLeast(1000L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = LoheCyanAccent,
                    activeTrackColor = LoheElectricBlue,
                    inactiveTrackColor = LoheBorderDark
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("full_player_scrubber")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-6).dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationMmSs(playbackState.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = LoheTextMutedDark
                )
                val remaining = (playbackState.durationMs - playbackState.positionMs).coerceAtLeast(0L)
                Text(
                    text = "-${formatDurationMmSs(remaining)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = LoheTextMutedDark
                )
            }

            if (currentChapter != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LoheSurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (showChapterPicker) LoheElectricBlue.copy(alpha = 0.6f) else LoheBorderDark,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .testTag("full_player_active_chapter")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChapterPicker = !showChapterPicker }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    if (activeChapterIdx > 0) {
                                        onSeekTo(activeChapters[activeChapterIdx - 1].startTimeMs)
                                    } else {
                                        onSeekTo(0L)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous Chapter", tint = LoheCyanAccent)
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "CHAPTER ${activeChapterIdx + 1} OF ${activeChapters.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LoheCyanAccent
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        imageVector = if (showChapterPicker) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = if (showChapterPicker) "Hide chapters" else "Select chapter",
                                        tint = LoheCyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = currentChapter.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LoheTextPrimaryDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (currentChapter.url.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(currentChapter.url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("full_player_chapter_link")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open Chapter Link",
                                        tint = LoheElectricBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (activeChapterIdx + 1 < activeChapters.size) {
                                        onSeekTo(activeChapters[activeChapterIdx + 1].startTimeMs)
                                    }
                                },
                                enabled = activeChapterIdx + 1 < activeChapters.size,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    contentDescription = "Next Chapter",
                                    tint = if (activeChapterIdx + 1 < activeChapters.size) LoheCyanAccent else LoheTextMutedDark
                                )
                            }
                        }

                        AnimatedVisibility(visible = showChapterPicker) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("full_player_chapter_picker_list")
                            ) {
                                HorizontalDivider(color = LoheBorderDark.copy(alpha = 0.7f))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    activeChapters.forEachIndexed { idx, ch ->
                                        val isSelected = idx == activeChapterIdx
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) LoheElectricBlue.copy(alpha = 0.18f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    onSeekTo(ch.startTimeMs)
                                                    showChapterPicker = false
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                                .testTag("full_player_chapter_option_$idx"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (ch.imageUrl.isNotBlank()) {
                                                PodcastArtworkThumbnail(
                                                    artworkUrl = ch.imageUrl,
                                                    title = ch.title,
                                                    cornerRadius = 6.dp,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) LoheElectricBlue else LoheBackgroundDark
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Filled.GraphicEq,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "${idx + 1}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = LoheTextMutedDark
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.width(10.dp))
                                            }
                                            Text(
                                                text = ch.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) LoheTextPrimaryDark else LoheTextPrimaryDark.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = PodcastingChaptersAndTranscripts.formatTimestamp(ch.startTimeMs),
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                color = if (isSelected) LoheCyanAccent else LoheTextMutedDark
                                            )
                                            if (ch.url.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        runCatching {
                                                            context.startActivity(
                                                                Intent(Intent.ACTION_VIEW, Uri.parse(ch.url)).apply {
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.OpenInNew,
                                                        contentDescription = "Open Chapter Link",
                                                        tint = LoheElectricBlue,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val speedActive = kotlin.math.abs(playbackState.playbackSpeed - 1.0f) >= 0.03f
                IconButton(
                    onClick = {
                        showSpeedPicker = !showSpeedPicker
                        if (showSpeedPicker) {
                            showVolumeBoostPicker = false
                            showSleepTimerPicker = false
                            showBookmarkDialog = false
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (speedActive || showSpeedPicker) LoheElectricBlue else LoheSurfaceDark)
                        .border(
                            1.dp,
                            if (speedActive || showSpeedPicker) LoheCyanAccent else LoheBorderDark,
                            CircleShape
                        )
                        .testTag("full_player_speed_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = "Playback Speed",
                        tint = if (speedActive || showSpeedPicker) Color.White else LoheElectricBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                OutlinedButton(
                    onClick = onSkipBack,
                    shape = RoundedCornerShape(50),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("full_player_skip_back")
                ) {
                    Icon(
                        Icons.Filled.FastRewind,
                        contentDescription = null,
                        tint = LoheTextPrimaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "-${settings.skipBackSeconds}s",
                        color = LoheTextPrimaryDark,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(LoheElectricBlue)
                        .testTag("full_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                OutlinedButton(
                    onClick = onSkipForward,
                    shape = RoundedCornerShape(50),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("full_player_skip_forward")
                ) {
                    Text(
                        text = "+${settings.skipForwardSeconds}s",
                        color = LoheTextPrimaryDark,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Filled.FastForward,
                        contentDescription = null,
                        tint = LoheTextPrimaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }

                val sleepTimerActive = playbackState.sleepTimerRemainingSeconds > 0
                IconButton(
                    onClick = {
                        showSleepTimerPicker = !showSleepTimerPicker
                        if (showSleepTimerPicker) {
                            showSpeedPicker = false
                            showVolumeBoostPicker = false
                            showBookmarkDialog = false
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (sleepTimerActive || showSleepTimerPicker) LoheElectricBlue else LoheSurfaceDark)
                        .border(
                            1.dp,
                            if (sleepTimerActive || showSleepTimerPicker) LoheCyanAccent else LoheBorderDark,
                            CircleShape
                        )
                        .testTag("full_player_sleep_timer_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimerActive || showSleepTimerPicker) Color.White else LoheElectricBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Slim 4-icon action bar (Volume Boost, Skip Silence, Sleep End of Ep, Chapters & Notes)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = LoheSurfaceDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .border(1.dp, LoheBorderDark, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val boostActive = playbackState.volumeBoostDb > 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                showVolumeBoostPicker = !showVolumeBoostPicker
                                if (showVolumeBoostPicker) {
                                    showSpeedPicker = false
                                    showSleepTimerPicker = false
                                    showBookmarkDialog = false
                                }
                            }
                            .testTag("full_player_volume_boost_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (boostActive || showVolumeBoostPicker) LoheElectricBlue else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume Boost",
                                tint = if (boostActive || showVolumeBoostPicker) Color.White else LoheCyanAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSkipSilenceChange(!playbackState.skipSilenceEnabled) }
                            .testTag("full_player_skip_silence_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (playbackState.skipSilenceEnabled) LoheElectricBlue else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = if (playbackState.skipSilenceEnabled) "Skip Silence On" else "Skip Silence Off",
                                tint = if (playbackState.skipSilenceEnabled) Color.White else LoheCyanAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSetSleepEndOfEpisode(!playbackState.sleepAtEndOfEpisode) }
                            .testTag("sleep_timer_end_of_ep"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (playbackState.sleepAtEndOfEpisode) LoheElectricBlue else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bedtime,
                                contentDescription = if (playbackState.sleepAtEndOfEpisode) "Sleep at End of Episode On" else "Sleep at End of Episode",
                                tint = if (playbackState.sleepAtEndOfEpisode) Color.White else LoheElectricBlue,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onOpenNotes(episode) }
                            .testTag("full_player_open_notes_sheet"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = "Episode Notes, Chapters & Transcript",
                                tint = LoheCyanAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showSpeedPicker) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LoheSurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, LoheBorderDark, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Variable Speed (${String.format("%.2fx", playbackState.playbackSpeed)})",
                            style = MaterialTheme.typography.titleSmall,
                            color = LoheTextPrimaryDark
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f).forEach { speed ->
                                val selected = kotlin.math.abs(playbackState.playbackSpeed - speed) < 0.03f
                                Button(
                                    onClick = {
                                        onSpeedChange(speed)
                                        showSpeedPicker = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) LoheElectricBlue else LoheSurfaceElevatedDark
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("${speed}x", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showVolumeBoostPicker) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LoheSurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, LoheBorderDark, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Volume Boost & Skip Silence (Free)",
                            style = MaterialTheme.typography.titleSmall,
                            color = LoheTextPrimaryDark
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0 to "Off (0dB)", 3 to "Mild (+3dB)", 6 to "Voice (+6dB)", 10 to "Max (+10dB)").forEach { (db, label) ->
                                val selected = playbackState.volumeBoostDb == db
                                Button(
                                    onClick = {
                                        onVolumeBoostChange(db)
                                        showVolumeBoostPicker = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) LoheElectricBlue else LoheSurfaceElevatedDark
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(label, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showSleepTimerPicker) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LoheSurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, LoheBorderDark, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = when {
                                playbackState.sleepAtEndOfEpisode -> "Sleep Timer: End of Episode"
                                playbackState.sleepTimerRemainingSeconds > 0 ->
                                    "Sleep Timer Active (${playbackState.sleepTimerRemainingSeconds / 60}m ${playbackState.sleepTimerRemainingSeconds % 60}s)"
                                else -> "Sleep Timer"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = LoheTextPrimaryDark
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0 to "Off", 5 to "5m", 15 to "15m", 30 to "30m", 60 to "60m").forEach { (mins, label) ->
                                val selected = !playbackState.sleepAtEndOfEpisode && (
                                    (mins == 0 && playbackState.sleepTimerRemainingSeconds == 0) ||
                                        (mins > 0 && playbackState.sleepTimerRemainingSeconds in ((mins - 1) * 60 + 1)..(mins * 60))
                                    )
                                Button(
                                    onClick = {
                                        onSleepTimerSelect(mins)
                                        showSleepTimerPicker = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) LoheElectricBlue else LoheSurfaceElevatedDark
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(label, color = Color.White)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Shake to extend (+5m)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LoheTextPrimaryDark
                                )
                                Text(
                                    text = "Shake phone when sleep timer is active to add 5 minutes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LoheTextMutedDark
                                )
                            }
                            Switch(
                                checked = sleepShakeToExtend,
                                onCheckedChange = onToggleSleepShakeExtend,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LoheElectricBlue),
                                modifier = Modifier.testTag("sleep_shake_extend_switch")
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showBookmarkDialog) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LoheSurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, LoheCyanAccent, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Save Timestamp Bookmark (${PodcastingChaptersAndTranscripts.formatTimestamp(playbackState.positionMs)})",
                            style = MaterialTheme.typography.labelLarge,
                            color = LoheCyanAccent
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = bookmarkNoteInput,
                            onValueChange = { bookmarkNoteInput = it },
                            label = { Text("Note or quote label") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bookmark_note_input")
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showBookmarkDialog = false }) {
                                Text("Cancel", color = LoheTextMutedDark)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onSaveBookmark(bookmarkNoteInput)
                                    if (!episode.isStarred) onToggleStarred(episode)
                                    showBookmarkDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                                modifier = Modifier.testTag("save_bookmark_confirm_button")
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Redesigned Inline Episode Notes & optional Transcript section (stays seamlessly in-place)
            val hasTranscript = activeTranscriptCues.isNotEmpty()
            val effectiveInlineTab = if (hasTranscript) inlineContentTab else 0

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("full_player_inline_rich_notes")
            ) {
                if (hasTranscript) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LoheSurfaceDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("Episode Notes", "Transcript").forEachIndexed { idx, label ->
                                val selected = effectiveInlineTab == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            if (selected) LoheElectricBlue.copy(alpha = 0.24f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (selected) 1.dp else 0.dp,
                                            color = if (selected) LoheElectricBlue.copy(alpha = 0.55f) else Color.Transparent,
                                            shape = RoundedCornerShape(9.dp)
                                        )
                                        .clickable { inlineContentTab = idx }
                                        .testTag(if (idx == 0) "full_player_inline_tab_notes" else "full_player_inline_tab_transcript"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) LoheTextPrimaryDark else LoheTextMutedDark
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Episode Notes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LoheTextPrimaryDark,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LoheSurfaceDark.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = effectiveInlineTab,
                        label = "inline_notes_transcript_transition"
                    ) { tab ->
                        if (tab == 0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                if (episode.durationMs > 0L || episode.publishedAt > 0L) {
                                    val formattedDate = remember(episode.publishedAt) {
                                        if (episode.publishedAt > 0L) {
                                            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                                .format(java.util.Date(episode.publishedAt))
                                        } else ""
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (formattedDate.isNotBlank()) {
                                            Text(
                                                text = formattedDate,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LoheTextMutedDark
                                            )
                                        }
                                        if (episode.durationMs > 0L) {
                                            val mins = (episode.durationMs / 60_000L).coerceAtLeast(1L)
                                            Text(
                                                text = "$mins min",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LoheCyanAccent
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = LoheBorderDark.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        android.widget.TextView(ctx).apply {
                                            setTextColor(android.graphics.Color.parseColor("#E6EAF2"))
                                            setLinkTextColor(android.graphics.Color.parseColor("#38BDF8"))
                                            textSize = 15f
                                            setLineSpacing(8f, 1.3f)
                                            movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                        }
                                    },
                                    update = { tv ->
                                        val htmlSource = episode.descriptionHtml.ifBlank {
                                            "<p>No show notes provided for this episode.</p>"
                                        }
                                        tv.text = Html.fromHtml(htmlSource, Html.FROM_HTML_MODE_COMPACT)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            val filteredCues = remember(activeTranscriptCues, inlineTranscriptQuery) {
                                val q = inlineTranscriptQuery.trim()
                                if (q.isBlank()) activeTranscriptCues
                                else activeTranscriptCues.filter {
                                    it.text.contains(q, ignoreCase = true) ||
                                        it.speaker.contains(q, ignoreCase = true)
                                }
                            }
                            val activeCueIdx = remember(filteredCues, playbackState.positionMs) {
                                if (filteredCues.isEmpty()) -1
                                else filteredCues.indexOfLast { it.startTimeMs <= playbackState.positionMs }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = inlineTranscriptQuery,
                                    onValueChange = { inlineTranscriptQuery = it },
                                    placeholder = {
                                        Text(
                                            "Search transcript...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LoheTextMutedDark
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = null,
                                            tint = LoheTextMutedDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = if (inlineTranscriptQuery.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { inlineTranscriptQuery = "" }) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "Clear search",
                                                    tint = LoheTextMutedDark,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    } else null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LoheElectricBlue,
                                        unfocusedBorderColor = LoheBorderDark,
                                        focusedTextColor = LoheTextPrimaryDark,
                                        unfocusedTextColor = LoheTextPrimaryDark
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("full_player_inline_transcript_search")
                                )

                                if (filteredCues.isEmpty()) {
                                    Text(
                                        text = "No transcript lines matched \"$inlineTranscriptQuery\".",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LoheTextMutedDark,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    )
                                } else {
                                    filteredCues.forEachIndexed { idx, cue ->
                                        val isCurrent = idx == activeCueIdx
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isCurrent) LoheElectricBlue.copy(alpha = 0.16f)
                                                    else Color.Transparent
                                                )
                                                .clickable { onSeekTo(cue.startTimeMs) }
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                                .testTag("full_player_inline_transcript_cue_$idx"),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = PodcastingChaptersAndTranscripts.formatTimestamp(cue.startTimeMs),
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                color = if (isCurrent) LoheCyanAccent else LoheTextMutedDark,
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .width(44.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                if (cue.speaker.isNotBlank()) {
                                                    Text(
                                                        text = cue.speaker,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = LoheElectricBlue
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                }
                                                Text(
                                                    text = cue.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isCurrent) LoheTextPrimaryDark else LoheTextPrimaryDark.copy(alpha = 0.85f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeNotesBottomSheet(
    episode: EpisodeEntity,
    chapters: List<EpisodeChapter> = emptyList(),
    transcriptCues: List<TranscriptCue> = emptyList(),
    bookmarks: List<EpisodeBookmarkEntity> = emptyList(),
    isLoadingExtras: Boolean = false,
    onDismiss: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onDeleteBookmark: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val plainFromHtml = remember(episode.descriptionHtml) {
        Html.fromHtml(episode.descriptionHtml, Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }
    var selectedTabIndex by remember(episode.id) { mutableIntStateOf(0) }
    var transcriptQuery by remember(episode.id) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LoheSurfaceDark,
        contentColor = LoheTextPrimaryDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .testTag("episode_notes_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.podcastTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = LoheCyanAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LoheTextPrimaryDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Notes", tint = LoheTextMutedDark)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val tabs = listOf(
                "Notes" to "notes_tab_notes",
                "Chapters" to "notes_tab_chapters",
                "Transcript" to "notes_tab_transcript",
                "Bookmarks" to "notes_tab_bookmarks"
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = LoheBackgroundDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { idx, (label, tag) ->
                        val selected = selectedTabIndex == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selected) LoheElectricBlue.copy(alpha = 0.24f) else Color.Transparent
                                )
                                .border(
                                    width = if (selected) 1.dp else 0.dp,
                                    color = if (selected) LoheElectricBlue.copy(alpha = 0.55f) else Color.Transparent,
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .clickable { selectedTabIndex = idx }
                                .testTag(tag),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) LoheTextPrimaryDark else LoheTextMutedDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingExtras) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        color = LoheCyanAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Syncing Podcasting 2.0 chapters & transcript...",
                        style = MaterialTheme.typography.bodySmall,
                        color = LoheTextMutedDark
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = LoheBackgroundDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .border(1.dp, LoheBorderDark, RoundedCornerShape(14.dp))
            ) {
                when (selectedTabIndex) {
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = plainFromHtml.ifBlank { "No show notes provided for this episode." },
                                style = MaterialTheme.typography.bodyLarge,
                                color = LoheTextPrimaryDark
                            )
                            if (chapters.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Quick Chapter Jumps",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LoheCyanAccent
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chapters.take(4).forEach { ch ->
                                        OutlinedButton(onClick = { onSeekToMs(ch.startTimeMs) }) {
                                            Text(
                                                "${PodcastingChaptersAndTranscripts.formatTimestamp(ch.startTimeMs)} ${ch.title}",
                                                color = LoheElectricBlue,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (chapters.isEmpty()) {
                                Text(
                                    text = "No Podcasting 2.0 chapters published for this episode.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LoheTextMutedDark,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                chapters.forEachIndexed { idx, ch ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LoheSurfaceDark)
                                            .border(1.dp, LoheBorderDark, RoundedCornerShape(10.dp))
                                            .clickable { onSeekToMs(ch.startTimeMs) }
                                            .padding(12.dp)
                                            .testTag("chapter_row_$idx"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (ch.imageUrl.isNotBlank()) {
                                            PodcastArtworkThumbnail(
                                                artworkUrl = ch.imageUrl,
                                                title = ch.title,
                                                cornerRadius = 6.dp,
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                        }
                                        Text(
                                            text = PodcastingChaptersAndTranscripts.formatTimestamp(ch.startTimeMs),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = LoheCyanAccent
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = ch.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = LoheTextPrimaryDark,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (ch.url.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(ch.url)).apply {
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("chapter_link_$idx")
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.OpenInNew,
                                                    contentDescription = "Open Chapter URL",
                                                    tint = LoheElectricBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        val filteredCues = remember(transcriptCues, transcriptQuery) {
                            val q = transcriptQuery.trim()
                            if (q.isBlank()) transcriptCues
                            else transcriptCues.filter {
                                it.text.contains(q, ignoreCase = true) ||
                                    it.speaker.contains(q, ignoreCase = true)
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            OutlinedTextField(
                                value = transcriptQuery,
                                onValueChange = { transcriptQuery = it },
                                placeholder = { Text("Search transcript keywords...") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("transcript_search_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (filteredCues.isEmpty()) {
                                    Text(
                                        text = if (transcriptCues.isEmpty()) "No Podcasting 2.0 transcript attached to this episode."
                                        else "No transcript lines matched \"$transcriptQuery\".",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LoheTextMutedDark,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                } else {
                                    filteredCues.forEachIndexed { idx, cue ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(LoheSurfaceDark)
                                                .clickable { onSeekToMs(cue.startTimeMs) }
                                                .padding(10.dp)
                                                .testTag("transcript_cue_$idx")
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = PodcastingChaptersAndTranscripts.formatTimestamp(cue.startTimeMs),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = LoheCyanAccent
                                                )
                                                if (cue.speaker.isNotBlank()) {
                                                    Text(
                                                        text = cue.speaker,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = LoheElectricBlue
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = cue.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = LoheTextPrimaryDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (bookmarks.isEmpty()) {
                                Text(
                                    text = "No saved timestamp bookmarks yet. Use Bookmark in the Full Player to capture moments.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LoheTextMutedDark,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                bookmarks.forEachIndexed { idx, bm ->
                                    val stamp = PodcastingChaptersAndTranscripts.formatTimestamp(bm.positionMs)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LoheSurfaceDark)
                                            .border(1.dp, LoheBorderDark, RoundedCornerShape(10.dp))
                                            .clickable { onSeekToMs(bm.positionMs) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                            .testTag("bookmark_row_$idx"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stamp,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = LoheCyanAccent
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = bm.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = LoheTextPrimaryDark,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                val text = "\"${bm.label}\" — ${episode.podcastTitle}: ${episode.title} @ $stamp\n${episode.audioUrl}"
                                                val send = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, text)
                                                }
                                                runCatching {
                                                    context.startActivity(
                                                        Intent.createChooser(send, "Share Bookmark").apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = "Share Bookmark", tint = LoheElectricBlue, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { onDeleteBookmark(bm.id) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Bookmark", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
