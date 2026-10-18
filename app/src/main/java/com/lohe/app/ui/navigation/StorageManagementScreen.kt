package com.lohe.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.data.CONTENT_SCHEME
import com.lohe.app.data.ServiceLocator
import com.lohe.app.data.SubscriptionsViewModel
import com.lohe.app.data.db.entity.EpisodeEntity
import kotlinx.coroutines.launch

/**
 * Storage: what the app is holding on disk and how to get rid of it.
 *
 * Two kinds of local data live here and they behave differently — imported
 * device audio (which the app only references, never owns) and offline
 * downloads (which the app owns outright and deletes) — so they are listed
 * separately rather than lumped into one number.
 */
@Composable
fun StorageManagementScreen(
    viewModel: SubscriptionsViewModel,
    onBack: () -> Unit,
) {
    val episodes by viewModel.allEpisodes.collectAsState()
    val importedBytes by viewModel.localFilesStorageSize.collectAsState()
    val downloads by ServiceLocator.downloadManager().downloads().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val importedCount = episodes.count { it.audioUrl.startsWith(CONTENT_SCHEME) }
    val downloadedCount = downloads.size

    var confirmDeleteDownloads by remember { mutableStateOf(false) }
    var confirmClearImports by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.storage_management),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.size(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StorageCard(
                    title = stringResource(R.string.storage_downloads),
                    count = downloadedCount,
                    emptyHint = stringResource(R.string.storage_no_downloads),
                    clearLabel = stringResource(R.string.storage_delete_downloads),
                    clearEnabled = downloadedCount > 0,
                    onClear = { confirmDeleteDownloads = true },
                )
            }

            item {
                StorageCard(
                    title = stringResource(R.string.storage_imported),
                    count = importedCount,
                    sizeLabel = formatBytes(importedBytes),
                    clearLabel = stringResource(R.string.storage_clear_imports),
                    clearEnabled = importedCount > 0,
                    onClear = { confirmClearImports = true },
                )
            }

            if (downloadedCount > 0) {
                item {
                    Text(
                        text = stringResource(R.string.storage_download_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                items(downloads, key = { it.uuid }) { episode ->
                    DownloadRow(
                        episode = episode,
                        onDelete = {
                            scope.launch { ServiceLocator.downloadManager().delete(episode) }
                        },
                    )
                }
            }
        }
    }

    if (confirmDeleteDownloads) {
        ConfirmDialog(
            title = stringResource(R.string.storage_delete_downloads),
            hint = stringResource(R.string.storage_delete_downloads_hint),
            onConfirm = {
                scope.launch { ServiceLocator.downloadManager().deleteAll() }
                confirmDeleteDownloads = false
            },
            onDismiss = { confirmDeleteDownloads = false },
        )
    }

    if (confirmClearImports) {
        ConfirmDialog(
            title = stringResource(R.string.storage_clear_imports),
            hint = stringResource(R.string.storage_clear_imports_hint),
            onConfirm = {
                viewModel.deleteLocalFiles()
                confirmClearImports = false
            },
            onDismiss = { confirmClearImports = false },
        )
    }
}

@Composable
private fun StorageCard(
    title: String,
    count: Int,
    emptyHint: String? = null,
    sizeLabel: String? = null,
    clearLabel: String,
    clearEnabled: Boolean,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            sizeLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (count == 0 && emptyHint != null) {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onClear,
                enabled = clearEnabled,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(clearLabel)
            }
        }
    }
}

@Composable
private fun DownloadRow(episode: EpisodeEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.localPath?.let { path ->
                Text(
                    text = path.substringAfterLast('/'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.DeleteSweep,
                contentDescription = stringResource(R.string.storage_delete_one),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    hint: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(hint) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.yes)) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.no)) }
        },
    )
}

/** mm:ss / h:mm:ss style byte formatter used by the storage cards. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt()} kB"
    val mb = kb / 1024
    if (mb < 1024) return "${mb.oneDecimal()} MB"
    val gb = mb / 1024
    return "${gb.oneDecimal()} GB"
}

private fun Double.oneDecimal(): String {
    val scaled = kotlin.math.round(this * 10) / 10
    return if (scaled % 1.0 == 0.0) scaled.toInt().toString() else scaled.toString()
}
