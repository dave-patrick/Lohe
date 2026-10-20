package com.lohe.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lohe.app.ui.player.formatSpeedLabel
import com.lohe.app.ui.theme.LoheSheet
import com.lohe.app.ui.theme.PlaybackSettings
import com.lohe.app.ui.theme.ShowPlaybackOverrides

/**
 * The playback settings for one show.
 *
 * Everything here also exists globally. A setting left alone here follows the
 * global one, so a listener who wants one show at 1.5x does not have to
 * change it everywhere, and a listener who wants everything at 1.5x does not
 * have to do it show by show.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShowSettingsSheet(
    showTitle: String,
    global: PlaybackSettings,
    overrides: ShowPlaybackOverrides,
    onSetVolumeBoost: (Boolean?) -> Unit,
    onSetSkipSilence: (Boolean?) -> Unit,
    onSetSpeed: (Float?) -> Unit,
    onSetSmartSpeed: (Boolean?) -> Unit,
    onSetAutoTrim: (Boolean?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LoheSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.RssFeed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    text = showTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "These apply to this show only. Anything left alone follows your " +
                    "global settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- Playback speed ---
            Column {
                Text(
                    text = "Playback speed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Global: ${formatSpeedLabel(global.defaultPlaybackSpeed)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    UseGlobalChip(
                        label = "Use global",
                        selected = overrides.playbackSpeed == null,
                        onClick = { onSetSpeed(null) },
                    )
                    PlaybackSettings.SPEED_CHOICES.forEach { speed ->
                        FilterChip(
                            selected = overrides.playbackSpeed == speed,
                            onClick = { onSetSpeed(speed) },
                            label = { Text(formatSpeedLabel(speed), maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }

            // --- Toggles ---
            ShowToggle(
                title = "Skip silence",
                globalOn = global.skipSilenceByDefault,
                override = overrides.skipSilence,
                onSet = onSetSkipSilence,
            )
            ShowToggle(
                title = "Volume boost",
                globalOn = global.volumeBoostByDefault,
                override = overrides.volumeBoost,
                onSet = onSetVolumeBoost,
            )
            ShowToggle(
                title = "Smart speed",
                globalOn = false,
                override = overrides.smartSpeed,
                onSet = onSetSmartSpeed,
            )
            ShowToggle(
                title = "Trim silence and ads",
                globalOn = false,
                override = overrides.autoTrim,
                onSet = onSetAutoTrim,
            )

            if (overrides.volumeBoost != null || overrides.skipSilence != null ||
                overrides.playbackSpeed != null || overrides.smartSpeed != null ||
                overrides.autoTrim != null
            ) {
                TextButton(onClick = onReset) {
                    Icon(
                        imageVector = Icons.Filled.Archive,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Use the global settings for everything")
                }
            }
        }
    }
}

/**
 * One on-or-off setting for a show, with the global value shown beside it.
 */
@Composable
private fun ShowToggle(
    title: String,
    globalOn: Boolean,
    override: Boolean?,
    onSet: (Boolean?) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (override == null) {
                "Following the global setting (${if (globalOn) "on" else "off"})"
            } else {
                "Set for this show"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UseGlobalChip(
                label = "Use global",
                selected = override == null,
                onClick = { onSet(null) },
            )
            FilterChip(
                selected = override == true,
                onClick = { onSet(true) },
                label = { Text("On") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            FilterChip(
                selected = override == false,
                onClick = { onSet(false) },
                label = { Text("Off") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** A chip for "follow the global setting". */
@Composable
private fun UseGlobalChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
