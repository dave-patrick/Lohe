package com.lohe.app.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lohe.app.data.ServiceLocator
import com.lohe.app.data.db.entity.toSummary

/**
 * Automatic downloads that wait for Wi-Fi.
 *
 * Each episode is one unique WorkManager job with an unmetered-network
 * constraint. The system runs it as soon as the phone is on Wi-Fi (straight
 * away if it already is), batches it with other network work, and keeps it
 * across app restarts and reboots. Queuing the same episode twice keeps the
 * first job.
 */
object AutoDownloadQueue {
    private const val KEY_EPISODE = "episode"

    private fun workName(uuid: String) = "auto-download-$uuid"

    fun enqueue(context: Context, episodeUuid: String) {
        val request = OneTimeWorkRequestBuilder<AutoDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setInputData(workDataOf(KEY_EPISODE to episodeUuid))
            .addTag("auto-download")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(workName(episodeUuid), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, episodeUuid: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(episodeUuid))
    }

    internal fun episodeUuid(params: WorkerParameters): String? =
        params.inputData.getString(KEY_EPISODE)
}

/** Runs one Wi-Fi-queued download through the shared [DownloadManager]. */
class AutoDownloadWorker(
    context: Context,
    private val params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)
        val uuid = AutoDownloadQueue.episodeUuid(params) ?: return Result.success()
        // Re-read the episode: it may have been played, archived, deleted or
        // downloaded by hand while the job waited for Wi-Fi.
        val episode = ServiceLocator.subscriptionRepository().episodeOnce(uuid)?.toSummary()
            ?: return Result.success()
        if (episode.isDownloaded || episode.isArchived || episode.isDeleted || episode.isPlayed) {
            return Result.success()
        }
        ServiceLocator.downloadManager().downloadAndWait(episode)
        return Result.success()
    }
}
