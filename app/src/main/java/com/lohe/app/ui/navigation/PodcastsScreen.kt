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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.data.FeedImportState
import com.lohe.app.data.OpmlImportState
import com.lohe.app.data.RefreshState
import com.lohe.app.data.SubscriptionsViewModel
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.GlassRow
import kotlinx.coroutines.launch

@Composable
fun PodcastsScreen(
    viewModel: SubscriptionsViewModel,
    onOpenPodcast: (String) -> Unit,
    onAddLocalFolder: () -> Unit,
    onManageStorage: () -> Unit,
    onPickOpmlToImport: () -> Unit,
    onPickExportDestination: (String) -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val podcasts by viewModel.podcasts.collectAsState()
    val query by viewModel.searchQueryState.collectAsState()
    val import by viewModel.feedImport.collectAsState()
    val opml by viewModel.opmlImport.collectAsState()
    val refresh by viewModel.refresh.collectAsState()
    val refreshing = refresh is RefreshState.Running
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddFeed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    LaunchedEffect(import) {
        when (val state = import) {
            is FeedImportState.Succeeded -> {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.podcasts_feed_added, state.showTitle),
                )
                showAddFeed = false
                viewModel.consumeFeedImport()
            }

            is FeedImportState.Failed -> {
                snackbarHostState.showSnackbar(state.reason)
                viewModel.consumeFeedImport()
            }

            else -> Unit
        }
    }

    LaunchedEffect(opml) {
        when (val state = opml) {
            is OpmlImportState.Done -> {
                val r = state.result
                val summary = if (r.failed == 0) {
                    context.getString(R.string.opml_imported_clean, r.imported)
                } else {
                    context.getString(R.string.opml_imported_partial, r.imported, r.failed)
                }
                snackbarHostState.showSnackbar(summary)
                viewModel.consumeOpmlImport()
            }

            is OpmlImportState.Failed -> {
                snackbarHostState.showSnackbar(state.reason)
                viewModel.consumeOpmlImport()
            }

            else -> Unit
        }
    }

    LaunchedEffect(refresh) {
        when (val state = refresh) {
            is RefreshState.Done -> {
                val s = state.summary
                val summary = if (s.failed == 0) {
                    context.getString(R.string.refresh_clean, s.refreshed)
                } else {
                    context.getString(R.string.refresh_partial, s.refreshed, s.failed)
                }
                snackbarHostState.showSnackbar(summary)
                viewModel.consumeRefresh()
            }

            is RefreshState.Failed -> {
                snackbarHostState.showSnackbar(state.reason)
                viewModel.consumeRefresh()
            }

            else -> Unit
        }
    }

    if (showAddFeed) {
        AddFeedDialog(
            loading = import is FeedImportState.Loading,
            onSubmit = viewModel::importFeed,
            onDismiss = {
                showAddFeed = false
                viewModel.consumeFeedImport()
            },
        )
    }

    // No nested Scaffold here: the outer one already consumed the system-bar
    // insets, and a second Scaffold would apply them again.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.podcasts_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showAddFeed = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.RssFeed,
                        contentDescription = stringResource(R.string.podcasts_add_feed),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onAddLocalFolder, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = stringResource(R.string.podcasts_add_folder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.podcasts_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (refreshing) R.string.refreshing else R.string.refresh_feeds,
                                    ),
                                )
                            },
                            leadingIcon = {
                                if (refreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                }
                            },
                            enabled = !refreshing && podcasts.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                viewModel.refreshFeeds()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.opml_import)) },
                            leadingIcon = {
                                Icon(Icons.Filled.FileOpen, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onPickOpmlToImport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.opml_export)) },
                            leadingIcon = {
                                Icon(Icons.Filled.FileUpload, contentDescription = null)
                            },
                            enabled = podcasts.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    val document = runCatching { viewModel.buildOpml() }
                                        .getOrNull()
                                    if (document == null) {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.opml_export_failed),
                                        )
                                    } else {
                                        onPickExportDestination(document)
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.storage_management)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Storage, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onManageStorage()
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChanged,
                label = { Text(stringResource(R.string.podcasts_search_label)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )

            if (podcasts.isEmpty()) {
                EmptyPodcastsState(
                    onAddFeed = { showAddFeed = true },
                    onImportFolder = onAddLocalFolder,
                    query = query,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(podcasts, key = { it.uuid }) { podcast ->
                        PodcastCard(
                            podcast = podcast,
                            onClick = { onOpenPodcast(podcast.uuid) },
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
}

@Composable
private fun AddFeedDialog(
    loading: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.podcasts_add_feed)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.podcasts_feed_url_label)) },
                placeholder = { Text(stringResource(R.string.podcasts_feed_url_hint)) },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(url) },
                enabled = url.isNotBlank() && !loading,
            ) {
                Text(
                    text = stringResource(
                        if (loading) R.string.podcasts_fetching else R.string.podcasts_add,
                    ),
                )
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.no))
            }
        },
    )
}

@Composable
private fun EmptyPodcastsState(
    onAddFeed: () -> Unit,
    onImportFolder: () -> Unit,
    query: String,
    modifier: Modifier = Modifier,
) {
    val searching = query.isNotBlank()
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.size(14.dp))

        Text(
            text = stringResource(
                if (searching) R.string.podcasts_empty_search else R.string.podcasts_empty_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(
                if (searching) R.string.podcasts_empty_search_hint
                else R.string.podcasts_empty_hint,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // A search that matched nothing needs no actions — the fix is to type
        // something else, not to add a show.
        if (!searching) {
            Spacer(Modifier.size(24.dp))
            Button(onClick = onAddFeed, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.podcasts_add_feed))
            }
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = onImportFolder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.podcasts_add_folder))
            }
        }
    }
}

@Composable
private fun PodcastCard(podcast: PodcastEntity, onClick: () -> Unit) {
    GlassRow(onClick = onClick) {
        RemoteImage(
            url = podcast.artworkUrl,
            contentDescription = null,
            size = 56.dp,
            cornerRadius = 10.dp,
        )

        Spacer(Modifier.size(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            podcast.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (podcast.uuid.startsWith("local:")) {
                Text(
                    text = stringResource(R.string.podcasts_local_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
