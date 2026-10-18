package com.lohe.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The list-row surface used across the library, queue, and search results.
 *
 * Material3's [androidx.compose.material3.Card] defaults to an opaque
 * `surfaceContainer` fill, which flattens the screen: rows read as slabs and
 * the accent has nothing to sit against. This is the brand recipe instead —
 * translucent fill, a hairline light catch along the top edge, and an optional
 * artwork-derived wash so a row carries a trace of its show's colour.
 *
 * @param accent tint sampled from the show's artwork, or null for neutral rows.
 * @param onClick when non-null the row becomes interactive and takes a ripple.
 */
@Composable
fun GlassCard(
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )
        .background(LoheAccent.glassFill)
        // The wash sits under the fill so it reads as light coming from the
        // artwork rather than as a tint painted on top of the row.
        .then(
            if (accent != null) {
                Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.22f),
                            accent.copy(alpha = 0.04f),
                        ),
                    ),
                )
            } else {
                Modifier
            },
        )
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                listOf(LoheAccent.glassBorder, Color.Transparent),
            ),
            shape = shape,
        )

    Box(
        modifier = modifier.then(base).padding(contentPadding),
        content = content,
    )
}

/** Row variant: a horizontal [GlassCard]. */
@Composable
fun GlassRow(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: Dp = 14.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(LoheAccent.glassFill)
            .then(
                if (accent != null) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.04f)),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(LoheAccent.glassBorder, Color.Transparent),
                ),
                shape = shape,
            )
            .padding(contentPadding),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}
