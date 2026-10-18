package com.lohe.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lohe.app.R
import kotlinx.coroutines.delay

/**
 * Branded splash screen using the supplied brand artwork.
 *
 * The artwork already contains the full brand composition (Kani mascot,
 * "Lohe" wordmark, tagline, credits), so this simply displays it
 * full-bleed for 5 seconds before handing off to the app.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Fade in.
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        )
        // Hold so the brand registers — 5 seconds total.
        delay(4300)
        // Fade out.
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300),
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_screen),
            contentDescription = "Lohe splash screen",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha.value),
        )
    }
}