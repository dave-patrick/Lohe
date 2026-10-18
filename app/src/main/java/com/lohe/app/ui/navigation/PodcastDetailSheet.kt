package com.lohe.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.data.fetch.CatalogPodcast
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.ui.image.RemoteImage

/**
 * Show details, opened by tapping a search result.
 *
 * Tapping a result used to subscribe immediately, which left no way to see
 * what a show actually is before committing to it — the blurb and artwork only
 * arrive after subscribing, by which point the decision is already made. The
 * row now opens this sheet; subscribing remains an explicit action inside it,
 * and the trailing icon on the row still subscribes directly for the
 * browse-and-follow case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailSheet(
    podcast: CatalogPodcast,
    /** Blurb, preferring the feed over the catalog's (usually absent) copy. */
    description: String?,
    subscribed: PodcastEntity?,
    autoDownload: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onAutoDownloadChange: (Boolean) -> Unit,
    onOpenShow: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(
                    url = podcast.artworkUrl,
                    contentDescription = null,
                    size = 88.dp,
                    cornerRadius = 16.dp,
                )

                Spacer(Modifier.size(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (podcast.author.isNotBlank()) {
                        Text(
                            text = podcast.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (podcast.genre != null) {
                        Text(
                            text = podcast.genre,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Follow / unfollow is the primary decision here, so it gets the
            // filled treatment while everything else stays quiet.
            if (subscribed == null) {
                Button(
                    onClick = onFollow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.RssFeed, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.discovery_subscribe))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onUnfollow,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.discovery_unsubscribe))
                    }

                    if (onOpenShow != null) {
                        OutlinedButton(
                            onClick = onOpenShow,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.discovery_open_show))
                        }
                    }
                }
            }

            val blurb = description
            if (!blurb.isNullOrBlank()) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Per-show settings only mean something once you follow the show.
            if (subscribed != null) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                SettingToggle(
                    title = stringResource(R.string.show_setting_auto_download),
                    summary = stringResource(R.string.show_setting_auto_download_summary),
                    checked = autoDownload,
                    onCheckedChange = onAutoDownloadChange,
                )
            }
        }
    }
}

/** One labelled switch in a settings list. */
@Composable
private fun SettingToggle(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Rounded container used by the detail header on wide layouts. */
@Composable
internal fun DetailArtwork(url: String?, modifier: Modifier = Modifier) {
    RemoteImage(
        url = url,
        contentDescription = null,
        size = 88.dp,
        cornerRadius = 16.dp,
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
    )
}