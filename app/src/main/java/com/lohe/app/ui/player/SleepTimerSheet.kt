package com.lohe.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BedtimeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.lohe.app.data.ServiceLocator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lohe.app.ui.theme.LoheSheet
import com.lohe.app.R
import com.lohe.app.playback.SleepTimer

/** Preset sleep durations offered as one-tap chips. */
private val PRESET_MINUTES = listOf(5, 10, 15, 30, 45, 60)

/**
 * Sleep timer picker.
 *
 * The sheet only decides a [SleepTimer.Mode]; arming and firing happen in
 * [com.lohe.app.playback.PlaybackService], so closing the app mid-timer does
 * not cancel it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    current: SleepTimer.State,
    remainingMs: Long,
    onArm: (SleepTimer.Mode) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    // The length the listener last chose, remembered. Most people set the
    // same one every night, and making them find it again every time is the
    // sort of small friction that stops a feature being used.
    val scope = rememberCoroutineScope()
    val store = remember { ServiceLocator.settingsStore() }
    val savedMinutes by remember(store) {
        store.settings.map { it.sleepTimerMinutes }
    }.collectAsState(30)

    LoheSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.sleep_timer_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            if (current.active) {
                Text(
                    text = current.activeDescription(remainingMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.sleep_timer_end_of_episode),
                style = MaterialTheme.typography.bodyLarge,
                color = if (current.stopsAtEpisodeEnd) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onArm(SleepTimer.Mode.EndOfEpisode)
                        onDismiss()
                    }
                    .padding(vertical = 12.dp),
            )

            PresetGrid(
                enabled = !current.stopsAtEpisodeEnd,
                rememberedMinutes = savedMinutes,
                onPick = {
                    onArm(SleepTimer.Mode.Duration(it))
                    scope.launch { store.setSleepTimerMinutes(it) }
                    onDismiss()
                },
            )

            if (current.active) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCancel()
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.BedtimeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.sleep_timer_cancel),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetGrid(
    enabled: Boolean,
    rememberedMinutes: Int,
    onPick: (Int) -> Unit,
) {
    // The remembered length is offered even when it is not one of the
    // presets, so a listener who used 90 minutes keeps 90 minutes.
    val choices = (PRESET_MINUTES + rememberedMinutes).distinct().sorted()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { minutes ->
                    val remembered = minutes == rememberedMinutes
                    Surface(
                        onClick = { onPick(minutes) },
                        enabled = enabled,
                        shape = MaterialTheme.shapes.medium,
                        color = if (remembered) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.sleep_timer_minutes, minutes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (remembered) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // Keep the last row's cells the same width as the rows above.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun SleepTimer.State.activeDescription(remainingMs: Long): String = when (mode) {
    is SleepTimer.Mode.EndOfEpisode -> "Stops when this episode ends"
    is SleepTimer.Mode.Duration -> "Stops in ${formatTimestamp(remainingMs)}"
    null -> ""
}
