package com.lohe.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the active palette is the light one.
 *
 * Read from the theme rather than passed down. Every glass surface needs this
 * answer, and threading an `isLight` boolean through each call site is exactly
 * how `GlassCard` ended up hardcoding the dark values and going invisible on
 * paper white.
 */
@Composable
fun backgroundIsLight(): Boolean = MaterialTheme.colorScheme.background.isLight()

/** Perceived lightness, ITU-R BT.601 weights. */
fun Color.isLight(): Boolean = red * 0.2126f + green * 0.7152f + blue * 0.0722f > 0.5f

/**
 * Lohe's signature surface: a frosted panel that lets episode artwork behind
 * it bleed through, producing a gradient unique to every show.
 *
 * Recipe (see BRAND.md):
 *   1. translucent fill
 *   2. backdrop blur
 *   3. hairline light catch on the top edge
 *
 * On API < 31 [Modifier.blur] is a no-op, so the surface degrades to a
 * translucent panel rather than disappearing. That is acceptable — the
 * artwork bleed still carries the colour.
 */
@Composable
fun Modifier.glassPanel(
    isLight: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    strong: Boolean = false,
    blurRadius: Dp = 24.dp,
): Modifier {
    val fill = when {
        isLight && strong -> Color.White.copy(alpha = 0.60f)  // Slightly reduced opacity for strong light mode
        isLight -> LoheAccent.glassFillLight
        strong -> LoheAccent.glassFillStrong
        else -> LoheAccent.glassFill
    }
    val edge = if (isLight) LoheAccent.glassBorderLight else LoheAccent.glassBorder

    return this
        .clip(shape)
        .background(fill)
        .blur(blurRadius)
        // Brand recipe: hairline light catch on the TOP edge only.
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(edge, Color.Transparent),
            ),
            shape = shape,
        )
}

/**
 * The artwork "bleed": a soft radial wash derived from the current episode's
 * dominant colour, painted under the glass so the panel appears lit from the
 * artwork rather than sitting on top of it.
 */
@Composable
fun Modifier.artworkBleed(
    tint: Color,
    centerX: Float = 0.5f,
    centerY: Float = 0.34f,
    radiusFraction: Float = 0.85f,
): Modifier = this.background(
    Brush.radialGradient(
        colors = listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0f)),
        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
        radius = radiusFraction * 1000f,
    ),
)