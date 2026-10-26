package com.lohe.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Checks every subscription for new episodes in the background.
 *
 * Shows with auto-download on fetch their new episodes, and playlists tied to
 * a show pick them up on their own, because they are resolved from the library.
 */
class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)
        return runCatching { ServiceLocator.subscriptionRepository().refreshAllFeeds() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}

/** Schedules or cancels the periodic [FeedRefreshWorker]. */
object FeedRefreshScheduler {
    private const val WORK_NAME = "feed-refresh"

    /** WorkManager will not run a periodic job more often than this. */
    private const val MIN_MINUTES = 15L

    /** [minutes] of 0 (or less) turns the automatic check off. */
    fun schedule(context: Context, minutes: Int) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (minutes <= 0) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(
            maxOf(minutes.toLong(), MIN_MINUTES),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        // UPDATE swaps in the new interval without piling up a second job.
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
