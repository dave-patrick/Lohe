package com.lohe.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.data.CatalogActionState
import com.lohe.app.data.CatalogState
import com.lohe.app.data.DiscoveryViewModel
import com.lohe.app.data.fetch.CatalogPodcast
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.GlassRow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/**
 * Catalog search.
 *
 * Replaces the previous placeholder screen, which rendered invented shows.
 * Results come from the public iTunes catalog, so what appears here is a real
 * feed the user can actually subscribe to.
 */
@OptIn(FlowPreview::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onOpenSubscribed: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val previews by viewModel.previews.collectAsState()
    val previewEpisodes by viewModel.previewEpisodes.collectAsState()
    val action by viewModel.action.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Which catalog result is open, if any. Held by feed URL + title rather
    // than the object so a re-emitted result does not reset the sheet.
    var openDetail by remember { mutableStateOf<CatalogPodcast?>(null) }

    // Debounced so typing a six-letter word is one network call, not six.
    LaunchedEffect(viewModel) {
        viewModel.query
            .map { it.trim() }
            .drop(1)
            .debounce(350)
            .distinctUntilChanged()
            .collectLatest { term -> viewModel.search(term) }
    }

    // Say what happened. A subscribe that fails because the feed is
    // unreachable looks exactly like a button that does nothing.
    LaunchedEffect(action) {
        when (val current = action) {
            is CatalogActionState.Done -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.consumeAction()
            }
            is CatalogActionState.Failed -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.consumeAction()
            }
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.discovery_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            HeaderSettingsSpacer()
        }

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.discovery_search_label)) },
            placeholder = { Text(stringResource(R.string.discovery_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when (val current = state) {
            is CatalogState.Idle -> HintText(
                text = stringResource(R.string.discovery_idle),
                modifier = Modifier.fillMaxSize(),
            )

            is CatalogState.Loading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            is CatalogState.Empty -> HintText(
                text = stringResource(R.string.discovery_no_results),
                modifier = Modifier.fillMaxSize(),
            )

            is CatalogState.Failed -> HintText(
                text = current.reason,
                modifier = Modifier.fillMaxSize(),
            )

            is CatalogState.Results -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 12.dp, bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Keyed by the composite rather than feedUrl alone: two catalog entries can
                // legitimately share a feed while differing in title, and Compose
                // throws on a duplicate key rather than tolerating one.
                items(current.items, key = { "${it.feedUrl}|${it.title}" }) { podcast ->
                    val existing = subscriptions[podcast.feedUrl]
                    CatalogRow(
                        podcast = podcast,
                        alreadySubscribed = existing != null,
                        // Tapping the row opens details; the trailing icon is
                        // the shortcut for someone who just wants to follow.
                        onOpen = { openDetail = podcast },
                        onSubscribe = { viewModel.subscribe(podcast) },
                    )
                }
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    openDetail?.let { podcast ->
        val existing = subscriptions[podcast.feedUrl]

        // Followed shows already carry a blurb from the feed we parsed at
        // subscribe time; an unfollowed one has to go and read it.
        val blurb = existing?.description
            ?: previews[podcast.feedUrl]
            ?: podcast.description

        // Fetch the episode list for preview when the detail opens.
        LaunchedEffect(podcast.feedUrl) {
            viewModel.fetchFeedPreview(podcast.feedUrl)
            viewModel.loadPreview(podcast, existing?.description)
        }

        val episodes = previewEpisodes[podcast.feedUrl] ?: emptyList()

        PodcastDetailSheet(
            podcast = podcast,
            description = blurb,
            subscribed = existing,
            autoDownload = existing?.autoDownload ?: false,
            onFollow = { viewModel.subscribe(podcast) },
            onUnfollow = { existing?.let { viewModel.unsubscribe(it.uuid) } },
            onAutoDownloadChange = { enabled ->
                existing?.let { viewModel.setAutoDownload(it.uuid, enabled) }
            },
            onOpenShow = existing?.let { { onOpenSubscribed(it.uuid) } },
            onDownloadEpisode = { episodeUuid ->
                viewModel.downloadEpisode(podcast, episodeUuid)
            },
            onDismiss = { openDetail = null },
            episodes = episodes,
        )
    }
}

@Composable
private fun HintText(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogRow(
    podcast: CatalogPodcast,
    alreadySubscribed: Boolean,
    onOpen: () -> Unit,
    onSubscribe: () -> Unit,
) {
    // The row opens details; following stays a deliberate act on the trailing
    // icon, so browsing never silently changes someone's library.
    GlassRow(onClick = onOpen) {
        RemoteImage(
            url = podcast.artworkUrl,
            contentDescription = null,
            size = 56.dp,
            fallbackLabel = podcast.title,
            cornerRadius = 10.dp,
        )

        Spacer(Modifier.size(14.dp))

        Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (podcast.author.isNotBlank()) {
                    Text(
                        text = podcast.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                podcast.genre?.let { genre ->
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            IconButton(onClick = onSubscribe, enabled = !alreadySubscribed) {
                Icon(
                    imageVector = if (alreadySubscribed) {
                        Icons.Filled.Check
                    } else {
                        Icons.Filled.Add
                    },
                    contentDescription = stringResource(
                        if (alreadySubscribed) R.string.discovery_already_subscribed
                        else R.string.discovery_subscribe,
                    ),
                    tint = if (alreadySubscribed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
    }
}
