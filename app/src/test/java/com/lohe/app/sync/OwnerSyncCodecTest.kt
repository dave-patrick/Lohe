package com.lohe.app.sync

import com.lohe.app.data.UserPlaylist
import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import com.lohe.app.data.db.entity.PodcastEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerSyncCodecTest {

    @Test
    fun snapshotRoundTripsWithoutLeakingLocalDownloadPaths() {
        val original = OwnerSyncSnapshot(
            exportedAt = 1_732_200_000_000L,
            sourcePackage = "com.lohe.app",
            podcasts = listOf(
                PodcastEntity(
                    uuid = "pod-1",
                    title = "Signal Cast",
                    author = "Host",
                    description = "Desc",
                    artworkUrl = "https://example.com/art.png",
                    feedUrl = "https://example.com/feed.xml",
                    lastEpisodePublished = 1_732_100_000_000L,
                    subscribedAt = 1_730_000_000_000L,
                    unreadCount = 3,
                    autoDownload = true,
                    autoAddNewEpisodesToUpNext = true,
                    autoAddNewEpisodesAtTop = true,
                    isSubscribed = true,
                    feedEtag = "\"abc\"",
                    feedLastModified = "Mon, 01 Nov 2026 00:00:00 GMT",
                ),
            ),
            episodes = listOf(
                EpisodeEntity(
                    uuid = "ep-1",
                    podcastUuid = "pod-1",
                    title = "Episode One",
                    audioUrl = "https://example.com/ep1.mp3",
                    published = 1_732_100_000_000L,
                    durationSeconds = 1800,
                    sizeBytes = 12_345_678L,
                    fileType = "audio/mpeg",
                    playingStatus = EpisodeEntity.IN_PROGRESS,
                    playedAt = null,
                    playedUpToSeconds = 420L,
                    listenedSeconds = 500L,
                    starred = true,
                    isDeleted = false,
                    isArchived = false,
                    archivedAt = null,
                    queuePosition = 0,
                    lastPlayedAt = 1_732_150_000_000L,
                    localPath = "/data/user/0/com.lohe.app/files/episodes/ep-1.mp3",
                    downloadState = DownloadState.DONE,
                    chapters = """[{"title":"Intro","startMs":0}]""",
                ),
            ),
            listeningHistory = listOf(
                ListeningHistoryEntity(
                    episodeUuid = "ep-old",
                    listenedSeconds = 900L,
                    durationSeconds = 1200,
                    title = "Old Episode",
                ),
            ),
            playlists = listOf(
                UserPlaylist(
                    id = "pl-1",
                    name = "Morning Commute",
                    episodeUuids = listOf("ep-1"),
                    folder = "Daily",
                    autoDownload = false,
                    shows = listOf("pod-1"),
                    minDurationMinutes = 10,
                    maxDurationMinutes = 60,
                    finished = false,
                    createdAt = 1_731_000_000_000L,
                    snapshot = true,
                    pinned = true,
                    excludedUuids = listOf("ep-x"),
                    assignedPodcastUuids = listOf("pod-1"),
                ),
            ),
            podcastFolders = mapOf("pod-1" to "Daily"),
        )

        val encoded = OwnerSyncCodec.encodeSnapshot(original)
        val decoded = OwnerSyncCodec.decodeSnapshot(encoded)

        assertEquals(original.exportedAt, decoded.exportedAt)
        assertEquals(original.sourcePackage, decoded.sourcePackage)
        assertEquals(original.podcasts, decoded.podcasts)
        assertEquals(original.listeningHistory, decoded.listeningHistory)
        assertEquals(original.playlists, decoded.playlists)
        assertEquals(original.podcastFolders, decoded.podcastFolders)

        val decodedEp = decoded.episodes.single()
        assertEquals(original.episodes.single().copy(localPath = null, downloadState = DownloadState.NONE), decodedEp)
        assertNull(decodedEp.localPath)
        assertEquals(DownloadState.NONE, decodedEp.downloadState)
    }
}
