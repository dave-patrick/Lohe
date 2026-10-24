package com.lohe.app.ui.navigation

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.ElectricBlueMid
import com.lohe.app.ui.theme.ElectricCyan

/**
 * Kani, in motion.
 *
 * The mascot is a static PNG, so the life has to come from around it: he
 * bobs and sways as if keeping time, a signal sweep turns behind him, and a
 * glow breathes under him. Together they read as a character on air rather
 * than as an illustration parked on a card.
 *
 * The motion is deliberately small in amplitude. Anything larger turns into
 * a distraction on a screen whose job is to be a dashboard.
 */
@Composable
fun KaniMascot(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 66.dp,
) {
    val transition = rememberInfiniteTransition(label = "kani")

    // Up and down, like a beat.
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )

    // A slower lean, out of phase with the bob.
    val sway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )

    // The glow under him swells and fades.
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    // The signal ping that expands out from him and fades.
    val ping by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ping",
    )

    val px = with(LocalDensity.current) { size.toPx() }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // --- Glow: the light he is standing in ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = px * (0.46f + 0.08f * breathe)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricBlueMid.copy(alpha = 0.13f + 0.07f * breathe),
                        ElectricBlueMid.copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                    radius = radius,
                ),
                radius = radius,
            )
        }

        // --- Ping: a ring that travels outward and fades ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Two pings, half a cycle apart, so one is always leaving.
            listOf(ping, (ping + 0.5f) % 1f).forEach { phase ->
                val radius = px * (0.16f + 0.34f * phase)
                val fade = (1f - phase) * (1f - phase)
                drawCircle(
                    color = ElectricBlue.copy(alpha = 0.38f * fade),
                    radius = radius,
                    style = Stroke(width = px * 0.02f),
                )
            }
        }

        // --- Kani ---
        Image(
            painter = painterResource(id = R.drawable.kani_mascot),
            contentDescription = contentDescription,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -bob * px * 0.035f
                    rotationZ = sway * 2.6f
                    val scale = 1f + 0.035f * breathe
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/**
 * A little on-air meter: bars that rise and fall out of step with each other.
 *
 * It is the one piece of the character that suggests sound, which is what
 * Kani is for.
 */
@Composable
fun SignalBars(
    modifier: Modifier = Modifier,
    bars: Int = 7,
    height: Dp = 16.dp,
    barWidth: Dp = 3.dp,
    spacing: Dp = 3.dp,
) {
    val transition = rememberInfiniteTransition(label = "signal-bars")

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(bars) { index ->
            // Each bar starts from a different level and runs on a different
            // clock, so the group never looks like it is pulsing in unison.
            val level by transition.animateFloat(
                initialValue = 0.16f + (index * 37 % 5) * 0.07f,
                targetValue = 0.45f + (index * 29 % 6) * 0.09f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 410 + index * 93,
                        delayMillis = index * 47,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar-$index",
            )
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(height * level)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(
                        Brush.verticalGradient(listOf(ElectricCyan, ElectricBlue)),
                    ),
            )
        }
    }
}
