package com.lohe.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lohe.app.ui.player.formatSpeedLabel
import com.lohe.app.ui.theme.LoheSheet
import com.lohe.app.ui.theme.PlaybackSettings
import com.lohe.app.ui.theme.ShowPlaybackOverrides
import com.lohe.app.ui.theme.ShowPlaybackSettings
import kotlinx.coroutines.launch

/**
 * The playback settings for one show, resolved from the store.
 *
 * Any screen that has a show can offer this, so the per-show settings are
 * reachable from the library as well as from the show's own page.
 */
@Composable
fun ShowSettingsSheetFor(
    podcastUuid: String,
    showTitle: String,
    onDismiss: () -> Unit,
) {
    val store = remember { com.lohe.app.data.ServiceLocator.settingsStore() }
    val scope = rememberCoroutineScope()
    val global by store.settings.collectAsState(initial = PlaybackSettings())
    val overrides by store.showPlaybackOverrides(podcastUuid)
        .collectAsState(initial = ShowPlaybackOverrides())

    ShowSettingsSheet(
        showTitle = showTitle,
        global = global,
        overrides = overrides,
        onSetVolumeBoost = { v -> scope.launch { store.setShowVolumeBoost(podcastUuid, v) } },
        onSetSkipSilence = { v -> scope.launch { store.setShowSkipSilence(podcastUuid, v) } },
        onSetSpeed = { v -> scope.launch { store.setShowPlaybackSpeed(podcastUuid, v) } },
        onSetSmartSpeed = { v -> scope.launch { store.setShowSmartSpeed(podcastUuid, v) } },
        onSetAutoTrim = { v -> scope.launch { store.setShowAutoTrim(podcastUuid, v) } },
        onSetSkipIntro = { v -> scope.launch { store.setShowSkipIntroSeconds(podcastUuid, v) } },
        onSetSkipOutro = { v -> scope.launch { store.setShowSkipOutroSeconds(podcastUuid, v) } },
        onReset = { scope.launch { store.clearShowPlaybackSettings(podcastUuid) } },
        onDismiss = onDismiss,
        extra = { ShowUpNextControls(podcastUuid) },
    )
}

/**
 * Up Next automation for one show, kept to a couple of lines: a switch that
 * queues new episodes as they arrive (with where to put them), and two small
 * buttons that pull in everything already published.
 */
@Composable
fun ShowUpNextControls(podcastUuid: String) {
    val repository = remember { com.lohe.app.data.ServiceLocator.subscriptionRepository() }
    val scope = rememberCoroutineScope()
    var podcast by remember(podcastUuid) {
        mutableStateOf<com.lohe.app.data.db.entity.PodcastEntity?>(null)
    }
    var message by remember(podcastUuid) { mutableStateOf<String?>(null) }
    LaunchedEffect(podcastUuid) { podcast = repository.getPodcast(podcastUuid) }
    val show = podcast ?: return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Add new episodes to Up Next",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (show.autoAddNewEpisodesToUpNext) {
                FilterChip(
                    selected = show.autoAddNewEpisodesAtTop,
                    onClick = {
                        val atTop = !show.autoAddNewEpisodesAtTop
                        podcast = show.copy(autoAddNewEpisodesAtTop = atTop)
                        scope.launch { repository.setAutoAddNewEpisodesAtTop(podcastUuid, atTop) }
                    },
                    label = { Text(if (show.autoAddNewEpisodesAtTop) "Top" else "Bottom") },
                )
                Spacer(Modifier.width(8.dp))
            }
            Switch(
                checked = show.autoAddNewEpisodesToUpNext,
                onCheckedChange = { enabled ->
                    podcast = show.copy(autoAddNewEpisodesToUpNext = enabled)
                    scope.launch { repository.setAutoAddNewEpisodesToUpNext(podcastUuid, enabled) }
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message ?: "Add all unplayed now",
                style = MaterialTheme.typography.bodySmall,
                color = if (message != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            listOf(true to "Top", false to "Bottom").forEach { (atTop, label) ->
                TextButton(
                    onClick = {
                        scope.launch {
                            val added = repository.addAllEpisodesToUpNext(podcastUuid, atTop)
                            message = if (added == 0) {
                                "Nothing unplayed to add"
                            } else {
                                "Added $added to Up Next"
                            }
                        }
                    },
                ) {
                    Text(label)
                }
            }
        }
    }
}

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
    onSetSkipIntro: (Int?) -> Unit,
    onSetSkipOutro: (Int?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LoheSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The sheet is taller than the screen once every setting is
                // listed, so it has to scroll or the bottom is unreachable.
                .verticalScroll(rememberScrollState())
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

            extra()

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
                globalOn = global.smartSpeedByDefault,
                override = overrides.smartSpeed,
                onSet = onSetSmartSpeed,
            )
            ShowToggle(
                title = "Trim silence and ads",
                globalOn = global.autoTrimByDefault,
                override = overrides.autoTrim,
                onSet = onSetAutoTrim,
            )

            // --- Fixed head and tail cuts ---
            SkipSecondsChips(
                title = "Skip first",
                hint = if (overrides.skipIntroSeconds != null) {
                    "Set for this show"
                } else {
                    "Off — start where the audio starts"
                },
                selected = overrides.skipIntroSeconds,
                onSet = onSetSkipIntro,
            )
            SkipSecondsChips(
                title = "Skip last",
                hint = if (overrides.skipOutroSeconds != null) {
                    "Set for this show"
                } else {
                    "Off — play to the very end"
                },
                selected = overrides.skipOutroSeconds,
                onSet = onSetSkipOutro,
            )

            if (overrides.volumeBoost != null || overrides.skipSilence != null ||
                overrides.playbackSpeed != null || overrides.smartSpeed != null ||
                overrides.autoTrim != null || overrides.skipIntroSeconds != null ||
                overrides.skipOutroSeconds != null
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
 * One on-or-off setting for a show.
 *
 * [globalOn] is null for a setting that has no global counterpart, which is
 * the case for smart speed and trimming: those exist only per show, so
 * offering to follow the global setting would be offering something that is
 * not there.
 */
@Composable
private fun ShowToggle(
    title: String,
    globalOn: Boolean?,
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
            text = when {
                globalOn == null && override == null -> "Off for this show"
                globalOn == null -> "Set for this show"
                override == null -> "Following the global setting (${if (globalOn) "on" else "off"})"
                else -> "Set for this show"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (globalOn != null) {
                UseGlobalChip(
                    label = "Use global",
                    selected = override == null,
                    onClick = { onSet(null) },
                )
            }
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

/**
 * A choice of seconds for the fixed head or tail cut.
 *
 * Unlike the other settings there is no global counterpart: cutting the first
 * minute is a property of one show's intro, not of the listener's taste, so
 * "Off" means the show has no cut of its own rather than "follow the global".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkipSecondsChips(
    title: String,
    hint: String,
    selected: Int?,
    onSet: (Int?) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSet(null) },
                label = { Text("Off", maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            ShowPlaybackSettings.SKIP_SECONDS_CHOICES.forEach { seconds ->
                FilterChip(
                    selected = selected == seconds,
                    onClick = { onSet(seconds) },
                    label = { Text("${seconds}s", maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}
