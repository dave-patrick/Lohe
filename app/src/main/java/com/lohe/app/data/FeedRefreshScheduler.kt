package com.lohe.app.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Keeps episode lists current without the listener pulling to refresh.
 *
 * Two triggers share one throttle: a periodic job that runs in the background
 * while the device has a network, and a refresh when the app is opened. Both
 * go through [refreshIfDue] so they cannot stack up or hammer feed hosts.
 */
internal object FeedRefreshScheduler {
    private const val JOB_ID = 4812
    private const val PREFS = "feed_refresh"
    private const val KEY_LAST_SUCCESS = "last_success_millis"
    private const val TAG = "FeedRefresh"

    /** Background cadence. JobScheduler will not run a periodic job more often than 15 minutes. */
    private const val PERIOD_MILLIS = 60 * 60_000L

    /** Minimum gap between refreshes, so reopening the app repeatedly stays cheap. */
    internal const val MIN_INTERVAL_MILLIS = 15 * 60_000L

    private val mutex = Mutex()

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, FeedRefreshJobService::class.java))
                .setPeriodic(PERIOD_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build(),
        )
    }

    /**
     * Refreshes every subscription unless one succeeded recently.
     *
     * Returns false only when the refresh failed outright, so the job can ask
     * to be retried. A skipped or already-running refresh counts as success.
     */
    suspend fun refreshIfDue(context: Context, force: Boolean = false): Boolean {
        // A refresh already in flight is doing the work; do not queue a second.
        if (!mutex.tryLock()) return true
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = prefs.getLong(KEY_LAST_SUCCESS, 0L)
            if (!isDue(last, now, force)) return true

            val summary = ServiceLocator.subscriptionRepository().refreshAllFeeds()
            // Only stamp when something came back; an offline sweep that fails
            // every show should be retried soon, not throttled for 15 minutes.
            if (summary.total == 0 || summary.refreshed > 0) {
                prefs.edit().putLong(KEY_LAST_SUCCESS, now).apply()
            }
            return summary.total == 0 || summary.refreshed > 0
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "Feed refresh will retry", failure)
            return false
        } finally {
            mutex.unlock()
        }
    }

    internal fun isDue(lastSuccessMillis: Long, nowMillis: Long, force: Boolean = false): Boolean =
        force || lastSuccessMillis <= 0L ||
            nowMillis < lastSuccessMillis || // clock moved backwards
            nowMillis - lastSuccessMillis >= MIN_INTERVAL_MILLIS
}

class FeedRefreshJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var task: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        ServiceLocator.init(applicationContext)
        task = scope.launch {
            val success = FeedRefreshScheduler.refreshIfDue(applicationContext)
            jobFinished(params, !success)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        task?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
