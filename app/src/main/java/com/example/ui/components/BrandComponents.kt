package com.example.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.LoheUserSettings
import com.example.data.local.QueueIndicatorStyle
import com.example.edition.Edition
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheBrightBlueText
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark
import com.example.ui.theme.LoheTextMutedDark
import com.example.ui.theme.LoheTextPrimaryDark
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.parseHexColorOrDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun rememberAnimatedWebpImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

fun formatDurationCompactHuman(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val mins = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${mins}m"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

/**
 * Podcast Cover Artwork with Coil AsyncImage + vibrant custom fallback when URL is blank.
 */
@Composable
fun PodcastArtworkThumbnail(
    artworkUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp
) {
    val palette = remember(title) {
        val hash = kotlin.math.abs(title.hashCode())
        when (hash % 4) {
            0 -> listOf(Color(0xFF0A4DA2), Color(0xFF008EFF))
            1 -> listOf(Color(0xFF0B525B), Color(0xFF00B4D8))
            2 -> listOf(Color(0xFF1D3557), Color(0xFF457B9D))
            else -> listOf(Color(0xFF283618), Color(0xFF0077B6))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center
    ) {
        if (artworkUrl.isNotBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = title,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title.split(" ").filter { it.isNotBlank() }.take(2)
                        .joinToString("") { it.take(1).uppercase() }.ifBlank { "LH" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Top Home Header:
 * - Centered glowing waveform LOHE wordmark + v0.11.1 tucked underneath
 * - Hovering Kani dragonfly mascot to the left of the wordmark
 * - Single top-right Settings gear
 */
@Composable
fun HomeBrandingHeader(
    reducedMotion: Boolean,
    onOpenSettings: () -> Unit,
    onKaniClick: () -> Unit = {},
    showKani: Boolean = true,
    showStaticKani: Boolean = reducedMotion,
    onKaniSlotPositioned: (LayoutCoordinates) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "home_wordmark_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wordmark_scale"
    )
    val hoverY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kani_hover_y"
    )
    val hoverX by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(4800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kani_hover_x"
    )
    val hoverTilt by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kani_hover_tilt"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
            .testTag("home_branding_row")
    ) {
        val wordmarkWidth = (maxWidth - 112.dp).coerceIn(120.dp, 232.dp)
        val wordmarkHeight = wordmarkWidth / 3.16f
        val leftOpenSpaceWidth = ((maxWidth - wordmarkWidth) / 2f).coerceAtLeast(52.dp)
        val kaniSlotSize = minOf(leftOpenSpaceWidth, wordmarkHeight).coerceAtLeast(52.dp)
        val kaniArtworkSize = (kaniSlotSize - 2.dp).coerceAtLeast(48.dp)

        if (showKani) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(leftOpenSpaceWidth),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(kaniSlotSize)
                        .onGloballyPositioned(onKaniSlotPositioned)
                        .clickable(onClick = onKaniClick)
                        .testTag("home_kani_mascot"),
                    contentAlignment = Alignment.Center
                ) {
                    if (showStaticKani) {
                        Image(
                            painter = painterResource(id = R.drawable.kani),
                            contentDescription = "Kani dragonfly mascot",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(kaniArtworkSize)
                                .offset(
                                    x = if (reducedMotion) 0.dp else hoverX.dp,
                                    y = if (reducedMotion) 0.dp else hoverY.dp
                                )
                                .rotate(if (reducedMotion) 0f else hoverTilt)
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .size(kaniArtworkSize)
                                .alpha(0.01f)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(wordmarkWidth),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.lohe_wordmark_transparent),
                contentDescription = "LOHE wordmark",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3.16f)
                    .scale(if (reducedMotion) 1f else pulseScale)
                    .testTag(if (reducedMotion) "home_lohe_wordmark_static" else "home_lohe_wordmark_animated"),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )

            Text(
                text = stringResource(id = R.string.version_display),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
                    .testTag("home_version_text")
            )
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp)
                .testTag("top_settings_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(id = R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Unclipped Home overlay rendering home-kani-flight.webp once along Kani's 3D flight path:
 * - Starts sized cleanly inside the left header space beside the wordmark
 * - Expands dramatically to fill much of the page (~86% width) as Kani flies toward the camera
 * - Returns to the left header space and swells to fill that space before the single playback ends
 */
@Composable
fun HomeFlyingKaniOverlay(
    restCenterPx: Offset,
    restSlotSizePx: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    reducedMotion: Boolean,
    onDrawableReady: (Boolean) -> Unit,
    onFlightFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val kaniDrawable by produceState<Drawable?>(initialValue = null, key1 = context, key2 = reducedMotion) {
        value = if (!reducedMotion && Build.VERSION.SDK_INT >= 28) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(context.assets, "home-kani-flight.webp")
                    ImageDecoder.decodeDrawable(source)
                }.getOrNull()
            }
        } else {
            null
        }
    }

    LaunchedEffect(kaniDrawable) {
        onDrawableReady(kaniDrawable != null)
    }

    val activeDrawable = kaniDrawable ?: return
    if (reducedMotion || containerWidthPx <= 0f || containerHeightPx <= 0f) return

    HomeFlyingKaniStage(
        activeDrawable = activeDrawable,
        restCenterPx = restCenterPx,
        restSlotSizePx = restSlotSizePx,
        containerWidthPx = containerWidthPx,
        containerHeightPx = containerHeightPx,
        onFlightFinished = onFlightFinished,
        modifier = modifier
    )
}

@Composable
private fun HomeFlyingKaniStage(
    activeDrawable: Drawable,
    restCenterPx: Offset,
    restSlotSizePx: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    onFlightFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var frameTick by remember { mutableIntStateOf(0) }
    val flightProgress = remember { Animatable(0f) }
    val currentOnFlightFinished by rememberUpdatedState(onFlightFinished)

    DisposableEffect(activeDrawable) {
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                frameTick++
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                mainHandler.postAtTime(what, who, `when`)
            }

            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                mainHandler.removeCallbacks(what, who)
            }
        }
        activeDrawable.callback = callback
        onDispose {
            (activeDrawable as? AnimatedImageDrawable)?.stop()
            mainHandler.removeCallbacksAndMessages(activeDrawable)
            activeDrawable.callback = null
        }
    }

    LaunchedEffect(activeDrawable) {
        flightProgress.snapTo(0f)
        (activeDrawable as? AnimatedImageDrawable)?.let { animDrawable ->
            animDrawable.stop()
            animDrawable.repeatCount = 0
            animDrawable.start()
        }
        flightProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 10376, easing = LinearEasing)
        )
        currentOnFlightFinished()
    }

    val p = flightProgress.value
    val slotPx = restSlotSizePx.coerceAtLeast(with(density) { 56.dp.toPx() })
    val startCanvasPx = slotPx * 2.10f
    val midReturnCanvasPx = slotPx * 1.45f
    val endCanvasPx = slotPx * 2.25f
    val peakCanvasPx = minOf(containerWidthPx * 0.86f, containerHeightPx * 0.56f).coerceAtLeast(endCanvasPx)

    val peakCenterX = containerWidthPx * 0.50f
    val peakCenterY = containerHeightPx * 0.39f

    val expandWeight = when {
        p < 0.11f -> 0f
        p < 0.25f -> FastOutSlowInEasing.transform(((p - 0.11f) / 0.14f).coerceIn(0f, 1f))
        p < 0.40f -> 1f
        p < 0.56f -> 1f - FastOutSlowInEasing.transform(((p - 0.40f) / 0.16f).coerceIn(0f, 1f))
        else -> 0f
    }

    val currentSizePx = when {
        p < 0.11f -> startCanvasPx
        p < 0.40f -> startCanvasPx + (peakCanvasPx - startCanvasPx) * expandWeight
        p < 0.56f -> midReturnCanvasPx + (peakCanvasPx - midReturnCanvasPx) * expandWeight
        p < 0.84f -> {
            val settleFrac = FastOutSlowInEasing.transform(((p - 0.56f) / 0.28f).coerceIn(0f, 1f))
            midReturnCanvasPx + (endCanvasPx - midReturnCanvasPx) * settleFrac
        }
        else -> endCanvasPx
    }

    val currentCenterX = restCenterPx.x + (peakCenterX - restCenterPx.x) * expandWeight
    val currentCenterY = restCenterPx.y + (peakCenterY - restCenterPx.y) * expandWeight

    val currentSizeDp = with(density) { currentSizePx.toDp() }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (currentCenterX - currentSizePx / 2f).roundToInt(),
                    y = (currentCenterY - currentSizePx / 2f).roundToInt()
                )
            }
            .size(currentSizeDp)
            .drawBehind {
                @Suppress("UNUSED_VARIABLE")
                val redrawTrigger = frameTick
                drawIntoCanvas { canvas ->
                    activeDrawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                    activeDrawable.draw(canvas.nativeCanvas)
                }
            }
            .testTag("home_kani_flight_overlay")
    )
}

/**
 * YOUR SIGNAL card:
 * - Header with YOUR SIGNAL + episode count left and clickable in-progress episodes pill right
 * - Concentric double-arc circular gauge showing % PLAYED
 * - TIME PLAYED, TIME SAVED, STILL TO HEAR
 * - Multi-bar equalizer progress strip at bottom
 */
@Composable
fun YourSignalCard(
    playedPercent: Int,
    playedCount: Int,
    totalEpisodesCount: Int,
    inProgressCount: Int,
    timePlayedMs: Long,
    totalDurationMs: Long,
    timeSavedMs: Long,
    stillToHearMs: Long,
    waitingEpisodesCount: Int,
    onInProgressClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardColor = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
    val trackColor = if (isDark) Color(0xFF282D37) else Color(0xFFE2E6EE)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .testTag("your_signal_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row with YOUR SIGNAL + In-progress badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LoheElectricBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "YOUR SIGNAL",
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 1.6.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = LoheBrightBlueText
                        )
                    }
                    Text(
                        text = "$playedCount of $totalEpisodesCount episodes",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoheElectricBlue.copy(alpha = 0.14f))
                        .border(1.dp, LoheElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .clickable(onClick = onInProgressClick)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("your_signal_in_progress_pill")
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(LoheCyanAccent)
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) {
                                append("$inProgressCount")
                            }
                            append(
                                if (inProgressCount == 1) " episode in progress"
                                else " episodes in progress"
                            )
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) LoheCyanAccent else LoheBrightBlueText,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Middle: Concentric Double-Arc Gauge + 3 stacked metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(134.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerRadius = size.minDimension * 0.43f
                        val innerRadius = size.minDimension * 0.32f
                        val outerStroke = 10.dp.toPx()
                        val innerStroke = 7.dp.toPx()

                        // Background arcs (270 degrees C-shape opening on left/bottom-left or top-bottom)
                        val startAngle = 140f
                        val sweepMax = 260f

                        drawArc(
                            color = trackColor,
                            startAngle = startAngle,
                            sweepAngle = sweepMax,
                            useCenter = false,
                            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                            size = Size(outerRadius * 2f, outerRadius * 2f),
                            style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                        )

                        drawArc(
                            color = trackColor.copy(alpha = 0.75f),
                            startAngle = startAngle,
                            sweepAngle = sweepMax,
                            useCenter = false,
                            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                            size = Size(innerRadius * 2f, innerRadius * 2f),
                            style = Stroke(width = innerStroke, cap = StrokeCap.Round)
                        )

                        val frac = (playedPercent.coerceIn(2, 100) / 100f)
                        val activeSweep = sweepMax * frac

                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(LoheElectricBlue, LoheCyanAccent, LoheElectricBlue)
                            ),
                            startAngle = startAngle,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                            size = Size(outerRadius * 2f, outerRadius * 2f),
                            style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                        )

                        val innerFrac = (frac * 1.15f).coerceIn(0.05f, 1f)
                        drawArc(
                            color = LoheCyanAccent,
                            startAngle = startAngle,
                            sweepAngle = sweepMax * innerFrac,
                            useCenter = false,
                            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                            size = Size(innerRadius * 2f, innerRadius * 2f),
                            style = Stroke(width = innerStroke, cap = StrokeCap.Round)
                        )

                        // Glowing tip dot at end of outer arc
                        val tipAngleRad = ((startAngle + activeSweep) * PI / 180.0)
                        val tipX = center.x + outerRadius * cos(tipAngleRad).toFloat()
                        val tipY = center.y + outerRadius * sin(tipAngleRad).toFloat()
                        drawCircle(
                            color = LoheCyanAccent.copy(alpha = 0.45f),
                            radius = 7.dp.toPx(),
                            center = Offset(tipX, tipY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.2.dp.toPx(),
                            center = Offset(tipX, tipY)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$playedPercent%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "PLAYED",
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SignalStatRow(
                        label = "TIME PLAYED",
                        value = formatDurationCompactHuman(timePlayedMs),
                        subtext = "of ${formatDurationCompactHuman(totalDurationMs)}"
                    )
                    SignalStatRow(
                        label = "TIME SAVED",
                        value = formatDurationCompactHuman(timeSavedMs),
                        subtext = "playing faster & skipping silence"
                    )
                    SignalStatRow(
                        label = "STILL TO HEAR",
                        value = formatDurationCompactHuman(stillToHearMs),
                        subtext = "$waitingEpisodesCount episodes waiting"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-bar equalizer progress strip
            val heights = listOf(
                8, 12, 16, 11, 14, 9, 17, 13, 10, 15,
                19, 12, 14, 9, 16, 11, 14, 18, 12, 10, 15, 11
            )
            val filledBars = ((playedPercent / 100f) * heights.size).toInt().coerceIn(1, heights.size)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                heights.forEachIndexed { idx, h ->
                    val active = idx < filledBars
                    Box(
                        modifier = Modifier
                            .width(7.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (active) LoheElectricBlue else trackColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalStatRow(
    label: String,
    value: String,
    subtext: String
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(LoheElectricBlue)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtext,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * LISTENING ACTIVITY 14-day bar chart matching compact-nav81-ready_5458b234.png
 */
@Composable
fun ListeningActivity14DayCard(
    dailyMinutes: List<Int>,
    totalTimeSavedMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardColor = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
    val maxMins = (dailyMinutes.maxOrNull() ?: 30).coerceAtLeast(10)
    val totalMins = dailyMinutes.sum()
    val avgMins = if (dailyMinutes.isNotEmpty()) totalMins / dailyMinutes.size else 0
    val streakDays = remember(dailyMinutes) {
        var streak = 0
        for (i in dailyMinutes.lastIndex downTo 0) {
            if (dailyMinutes[i] > 0) streak++ else break
        }
        streak
    }
    val savedMins = (totalTimeSavedMs / 60_000L).coerceAtLeast(0L)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .testTag("listening_activity_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LISTENING ACTIVITY",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${totalMins}m total • $streakDays-day streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = LoheCyanAccent
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                dailyMinutes.forEach { mins ->
                    val frac = (mins.toFloat() / maxMins.toFloat()).coerceIn(0.08f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "$mins",
                            fontSize = 8.sp,
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((58f * frac).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(LoheCyanAccent, LoheElectricBlue)
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Avg ${avgMins}m/day",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Best ${dailyMinutes.maxOrNull() ?: 0}m",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${savedMins}m saved",
                    style = MaterialTheme.typography.labelMedium,
                    color = LoheElectricBlue
                )
            }
        }
    }
}

/**
 * Standard compact top header for Podcasts, Playlists, Up Next, and Discover tabs.
 * Contains ONLY a single top-right Settings button (never duplicate settings gears).
 */
@Composable
fun PrimaryTabTopBar(
    title: String,
    subtitle: String? = null,
    badgeCount: Int? = null,
    trailingActions: @Composable () -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeCount != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF153456))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$badgeCount",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoheBrightBlueText
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingActions()
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("top_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(id = R.string.settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * About screen branding header (inside Settings -> About):
 * - Displays about-kani-animation.webp seamlessly on the #121214 background
 *   (no box background, no CSS/elevation shadow, fallback to official_app_icon.png on reduced motion)
 * - Larger LOHE wordmark tucked underneath
 * - 9sp version text ("Version 0.11.1 • build 2026.12.11.110")
 * - Tagline "ua lohe ‘oe? • Have you heard?" centered in a 24dp line box.
 */
@Composable
fun AboutKaniHeroSection(
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = rememberAnimatedWebpImageLoader()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LoheBackgroundDark)
            .padding(top = 0.dp, bottom = 6.dp)
            .testTag("about_kani_hero"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (reducedMotion) {
            Image(
                painter = painterResource(id = R.drawable.official_app_icon),
                contentDescription = "Lohe official app icon",
                modifier = Modifier
                    .size(256.dp)
                    .testTag("about_kani_static_fallback"),
                contentScale = ContentScale.Fit
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/about-kani-animation.webp")
                    .build(),
                imageLoader = imageLoader,
                placeholder = painterResource(id = R.drawable.official_app_icon),
                error = painterResource(id = R.drawable.official_app_icon),
                contentDescription = "Animated Kani dragonfly mascot",
                modifier = Modifier
                    .size(256.dp)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.radialGradient(
                                0.72f to Color.Black,
                                1.0f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    .testTag("about_kani_animation_webp"),
                contentScale = ContentScale.Fit
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 264.dp)
                .fillMaxWidth()
                .offset(y = (-10).dp),
            contentAlignment = Alignment.Center
        ) {
            if (reducedMotion) {
                Image(
                    painter = painterResource(id = R.drawable.lohe_wordmark_transparent),
                    contentDescription = "LOHE wordmark",
                    modifier = Modifier
                        .widthIn(max = 264.dp)
                        .fillMaxWidth()
                        .aspectRatio(4.8f)
                        .testTag("about_lohe_wordmark"),
                    contentScale = ContentScale.Fit
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/about-wordmark-animation.webp")
                        .build(),
                    imageLoader = imageLoader,
                    placeholder = painterResource(id = R.drawable.lohe_wordmark_transparent),
                    error = painterResource(id = R.drawable.lohe_wordmark_transparent),
                    contentDescription = "Animated LOHE wordmark",
                    modifier = Modifier
                        .widthIn(max = 264.dp)
                        .fillMaxWidth()
                        .aspectRatio(4.8f)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0.0f to Color.Transparent,
                                    0.08f to Color.Black,
                                    0.92f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                        .testTag("about_lohe_wordmark"),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                text = stringResource(id = R.string.about_version_display),
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Medium,
                color = LoheTextMutedDark,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 0.dp)
                    .testTag("about_version_9sp")
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        val taglineAnnotated = remember {
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                    append("ua lohe ‘oe?")
                }
                withStyle(SpanStyle(color = LoheTextMutedDark, fontWeight = FontWeight.Medium)) {
                    append(" • ")
                }
                withStyle(SpanStyle(color = LoheBrightBlueText, fontWeight = FontWeight.Bold)) {
                    append("Have you heard?")
                }
            }
        }
        val baseTaglineStyle = MaterialTheme.typography.titleMedium.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold
        )
        val textMeasurer = rememberTextMeasurer()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("about_tagline_box"),
            contentAlignment = Alignment.Center
        ) {
            val fittedFontSize = remember(constraints.maxWidth, taglineAnnotated) {
                var sizeSp = 30f
                while (sizeSp > 12f) {
                    val measuredWidth = textMeasurer.measure(
                        text = taglineAnnotated,
                        style = baseTaglineStyle.copy(fontSize = sizeSp.sp),
                        maxLines = 1,
                        softWrap = false
                    ).size.width
                    if (measuredWidth <= constraints.maxWidth) break
                    sizeSp -= 0.5f
                }
                sizeSp.sp
            }
            Text(
                text = taglineAnnotated,
                style = baseTaglineStyle.copy(fontSize = fittedFontSize),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Renders the Up Next queue indicator according to user settings:
 * - Master toggle (`upNextIndicatorEnabled`)
 * - Free styles: Dot, Ring, Queue icon + preset colors + Small/Medium/Large size
 * - Pro styles: Pill, Diamond, Glow + custom hex color
 */
@Composable
fun QueueIndicatorBadge(
    settings: LoheUserSettings,
    queuePosition: Int? = null,
    sizeOverride: Dp? = null,
    modifier: Modifier = Modifier
) {
    if (!settings.upNextIndicatorEnabled) return

    val size = sizeOverride ?: settings.queueIndicatorSize.sizeDp.dp
    val activeColor: Color = if (Edition.HAS_PRO && settings.useCustomHexColor && settings.isProUnlocked) {
        parseHexColorOrDefault(settings.customHexColor, LoheElectricBlue)
    } else {
        settings.queuePresetColor.color
    }
    val effectiveStyle = if (!Edition.HAS_PRO && settings.queueIndicatorStyle.isPro) {
        QueueIndicatorStyle.DOT
    } else {
        settings.queueIndicatorStyle
    }

    when (effectiveStyle) {
        QueueIndicatorStyle.DOT -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(activeColor)
                    .testTag("queue_indicator_dot")
            )
        }

        QueueIndicatorStyle.RING -> {
            Canvas(
                modifier = modifier
                    .size(size)
                    .testTag("queue_indicator_ring")
            ) {
                drawCircle(
                    color = activeColor,
                    style = Stroke(width = (size.toPx() * 0.24f).coerceAtLeast(1.8.dp.toPx()))
                )
            }
        }

        QueueIndicatorStyle.ICON -> {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Queued in Up Next",
                tint = activeColor,
                modifier = modifier
                    .size(size + 4.dp)
                    .testTag("queue_indicator_icon")
            )
        }

        QueueIndicatorStyle.PILL -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(50))
                    .background(activeColor.copy(alpha = 0.18f))
                    .border(1.dp, activeColor, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("queue_indicator_pill"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (queuePosition != null && queuePosition > 0) "NEXT #$queuePosition" else "QUEUED",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = activeColor
                )
            }
        }

        QueueIndicatorStyle.DIAMOND -> {
            Box(
                modifier = modifier
                    .size(size)
                    .rotate(45f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(activeColor)
                    .testTag("queue_indicator_diamond")
            )
        }

        QueueIndicatorStyle.GLOW -> {
            Canvas(
                modifier = modifier
                    .size(size + 6.dp)
                    .testTag("queue_indicator_glow")
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(activeColor, activeColor.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
                drawCircle(
                    color = LoheCyanAccent,
                    radius = this.size.minDimension * 0.26f
                )
            }
        }
    }
}
