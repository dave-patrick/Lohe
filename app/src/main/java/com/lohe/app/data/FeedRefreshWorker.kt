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

object FeedRefreshScheduler {
    private const val WORK_NAME = "feed-refresh"
    private const val MIN_INTERVAL_MINUTES = 15L

    fun schedule(context: Context, minutes: Int) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (minutes <= 0) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(
            maxOf(minutes.toLong(), MIN_INTERVAL_MINUTES),
            TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
