package com.lohe.app.ui.image

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lohe.app.data.ServiceLocator
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.ElectricCyan

/**
 * Remote artwork with a branded placeholder underneath.
 *
 * The placeholder is always drawn, so a slow or missing image never leaves a
 * hole in a list; the bitmap fades in over it once loaded. When
 * [fallbackLabel] is given, its first letter is drawn on the placeholder, so
 * a show with no artwork of its own is still told apart from its neighbours.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    cornerRadius: Dp = 12.dp,
    fallbackLabel: String? = null,
) {
    val loader = remember { ServiceLocator.artworkLoader() }
    var bitmap by remember(url) { mutableStateOf(loader.cached(url.orEmpty())) }

    LaunchedEffect(url) {
        val target = url?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        // Don't re-decode what is already in memory.
        if (bitmap != null) return@LaunchedEffect
        bitmap = loader.load(target)
    }

    val fade by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        label = "artwork-fade",
    )

    Box(
        modifier = modifier
            .then(size?.let { Modifier.size(it) } ?: Modifier)
            .background(
                Brush.linearGradient(
                    listOf(
                        ElectricBlue.copy(alpha = 0.35f),
                        ElectricCyan.copy(alpha = 0.22f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            val initial = fallbackLabel
                ?.trim()
                ?.firstOrNull()
                ?.uppercase()
            if (initial != null) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
        bitmap?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(fade),
            )
        }
    }
}
