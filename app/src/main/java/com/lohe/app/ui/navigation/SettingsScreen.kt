package com.lohe.app.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.playback.Playback
import com.lohe.app.ui.player.formatSpeedLabel
import com.lohe.app.ui.player.FloatingPlayerSettings
import com.lohe.app.ui.theme.Appearance
import com.lohe.app.ui.theme.PlaybackSettings
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.launch

/**
 * Settings.
 *
 * Deliberately a screen rather than a dialog: these are choices a user sets once
 * and forgets, and burying them behind an overflow menu makes them undiscoverable.
 */
@Composable
fun SettingsScreen(
    appearance: Appearance,
    onToggleAppearance: () -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    settingsStore: SettingsStore,
    category: SettingsCategory? = null,
    onOpenCategory: (SettingsCategory) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(PlaybackSettings(appearance = appearance)) }
    LaunchedEffect(settingsStore) {
        settingsStore.settings.collect { settings = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = category?.title ?: stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.size(16.dp))

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        if (category == null) {
            SettingsCategory.entries.forEach { section ->
                SettingsMenuRow(section.title, section.summary) { onOpenCategory(section) }
            }
            SettingsMenuRow("About", "Version and app information", onOpenAbout)
        }

        if (category == SettingsCategory.APPEARANCE) {
            ChoiceRowItem(
                label = stringResource(R.string.settings_dark),
                selected = appearance == Appearance.DARK,
                onClick = { if (appearance != Appearance.DARK) onToggleAppearance() },
            )
            ChoiceRowItem(
                label = stringResource(R.string.settings_light),
                selected = appearance == Appearance.LIGHT,
                onClick = { if (appearance != Appearance.LIGHT) onToggleAppearance() },
            )
        }

        if (category == SettingsCategory.PLAYBACK) {
            ChoiceRow(
                label = stringResource(R.string.settings_skip_back),
                choices = PlaybackSettings.SKIP_CHOICES,
                selected = settings.skipBackSeconds,
                format = { stringResource(R.string.settings_seconds, it) },
                onSelect = { scope.launch { settingsStore.setSkipBackSeconds(it) } },
            )

            ChoiceRow(
                label = stringResource(R.string.settings_skip_forward),
                choices = PlaybackSettings.SKIP_CHOICES,
                selected = settings.skipForwardSeconds,
                format = { stringResource(R.string.settings_seconds, it) },
                onSelect = { scope.launch { settingsStore.setSkipForwardSeconds(it) } },
            )

            SpeedChoiceRow(
                label = "Default playback speed",
                choices = PlaybackSettings.SPEED_CHOICES,
                selected = settings.defaultPlaybackSpeed,
                onSelect = { speed ->
                    Playback.setPlaybackSpeed(speed)
                    scope.launch { settingsStore.setDefaultPlaybackSpeed(speed) }
                },
            )

            ToggleRow(
                label = stringResource(R.string.settings_volume_boost),
                hint = stringResource(R.string.settings_volume_boost_hint),
                checked = settings.volumeBoostByDefault,
                onChange = { scope.launch { settingsStore.setVolumeBoostDefault(it) } },
            )

            ToggleRow(
                label = "Skip silence",
                hint = "Automatically trim dead air and long pauses during playback.",
                checked = settings.skipSilenceByDefault,
                onChange = {
                    Playback.setSkipSilence(it)
                    scope.launch { settingsStore.setSkipSilenceDefault(it) }
                },
            )

            ToggleRow(
                label = "Smart speed",
                hint = "Skip silence-like passages on their own, wherever they are.",
                checked = settings.smartSpeedByDefault,
                onChange = { scope.launch { settingsStore.setSmartSpeedDefault(it) } },
            )

            ToggleRow(
                label = "Trim silence and ads",
                hint = "Cut the dead air and the adverts off the ends.",
                checked = settings.autoTrimByDefault,
                onChange = { scope.launch { settingsStore.setAutoTrimDefault(it) } },
            )

            ToggleRow(
                label = stringResource(R.string.settings_mark_played),
                hint = stringResource(R.string.settings_mark_played_hint),
                checked = settings.markPlayedAtEnd,
                onChange = { scope.launch { settingsStore.setMarkPlayedAtEnd(it) } },
            )
        }

        Spacer(Modifier.size(8.dp))

        if (category == SettingsCategory.FLOATING_PLAYER) {
            FloatingPlayerSettings(enabled = settings.floatingPlayerEnabled, sizeDp = settings.floatingPlayerSizeDp,
                opacity = settings.floatingPlayerOpacity, settingsStore = settingsStore)
        }

        Spacer(Modifier.size(8.dp))

        if (category == SettingsCategory.ARCHIVING) {
            ChoiceRow(
                label = "Auto-archive after marking played",
                choices = listOf(-1, 0, 1, 24, 168),
                selected = settings.autoArchiveHours,
                format = {
                    when (it) {
                        -1 -> "Off"
                        0 -> "Immediately"
                        1 -> "1 hour"
                        24 -> "24 hours"
                        else -> "7 days"
                    }
                },
                onSelect = { scope.launch { settingsStore.setAutoArchiveHours(it) } },
            )
            Text(
                "Played episodes leave playlists immediately. Archiving deletes their downloaded copy, not their listening history. Android may delay background cleanup after the selected time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Spacer(Modifier.size(8.dp))

        if (category == SettingsCategory.DOWNLOADS) {
            ToggleRow(
                label = "Auto-download Up Next",
                hint = "Download episodes as they are added to your queue, so they are ready offline.",
                checked = settings.autoDownloadQueue,
                onChange = { scope.launch { settingsStore.setAutoDownloadQueue(it) } },
            )
        }

        Spacer(Modifier.size(8.dp))

        Spacer(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun SettingsMenuRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider()
}

/**
 * A single-choice row.
 *
 * Uses a real RadioButton rather than an "On" label: the previous version put
 * a filled container behind the selected row, which read as a rendering fault
 * rather than a selection, and left the choice ambiguous.
 */
@Composable
private fun ChoiceRowItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(
    label: String,
    choices: List<Int>,
    selected: Int,
    // Composable because the caller formats through stringResource, which is.
    format: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // FlowRow rather than Row: six chips do not fit across a narrow phone,
        // and a fixed Row wrapped "60s" onto two lines.
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    label = { Text(format(choice), maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        // The default selected chip takes the theme's
                        // secondary container, which is a teal that reads as
                        // green against the blue the app is built on.
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedChoiceRow(
    label: String,
    choices: List<Float>,
    selected: Float,
    onSelect: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            choices.forEach { choice ->
                FilterChip(
                    selected = kotlin.math.abs(choice - selected) < 0.01f,
                    onClick = { onSelect(choice) },
                    label = { Text(formatSpeedLabel(choice), maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        // The default selected chip takes the theme's
                        // secondary container, which is a teal that reads as
                        // green against the blue the app is built on.
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
