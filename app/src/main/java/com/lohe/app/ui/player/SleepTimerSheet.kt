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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                onPick = {
                    onArm(SleepTimer.Mode.Duration(it))
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
private fun PresetGrid(enabled: Boolean, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PRESET_MINUTES.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { minutes ->
                    Surface(
                        onClick = { onPick(minutes) },
                        enabled = enabled,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.sleep_timer_minutes, minutes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
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
