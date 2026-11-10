package com.lohe.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lohe.app.ui.theme.LoheAccent
import com.lohe.app.ui.theme.backgroundIsLight

/**
 * Lohe's bottom navigation.
 *
 * Not Material3's [androidx.compose.material3.NavigationBar]: that ships an
 * opaque surface with a pill behind the selected item, which read as "generic
 * Android app" rather than as this product.
 *
 * What it does instead:
 *   - a mostly opaque, softly frosted surface with a quiet top-edge light
 *     catch, keeping navigation legible over every screen
 *   - accent reserved for the active item only, per the brand's "electric blue
 *     marks what is tappable or currently active, and nothing else" rule
 *   - a dot rather than a pill, to keep the bar visually light
 */
@Composable
fun LoheBottomBar(
    destinations: List<BottomDestination>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = backgroundIsLight()
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .heightIn(min = BarHeight)
            .clip(shape)
            .background(if (isLight) LoheAccent.barGlassFillLight else LoheAccent.barGlassFill)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        (if (isLight) LoheAccent.barGlassEdgeLight else LoheAccent.barGlassEdge),
                        Color.Transparent,
                    ),
                ),
                shape = shape,
            ),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                BottomBarItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onSelect(destination.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: BottomDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val itemShape = MaterialTheme.shapes.extraLarge

    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "nav-tint",
    )

    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        label = "nav-dot",
    )

    Column(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(itemShape)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, itemShape) else Modifier)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                // The theme's ripple, clipped to the pill above so presses and
                // focus read as a contained state layer, not a square block.
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(width = 26.dp, height = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        // Active indicator: an accent dot that scales in, rather than the pill
        // that Material3 draws behind the whole item. The scale has to be
        // applied via graphicsLayer — sizing the Box conditionally would snap
        // instead of animate, and would relayout the bar on every selection.
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer {
                    scaleX = dotScale
                    scaleY = dotScale
                    alpha = dotScale
                }
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** One destination in [LoheBottomBar]. */
data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BarHeight = 62.dp
