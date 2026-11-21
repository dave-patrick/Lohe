package com.example.ui.components

import android.content.Intent
import android.graphics.BlurMaskFilter
import android.net.Uri
import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
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
import com.example.data.local.EpisodeBookmarkEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheUserSettings
import com.example.data.local.PlaylistEntity
import com.example.data.network.EpisodeChapter
import com.example.data.network.PodcastingChaptersAndTranscripts
import com.example.data.network.TranscriptCue
import com.example.playback.PlaybackUiState
import com.example.ui.PrimaryTab
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheBrightBlueText
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheGlassNavBorderDark
import com.example.ui.theme.LoheGlassNavDark
import com.example.ui.theme.LoheMiniPlayerBorderDark
import com.example.ui.theme.LoheMiniPlayerEndDark
import com.example.ui.theme.LoheMiniPlayerStartDark
import com.example.ui.theme.LoheSurfaceDark
import com.example.ui.theme.LoheSurfaceElevatedDark
import com.example.ui.theme.LoheTextMutedDark
import com.example.ui.theme.LoheTextPrimaryDark
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
 * Compact full-width docked Bottom Navigation Bar attached flush to the bottom edge
 * with a solid opaque background (no floating card, nothing visible behind it).
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
    val containerColor = if (isDark) Color(0xFF0A0F1A) else Color.White
    val dividerColor = if (isDark) LoheBorderDark else Color(0xFFD8DEE8)
    val mutedColor = if (isDark) LoheTextMutedDark else Color(0xFF6B7280)

    Surface(
        shape = RectangleShape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_navigation_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(thickness = 1.dp, color = dividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
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
                            .fillMaxHeight()
                            .clickable { onSelectTab(tab) }
                            .testTag(tab.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) LoheElectricBlue else Color.Transparent)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = iconVec,
                                contentDescription = tab.label,
                                tint = tintColor,
                                modifier = Modifier.size(20.dp)
                            )
                            if (tab == PrimaryTab.UP_NEXT && upNextCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 5.dp, y = (-2).dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(LoheCyanAccent)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = tintColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
 * Floating rounded-card Mini-Player sitting inside a locked-off dedicated bottom slot
 * directly above the docked bottom navigation bar so it never overlaps page content.
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
        Brush.horizontalGradient(listOf(Color(0xFFF6F9FE), Color(0xFFEDF4FD)))
    }
    val cardBorderColor = if (isDark) LoheElectricBlue.copy(alpha = 0.50f) else Color(0xFFBFD4F5)
    val trackColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val cardShape = RoundedCornerShape(22.dp)

    val glowAlpha = if (isDark) 115 else 55
    val glowPaint = remember(glowAlpha) {
        Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(glowAlpha, 0, 142, 255)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = if (isDark) 14.dp else 5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val inset = 2.dp.toPx()
                    val right = size.width - inset
                    val bottom = size.height - inset
                    val blurPx = 14.dp.toPx()
                    if (right > inset && bottom > inset && blurPx >= 1f) {
                        drawIntoCanvas { canvas ->
                            glowPaint.asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                            val r = 22.dp.toPx()
                            canvas.drawRoundRect(
                                left = inset,
                                top = inset,
                                right = right,
                                bottom = bottom,
                                radiusX = r,
                                radiusY = r,
                                paint = glowPaint
                            )
                        }
                    }
                }
                .shadow(
                    elevation = if (isDark) 14.dp else 6.dp,
                    shape = cardShape,
                    ambientColor = LoheElectricBlue,
                    spotColor = LoheElectricBlue
                )
                .clip(cardShape)
                .background(containerBrush)
                .border(1.dp, cardBorderColor, cardShape)
                .clickable(onClick = onOpenFullPlayer)
                .testTag("mini_player_bar")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PodcastArtworkThumbnail(
                        artworkUrl = episode.artworkUrl,
                        title = episode.podcastTitle,
                        cornerRadius = 12.dp,
                        modifier = Modifier.size(44.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = episode.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (nextUpEpisode != null) {
                                "${episode.podcastTitle} • Next: ${nextUpEpisode.title}"
                            } else {
                                "${episode.podcastTitle} • ${formatDurationMmSs(playbackState.positionMs)} / ${formatDurationMmSs(playbackState.durationMs)}"
                            },
                            fontSize = 11.sp,
                            color = if (isDark) LoheBrightBlueText else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(40.dp)
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
                            .size(38.dp)
                            .testTag("mini_player_expand_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Open Full Player",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Subtle playback progress bar nested cleanly along the bottom edge of the card
                LinearProgressIndicator(
                    progress = { playbackState.progressFraction.coerceIn(0.01f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = LoheElectricBlue,
                    trackColor = trackColor
                )
            }
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

    val glowPaint = remember {
        Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(130, 0, 142, 255)
            }
        }
    }

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
                val blurPx = 14.dp.toPx()
                if (size.width > 0f && size.height > 0f && blurPx >= 1f) {
                    drawIntoCanvas { canvas ->
                        glowPaint.asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                        val radius = size.height / 2f
                        canvas.drawRoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height,
                            radiusX = radius,
                            radiusY = radius,
                            paint = glowPaint
                        )
                    }
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    var showNowPlayingSettingsSheet by remember { mutableStateOf(false) }
    var showChapterPicker by remember(episode.id) { mutableStateOf(false) }
    var inlineContentTab by remember(episode.id) { mutableIntStateOf(0) } // 0 = Notes, 1 = Transcript
    var inlineTranscriptQuery by remember(episode.id) { mutableStateOf("") }
    var dismissDragOffsetY by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()
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

    val swipeDownModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (dismissDragOffsetY > 180f) {
                    dismissDragOffsetY = 0f
                    onDismiss()
                } else {
                    dismissDragOffsetY = 0f
                }
            },
            onDragCancel = { dismissDragOffsetY = 0f },
            onVerticalDrag = { change, dragAmount ->
                val next = dismissDragOffsetY + dragAmount
                if (next >= 0f) {
                    change.consume()
                    dismissDragOffsetY = next.coerceAtMost(420f)
                }
            }
        )
    }

    Surface(
        color = LoheBackgroundDark,
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dismissDragOffsetY.roundToInt()) }
            .background(LoheBackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoheBackgroundDark)
        ) {
            // Swipe-down grab bar + header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(swipeDownModifier)
                    .testTag("full_player_drag_header"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, bottom = 2.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.28f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
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
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val maxArtworkSide = minOf(
                    (maxWidth - 28.dp).coerceAtLeast(120.dp),
                    (maxHeight * 0.65f).coerceIn(140.dp, 460.dp)
                )
                val artCornerRadius = 24.dp
                val artworkGlowPaint = remember {
                    Paint().apply {
                        asFrameworkPaint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(95, 0, 142, 255)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .testTag("full_player_modal")
                ) {
                    // Floating Artwork Card maximizing visual real estate with swipe-down & Now Playing Settings button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(swipeDownModifier)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(maxArtworkSide)
                                .drawBehind {
                                    val left = 6.dp.toPx()
                                    val top = 10.dp.toPx()
                                    val right = size.width - 6.dp.toPx()
                                    val bottom = size.height + 6.dp.toPx()
                                    val blurPx = 22.dp.toPx()
                                    if (right > left && bottom > top && blurPx >= 1f) {
                                        drawIntoCanvas { canvas ->
                                            artworkGlowPaint.asFrameworkPaint().maskFilter =
                                                BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                                            val r = artCornerRadius.toPx()
                                            canvas.drawRoundRect(
                                                left = left,
                                                top = top,
                                                right = right,
                                                bottom = bottom,
                                                radiusX = r,
                                                radiusY = r,
                                                paint = artworkGlowPaint
                                            )
                                        }
                                    }
                                }
                                .shadow(
                                    elevation = 20.dp,
                                    shape = RoundedCornerShape(artCornerRadius),
                                    ambientColor = LoheElectricBlue,
                                    spotColor = Color.Black
                                )
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
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            LoheElectricBlue.copy(alpha = 0.40f),
                                            Color.White.copy(alpha = 0.08f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(artCornerRadius)
                                )
                                .testTag("full_player_artwork"),
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

                            // Chapters, Notes & Transcript floating button in lower-left corner of artwork card
                            val hasChaptersOrTranscript = activeChapters.isNotEmpty() || activeTranscriptCues.isNotEmpty()
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                            ) {
                                IconButton(
                                    onClick = { onOpenNotes(episode) },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(LoheBackgroundDark.copy(alpha = 0.80f))
                                        .border(
                                            width = 1.dp,
                                            color = if (hasChaptersOrTranscript) LoheElectricBlue else Color.White.copy(alpha = 0.24f),
                                            shape = CircleShape
                                        )
                                        .testTag("full_player_open_notes_sheet")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoStories,
                                        contentDescription = "Chapters, Notes & Transcript",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                if (hasChaptersOrTranscript) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(LoheElectricBlue)
                                    )
                                }
                            }

                            // Now Playing Settings floating button in lower-right corner of artwork card
                            val hasActiveAudioSetting = playbackState.volumeBoostDb > 0 ||
                                playbackState.skipSilenceEnabled ||
                                playbackState.sleepAtEndOfEpisode
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                            ) {
                                IconButton(
                                    onClick = { showNowPlayingSettingsSheet = true },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(LoheBackgroundDark.copy(alpha = 0.80f))
                                        .border(
                                            width = 1.dp,
                                            color = if (hasActiveAudioSetting) LoheElectricBlue else Color.White.copy(alpha = 0.24f),
                                            shape = CircleShape
                                        )
                                        .testTag("full_player_audio_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = "Now Playing Settings",
                                        tint = Color.White,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                if (hasActiveAudioSetting) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(LoheElectricBlue)
                                    )
                                }
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

            val maxSliderMs = playbackState.durationMs.coerceAtLeast(1000L).toFloat()
            Slider(
                value = playbackState.positionMs.toFloat().coerceIn(0f, maxSliderMs),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..maxSliderMs,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFB4BCC8),
                    activeTrackColor = LoheElectricBlue,
                    inactiveTrackColor = Color(0xFF2C3340)
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
                            val safeCurrentChapterUrl = remember(currentChapter.url) { sanitizeExternalNoteUrl(currentChapter.url) }
                            if (safeCurrentChapterUrl != null) {
                                IconButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(safeCurrentChapterUrl)).apply {
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
                                            val safeChapterUrl = remember(ch.url) { sanitizeExternalNoteUrl(ch.url) }
                                            if (safeChapterUrl != null) {
                                                IconButton(
                                                    onClick = {
                                                        runCatching {
                                                            context.startActivity(
                                                                Intent(Intent.ACTION_VIEW, Uri.parse(safeChapterUrl)).apply {
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

            val controlsCardShape = RoundedCornerShape(22.dp)
            val controlsCardBrush = Brush.horizontalGradient(listOf(LoheMiniPlayerStartDark, LoheMiniPlayerEndDark))
            val controlsGlowPaint = remember {
                Paint().apply {
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.argb(115, 0, 142, 255)
                    }
                }
            }
            Surface(
                shape = controlsCardShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .drawBehind {
                        val inset = 2.dp.toPx()
                        val right = size.width - inset
                        val bottom = size.height - inset
                        val blurPx = 14.dp.toPx()
                        if (right > inset && bottom > inset && blurPx >= 1f) {
                            drawIntoCanvas { canvas ->
                                controlsGlowPaint.asFrameworkPaint().maskFilter =
                                    BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                                val r = 22.dp.toPx()
                                canvas.drawRoundRect(
                                    left = inset,
                                    top = inset,
                                    right = right,
                                    bottom = bottom,
                                    radiusX = r,
                                    radiusY = r,
                                    paint = controlsGlowPaint
                                )
                            }
                        }
                    }
                    .shadow(
                        elevation = 14.dp,
                        shape = controlsCardShape,
                        ambientColor = LoheElectricBlue,
                        spotColor = LoheElectricBlue
                    )
                    .clip(controlsCardShape)
                    .background(controlsCardBrush)
                    .border(1.dp, LoheElectricBlue.copy(alpha = 0.50f), controlsCardShape)
                    .testTag("full_player_controls_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val speedActive = kotlin.math.abs(playbackState.playbackSpeed - 1.0f) >= 0.03f
                    IconButton(
                        onClick = {
                            showSpeedPicker = !showSpeedPicker
                            if (showSpeedPicker) {
                                showSleepTimerPicker = false
                                showBookmarkDialog = false
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (speedActive || showSpeedPicker) LoheElectricBlue else LoheSurfaceDark.copy(alpha = 0.85f))
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
                                showBookmarkDialog = false
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (sleepTimerActive || showSleepTimerPicker) LoheElectricBlue else LoheSurfaceDark.copy(alpha = 0.85f))
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
                    VariableSpeedSliderBar(
                        speed = playbackState.playbackSpeed,
                        onSpeedChange = onSpeedChange,
                        title = "Variable Speed",
                        keySpeedTagPrefix = "full_player_speed",
                        onDarkSurface = true,
                        modifier = Modifier.padding(14.dp)
                    )
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

                                ClickableShowNotesText(
                                    rawHtml = episode.descriptionHtml,
                                    textColor = LoheTextPrimaryDark,
                                    linkColor = LoheElectricBlue,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 23.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("full_player_notes_clickable_text")
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

    if (showNowPlayingSettingsSheet) {
        val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNowPlayingSettingsSheet = false },
            sheetState = settingsSheetState,
            containerColor = LoheSurfaceDark,
            contentColor = LoheTextPrimaryDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .testTag("now_playing_settings_sheet"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = LoheCyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Now Playing Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LoheTextPrimaryDark
                        )
                    }
                    IconButton(
                        onClick = { showNowPlayingSettingsSheet = false },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Settings", tint = LoheTextMutedDark)
                    }
                }

                // 1) Volume Boost
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LoheBackgroundDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LoheBorderDark, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .testTag("full_player_volume_boost_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = LoheCyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Volume Boost",
                                style = MaterialTheme.typography.titleSmall,
                                color = LoheTextPrimaryDark
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0 to "Off", 3 to "+3dB", 6 to "+6dB", 10 to "+10dB").forEach { (db, label) ->
                                val selected = playbackState.volumeBoostDb == db
                                FilterChip(
                                    selected = selected,
                                    onClick = { onVolumeBoostChange(db) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LoheElectricBlue,
                                        selectedLabelColor = Color.White,
                                        labelColor = LoheTextPrimaryDark
                                    )
                                )
                            }
                        }
                    }
                }

                // 2) Skip Silence toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LoheBackgroundDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LoheBorderDark, RoundedCornerShape(14.dp))
                        .clickable { onSkipSilenceChange(!playbackState.skipSilenceEnabled) }
                        .testTag("full_player_skip_silence_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = LoheCyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Skip Silence",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = LoheTextPrimaryDark
                                )
                                Text(
                                    text = "Automatically trim quiet pauses during speech",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LoheTextMutedDark
                                )
                            }
                        }
                        Switch(
                            checked = playbackState.skipSilenceEnabled,
                            onCheckedChange = onSkipSilenceChange,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LoheElectricBlue)
                        )
                    }
                }

                // 3) Sleep at End of Episode toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LoheBackgroundDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LoheBorderDark, RoundedCornerShape(14.dp))
                        .clickable { onSetSleepEndOfEpisode(!playbackState.sleepAtEndOfEpisode) }
                        .testTag("sleep_timer_end_of_ep")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bedtime,
                                contentDescription = null,
                                tint = LoheElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Sleep at End of Episode",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = LoheTextPrimaryDark
                                )
                                Text(
                                    text = "Pause automatically when this episode finishes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LoheTextMutedDark
                                )
                            }
                        }
                        Switch(
                            checked = playbackState.sleepAtEndOfEpisode,
                            onCheckedChange = onSetSleepEndOfEpisode,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LoheElectricBlue)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
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
    onDeleteBookmark: (String) -> Unit = {},
    isPlaying: Boolean = false,
    isDownloading: Boolean = false,
    onPlayToggle: (() -> Unit)? = null,
    onAddToUpNext: ((Boolean) -> Unit)? = null,
    onRemoveFromUpNext: (() -> Unit)? = null,
    onToggleStarred: (() -> Unit)? = null,
    onToggleDownloaded: (() -> Unit)? = null,
    onMarkPlayed: (() -> Unit)? = null,
    customPlaylists: List<PlaylistEntity> = emptyList(),
    onToggleEpisodeInPlaylist: ((PlaylistEntity) -> Unit)? = null,
    onCreatePlaylistWithEpisode: ((String) -> Unit)? = null,
    onOpenPodcast: (() -> Unit)? = null,
    onResetProgress: (() -> Unit)? = null,
    onArchiveEpisode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val plainFromHtml = remember(episode.descriptionHtml) {
        Html.fromHtml(episode.descriptionHtml, Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }
    var selectedTabIndex by remember(episode.id) { mutableIntStateOf(0) }
    var transcriptQuery by remember(episode.id) { mutableStateOf("") }
    var showPlaylistDialog by remember(episode.id) { mutableStateOf(false) }
    var newPlaylistName by remember(episode.id) { mutableStateOf("") }
    val userPlaylists = remember(customPlaylists) {
        customPlaylists.filter { it.smartRuleType.isEmpty() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LoheSurfaceDark,
        contentColor = LoheTextPrimaryDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .testTag("episode_notes_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PodcastArtworkThumbnail(
                    artworkUrl = episode.artworkUrl,
                    title = episode.podcastTitle,
                    cornerRadius = 14.dp,
                    modifier = Modifier
                        .size(66.dp)
                        .testTag("episode_sheet_artwork")
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.podcastTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = LoheCyanAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onOpenPodcast != null) {
                            Modifier.clickable { onOpenPodcast() }
                        } else {
                            Modifier
                        }
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
                    val durMins = (episode.durationMs / 60_000L).coerceAtLeast(1L)
                    val dateStr = remember(episode.publishedAt) {
                        if (episode.publishedAt > 0L) {
                            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                .format(java.util.Date(episode.publishedAt))
                        } else ""
                    }
                    Text(
                        text = listOfNotNull(
                            dateStr.takeIf { it.isNotBlank() },
                            "$durMins min",
                            if (episode.isPlayed) "Played" else null
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = LoheTextMutedDark
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Episode Details", tint = LoheTextMutedDark)
                }
            }

            if (onPlayToggle != null || onAddToUpNext != null || onToggleStarred != null || onToggleDownloaded != null || onMarkPlayed != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("episode_sheet_controls_row"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onPlayToggle != null) {
                        Button(
                            onClick = onPlayToggle,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("episode_sheet_play_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isPlaying) "Pause" else "Play",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (onAddToUpNext != null) {
                        UpNextQueueActionButton(
                            inUpNext = episode.inUpNext,
                            onAddToBeginning = { onAddToUpNext(true) },
                            onAddToEnd = { onAddToUpNext(false) },
                            onRemoveFromUpNext = onRemoveFromUpNext,
                            buttonSize = 42.dp,
                            testTagPrefix = "episode_sheet_up_next_button"
                        )
                    }

                    if (onToggleStarred != null) {
                        IconButton(
                            onClick = onToggleStarred,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("episode_sheet_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (episode.isStarred) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (episode.isStarred) "Remove Bookmark" else "Bookmark Episode",
                                tint = if (episode.isStarred) LoheCyanAccent else LoheTextMutedDark
                            )
                        }
                    }

                    if (onToggleDownloaded != null) {
                        IconButton(
                            onClick = onToggleDownloaded,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("episode_sheet_download_button")
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    color = LoheCyanAccent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (episode.isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.DownloadForOffline,
                                    contentDescription = if (episode.isDownloaded) "Remove Download" else "Download Episode",
                                    tint = if (episode.isDownloaded) LoheCyanAccent else LoheTextMutedDark
                                )
                            }
                        }
                    }

                    if (onMarkPlayed != null) {
                        IconButton(
                            onClick = onMarkPlayed,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("episode_sheet_played_button")
                        ) {
                            Icon(
                                imageVector = if (episode.isPlayed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = if (episode.isPlayed) "Mark Unplayed" else "Mark Played",
                                tint = if (episode.isPlayed) Color(0xFF22C55E) else LoheTextMutedDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag("episode_sheet_secondary_actions_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onAddToUpNext != null) {
                        EpisodeSheetQuickActionChip(
                            label = "Play Next",
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            tint = LoheCyanAccent,
                            testTag = "episode_sheet_play_next_chip",
                            onClick = { onAddToUpNext(true) }
                        )
                        if (episode.inUpNext && onRemoveFromUpNext != null) {
                            EpisodeSheetQuickActionChip(
                                label = "Remove Queue",
                                icon = Icons.Filled.Remove,
                                tint = Color(0xFFF59E0B),
                                testTag = "episode_sheet_remove_queue_chip",
                                onClick = onRemoveFromUpNext
                            )
                        } else {
                            EpisodeSheetQuickActionChip(
                                label = "Play Last",
                                icon = Icons.AutoMirrored.Filled.QueueMusic,
                                tint = LoheBrightBlueText,
                                testTag = "episode_sheet_play_last_chip",
                                onClick = { onAddToUpNext(false) }
                            )
                        }
                    }

                    if (onToggleEpisodeInPlaylist != null || onCreatePlaylistWithEpisode != null) {
                        val matchingCount = userPlaylists.count { episode.id in it.episodeIdList() }
                        EpisodeSheetQuickActionChip(
                            label = if (matchingCount > 0) "Playlists ($matchingCount)" else "Add to Playlist",
                            icon = Icons.Filled.Add,
                            tint = if (matchingCount > 0 || showPlaylistDialog) LoheCyanAccent else LoheTextPrimaryDark,
                            testTag = "episode_sheet_playlist_chip",
                            onClick = { showPlaylistDialog = !showPlaylistDialog }
                        )
                    }

                    if (onOpenPodcast != null) {
                        EpisodeSheetQuickActionChip(
                            label = "Go to Show",
                            icon = Icons.Filled.Podcasts,
                            tint = LoheBrightBlueText,
                            testTag = "episode_sheet_open_podcast_chip",
                            onClick = onOpenPodcast
                        )
                    }

                    if (onResetProgress != null && (episode.resumePositionMs > 0L || episode.isPlayed)) {
                        EpisodeSheetQuickActionChip(
                            label = "Reset Progress",
                            icon = Icons.Filled.RestartAlt,
                            tint = LoheTextPrimaryDark,
                            testTag = "episode_sheet_reset_progress_chip",
                            onClick = onResetProgress
                        )
                    }

                    if (onArchiveEpisode != null && !episode.isArchived) {
                        EpisodeSheetQuickActionChip(
                            label = "Archive",
                            icon = Icons.Filled.Archive,
                            tint = LoheTextPrimaryDark,
                            testTag = "episode_sheet_archive_chip",
                            onClick = onArchiveEpisode
                        )
                    }

                    EpisodeSheetQuickActionChip(
                        label = "Share",
                        icon = Icons.Filled.Share,
                        tint = LoheTextPrimaryDark,
                        testTag = "episode_sheet_share_chip",
                        onClick = {
                            val shareText = buildString {
                                append(episode.title)
                                if (episode.podcastTitle.isNotBlank()) {
                                    append(" — ")
                                    append(episode.podcastTitle)
                                }
                                if (episode.audioUrl.isNotBlank()) {
                                    append("\n")
                                    append(episode.audioUrl)
                                }
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "${episode.title} — ${episode.podcastTitle}")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            try {
                                context.startActivity(
                                    Intent.createChooser(sendIntent, "Share Episode")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                            }
                        }
                    )
                }

                AnimatedVisibility(visible = showPlaylistDialog) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = LoheBackgroundDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .border(1.dp, LoheBorderDark, RoundedCornerShape(14.dp))
                            .testTag("episode_sheet_playlist_dialog")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Add to Playlist",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LoheTextPrimaryDark
                                )
                                TextButton(onClick = { showPlaylistDialog = false }) {
                                    Text("Done", color = LoheCyanAccent)
                                }
                            }
                            if (userPlaylists.isEmpty()) {
                                Text(
                                    text = "No custom playlists yet. Create one below to add this episode.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LoheTextMutedDark
                                )
                            } else {
                                userPlaylists.forEach { playlist ->
                                    val inPlaylist = episode.id in playlist.episodeIdList()
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (inPlaylist) LoheElectricBlue.copy(alpha = 0.16f) else LoheSurfaceDark,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                1.dp,
                                                if (inPlaylist) LoheElectricBlue else LoheBorderDark,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                onToggleEpisodeInPlaylist?.invoke(playlist)
                                            }
                                            .testTag("episode_sheet_playlist_item_${playlist.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = LoheTextPrimaryDark
                                                )
                                                Text(
                                                    text = playlist.folderName.ifBlank { "General" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = LoheTextMutedDark
                                                )
                                            }
                                            Icon(
                                                imageVector = if (inPlaylist) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                                contentDescription = if (inPlaylist) "In playlist" else "Not in playlist",
                                                tint = if (inPlaylist) LoheCyanAccent else LoheTextMutedDark
                                            )
                                        }
                                    }
                                }
                            }

                            if (onCreatePlaylistWithEpisode != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newPlaylistName,
                                        onValueChange = { newPlaylistName = it },
                                        placeholder = { Text("New playlist name...") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("episode_sheet_new_playlist_input")
                                    )
                                    Button(
                                        onClick = {
                                            val trimmed = newPlaylistName.trim()
                                            if (trimmed.isNotBlank()) {
                                                onCreatePlaylistWithEpisode(trimmed)
                                                newPlaylistName = ""
                                            }
                                        },
                                        enabled = newPlaylistName.trim().isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                                        modifier = Modifier.testTag("episode_sheet_new_playlist_create")
                                    ) {
                                        Text("Create", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
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
                            ClickableShowNotesText(
                                rawHtml = episode.descriptionHtml,
                                textColor = LoheTextPrimaryDark,
                                linkColor = LoheElectricBlue,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("episode_notes_clickable_text")
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
                                        val safeChapterUrl = remember(ch.url) { sanitizeExternalNoteUrl(ch.url) }
                                        if (safeChapterUrl != null) {
                                            IconButton(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(safeChapterUrl)).apply {
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

@Composable
private fun EpisodeSheetQuickActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = LoheBackgroundDark,
        modifier = Modifier
            .border(1.dp, LoheBorderDark, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = LoheTextPrimaryDark,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

fun formatPlaybackSpeedLabel(speed: Float): String {
    val snapped = ((speed * 20f).roundToInt() / 20f).coerceIn(0.5f, 3.0f)
    val hundredths = (snapped * 100f).roundToInt()
    return when {
        hundredths % 100 == 0 -> "${hundredths / 100}.0x"
        hundredths % 10 == 0 -> String.format("%.1fx", snapped)
        else -> String.format("%.2fx", snapped)
    }
}

/**
 * Horizontal sliding Variable Speed bar (0.5x – 3.0x in 0.05x steps)
 * with calibrated tick marks and tappable key-speed markers (0.5x, 1.0x, 1.5x, 2.0x, 2.5x, 3.0x).
 */
@Composable
fun VariableSpeedSliderBar(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Variable Speed",
    keySpeedTagPrefix: String = "speed_mark",
    onDarkSurface: Boolean = true
) {
    val minSpeed = 0.5f
    val maxSpeed = 3.0f
    val keySpeeds = remember { listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f) }
    val minorSpeeds = remember { listOf(0.75f, 1.25f, 1.75f, 2.25f, 2.75f) }

    val clamped = speed.coerceIn(minSpeed, maxSpeed)
    val snapped = ((clamped * 20f).roundToInt() / 20f).coerceIn(minSpeed, maxSpeed)
    val isDark = onDarkSurface || MaterialTheme.colorScheme.background == LoheBackgroundDark
    val primaryText = if (isDark) LoheTextPrimaryDark else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) LoheTextMutedDark else MaterialTheme.colorScheme.onSurfaceVariant
    val stepperBg = if (isDark) LoheSurfaceElevatedDark else MaterialTheme.colorScheme.surfaceVariant
    val borderClr = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoheElectricBlue.copy(alpha = 0.18f))
                        .border(1.dp, LoheCyanAccent.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatPlaybackSpeedLabel(snapped),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) LoheCyanAccent else LoheElectricBlue
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (kotlin.math.abs(snapped - 1.0f) >= 0.03f) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(stepperBg)
                            .border(1.dp, borderClr, RoundedCornerShape(50))
                            .clickable { onSpeedChange(1.0f) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("${keySpeedTagPrefix}_reset")
                    ) {
                        Text(
                            text = "1.0x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LoheBrightBlueText
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(stepperBg)
                        .border(1.dp, borderClr, CircleShape)
                        .clickable {
                            val next = (((snapped - 0.05f) * 20f).roundToInt() / 20f).coerceIn(minSpeed, maxSpeed)
                            onSpeedChange(next)
                        }
                        .testTag("${keySpeedTagPrefix}_dec"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Decrease speed",
                        tint = primaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(stepperBg)
                        .border(1.dp, borderClr, CircleShape)
                        .clickable {
                            val next = (((snapped + 0.05f) * 20f).roundToInt() / 20f).coerceIn(minSpeed, maxSpeed)
                            onSpeedChange(next)
                        }
                        .testTag("${keySpeedTagPrefix}_inc"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Increase speed",
                        tint = primaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Slider(
            value = snapped,
            onValueChange = { raw ->
                val quantized = ((raw * 20f).roundToInt() / 20f).coerceIn(minSpeed, maxSpeed)
                onSpeedChange(quantized)
            },
            valueRange = minSpeed..maxSpeed,
            steps = 49,
            colors = SliderDefaults.colors(
                thumbColor = LoheCyanAccent,
                activeTrackColor = LoheElectricBlue,
                inactiveTrackColor = borderClr,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${keySpeedTagPrefix}_slider")
        )

        // Calibrated horizontal tick bar aligned with the slider range
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(10.dp)
                .offset(y = (-6).dp)
        ) {
            val span = maxSpeed - minSpeed
            minorSpeeds.forEach { m ->
                val frac = ((m - minSpeed) / span).coerceIn(0f, 1f)
                val x = size.width * frac
                val near = kotlin.math.abs(snapped - m) < 0.04f
                drawLine(
                    color = if (near) LoheCyanAccent else mutedText.copy(alpha = 0.45f),
                    start = Offset(x, 2.dp.toPx()),
                    end = Offset(x, 7.dp.toPx()),
                    strokeWidth = if (near) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            keySpeeds.forEach { k ->
                val frac = ((k - minSpeed) / span).coerceIn(0f, 1f)
                val x = size.width * frac
                val active = kotlin.math.abs(snapped - k) < 0.04f
                drawLine(
                    color = if (active) LoheCyanAccent else LoheElectricBlue.copy(alpha = 0.85f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = if (active) 2.5.dp.toPx() else 1.75.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Tappable key speed labels aligned directly under each major tick
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-4).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            keySpeeds.forEach { mark ->
                val isSelected = kotlin.math.abs(snapped - mark) < 0.04f
                val label = if (mark % 1f == 0f) "${mark.toInt()}.0x" else "${mark}x"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) LoheElectricBlue.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { onSpeedChange(mark) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("${keySpeedTagPrefix}_${mark}")
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) (if (isDark) LoheCyanAccent else LoheElectricBlue) else mutedText,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

