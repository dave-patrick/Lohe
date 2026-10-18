package com.lohe.app.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.data.ContinueEntry
import com.lohe.app.data.QueueViewModel
import com.lohe.app.ui.theme.Appearance
import com.lohe.app.ui.theme.GlassCard

/**
 * Home: what to listen to next.
 *
 * When there is something in progress the screen leads with it; an empty-state
 * poster is only shown to someone who has not started anything, because showing
 * "add a feed" to a user with a half-finished episode is noise.
 */
@Composable
fun HomeScreen(
    queueViewModel: QueueViewModel,
    onOpenPodcast: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (ContinueEntry) -> Unit,
    appearance: Appearance,
    modifier: Modifier = Modifier,
) {
    val inProgress by queueViewModel.continueListening.collectAsState()
    val queueSize by queueViewModel.queue.collectAsState()
    val isLight = appearance == Appearance.LIGHT

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The wordmark is a white/cyan mark built for the dark palette; in
            // light mode it would disappear against paper white, so it falls
            // back to the plain name.
            if (isLight) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.wordmark),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(24.dp)
                        .width(128.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (inProgress.isEmpty()) {
            EmptyHome(
                onOpenLibrary = onOpenLibrary,
                onOpenDiscover = onOpenDiscover,
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.home_continue),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
            }

            items(inProgress, key = { it.episode.uuid }) { entry ->
                ContinueCard(
                    entry = entry,
                    onPlay = { onPlay(entry) },
                    onOpenShow = { onOpenPodcast(entry.episode.podcastUuid) },
                )
            }

            if (queueSize.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.queue_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(
    entry: ContinueEntry,
    onPlay: () -> Unit,
    onOpenShow: () -> Unit,
) {
    GlassCard(onClick = onPlay, contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.showTitle.isNotBlank()) {
                    Text(
                        text = entry.showTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.episode.completionFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { entry.episode.completionFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                TextButton(onClick = onOpenShow) {
                    Text(
                        text = stringResource(R.string.home_recent_shows),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.player_now_playing),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(
    onOpenLibrary: () -> Unit,
    onOpenDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Kani, the brand mascot, holds the empty library. An abstract gradient tile
        // with a waveform glyph read as a placeholder nobody had finished; this
        // is the app's own character doing what the brand says it is for —
        // present in empty states.
        Image(
            painter = painterResource(id = R.drawable.kani_mascot),
            contentDescription = stringResource(R.string.home_mascot_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(232.dp),
        )

        Spacer(Modifier.size(20.dp))

        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(8.dp))

        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(28.dp))

        Button(
            onClick = onOpenDiscover,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_cta_discover))
        }

        Spacer(Modifier.size(10.dp))

        OutlinedButton(
            onClick = onOpenLibrary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_cta_import))
        }
    }
}
