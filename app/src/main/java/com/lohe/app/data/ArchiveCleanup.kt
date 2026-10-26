package com.lohe.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Removes the downloads of archived episodes once they have been archived for
 * longer than the keep time in Settings. The episodes themselves stay archived.
 */
object ArchiveCleanup {
    private const val WORK_NAME = "archive-cleanup"

    suspend fun run(now: Long = System.currentTimeMillis()): Int {
        val keepHours = ServiceLocator.settingsStore().settings.first().archiveKeepHours
        val cutoff = now - TimeUnit.HOURS.toMillis(keepHours.toLong())
        val expired = ServiceLocator.subscriptionRepository().archivedDownloadsOlderThan(cutoff)
        val downloads = ServiceLocator.downloadManager()
        expired.forEach { downloads.delete(it) }
        return expired.size
    }

    /** Checks hourly, whether or not the app is open. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ArchiveCleanupWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

class ArchiveCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)
        return runCatching { ArchiveCleanup.run() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
