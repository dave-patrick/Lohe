package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.EpisodeEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.playback.PlaybackUiState
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheBrightBlueText
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark

private val LibraryInProgressViolet = Color(0xFF7B6CFF)
private val LibraryWaitingSlate = Color(0xFF566B85)

/**
 * Editorial Library Details card featuring:
 * - Atmospheric gradient wash & completion badge
 * - Hero Episodes counter with proportional 3-segment status bar (Played / In Progress / Waiting)
 * - Color-accented breakdown pills aligned with the status bar
 * - Interactive Subscriptions & Playlists collection launcher cards
 */
@Composable
fun LibraryDetailsCard(
    totalEpisodes: Int,
    playedCount: Int,
    inProgressCount: Int,
    waitingCount: Int,
    subscriptionsCount: Int,
    playlistsCount: Int,
    onNavigateToPodcasts: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onInProgressClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardColor = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
    val unstartedWaiting = (waitingCount - inProgressCount).coerceAtLeast(0)
    val safeTotal = totalEpisodes.coerceAtLeast(1)
    val completionPct = if (totalEpisodes > 0) ((playedCount * 100f) / totalEpisodes).toInt().coerceIn(0, 100) else 0

    val playedWeight = (playedCount.toFloat() / safeTotal).coerceAtLeast(0f)
    val inProgressWeight = (inProgressCount.toFloat() / safeTotal).coerceAtLeast(0f)
    val waitingWeight = (unstartedWaiting.toFloat() / safeTotal).coerceAtLeast(0f)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .testTag("home_library_details_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(
                                LoheElectricBlue.copy(alpha = 0.14f),
                                LoheCyanAccent.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                LoheElectricBlue.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        }
                    )
                )
        ) {
            // Subtle ambient glow arc in top-right corner
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            LoheCyanAccent.copy(alpha = if (isDark) 0.11f else 0.07f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.92f, size.height * 0.12f),
                        radius = size.width * 0.45f
                    ),
                    center = Offset(size.width * 0.92f, size.height * 0.12f),
                    radius = size.width * 0.45f
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Top Header + Completion Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(LoheCyanAccent, LoheElectricBlue))
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIBRARY DETAILS",
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = LoheBrightBlueText
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoheCyanAccent.copy(alpha = 0.14f))
                            .border(1.dp, LoheCyanAccent.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$completionPct% played",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) LoheCyanAccent else LoheBrightBlueText,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hero Unplayed Spotlight Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_detail_tile_unplayed"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatCompactCount(waitingCount),
                            fontSize = 34.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoheElectricBlue,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("library_detail_hero_unplayed_count")
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Waiting • Unplayed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        LoheElectricBlue.copy(alpha = 0.28f),
                                        LoheElectricBlue.copy(alpha = 0.12f)
                                    )
                                )
                            )
                            .border(1.dp, LoheElectricBlue.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = null,
                            tint = LoheElectricBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3-Segment Proportional Episode Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isDark) Color(0xFF152030) else MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (totalEpisodes > 0) {
                        if (playedWeight > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(playedWeight.coerceAtLeast(0.03f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.horizontalGradient(listOf(LoheElectricBlue, LoheCyanAccent))
                                    )
                            )
                        }
                        if (inProgressWeight > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(inProgressWeight.coerceAtLeast(0.03f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(LibraryInProgressViolet)
                            )
                        }
                        if (waitingWeight > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(waitingWeight.coerceAtLeast(0.03f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(LibraryWaitingSlate.copy(alpha = if (isDark) 0.65f else 0.45f))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3 Accent-Striped Breakdown Cards aligned with the status bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibraryStatusAccentTile(
                        label = "Played",
                        value = formatCompactCount(playedCount),
                        accent = LoheCyanAccent,
                        isDark = isDark,
                        borderColor = borderColor,
                        testTag = "library_detail_tile_played",
                        modifier = Modifier.weight(1f)
                    )
                    LibraryStatusAccentTile(
                        label = "In Progress",
                        value = formatCompactCount(inProgressCount),
                        accent = LibraryInProgressViolet,
                        isDark = isDark,
                        borderColor = borderColor,
                        testTag = "library_detail_tile_in_progress",
                        onClick = onInProgressClick,
                        modifier = Modifier.weight(1f)
                    )
                    LibraryStatusAccentTile(
                        label = "Total Eps",
                        value = formatCompactCount(totalEpisodes),
                        accent = LibraryWaitingSlate,
                        isDark = isDark,
                        borderColor = borderColor,
                        testTag = "library_detail_tile_episodes",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2 Tactile Interactive Collection Cards (Subscriptions & Playlists)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibraryCollectionLauncherTile(
                        label = "Subscriptions",
                        value = formatCompactCount(subscriptionsCount),
                        icon = Icons.Filled.Podcasts,
                        accent = LoheElectricBlue,
                        isDark = isDark,
                        borderColor = borderColor,
                        testTag = "library_detail_tile_subscriptions",
                        onClick = onNavigateToPodcasts,
                        modifier = Modifier.weight(1f)
                    )
                    LibraryCollectionLauncherTile(
                        label = "Playlists",
                        value = formatCompactCount(playlistsCount),
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        accent = LoheCyanAccent,
                        isDark = isDark,
                        borderColor = borderColor,
                        testTag = "library_detail_tile_playlists",
                        onClick = onNavigateToPlaylists,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryStatusAccentTile(
    label: String,
    value: String,
    accent: Color,
    isDark: Boolean,
    borderColor: Color,
    testTag: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tileBg = if (isDark) Color(0xFF0A111D) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tileBg)
            .border(1.dp, borderColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .then(clickModifier)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibraryCollectionLauncherTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    isDark: Boolean,
    borderColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(accent.copy(alpha = 0.16f), Color(0xFF0A111D))
        } else {
            listOf(accent.copy(alpha = 0.10f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        }
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tileBrush)
            .border(1.dp, accent.copy(alpha = if (isDark) 0.34f else 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDark) accent else LoheBrightBlueText,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

private data class RankedPodcastEpisodes(
    val podcast: PodcastEntity,
    val totalEpisodes: Int,
    val unplayedEpisodes: Int
)

/**
 * Pro Home card: Top shows in the user's library ranked by total episode count.
 */
@Composable
fun MostEpisodesCard(
    podcasts: List<PodcastEntity>,
    episodes: List<EpisodeEntity>,
    onSelectPodcast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardColor = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
    val trackColor = if (isDark) Color(0xFF1A2535) else MaterialTheme.colorScheme.surfaceVariant

    val ranked = remember(podcasts, episodes) {
        val byPodcast = episodes.groupBy { it.podcastId }
        podcasts.map { pod ->
            val eps = byPodcast[pod.id].orEmpty()
            RankedPodcastEpisodes(
                podcast = pod,
                totalEpisodes = eps.size,
                unplayedEpisodes = eps.count { !it.isPlayed }
            )
        }
            .filter { it.totalEpisodes > 0 }
            .sortedWith(compareByDescending<RankedPodcastEpisodes> { it.totalEpisodes }.thenBy { it.podcast.title })
            .take(5)
    }
    val maxCount = (ranked.firstOrNull()?.totalEpisodes ?: 1).coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .testTag("home_most_episodes_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(LoheElectricBlue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MOST EPISODES",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = LoheBrightBlueText
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoheCyanAccent.copy(alpha = 0.14f))
                        .border(1.dp, LoheCyanAccent.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = LoheCyanAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "PRO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) LoheCyanAccent else LoheBrightBlueText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (ranked.isEmpty()) {
                Text(
                    text = "Subscribe to shows or sync your RSS feeds to rank podcasts by episode count.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ranked.forEachIndexed { idx, row ->
                        val fraction = (row.totalEpisodes.toFloat() / maxCount.toFloat()).coerceIn(0.08f, 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectPodcast(row.podcast.id) }
                                .padding(vertical = 3.dp)
                                .testTag("most_episodes_row_${row.podcast.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (idx == 0) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(18.dp)
                            )
                            PodcastArtworkThumbnail(
                                artworkUrl = row.podcast.artworkUrl,
                                title = row.podcast.title,
                                cornerRadius = 10.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.podcast.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(trackColor)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(LoheElectricBlue, LoheCyanAccent)
                                                )
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${formatCompactCount(row.unplayedEpisodes)} unplayed",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LoheElectricBlue.copy(alpha = 0.14f))
                                    .border(1.dp, LoheElectricBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatCompactCount(row.totalEpisodes),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) LoheCyanAccent else LoheBrightBlueText,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class RankedPodcastListening(
    val podcast: PodcastEntity,
    val listenedMs: Long,
    val playedCount: Int
)

/**
 * Pro Home card: Top shows ranked by total listening time and completed episodes.
 */
@Composable
fun MostListenedPodcastsCard(
    podcasts: List<PodcastEntity>,
    episodes: List<EpisodeEntity>,
    onSelectPodcast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardColor = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
    val trackColor = if (isDark) Color(0xFF1A2535) else MaterialTheme.colorScheme.surfaceVariant

    val ranked = remember(podcasts, episodes) {
        val byPodcast = episodes.groupBy { it.podcastId }
        podcasts.map { pod ->
            val eps = byPodcast[pod.id].orEmpty()
            val listened = eps.sumOf { ep ->
                if (ep.isPlayed) ep.durationMs.coerceAtLeast(ep.resumePositionMs)
                else ep.resumePositionMs.coerceAtLeast(0L)
            }
            val played = eps.count { it.isPlayed }
            RankedPodcastListening(
                podcast = pod,
                listenedMs = listened,
                playedCount = played
            )
        }
            .filter { it.listenedMs > 0L || it.playedCount > 0 }
            .sortedWith(
                compareByDescending<RankedPodcastListening> { it.listenedMs }
                    .thenByDescending { it.playedCount }
            )
            .take(5)
    }
    val maxListenedMs = (ranked.firstOrNull()?.listenedMs ?: 1L).coerceAtLeast(1L)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, LoheCyanAccent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .testTag("home_most_listened_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(LoheCyanAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MOST LISTENED",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = LoheBrightBlueText,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoheCyanAccent.copy(alpha = 0.14f))
                        .border(1.dp, LoheCyanAccent.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = LoheCyanAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "PRO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) LoheCyanAccent else LoheBrightBlueText,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (ranked.isEmpty()) {
                Text(
                    text = "Listen to episodes across your shows and your top podcasts by time played will rank here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ranked.forEachIndexed { idx, row ->
                        val fraction = (row.listenedMs.toFloat() / maxListenedMs.toFloat()).coerceIn(0.08f, 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectPodcast(row.podcast.id) }
                                .padding(vertical = 3.dp)
                                .testTag("most_listened_row_${row.podcast.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (idx == 0) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(18.dp)
                            )
                            PodcastArtworkThumbnail(
                                artworkUrl = row.podcast.artworkUrl,
                                title = row.podcast.title,
                                cornerRadius = 10.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.podcast.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(trackColor)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(LoheCyanAccent, LoheElectricBlue)
                                                )
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${formatCompactCount(row.playedCount)} played",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LoheCyanAccent.copy(alpha = 0.14f))
                                    .border(1.dp, LoheCyanAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatCompactMinutes(row.listenedMs / 60_000L),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) LoheCyanAccent else LoheBrightBlueText,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pro Home card: Compact Up Next queue preview with remaining duration and 1-tap play.
 */
@Composable
fun UpNextSpotlightCard(
    upNextEpisodes: List<EpisodeEntity>,
    playbackState: PlaybackUiState,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onNavigateToUpNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardColor = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val remainingMs = remember(upNextEpisodes) {
        upNextEpisodes.sumOf { (it.durationMs - it.resumePositionMs).coerceAtLeast(0L) }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, LoheCyanAccent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .testTag("home_up_next_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(LoheCyanAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UP NEXT SPOTLIGHT",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = LoheBrightBlueText,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${formatCompactCount(upNextEpisodes.size)} • ${formatCompactMinutes(remainingMs / 60_000L)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) LoheCyanAccent else LoheBrightBlueText,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (upNextEpisodes.isEmpty()) {
                Text(
                    text = "Your Up Next queue is empty. Queue episodes from any show to preview them here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    upNextEpisodes.take(3).forEachIndexed { idx, ep ->
                        val isPlayingThis = playbackState.currentEpisode?.id == ep.id && playbackState.isPlaying
                        val epRemainingMs = (ep.durationMs - ep.resumePositionMs).coerceAtLeast(0L)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlayEpisode(ep) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (idx == 0) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(18.dp)
                            )
                            PodcastArtworkThumbnail(
                                artworkUrl = ep.artworkUrl,
                                title = ep.podcastTitle,
                                cornerRadius = 8.dp,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ep.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${ep.podcastTitle} • ${formatCompactMinutes(epRemainingMs / 60_000L)} left",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onPlayEpisode(ep) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlayingThis) "Pause" else "Play ${ep.title}",
                                    tint = LoheCyanAccent
                                )
                            }
                        }
                    }
                }
                if (upNextEpisodes.size > 3) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onNavigateToUpNext,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "View all ${formatCompactCount(upNextEpisodes.size)} queued",
                            color = LoheCyanAccent,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
