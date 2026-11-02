package com.lohe.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun FloatingPlayerToggleButton(
    enabled: Boolean,
    isPlaying: Boolean,
    settingsStore: SettingsStore,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val enablePlayer = rememberFloatingPlayerPermissionFlow(settingsStore)

    IconButton(
        onClick = {
            if (enabled) {
                scope.launch { settingsStore.setFloatingPlayerEnabled(false) }
            } else {
                enablePlayer()
            }
        },
        modifier = modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                )
                .clip(CircleShape)
                .background(
                    brush = if (enabled) {
                        Brush.verticalGradient(
                            listOf(Color(0xFF79D5FF), MaterialTheme.colorScheme.primary, Color(0xFF064A9C)),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, Color.White.copy(alpha = if (enabled) 0.7f else 0.35f), CircleShape),
            )
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (enabled) R.string.floating_player_turn_off else R.string.floating_player_turn_on,
                ),
                tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }

}
