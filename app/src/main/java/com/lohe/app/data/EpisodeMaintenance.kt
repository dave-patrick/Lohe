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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
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
        if (scheduler.getPendingJob(4811) == null) {
            scheduler.schedule(JobInfo.Builder(4811, ComponentName(context, EpisodeArchiveJobService::class.java))
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build())
        }
        scope.launch {
            ServiceLocator.settingsStore().settings
                .map { it.autoArchiveHours to it.archiveKeepHours }
                .distinctUntilChanged()
                .collect {
                safelyRun()
            }
        }
        scope.launch {
            while (isActive) {
                delay(60_000L)
                safelyRun()
            }
        }
    }

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
