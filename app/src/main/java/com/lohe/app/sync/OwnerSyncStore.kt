package com.lohe.app.sync

import androidx.room.withTransaction
import com.lohe.app.data.PlaylistRepository
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity

data class OwnerSyncSummary(
    val subscribedPodcasts: Int,
    val totalPodcasts: Int,
    val episodes: Int,
    val played: Int,
    val inProgress: Int,
    val queued: Int,
    val playlists: Int,
)

data class OwnerSyncApplyResult(
    val podcasts: Int,
    val episodes: Int,
    val queued: Int,
    val playlists: Int,
)

object OwnerSyncStore {

    suspend fun summary(
        db: AppDatabase,
        playlistRepository: PlaylistRepository,
    ): OwnerSyncSummary {
        val podcasts = db.podcastDao().allIncludingUnsubscribed()
        val episodes = db.episodeDao().allOnce()
        val playlists = playlistRepository.playlists.value
        return OwnerSyncSummary(
            subscribedPodcasts = podcasts.count { it.isSubscribed },
            totalPodcasts = podcasts.size,
            episodes = episodes.size,
            played = episodes.count { it.playingStatus == EpisodeEntity.PLAYED },
            inProgress = episodes.count { it.playingStatus == EpisodeEntity.IN_PROGRESS },
            queued = episodes.count { it.queuePosition != null },
            playlists = playlists.size,
        )
    }

    suspend fun exportSnapshot(
        db: AppDatabase,
        playlistRepository: PlaylistRepository,
        sourcePackage: String,
        exportedAt: Long = System.currentTimeMillis(),
    ): OwnerSyncSnapshot {
        val podcasts = db.podcastDao().allIncludingUnsubscribed()
        val episodes = db.episodeDao().allOnce().map { ep ->
            // Never export app-private file paths across sandboxes.
            ep.copy(localPath = null, downloadState = DownloadState.NONE)
        }
        val history = db.listeningHistoryDao().allOnce()
        return OwnerSyncSnapshot(
            exportedAt = exportedAt,
            sourcePackage = sourcePackage,
            podcasts = podcasts,
            episodes = episodes,
            listeningHistory = history,
            playlists = playlistRepository.playlists.value,
            podcastFolders = playlistRepository.podcastFolders.value,
        )
    }

    suspend fun applySnapshot(
        db: AppDatabase,
        playlistRepository: PlaylistRepository,
        snapshot: OwnerSyncSnapshot,
    ): OwnerSyncApplyResult {
        val podcastDao = db.podcastDao()
        val episodeDao = db.episodeDao()
        val historyDao = db.listeningHistoryDao()

        val validPodcastUuids = HashSet<String>(snapshot.podcasts.size)
        var appliedEpisodes = 0
        var appliedQueued = 0

        db.withTransaction {
            // Preserve existing destination download paths so local audio files are never lost
            // or replaced with another app's unreadable private path.
            val existingDownloads = episodeDao.allOnce().associate { ep ->
                ep.uuid to (ep.localPath to ep.downloadState)
            }

            for (podcast in snapshot.podcasts) {
                podcastDao.upsertForSync(podcast)
                validPodcastUuids.add(podcast.uuid)
            }

            // Include any pre-existing podcast UUIDs already in this DB for FK safety.
            podcastDao.allIncludingUnsubscribed().forEach { validPodcastUuids.add(it.uuid) }

            // Clear old queue positions before writing the synced Up Next order so stale
            // destination-only queued items don't collide or linger.
            episodeDao.clearAllQueuePositions()

            val toWrite = snapshot.episodes
                .filter { it.podcastUuid in validPodcastUuids }
                .map { incoming ->
                    val preserved = existingDownloads[incoming.uuid]
                    incoming.copy(
                        localPath = preserved?.first,
                        downloadState = preserved?.second ?: DownloadState.NONE,
                    )
                }

            toWrite.chunked(500).forEach { batch ->
                episodeDao.upsertAll(batch)
            }
            appliedEpisodes = toWrite.size
            appliedQueued = toWrite.count { it.queuePosition != null }

            if (snapshot.listeningHistory.isNotEmpty()) {
                snapshot.listeningHistory.chunked(500).forEach { batch ->
                    historyDao.upsertAll(batch)
                }
            }
        }

        playlistRepository.replaceAll(snapshot.playlists, snapshot.podcastFolders)

        return OwnerSyncApplyResult(
            podcasts = snapshot.podcasts.size,
            episodes = appliedEpisodes,
            queued = appliedQueued,
            playlists = snapshot.playlists.size,
        )
    }
}
