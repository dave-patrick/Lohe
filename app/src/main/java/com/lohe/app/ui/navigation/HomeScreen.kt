package com.lohe.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lohe.app.R
import com.lohe.app.data.ContinueEntry
import com.lohe.app.data.HomeDashboardStats
import com.lohe.app.data.QueueViewModel
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.Appearance
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.ElectricCyan
import com.lohe.app.ui.theme.GlassCard
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Home: library telemetry dashboard + what to listen to next.
 *
 * When no podcasts have been added yet, Kani the mascot holds the empty state.
 * Once the library has podcasts, the screen opens with a graphical telemetry
 * dashboard (Subscriptions, Episodes, Downloaded, Total Time Remaining, and
 * Total Time Playing Altogether) followed by in-progress episodes.
 */
@Composable
fun HomeScreen(
    queueViewModel: QueueViewModel,
    currentEpisodeId: String? = null,
    onOpenPodcast: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (ContinueEntry) -> Unit,
    appearance: Appearance,
    modifier: Modifier = Modifier,
) {
    val inProgress by queueViewModel.continueListening.collectAsState()
    val stats by queueViewModel.dashboardStats.collectAsState()
    val isLight = appearance == Appearance.LIGHT

    val currentEpisode: ContinueEntry? = inProgress.firstOrNull {
        currentEpisodeId != null && it.episode.uuid == currentEpisodeId
    } ?: inProgress.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLight) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.wordmark),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(24.dp)
                        .width(128.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (stats.subscriptionCount == 0 && stats.totalEpisodes == 0) {
            EmptyHome(
                onOpenLibrary = onOpenLibrary,
                onOpenDiscover = onOpenDiscover,
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HomeTelemetryDashboard(
                    stats = stats,
                    isLight = isLight,
                    onOpenLibrary = onOpenLibrary,
                )
            }

            if (currentEpisode != null) {
                item {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = if (isLight) ElectricBlue else ElectricCyan,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }

                item(key = currentEpisode.episode.uuid) {
                    ContinueCard(
                        entry = currentEpisode,
                        onPlay = { onPlay(currentEpisode) },
                        onOpenShow = { onOpenPodcast(currentEpisode.episode.podcastUuid) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTelemetryDashboard(
    stats: HomeDashboardStats,
    isLight: Boolean,
    onOpenLibrary: () -> Unit,
) {
    val animatedListened by animateFloatAsState(
        targetValue = stats.listenedFraction,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "listened-arc",
    )
    val animatedDownloaded by animateFloatAsState(
        targetValue = stats.downloadedFraction,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "downloaded-arc",
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Hero Radial Telemetry Card
        GlassCard(onClick = onOpenLibrary, contentPadding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "FREQUENCY TELEMETRY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = if (isLight) ElectricBlue else ElectricCyan,
                        )
                    }
                    Text(
                        text = "${stats.playedEpisodesCount}/${stats.totalEpisodes} completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Dual concentric radial gauge
                    Box(
                        modifier = Modifier.size(132.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val trackColor = if (isLight) {
                            Color.Black.copy(alpha = 0.08f)
                        } else {
                            Color.White.copy(alpha = 0.10f)
                        }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeOuter = 11.dp.toPx()
                            val strokeInner = 7.dp.toPx()
                            val padOuter = strokeOuter / 2f + 4.dp.toPx()
                            val padInner = padOuter + strokeOuter + 5.dp.toPx()

                            val outerSize = Size(size.width - padOuter * 2, size.height - padOuter * 2)
                            val innerSize = Size(size.width - padInner * 2, size.height - padInner * 2)

                            // Outer background ring (Listen progress)
                            drawArc(
                                color = trackColor,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = Offset(padOuter, padOuter),
                                size = outerSize,
                                style = Stroke(width = strokeOuter, cap = StrokeCap.Round),
                            )

                            // Inner background ring (Downloaded ratio)
                            drawArc(
                                color = trackColor,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = Offset(padInner, padInner),
                                size = innerSize,
                                style = Stroke(width = strokeInner, cap = StrokeCap.Round),
                            )

                            val outerSweep = (270f * animatedListened.coerceIn(0f, 1f)).coerceAtLeast(
                                if (stats.totalPlayedSeconds > 0L) 8f else 0f,
                            )
                            if (outerSweep > 0f) {
                                drawArc(
                                    brush = Brush.linearGradient(listOf(ElectricBlue, ElectricCyan)),
                                    startAngle = 135f,
                                    sweepAngle = outerSweep,
                                    useCenter = false,
                                    topLeft = Offset(padOuter, padOuter),
                                    size = outerSize,
                                    style = Stroke(width = strokeOuter, cap = StrokeCap.Round),
                                )
                                // Glowing indicator dot at arc tip
                                val angleRad = Math.toRadians((135f + outerSweep).toDouble())
                                val radius = outerSize.width / 2f
                                val cx = size.width / 2f + (radius * cos(angleRad)).toFloat()
                                val cy = size.height / 2f + (radius * sin(angleRad)).toFloat()
                                drawCircle(
                                    color = ElectricCyan.copy(alpha = 0.4f),
                                    radius = strokeOuter * 0.85f,
                                    center = Offset(cx, cy),
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = strokeOuter * 0.35f,
                                    center = Offset(cx, cy),
                                )
                            }

                            val innerSweep = (270f * animatedDownloaded.coerceIn(0f, 1f)).coerceAtLeast(
                                if (stats.downloadedCount > 0) 8f else 0f,
                            )
                            if (innerSweep > 0f) {
                                drawArc(
                                    color = ElectricCyan.copy(alpha = 0.85f),
                                    startAngle = 135f,
                                    sweepAngle = innerSweep,
                                    useCenter = false,
                                    topLeft = Offset(padInner, padInner),
                                    size = innerSize,
                                    style = Stroke(width = strokeInner, cap = StrokeCap.Round),
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val pct = (stats.listenedFraction * 100f).roundToInt()
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "PLAYED",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Right column: Time Remaining + Total Time Playing Altogether
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TelemetryTimeBlock(
                            label = "TIME REMAINING (ALL FILES)",
                            value = formatDashboardDuration(stats.totalRemainingSeconds),
                            subtitle = "${(stats.totalEpisodes - stats.playedEpisodesCount).coerceAtLeast(0)} unplayed episodes",
                            accent = ElectricCyan,
                        )

                        TelemetryTimeBlock(
                            label = "TOTAL TIME PLAYING ALTOGETHER",
                            value = formatDashboardDuration(stats.totalDurationSeconds),
                            subtitle = "${formatDashboardDuration(stats.totalPlayedSeconds)} listened so far",
                            accent = ElectricBlue,
                        )
                    }
                }

                // Graphical frequency spectrum bar showing Played vs Remaining across all files
                FrequencySpectrumBar(
                    listenedFraction = stats.listenedFraction,
                    isLight = isLight,
                )
            }
        }

        // 2. Three Bento Metric Tiles: Subscriptions | Episodes | Downloaded
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricBentoTile(
                icon = Icons.Filled.Podcasts,
                label = "Subscriptions",
                value = stats.subscriptionCount.toString(),
                sublabel = if (stats.subscriptionCount == 1) "active show" else "active shows",
                progress = 1f,
                accent = ElectricBlue,
                modifier = Modifier.weight(1f),
            )
            MetricBentoTile(
                icon = Icons.Filled.GraphicEq,
                label = "Episodes",
                value = stats.totalEpisodes.toString(),
                sublabel = "${stats.inProgressCount} in progress",
                progress = stats.completedEpisodesFraction,
                accent = ElectricCyan,
                modifier = Modifier.weight(1f),
            )
            MetricBentoTile(
                icon = Icons.Filled.DownloadDone,
                label = "Downloaded",
                value = stats.downloadedCount.toString(),
                sublabel = "${(stats.downloadedFraction * 100).roundToInt()}% offline",
                progress = stats.downloadedFraction,
                accent = ElectricCyan,
                modifier = Modifier.weight(1f),
            )
        }

        // 3. Dedicated Graphical Time Cards side-by-side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TimeDetailCard(
                icon = Icons.Filled.HourglassBottom,
                title = "Remaining Time",
                primaryValue = formatDashboardDuration(stats.totalRemainingSeconds),
                caption = "Left across all files",
                fraction = (1f - stats.listenedFraction).coerceIn(0f, 1f),
                accent = ElectricCyan,
                modifier = Modifier.weight(1f),
            )
            TimeDetailCard(
                icon = Icons.Filled.Headphones,
                title = "Total Time Playing",
                primaryValue = formatDashboardDuration(stats.totalPlayedSeconds),
                caption = "of ${formatDashboardDuration(stats.totalDurationSeconds)} total",
                fraction = stats.listenedFraction,
                accent = ElectricBlue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TelemetryTimeBlock(
    label: String,
    value: String,
    subtitle: String,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Multi-bar waveform spectrum where lit bars represent listened proportion and
 * cool cyan/slate bars represent remaining audio across the library.
 */
@Composable
private fun FrequencySpectrumBar(
    listenedFraction: Float,
    isLight: Boolean,
) {
    val heights = listOf(
        0.38f, 0.62f, 0.85f, 0.52f, 0.95f, 0.70f, 0.44f, 0.88f,
        1.00f, 0.64f, 0.48f, 0.92f, 0.76f, 0.55f, 0.83f, 0.60f,
        0.96f, 0.50f, 0.74f, 0.88f, 0.45f, 0.68f, 0.80f, 0.42f,
    )
    val litCount = (heights.size * listenedFraction.coerceIn(0f, 1f)).roundToInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEachIndexed { index, ratio ->
            val isLit = index < litCount
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(ratio)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isLit) {
                            Brush.verticalGradient(listOf(ElectricCyan, ElectricBlue))
                        } else {
                            val idle = if (isLight) {
                                ElectricBlue.copy(alpha = 0.18f)
                            } else {
                                ElectricCyan.copy(alpha = 0.18f)
                            }
                            Brush.verticalGradient(listOf(idle, idle))
                        },
                    ),
            )
        }
    }
}

@Composable
private fun MetricBentoTile(
    icon: ImageVector,
    label: String,
    value: String,
    sublabel: String,
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, contentPadding = 12.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.06f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(ElectricBlue, ElectricCyan))),
                )
            }
        }
    }
}

@Composable
private fun TimeDetailCard(
    icon: ImageVector,
    title: String,
    primaryValue: String,
    caption: String,
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, contentPadding = 14.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(ElectricBlue, ElectricCyan))),
                )
            }
        }
    }
}

/**
 * Formats a total duration in seconds as `Xh Ym`, `Xd Yh`, or `Xm Ys` for the
 * Home telemetry dashboard.
 */
internal fun formatDashboardDuration(seconds: Long): String {
    if (seconds <= 0L) return "0m"
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    val secs = seconds % 60
    return when {
        hours >= 100 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h ${mins}m"
        totalMinutes > 0 -> "${totalMinutes}m ${secs}s"
        else -> "${secs}s"
    }
}

@Composable
private fun ContinueCard(
    entry: ContinueEntry,
    onPlay: () -> Unit,
    onOpenShow: () -> Unit,
) {
    GlassCard(onClick = onPlay, contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RemoteImage(
                url = entry.episode.artworkUrl,
                contentDescription = null,
                size = 54.dp,
                cornerRadius = 12.dp,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                         Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp),
                    ),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.showTitle.isNotBlank()) {
                    Text(
                        text = entry.showTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.episode.completionFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { entry.episode.completionFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }

            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.player_now_playing),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(
    onOpenLibrary: () -> Unit,
    onOpenDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.kani_mascot),
            contentDescription = stringResource(R.string.home_mascot_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(232.dp),
        )

        Spacer(Modifier.size(20.dp))

        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(8.dp))

        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(28.dp))

        Button(
            onClick = onOpenDiscover,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_cta_discover))
        }

        Spacer(Modifier.size(10.dp))

        OutlinedButton(
            onClick = onOpenLibrary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_cta_import))
        }
    }
}
