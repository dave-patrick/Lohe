package com.lohe.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.RssFeed
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lohe.app.R
import com.lohe.app.data.ContinueEntry
import com.lohe.app.data.HomeDashboardStats
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.ShowActivity
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.Appearance
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.ElectricBlueMid
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
    onOpenSubscriptions: () -> Unit,
    onOpenPlaylists: () -> Unit,
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
                // The band around the wordmark and the settings control. The
                // padding below them is what centres them in the header
                // instead of leaving them sitting on its bottom edge.
                .padding(top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLight) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                // The wordmark artwork carries waveform spikes above and below
                // the word, so the letters themselves sit below the centre of
                // the image. The image is nudged so the word lands on the
                // same centreline as the settings control beside it.
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.wordmark),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = (-3.4).dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // A filled round control rather than a bare icon. IconButton
            // reserves a 48dp touch target around a 20dp glyph, so the gear
            // read as a small dot in a large empty box and sat oddly beside
            // the wordmark. This is sized to be a deliberate peer of it.
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        if (!stats.isLoaded) {
            // The library has not answered yet. Show nothing rather than the
            // empty state: a flash of "no podcasts yet" before the real
            // dashboard arrives reads as the app having lost the library, and
            // the old Kani hero flashing past is the loudest version of that.
            Spacer(Modifier.fillMaxSize())
            return@Column
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
                KaniHero(
                    stats = stats,
                    onOpenDiscover = onOpenDiscover,
                    onOpenLibrary = onOpenLibrary,
                )
            }
            item {
                HomeTelemetryDashboard(
                    stats = stats,
                    isLight = isLight,
                    onOpenLibrary = onOpenLibrary,
                    onOpenSubscriptions = onOpenSubscriptions,
                    onOpenPlaylists = onOpenPlaylists,
                )
            }
        }
    }
}

@Composable
private fun HomeTelemetryDashboard(
    stats: HomeDashboardStats,
    isLight: Boolean,
    onOpenLibrary: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenPlaylists: () -> Unit,
) {
    val animatedListened by animateFloatAsState(
        targetValue = stats.listenedFraction,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "listened-arc",
    )
    val animatedDownloaded by animateFloatAsState(
        targetValue = stats.downloadedFraction,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "downloaded-arc",
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // --- 1. Hero: the listening signal, as one composed instrument ---
        GlassCard(onClick = onOpenLibrary, contentPadding = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(ElectricBlueMid, ElectricBlue)),
                                ),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = "YOUR SIGNAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                            color = if (isLight) ElectricBlue else ElectricBlueMid,
                        )
                    }
                    Text(
                        text = "${stats.playedEpisodesCount} of ${stats.totalEpisodes} episodes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SignalGauge(
                        listenedFraction = animatedListened,
                        downloadedFraction = animatedDownloaded,
                        isLight = isLight,
                        modifier = Modifier.size(148.dp),
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SignalReadout(
                            label = "TIME PLAYED",
                            value = formatDashboardDuration(stats.totalPlayedSeconds),
                            caption = "of ${formatDashboardDuration(stats.totalDurationSeconds)}",
                            accent = ElectricBlue,
                        )
                        SignalReadout(
                            label = "STILL TO HEAR",
                            value = formatDashboardDuration(stats.totalRemainingSeconds),
                            caption = "${(stats.totalEpisodes - stats.playedEpisodesCount).coerceAtLeast(0)} episodes waiting",
                            accent = ElectricBlueMid,
                        )
                    }
                }

                FrequencySpectrumBar(
                    listenedFraction = stats.listenedFraction,
                    isLight = isLight,
                )
            }
        }

        // --- 2. Listening activity: when the hours actually went ---
        if (stats.dailyActivityMinutes.any { it > 0 }) {
            GlassCard(contentPadding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "LISTENING ACTIVITY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "last 14 days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    ActivityChart(
                        minutes = stats.dailyActivityMinutes,
                        isLight = isLight,
                    )
                }
            }
        }

        // --- 3. The shape of the library ---
        if (stats.topShows.isNotEmpty()) {
            GlassCard(contentPadding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "MOST EPISODES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    TopShowsChart(
                        shows = stats.topShows,
                        isLight = isLight,
                    )
                }
            }
        }

        // --- 4. The counts, as one card with four figures ---
        LibraryStatsCard(
            stats = stats,
            onOpenSubscriptions = onOpenSubscriptions,
            onOpenPlaylists = onOpenPlaylists,
        )

        // --- 5. Time, side by side ---
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
                accent = ElectricBlueMid,
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

/**
 * The hero instrument: two concentric rings for listened and downloaded, with
 * a glowing tip on the outer arc and a soft halo behind it.
 */
@Composable
private fun SignalGauge(
    listenedFraction: Float,
    downloadedFraction: Float,
    isLight: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val trackColor = if (isLight) {
            Color.Black.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.10f)
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeOuter = 12.dp.toPx()
            val strokeInner = 7.dp.toPx()
            val padOuter = strokeOuter / 2f + 4.dp.toPx()
            val padInner = padOuter + strokeOuter + 6.dp.toPx()

            val outerSize = Size(size.width - padOuter * 2, size.height - padOuter * 2)
            val innerSize = Size(size.width - padInner * 2, size.height - padInner * 2)

            // Soft halo behind the whole instrument.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ElectricBlueMid.copy(alpha = 0.16f), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
            )

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(padOuter, padOuter),
                size = outerSize,
                style = Stroke(width = strokeOuter, cap = StrokeCap.Round),
            )
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(padInner, padInner),
                size = innerSize,
                style = Stroke(width = strokeInner, cap = StrokeCap.Round),
            )

            val outerSweep = (270f * listenedFraction.coerceIn(0f, 1f)).coerceAtLeast(
                if (listenedFraction > 0f) 10f else 0f,
            )
            if (outerSweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(ElectricBlue, ElectricBlueMid, ElectricBlue)),
                    startAngle = 135f,
                    sweepAngle = outerSweep,
                    useCenter = false,
                    topLeft = Offset(padOuter, padOuter),
                    size = outerSize,
                    style = Stroke(width = strokeOuter, cap = StrokeCap.Round),
                )
                val angleRad = Math.toRadians((135f + outerSweep).toDouble())
                val radius = outerSize.width / 2f
                val cx = size.width / 2f + (radius * cos(angleRad)).toFloat()
                val cy = size.height / 2f + (radius * sin(angleRad)).toFloat()
                drawCircle(
                    color = ElectricBlueMid.copy(alpha = 0.35f),
                    radius = strokeOuter * 0.95f,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = Color.White,
                    radius = strokeOuter * 0.32f,
                    center = Offset(cx, cy),
                )
            }

            val innerSweep = (270f * downloadedFraction.coerceIn(0f, 1f)).coerceAtLeast(
                if (downloadedFraction > 0f) 10f else 0f,
            )
            if (innerSweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(ElectricBlueMid, ElectricBlue, ElectricBlueMid)),
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
            Text(
                text = "${(listenedFraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "PLAYED",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One labelled figure in the hero card. */
@Composable
private fun SignalReadout(
    label: String,
    value: String,
    caption: String,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Fourteen vertical bars, one per day, tallest scaled to the best day.
 */
@Composable
private fun ActivityChart(
    minutes: List<Int>,
    isLight: Boolean,
) {
    val peak = (minutes.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        minutes.forEach { value ->
            val fraction = value.toFloat() / peak
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight((fraction.coerceIn(0.06f, 1f))),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (value > 0) {
                                Brush.verticalGradient(listOf(ElectricBlueMid, ElectricBlue))
                            } else {
                                val idle = if (isLight) {
                                    ElectricBlue.copy(alpha = 0.10f)
                                } else {
                                    ElectricBlueMid.copy(alpha = 0.12f)
                                }
                                Brush.verticalGradient(listOf(idle, idle))
                            },
                        ),
                )
            }
        }
    }
}

/** Ranked bars for the shows carrying the most episodes. */
@Composable
private fun TopShowsChart(
    shows: List<ShowActivity>,
    isLight: Boolean,
) {
    val peak = shows.maxOfOrNull { it.episodeCount }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shows.forEach { show ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = show.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${show.episodeCount} ep",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isLight) {
                                ElectricBlue.copy(alpha = 0.08f)
                            } else {
                                ElectricBlueMid.copy(alpha = 0.10f)
                            },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(show.episodeCount.toFloat() / peak)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(ElectricBlue, ElectricBlueMid))),
                    )
                }
            }
        }
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
                            Brush.verticalGradient(listOf(ElectricBlueMid, ElectricBlue))
                        } else {
                            val idle = if (isLight) {
                                ElectricBlue.copy(alpha = 0.18f)
                            } else {
                                ElectricBlueMid.copy(alpha = 0.18f)
                            }
                            Brush.verticalGradient(listOf(idle, idle))
                        },
                    ),
            )
        }
    }
}

/**
 * The library at a glance.
 *
 * This went through three shapes. Four tiles in a row gave each tile a
 * quarter of the width, so every label truncated. A plain 2x2 grid fitted,
 * but read as a table: four numbers in boxes, flat and uninformative about
 * the shape of the library. The one after that was tidier but still a list
 * of figures with nothing pulling the eye anywhere.
 *
 * This one leads with the total, large and lit, and puts a single
 * proportioned bar under it showing how much of the library is played, in
 * progress and still waiting. The counts that can be acted on sit below in
 * badges. Everything counts up as it arrives, so the card settles rather
 * than snapping into place.
 */
@Composable
private fun LibraryStatsCard(
    stats: HomeDashboardStats,
    onOpenSubscriptions: () -> Unit,
    onOpenPlaylists: () -> Unit,
) {
    GlassCard(contentPadding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "YOUR LIBRARY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Episodes in your library",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // The total, lit from behind so it reads as the headline
                // rather than another figure in a row.
                Box(
                    modifier = Modifier.size(width = 150.dp, height = 62.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        ElectricBlueMid.copy(alpha = 0.22f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                    Text(
                        text = animatedCount(stats.totalEpisodes),
                        style = MaterialTheme.typography.displaySmall.merge(
                            TextStyle(
                                brush = Brush.horizontalGradient(
                                    listOf(ElectricBlue, ElectricBlueMid),
                                ),
                            ),
                        ),
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
            }

            LibraryCompositionBar(stats = stats)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibraryBadge(
                    icon = Icons.Filled.Podcasts,
                    label = "Subscriptions",
                    value = stats.subscriptionCount,
                    accent = ElectricBlue,
                    onClick = onOpenSubscriptions,
                    modifier = Modifier.weight(1f),
                )
                LibraryBadge(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Playlists",
                    value = stats.playlistCount,
                    accent = ElectricBlueMid,
                    onClick = onOpenPlaylists,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The library's composition, as one bar.
 *
 * Played, in progress and still waiting as three segments of the same
 * width, so the shape of the library is legible at a glance instead of by
 * reading three separate numbers against each other. The played segment
 * carries a lit edge, so the bar reads as filling up rather than as a
 * static scale.
 */
@Composable
private fun LibraryCompositionBar(stats: HomeDashboardStats) {
    val total = stats.totalEpisodes.coerceAtLeast(1).toFloat()
    val playedFraction = (stats.playedEpisodesCount / total).coerceIn(0f, 1f)
    // In progress can only occupy what played has not already taken.
    val progressFraction =
        (stats.inProgressCount / total).coerceIn(0f, 1f - playedFraction)
    val waiting = (
        stats.totalEpisodes - stats.playedEpisodesCount - stats.inProgressCount
        ).coerceAtLeast(0)

    val animatedPlayed by animateFloatAsState(
        targetValue = playedFraction,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "played-segment",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "progress-segment",
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Drawn rather than composed from weighted children. In a Row, a
        // child with fillMaxWidth(fraction) is measured against the space
        // left over by its siblings, so the second segment would be a
        // fraction of what is left rather than of the whole bar.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            val w = size.width
            val h = size.height

            drawRect(color = ElectricBlue.copy(alpha = 0.13f))

            drawRect(
                brush = Brush.horizontalGradient(listOf(ElectricBlueMid, ElectricBlue)),
                size = Size(w * animatedPlayed, h),
            )

            drawRect(
                color = ElectricBlueMid.copy(alpha = 0.45f),
                topLeft = Offset(w * animatedPlayed, 0f),
                size = Size(w * animatedProgress, h),
            )

            // A lit edge on the front of what has been played, so the bar
            // reads as filling rather than as a static scale.
            if (animatedPlayed > 0.01f) {
                val edge = w * animatedPlayed
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.9f), Color.Transparent),
                    ),
                    radius = h * 0.9f,
                    center = Offset(edge, h / 2),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LegendDot("Played", stats.playedEpisodesCount, ElectricBlue)
            LegendDot("In progress", stats.inProgressCount, ElectricBlueMid.copy(alpha = 0.5f))
            LegendDot("Waiting", waiting, ElectricBlue.copy(alpha = 0.25f))
        }
    }
}

/** A colour swatch and its count. */
@Composable
private fun LegendDot(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label ${compactCount(count)}",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * One count in a badge: a lit icon disc, the number, and what it counts.
 */
@Composable
private fun LibraryBadge(
    icon: ImageVector,
    label: String,
    value: Int,
    accent: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.38f), accent.copy(alpha = 0.14f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = animatedCount(value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A count that counts up to its value.
 *
 * The library arrives a moment after the screen does, and a number that
 * springs from zero to its final figure reads as a change rather than as a
 * number that was always there.
 */
@Composable
private fun animatedCount(value: Int): String {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "count-up",
    )
    return compactCount(animated)
}

/**
 * Formats a count compactly so a figure never has to be truncated.
 *
 * 37604 becomes "37.6k", which fits the width of a figure on any screen.
 */
internal fun compactCount(value: Int): String = when {
    value >= 1_000_000 -> String.format("%.1fm", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fk", value / 1_000.0)
    else -> value.toString()
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
                        .background(Brush.horizontalGradient(listOf(ElectricBlue, ElectricBlueMid))),
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
private fun KaniHero(
    stats: HomeDashboardStats,
    onOpenDiscover: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    // Kani, integrated rather than standing alone.
    //
    // A full-height mascot in its own band read as a placeholder and pushed
    // the dashboard off the fold. Here the mascot is small and sits beside a
    // live line about the library, so it reads as the character reporting in
    // rather than a picture parked above the content.
    val statusLine = when {
        stats.totalEpisodes == 0 -> stringResource(R.string.home_kani_body)
        stats.inProgressCount > 0 -> stringResource(
            R.string.home_kani_in_progress,
            stats.inProgressCount,
        )
        stats.playedEpisodesCount > 0 -> stringResource(
            R.string.home_kani_played,
            stats.playedEpisodesCount,
        )
        else -> stringResource(R.string.home_kani_body)
    }

    GlassCard(contentPadding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KaniMascot(
                contentDescription = stringResource(R.string.home_mascot_description),
                size = 92.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The meter leads the line: an on-air indicator belongs in
                    // front of the name, not floating off on its own.
                    SignalBars()
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = stringResource(R.string.home_kani_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onOpenDiscover,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.RssFeed,
                        contentDescription = stringResource(R.string.home_cta_discover),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onOpenLibrary,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = stringResource(R.string.home_cta_import),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
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
        // With no library to show, Kani can be as big as he likes: there is
        // nothing to compete with, and the empty state is exactly where a
        // character belongs.
        KaniMascot(
            contentDescription = stringResource(R.string.home_mascot_description),
            size = 176.dp,
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
