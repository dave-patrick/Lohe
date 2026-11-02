package com.lohe.app.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.lohe.app.data.db.entity.toSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object EpisodeMaintenance {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val scheduler = context.getSystemService(JobScheduler::class.java)
        // Archive windows are whole hours (1, 24 or 168), so an hourly sweep is
        // enough. It used to run every 15 minutes in the background plus every
        // minute while the app was open, waking the phone far more often than
        // the settings need. Installs that still hold the old 15-minute job
        // are moved to the hourly one.
        val pending = scheduler.getPendingJob(JOB_ID)
        if (pending == null || pending.intervalMillis != INTERVAL_MS) {
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, EpisodeArchiveJobService::class.java))
                .setPeriodic(INTERVAL_MS, FLEX_MS)
                .setPersisted(true)
                .build())
        }
        // Also sweep when the process starts and whenever the archive settings
        // change, so a new setting takes effect without waiting for the job.
        scope.launch {
            ServiceLocator.settingsStore().settings
                .map { it.autoArchiveHours to it.archiveKeepHours }
                .distinctUntilChanged()
                .collect {
                safelyRun()
            }
        }
    }

    private const val JOB_ID = 4811
    private const val INTERVAL_MS = 60 * 60_000L
    private const val FLEX_MS = 15 * 60_000L

    suspend fun safelyRun(): Boolean = try {
        runOnce()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.e("EpisodeArchive", "Archive cleanup will retry", failure)
        false
    }

    suspend fun runOnce() = mutex.withLock {
        val repository = ServiceLocator.subscriptionRepository()
        val settings = ServiceLocator.settingsStore().settings.first()
        val now = System.currentTimeMillis()
        val dao = ServiceLocator.database().episodeDao()
        var afterUuid = ""
        while (true) {
            val ids = dao.dueArchiveIds(settings.autoArchiveHours >= 0,
                now - settings.autoArchiveHours.coerceAtLeast(0) * 3_600_000L,
                now - settings.archiveKeepHours * 3_600_000L, afterUuid, 200)
            if (ids.isEmpty()) break
            for (id in ids) {
                val episode = dao.get(id) ?: continue
                if (shouldAutoArchive(episode.isPlayed, episode.isArchived, episode.playedAt, settings.autoArchiveHours, now)) {
                    repository.archivePlayedIfDue(
                        episode.uuid,
                        now - settings.autoArchiveHours * 3_600_000L,
                    )
                } else if (
                    episode.isArchived && episode.archivedAt != null && now >= episode.archivedAt &&
                    now - episode.archivedAt >= settings.archiveKeepHours * 3_600_000L &&
                    (episode.localPath != null || episode.downloadState != 0)
                ) {
                    ServiceLocator.downloadManager().delete(episode.toSummary())
                }
            }
            afterUuid = ids.last()
        }
    }
}

class EpisodeArchiveJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var task: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        ServiceLocator.init(applicationContext)
        task = scope.launch {
            val success = EpisodeMaintenance.safelyRun()
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
