package com.lohe.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The building blocks of an action sheet.
 *
 * Shared so that every sheet — the episode sheet, the queue sheet, anything
 * added later — is assembled the same way: a tinted icon tile, the label
 * beside it, hairline dividers between rows, and destructive actions in a
 * group of their own so they cannot be hit by accident.
 */
internal data class SheetAction(
    val icon: ImageVector,
    val label: String,
    val accent: Color,
    val onClick: () -> Unit,
)

/** One action: a tinted icon tile and its label. */
@Composable
internal fun SheetActionRow(action: SheetAction) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = action.onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(action.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = action.accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = action.accent,
        )
    }
}

/** A set of actions in one quiet surface, divided by hairlines. */
@Composable
internal fun SheetActionGroup(actions: List<SheetAction>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        actions.forEachIndexed { index, action ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    modifier = Modifier.padding(start = 54.dp),
                )
            }
            SheetActionRow(action)
        }
    }
}
