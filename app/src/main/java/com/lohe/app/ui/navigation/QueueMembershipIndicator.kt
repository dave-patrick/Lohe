package com.lohe.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.QueueIndicatorColor
import com.lohe.app.ui.theme.QueueIndicatorSettings
import com.lohe.app.ui.theme.QueueIndicatorStyle

/** A passive membership marker: the row's existing tap and swipe actions stay authoritative. */
@Composable
fun QueueMembershipIndicator(
    isQueued: Boolean,
    settings: QueueIndicatorSettings,
    modifier: Modifier = Modifier,
) {
    if (!isQueued || !settings.enabled) return
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val color = when (settings.color) {
        QueueIndicatorColor.BLUE -> ElectricBlue
        QueueIndicatorColor.TEAL -> if (dark) Color(0xFF2DD4BF) else Color(0xFF00796B)
        QueueIndicatorColor.AMBER -> if (dark) Color(0xFFFFC24B) else Color(0xFFA86000)
    }
    val size = settings.size.dp.dp
    Box(
        modifier = modifier.width(size + 8.dp).height(size + 4.dp)
            .semantics { contentDescription = "In Up Next" },
        contentAlignment = Alignment.Center,
    ) {
        when (settings.style) {
            QueueIndicatorStyle.DOT -> Box(Modifier.size(size).background(color, CircleShape))
            QueueIndicatorStyle.RING -> Box(Modifier.size(size).border(1.5.dp, color, CircleShape))
            QueueIndicatorStyle.QUEUE_ICON -> Icon(
                Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null,
                tint = color, modifier = Modifier.size(size),
            )
        }
    }
}
