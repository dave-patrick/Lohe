package com.lohe.app.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    // Resolved from the theme, not a parameter. These two used to be the dark
    // values unconditionally, so on paper white the fill and the hairline were
    // both white-on-white and the row vanished into the page.
    val isLight = backgroundIsLight()
    val fill = if (isLight) LoheAccent.glassFillLight else LoheAccent.glassFill
    val edge = if (isLight) LoheAccent.glassBorderLight else LoheAccent.glassBorder

    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            when {
                onLongClick != null -> Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick,
                )
                onClick != null -> Modifier.clickable(onClick = onClick)
                else -> Modifier
            },
        )
        .background(fill)
        // The wash sits under the fill so it reads as light coming from the
        // artwork rather than as a tint painted on top of the row.
        .then(
            if (accent != null) {
                Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = if (isLight) 0.16f else 0.22f),
                            accent.copy(alpha = if (isLight) 0.03f else 0.04f),
                        ),
                    ),
                )
            } else {
                Modifier
            },
        )
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(listOf(edge, Color.Transparent)),
            shape = shape,
        )

    Box(
        modifier = modifier.then(base).padding(contentPadding),
        content = content,
    )
}

/** Row variant: a horizontal [GlassCard]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassRow(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: Dp = 14.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val isLight = backgroundIsLight()
    val fill = if (isLight) LoheAccent.glassFillLight else LoheAccent.glassFill
    val edge = if (isLight) LoheAccent.glassBorderLight else LoheAccent.glassBorder

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                when {
                    onLongClick != null -> Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            )
            .background(fill)
            .then(
                if (accent != null) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = if (isLight) 0.16f else 0.22f),
                                accent.copy(alpha = if (isLight) 0.03f else 0.04f),
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(edge, Color.Transparent)),
                shape = shape,
            )
            .padding(contentPadding),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}
