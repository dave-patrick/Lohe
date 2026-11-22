package com.lohe.app.ui.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lohe.app.data.db.DatabaseExporter
import com.lohe.app.data.sync.DriveAuthOutcome
import com.lohe.app.data.sync.GoogleDriveSyncManager
import com.lohe.app.data.sync.GoogleDriveSyncScheduler
import com.lohe.app.ui.theme.PlaybackSettings
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingDriveAction {
    UPLOAD,
    RESTORE,
}

@Composable
internal fun GoogleDriveSyncSection(
    settings: PlaybackSettings,
    settingsStore: SettingsStore,
    onExportDatabase: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val driveManager = remember(context) { GoogleDriveSyncManager(context) }

    var busy by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<PendingDriveAction?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun timestamp(): String = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())

    fun performUpload(accessToken: String) {
        scope.launch {
            busy = true
            statusBanner = "Backing up lohe.db to Google Drive..."
            val snapshot = DatabaseExporter.createSnapshotFile(context).getOrElse { err ->
                statusBanner = "Couldn't prepare database: ${err.localizedMessage ?: "unknown error"}"
                busy = false
                return@launch
            }
            try {
                driveManager.uploadDatabaseBackup(accessToken, snapshot).fold(
                    onSuccess = { summary ->
                        val msg = "$summary • ${timestamp()}"
                        statusBanner = msg
                        settingsStore.setGoogleDriveLastSyncStatus(msg)
                    },
                    onFailure = { err ->
                        statusBanner = "Sync failed: ${err.localizedMessage ?: "unknown error"}"
                    },
                )
            } finally {
                snapshot.delete()
                busy = false
            }
        }
    }

    fun performRestore(accessToken: String) {
        scope.launch {
            busy = true
            statusBanner = "Downloading backup from Google Drive..."
            val tempDownload = File.createTempFile("lohe_drive_restore_", ".db", context.cacheDir)
            try {
                val found = driveManager.downloadDatabaseBackup(accessToken, tempDownload).getOrElse { err ->
                    statusBanner = "Download failed: ${err.localizedMessage ?: "unknown error"}"
                    return@launch
                }
                if (!found) {
                    statusBanner = "No Google Drive backup found yet."
                    return@launch
                }
                DatabaseExporter.restoreFromFile(context, tempDownload).fold(
                    onSuccess = { bytes ->
                        val kb = (bytes + 1023L) / 1024L
                        val msg = "Restored backup ($kb KB) • ${timestamp()}"
                        settingsStore.setGoogleDriveLastSyncStatus(msg)
                        // Restart the app cleanly so Room reopens the restored database.
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        if (launchIntent != null) {
                            val restartIntent = Intent.makeRestartActivityTask(launchIntent.component)
                            context.startActivity(restartIntent)
                            Runtime.getRuntime().exit(0)
                        } else {
                            statusBanner = "$msg. Please restart Lohe."
                        }
                    },
                    onFailure = { err ->
                        statusBanner = "Restore failed: ${err.localizedMessage ?: "unknown error"}"
                    },
                )
            } finally {
                tempDownload.delete()
                busy = false
            }
        }
    }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val action = pendingAction
        pendingAction = null
        val token = driveManager.extractTokenFromResult(result.resultCode, result.data)
        if (token.isNullOrBlank()) {
            busy = false
            statusBanner = "Google Drive authorization canceled or failed."
            return@rememberLauncherForActivityResult
        }
        when (action) {
            PendingDriveAction.UPLOAD -> performUpload(token)
            PendingDriveAction.RESTORE -> performRestore(token)
            null -> busy = false
        }
    }

    fun startDriveAction(action: PendingDriveAction) {
        if (busy) return
        scope.launch {
            busy = true
            statusBanner = "Authorizing Google Drive..."
            when (val auth = driveManager.requestDriveAuthorization()) {
                is DriveAuthOutcome.Authorized -> {
                    when (action) {
                        PendingDriveAction.UPLOAD -> performUpload(auth.accessToken)
                        PendingDriveAction.RESTORE -> performRestore(auth.accessToken)
                    }
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    pendingAction = action
                    runCatching {
                        authLauncher.launch(IntentSenderRequest.Builder(auth.pendingIntent).build())
                    }.onFailure { err ->
                        pendingAction = null
                        busy = false
                        statusBanner = "Could not open Google sign-in: ${err.localizedMessage ?: "unknown error"}"
                    }
                }
                is DriveAuthOutcome.Unavailable -> {
                    busy = false
                    statusBanner = auth.reason
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Back up your Lohe database (podcasts, episodes, playlists, Up Next, and listening history) to your private Google Drive AppData folder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val statusText = statusBanner
            ?: settings.googleDriveLastSyncStatus.ifBlank { "Not synced yet." }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Button(
            onClick = { startDriveAction(PendingDriveAction.UPLOAD) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Working..." else "Sync to Google Drive now")
        }

        OutlinedButton(
            onClick = { confirmRestore = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore from Google Drive")
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        ToggleRow(
            label = "Auto-sync to Google Drive",
            hint = "Automatically back up your library every 6 hours when connected.",
            checked = settings.googleDriveAutoSync,
            onChange = { enabled ->
                scope.launch {
                    settingsStore.setGoogleDriveAutoSync(enabled)
                    GoogleDriveSyncScheduler.schedule(context, enabled)
                }
            },
        )

        if (onExportDatabase != null) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            OutlinedButton(
                onClick = onExportDatabase,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export local lohe.db copy")
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Google Drive?") },
            text = {
                Text(
                    "This replaces your local Lohe database with the latest backup from Google Drive and restarts the app.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        startDriveAction(PendingDriveAction.RESTORE)
                    },
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
