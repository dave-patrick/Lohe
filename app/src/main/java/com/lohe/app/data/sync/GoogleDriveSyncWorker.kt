package com.lohe.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lohe.app.data.db.DatabaseExporter
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GoogleDriveSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        val settings = store.settings.first()
        if (!settings.googleDriveAutoSync) return Result.success()

        val driveManager = GoogleDriveSyncManager(applicationContext)
        return when (val auth = driveManager.requestDriveAuthorization()) {
            is DriveAuthOutcome.Authorized -> {
                val snapshot = DatabaseExporter.createSnapshotFile(applicationContext)
                    .getOrElse { return Result.retry() }
                try {
                    val upload = driveManager.uploadDatabaseBackup(auth.accessToken, snapshot)
                    upload.fold(
                        onSuccess = { summary ->
                            val stamp = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
                            store.setGoogleDriveLastSyncStatus("$summary • $stamp")
                            Result.success()
                        },
                        onFailure = { Result.retry() },
                    )
                } finally {
                    snapshot.delete()
                }
            }
            is DriveAuthOutcome.NeedsResolution -> {
                store.setGoogleDriveLastSyncStatus("Google Drive sign-in needed (tap Sync now)")
                Result.success()
            }
            is DriveAuthOutcome.Unavailable -> {
                Result.retry()
            }
        }
    }
}

object GoogleDriveSyncScheduler {
    private const val WORK_NAME = "google-drive-sync"
    private const val INTERVAL_HOURS = 6L

    fun schedule(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<GoogleDriveSyncWorker>(
            INTERVAL_HOURS,
            TimeUnit.HOURS,
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
