package com.example.data.sync

import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeBookmarkEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.network.ParsedLibraryBackup
import com.example.data.network.RssAndOpmlParser
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/**
 * Tracks local user mutations and tombstones in DataStore so two-way Cloud Drive sync
 * (Google Drive, OneDrive, Dropbox, Nextcloud/WebDAV, Box, etc.) can cleanly merge
 * Playlists, Up Next queue & order, Podcasts, and Episode Status without needing a central backend.
 */
data class DriveSyncLocalJournal(
    val lastSyncedAt: Long = 0L,
    val upNextUpdatedAt: Long = 0L,
    val lastPlayingEpisodeKey: String = "",
    val lastPlayingUpdatedAt: Long = 0L,
    val deletedPlaylists: Map<String, Long> = emptyMap(),
    val playlistUpdatedAt: Map<String, Long> = emptyMap(),
    val unsubscribedPodcasts: Map<String, Long> = emptyMap(),
    val podcastUpdatedAt: Map<String, Long> = emptyMap(),
    val removedFromUpNext: Map<String, Long> = emptyMap(),
    val addedToUpNext: Map<String, Long> = emptyMap(),
    val episodeStatusUpdatedAt: Map<String, Long> = emptyMap()
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("lastSyncedAt", lastSyncedAt)
        root.put("upNextUpdatedAt", upNextUpdatedAt)
        root.put("lastPlayingEpisodeKey", lastPlayingEpisodeKey)
        root.put("lastPlayingUpdatedAt", lastPlayingUpdatedAt)
        root.put("deletedPlaylists", mapToJsonObject(deletedPlaylists))
        root.put("playlistUpdatedAt", mapToJsonObject(playlistUpdatedAt))
        root.put("unsubscribedPodcasts", mapToJsonObject(unsubscribedPodcasts))
        root.put("podcastUpdatedAt", mapToJsonObject(podcastUpdatedAt))
        root.put("removedFromUpNext", mapToJsonObject(removedFromUpNext))
        root.put("addedToUpNext", mapToJsonObject(addedToUpNext))
        root.put("episodeStatusUpdatedAt", mapToJsonObject(episodeStatusUpdatedAt))
        return root.toString()
    }

    companion object {
        private const val MAX_ENTRY_COUNT = 1500

        fun fromJsonString(raw: String?): DriveSyncLocalJournal {
            if (raw.isNullOrBlank()) return DriveSyncLocalJournal()
            return runCatching {
                val root = JSONObject(raw)
                DriveSyncLocalJournal(
                    lastSyncedAt = root.optLong("lastSyncedAt", 0L),
                    upNextUpdatedAt = root.optLong("upNextUpdatedAt", 0L),
                    lastPlayingEpisodeKey = root.optString("lastPlayingEpisodeKey", ""),
                    lastPlayingUpdatedAt = root.optLong("lastPlayingUpdatedAt", 0L),
                    deletedPlaylists = jsonObjectToMap(root.optJSONObject("deletedPlaylists")),
                    playlistUpdatedAt = jsonObjectToMap(root.optJSONObject("playlistUpdatedAt")),
                    unsubscribedPodcasts = jsonObjectToMap(root.optJSONObject("unsubscribedPodcasts")),
                    podcastUpdatedAt = jsonObjectToMap(root.optJSONObject("podcastUpdatedAt")),
                    removedFromUpNext = jsonObjectToMap(root.optJSONObject("removedFromUpNext")),
                    addedToUpNext = jsonObjectToMap(root.optJSONObject("addedToUpNext")),
                    episodeStatusUpdatedAt = jsonObjectToMap(root.optJSONObject("episodeStatusUpdatedAt"))
                )
            }.getOrDefault(DriveSyncLocalJournal())
        }

        fun trimMap(map: Map<String, Long>): Map<String, Long> {
            if (map.size <= MAX_ENTRY_COUNT) return map
            return map.entries
                .sortedByDescending { it.value }
                .take(MAX_ENTRY_COUNT)
                .associate { it.key to it.value }
        }

        private fun mapToJsonObject(map: Map<String, Long>): JSONObject {
            val obj = JSONObject()
            trimMap(map).forEach { (k, v) -> obj.put(k, v) }
            return obj
        }

        private fun jsonObjectToMap(obj: JSONObject?): Map<String, Long> {
            if (obj == null) return emptyMap()
            val result = linkedMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optLong(key, 0L)
                if (key.isNotBlank() && value > 0L) {
                    result[key] = value
                }
            }
            return result
        }
    }
}

data class DriveUpNextQueueEntry(
    val episodeKey: String,
    val episodeId: String,
    val podcastFeedUrl: String,
    val guid: String,
    val audioUrl: String,
    val title: String,
    val order: Int
)

data class DriveSyncMetadataEnvelope(
    val schemaVersion: Int = 2,
    val syncedAt: Long = 0L,
    val upNextUpdatedAt: Long = 0L,
    val lastPlayingEpisodeKey: String = "",
    val lastPlayingUpdatedAt: Long = 0L,
    val upNextQueue: List<DriveUpNextQueueEntry> = emptyList(),
    val deletedPlaylists: Map<String, Long> = emptyMap(),
    val playlistUpdatedAt: Map<String, Long> = emptyMap(),
    val unsubscribedPodcasts: Map<String, Long> = emptyMap(),
    val podcastUpdatedAt: Map<String, Long> = emptyMap(),
    val removedFromUpNext: Map<String, Long> = emptyMap(),
    val addedToUpNext: Map<String, Long> = emptyMap(),
    val episodeStatusUpdatedAt: Map<String, Long> = emptyMap()
)

data class DriveSyncSummary(
    val podcastsSynced: Int,
    val playlistsSynced: Int,
    val upNextCount: Int,
    val episodeStatusesUpdated: Int,
    val bookmarksSynced: Int
) {
    fun toShortBanner(providerTitle: String): String =
        "Synced via $providerTitle • $podcastsSynced shows, $playlistsSynced playlists, $upNextCount Up Next, $episodeStatusesUpdated episode updates"
}

data class DriveMergeResult(
    val mergedPodcasts: List<PodcastEntity>,
    val mergedEpisodes: List<EpisodeEntity>,
    val mergedPlaylists: List<PlaylistEntity>,
    val deletedPodcastIds: Set<String>,
    val deletedPlaylistIds: Set<String>,
    val mergedBookmarks: List<EpisodeBookmarkEntity>,
    val updatedJournal: DriveSyncLocalJournal,
    val resolvedLastPlayingEpisodeId: String?,
    val summary: DriveSyncSummary,
    val canonicalJsonPayload: String
)

object DriveSyncEngine {
    const val DRIVE_SYNC_DEFAULT_FILENAME = "lohe_cloud_sync.json"

    fun normalizeFeedKey(feedUrl: String, fallbackId: String = "", title: String = ""): String {
        val cleanUrl = feedUrl.trim().lowercase(Locale.ROOT).trimEnd('/')
        if (cleanUrl.isNotBlank() && !cleanUrl.startsWith("https://example.org/feed/")) {
            return "feed:$cleanUrl"
        }
        if (fallbackId.isNotBlank()) return "id:${fallbackId.trim()}"
        val cleanTitle = title.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        return "title:$cleanTitle"
    }

    fun episodeCanonicalKey(episode: EpisodeEntity, podcastFeedUrl: String = ""): String {
        val showScope = podcastFeedUrl.trim().lowercase(Locale.ROOT).trimEnd('/')
            .ifBlank { episode.podcastId.trim() }
        val cleanGuid = episode.guid.trim()
        if (cleanGuid.isNotBlank()) return "$showScope|guid:$cleanGuid"
        val cleanAudio = episode.audioUrl.trim()
        if (cleanAudio.isNotBlank()) return "$showScope|audio:$cleanAudio"
        val cleanTitle = episode.title.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        return if (cleanTitle.isNotBlank()) {
            "$showScope|title:$cleanTitle|${episode.publishedAt / 60_000L}"
        } else {
            "id:${episode.id}"
        }
    }

    fun buildSyncPayload(
        podcasts: List<PodcastEntity>,
        episodes: List<EpisodeEntity>,
        playlists: List<PlaylistEntity>,
        bookmarks: List<EpisodeBookmarkEntity>,
        journal: DriveSyncLocalJournal,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val compactForLargeLibrary = episodes.size > 500
        val trimmedEpisodes = if (compactForLargeLibrary) {
            episodes.map { ep ->
                if (ep.descriptionHtml.isNotEmpty() || ep.transcriptText.isNotEmpty() || ep.chaptersJson.isNotEmpty()) {
                    ep.copy(descriptionHtml = "", transcriptText = "", chaptersJson = "")
                } else {
                    ep
                }
            }
        } else {
            episodes
        }
        val baseJson = RssAndOpmlParser().exportJsonBackup(podcasts, trimmedEpisodes, playlists, bookmarks)
        val root = JSONObject(baseJson)
        val podFeedById = podcasts.associateBy { it.id }

        val upNextList = episodes
            .filter { it.inUpNext && !it.isPlayed && !it.isArchived }
            .sortedWith(compareBy<EpisodeEntity> { it.upNextOrder }.thenByDescending { it.publishedAt })

        val upNextArr = JSONArray()
        upNextList.forEachIndexed { index, ep ->
            val feedUrl = podFeedById[ep.podcastId]?.feedUrl.orEmpty()
            upNextArr.put(
                JSONObject().apply {
                    put("episodeKey", episodeCanonicalKey(ep, feedUrl))
                    put("episodeId", ep.id)
                    put("podcastFeedUrl", feedUrl)
                    put("guid", ep.guid)
                    put("audioUrl", ep.audioUrl)
                    put("title", ep.title)
                    put("order", index)
                }
            )
        }

        val syncObj = JSONObject().apply {
            put("schemaVersion", 2)
            put("syncedAt", nowMs)
            put("upNextUpdatedAt", journal.upNextUpdatedAt)
            put("lastPlayingEpisodeKey", journal.lastPlayingEpisodeKey)
            put("lastPlayingUpdatedAt", journal.lastPlayingUpdatedAt)
            put("upNextQueue", upNextArr)
            put("deletedPlaylists", mapToJson(journal.deletedPlaylists))
            put("playlistUpdatedAt", mapToJson(journal.playlistUpdatedAt))
            put("unsubscribedPodcasts", mapToJson(journal.unsubscribedPodcasts))
            put("podcastUpdatedAt", mapToJson(journal.podcastUpdatedAt))
            put("removedFromUpNext", mapToJson(journal.removedFromUpNext))
            put("addedToUpNext", mapToJson(journal.addedToUpNext))
            put("episodeStatusUpdatedAt", mapToJson(journal.episodeStatusUpdatedAt))
        }
        root.put("driveSync", syncObj)
        return root.toString()
    }

    fun parseRemoteSyncPayload(rawJson: String): Pair<ParsedLibraryBackup, DriveSyncMetadataEnvelope> {
        val parser = RssAndOpmlParser()
        val backup = parser.parseJsonBackup(rawJson)
        val root = JSONObject(rawJson.trim())
        val exportedAt = root.optLong("exportedAt", 0L)
        val syncObj = root.optJSONObject("driveSync")

        if (syncObj == null) {
            val podFeedById = backup.podcasts.associateBy { it.id }
            val fallbackQueue = backup.episodes
                .filter { it.inUpNext && !it.isPlayed && !it.isArchived }
                .sortedWith(compareBy<EpisodeEntity> { it.upNextOrder }.thenByDescending { it.publishedAt })
                .mapIndexed { idx, ep ->
                    DriveUpNextQueueEntry(
                        episodeKey = episodeCanonicalKey(ep, podFeedById[ep.podcastId]?.feedUrl.orEmpty()),
                        episodeId = ep.id,
                        podcastFeedUrl = podFeedById[ep.podcastId]?.feedUrl.orEmpty(),
                        guid = ep.guid,
                        audioUrl = ep.audioUrl,
                        title = ep.title,
                        order = idx
                    )
                }
            return backup to DriveSyncMetadataEnvelope(
                schemaVersion = 1,
                syncedAt = exportedAt,
                upNextUpdatedAt = exportedAt,
                upNextQueue = fallbackQueue
            )
        }

        val queueArr = syncObj.optJSONArray("upNextQueue") ?: JSONArray()
        val queueEntries = buildList {
            for (i in 0 until queueArr.length()) {
                val obj = queueArr.optJSONObject(i) ?: continue
                add(
                    DriveUpNextQueueEntry(
                        episodeKey = obj.optString("episodeKey", ""),
                        episodeId = obj.optString("episodeId", ""),
                        podcastFeedUrl = obj.optString("podcastFeedUrl", ""),
                        guid = obj.optString("guid", ""),
                        audioUrl = obj.optString("audioUrl", ""),
                        title = obj.optString("title", ""),
                        order = obj.optInt("order", i)
                    )
                )
            }
        }
        val env = DriveSyncMetadataEnvelope(
            schemaVersion = syncObj.optInt("schemaVersion", 2),
            syncedAt = syncObj.optLong("syncedAt", exportedAt),
            upNextUpdatedAt = syncObj.optLong("upNextUpdatedAt", exportedAt),
            lastPlayingEpisodeKey = syncObj.optString("lastPlayingEpisodeKey", ""),
            lastPlayingUpdatedAt = syncObj.optLong("lastPlayingUpdatedAt", 0L),
            upNextQueue = queueEntries,
            deletedPlaylists = jsonToLongMap(syncObj.optJSONObject("deletedPlaylists")),
            playlistUpdatedAt = jsonToLongMap(syncObj.optJSONObject("playlistUpdatedAt")),
            unsubscribedPodcasts = jsonToLongMap(syncObj.optJSONObject("unsubscribedPodcasts")),
            podcastUpdatedAt = jsonToLongMap(syncObj.optJSONObject("podcastUpdatedAt")),
            removedFromUpNext = jsonToLongMap(syncObj.optJSONObject("removedFromUpNext")),
            addedToUpNext = jsonToLongMap(syncObj.optJSONObject("addedToUpNext")),
            episodeStatusUpdatedAt = jsonToLongMap(syncObj.optJSONObject("episodeStatusUpdatedAt"))
        )
        return backup to env
    }

    fun mergeSnapshots(
        localPodcasts: List<PodcastEntity>,
        localEpisodes: List<EpisodeEntity>,
        localPlaylists: List<PlaylistEntity>,
        localBookmarks: List<EpisodeBookmarkEntity>,
        localJournal: DriveSyncLocalJournal,
        remoteRawJson: String?,
        pullOnly: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): DriveMergeResult {
        if (remoteRawJson.isNullOrBlank()) {
            val payload = buildSyncPayload(
                podcasts = localPodcasts,
                episodes = localEpisodes,
                playlists = localPlaylists,
                bookmarks = localBookmarks,
                journal = localJournal.copy(lastSyncedAt = nowMs),
                nowMs = nowMs
            )
            val updatedJournal = localJournal.copy(lastSyncedAt = nowMs)
            return DriveMergeResult(
                mergedPodcasts = localPodcasts,
                mergedEpisodes = localEpisodes,
                mergedPlaylists = localPlaylists,
                deletedPodcastIds = emptySet(),
                deletedPlaylistIds = emptySet(),
                mergedBookmarks = localBookmarks,
                updatedJournal = updatedJournal,
                resolvedLastPlayingEpisodeId = null,
                summary = DriveSyncSummary(
                    podcastsSynced = localPodcasts.count { it.isSubscribed },
                    playlistsSynced = localPlaylists.size,
                    upNextCount = localEpisodes.count { it.inUpNext && !it.isPlayed && !it.isArchived },
                    episodeStatusesUpdated = 0,
                    bookmarksSynced = localBookmarks.size
                ),
                canonicalJsonPayload = payload
            )
        }

        val (remoteBackup, remoteEnv) = parseRemoteSyncPayload(remoteRawJson)

        // 1. Merge Podcasts (subscriptions, unsubscribes, auto-queue, per-show audio overrides)
        val mergedPodcasts = localPodcasts.toMutableList()
        val remotePodToLocalPodId = mutableMapOf<String, String>()
        val mergedPodcastUpdatedAt = mergeTimestampMaps(localJournal.podcastUpdatedAt, remoteEnv.podcastUpdatedAt)
        val mergedUnsubscribed = mergeTimestampMaps(localJournal.unsubscribedPodcasts, remoteEnv.unsubscribedPodcasts)

        for (remotePod in remoteBackup.podcasts) {
            val matchedIdx = findMatchingPodcastIndex(mergedPodcasts, remotePod)
            val remoteFeedKey = normalizeFeedKey(remotePod.feedUrl, remotePod.id, remotePod.title)
            val remoteUpdateTs = remoteEnv.podcastUpdatedAt[remoteFeedKey]
                ?: remotePod.lastSyncedAt.takeIf { it > 0L }
                ?: remoteEnv.syncedAt

            if (matchedIdx >= 0) {
                val localPod = mergedPodcasts[matchedIdx]
                val localFeedKey = normalizeFeedKey(localPod.feedUrl, localPod.id, localPod.title)
                remotePodToLocalPodId[remotePod.id] = localPod.id

                val localUpdateTs = localJournal.podcastUpdatedAt[localFeedKey] ?: localPod.lastSyncedAt
                val unsubTs = maxOf(
                    localJournal.unsubscribedPodcasts[localFeedKey] ?: 0L,
                    localJournal.unsubscribedPodcasts[remoteFeedKey] ?: 0L,
                    remoteEnv.unsubscribedPodcasts[localFeedKey] ?: 0L,
                    remoteEnv.unsubscribedPodcasts[remoteFeedKey] ?: 0L
                )

                val remoteConfigNewer = pullOnly || remoteUpdateTs > localUpdateTs
                val resolvedSubscribed = when {
                    unsubTs > maxOf(localUpdateTs, remoteUpdateTs) -> false
                    !localPod.isSubscribed && (localJournal.unsubscribedPodcasts[localFeedKey] ?: 0L) > remoteUpdateTs -> false
                    !remotePod.isSubscribed && (remoteEnv.unsubscribedPodcasts[remoteFeedKey] ?: 0L) > localUpdateTs -> false
                    else -> localPod.isSubscribed || remotePod.isSubscribed
                }

                val mergedPod = localPod.copy(
                    artworkUrl = localPod.artworkUrl.ifBlank { remotePod.artworkUrl },
                    description = localPod.description.ifBlank { remotePod.description },
                    category = localPod.category.ifBlank { remotePod.category },
                    autoQueueMode = if (remoteConfigNewer) remotePod.autoQueueMode else localPod.autoQueueMode,
                    useCustomAudio = if (remoteConfigNewer) remotePod.useCustomAudio else localPod.useCustomAudio,
                    customSpeed = if (remoteConfigNewer) remotePod.customSpeed else localPod.customSpeed,
                    customSkipSilence = if (remoteConfigNewer) remotePod.customSkipSilence else localPod.customSkipSilence,
                    customVolumeBoostDb = if (remoteConfigNewer) remotePod.customVolumeBoostDb else localPod.customVolumeBoostDb,
                    notifyNewEpisodes = if (remoteConfigNewer) remotePod.notifyNewEpisodes else localPod.notifyNewEpisodes,
                    isSubscribed = resolvedSubscribed,
                    lastSyncedAt = maxOf(localPod.lastSyncedAt, remotePod.lastSyncedAt)
                )
                mergedPodcasts[matchedIdx] = mergedPod
                mergedPodcastUpdatedAt[localFeedKey] = maxOf(localUpdateTs, remoteUpdateTs)
            } else {
                val unsubLocally = localJournal.unsubscribedPodcasts[remoteFeedKey] ?: 0L
                if (unsubLocally <= remoteUpdateTs || pullOnly) {
                    val inserted = if (unsubLocally > remoteUpdateTs && !pullOnly) {
                        remotePod.copy(isSubscribed = false)
                    } else {
                        remotePod
                    }
                    mergedPodcasts.add(inserted)
                    remotePodToLocalPodId[remotePod.id] = inserted.id
                    mergedPodcastUpdatedAt[remoteFeedKey] = remoteUpdateTs
                }
            }
        }

        // Also apply remote unsubscribe tombstones to local shows absent from remoteBackup.podcasts
        val deletedPodcastIdsToApply = mutableSetOf<String>()
        if (!pullOnly) {
            val podIter = mergedPodcasts.iterator()
            while (podIter.hasNext()) {
                val localPod = podIter.next()
                val feedKey = normalizeFeedKey(localPod.feedUrl, localPod.id, localPod.title)
                val unsubTs = mergedUnsubscribed[feedKey] ?: 0L
                val podTs = maxOf(
                    localJournal.podcastUpdatedAt[feedKey] ?: 0L,
                    remoteEnv.podcastUpdatedAt[feedKey] ?: 0L
                )
                if (unsubTs > podTs) {
                    deletedPodcastIdsToApply.add(localPod.id)
                    podIter.remove()
                }
            }
        }

        val podById = mergedPodcasts.associateBy { it.id }

        // 2. Merge Episodes (status: isPlayed, playedAt, resumePositionMs, isStarred, isArchived)
        val workingEpisodes = localEpisodes
            .filterNot { it.podcastId in deletedPodcastIdsToApply }
            .toMutableList()
        val remoteEpToLocalEpId = mutableMapOf<String, String>()
        val mergedEpStatusUpdatedAt = mergeTimestampMaps(
            localJournal.episodeStatusUpdatedAt,
            remoteEnv.episodeStatusUpdatedAt
        )
        var episodeStatusesUpdated = 0

        for (remoteEp in remoteBackup.episodes) {
            val mappedPodId = remotePodToLocalPodId[remoteEp.podcastId] ?: remoteEp.podcastId
            if (mappedPodId in deletedPodcastIdsToApply) continue
            val mappedPod = podById[mappedPodId]
            val podFeedUrl = mappedPod?.feedUrl.orEmpty()
            val matchedIdx = findMatchingEpisodeIndex(workingEpisodes, remoteEp, mappedPodId)
            val remoteKey = episodeCanonicalKey(remoteEp, podFeedUrl)

            if (matchedIdx >= 0) {
                val localEp = workingEpisodes[matchedIdx]
                val localKey = episodeCanonicalKey(localEp, podFeedUrl)
                remoteEpToLocalEpId[remoteEp.id] = localEp.id

                val localStatusTs = maxOf(
                    localJournal.episodeStatusUpdatedAt[localKey] ?: 0L,
                    localJournal.episodeStatusUpdatedAt[remoteKey] ?: 0L,
                    localEp.playedAt
                )
                val remoteExplicitStatusTs = maxOf(
                    remoteEnv.episodeStatusUpdatedAt[remoteKey] ?: 0L,
                    remoteEnv.episodeStatusUpdatedAt[localKey] ?: 0L,
                    remoteEp.playedAt
                )
                val remoteStatusTs = if (remoteExplicitStatusTs > 0L) {
                    remoteExplicitStatusTs
                } else if (remoteEnv.schemaVersion < 2 &&
                    (remoteEp.isPlayed || remoteEp.resumePositionMs > 0L || remoteEp.isStarred || remoteEp.isArchived)
                ) {
                    remoteEnv.syncedAt
                } else {
                    0L
                }

                val effectiveDuration = maxOf(localEp.durationMs, remoteEp.durationMs)
                val remoteNearComplete = effectiveDuration >= 10_000L &&
                    remoteEp.resumePositionMs.toDouble() / effectiveDuration.toDouble() >= 0.98
                val localNearComplete = effectiveDuration >= 10_000L &&
                    localEp.resumePositionMs.toDouble() / effectiveDuration.toDouble() >= 0.98
                val remotePlayedEff = remoteEp.isPlayed || remoteNearComplete
                val localPlayedEff = localEp.isPlayed || localNearComplete

                val localExplicitUnplayReset = !localPlayedEff &&
                    (localJournal.episodeStatusUpdatedAt[localKey] ?: 0L) > maxOf(remoteEp.playedAt, remoteExplicitStatusTs)
                val remoteExplicitUnplayReset = !remotePlayedEff &&
                    (remoteEnv.episodeStatusUpdatedAt[remoteKey] ?: 0L) > maxOf(localEp.playedAt, localStatusTs.takeIf { localPlayedEff } ?: 0L)

                val mergedPlayed: Boolean
                val mergedPlayedAt: Long
                val mergedResumeMs: Long
                when {
                    pullOnly && remoteStatusTs > 0L -> {
                        mergedPlayed = remotePlayedEff
                        mergedPlayedAt = remoteEp.playedAt
                        mergedResumeMs = if (mergedPlayed) 0L else remoteEp.resumePositionMs
                    }
                    localExplicitUnplayReset -> {
                        mergedPlayed = false
                        mergedPlayedAt = 0L
                        mergedResumeMs = localEp.resumePositionMs
                    }
                    remoteExplicitUnplayReset -> {
                        mergedPlayed = false
                        mergedPlayedAt = 0L
                        mergedResumeMs = remoteEp.resumePositionMs
                    }
                    localPlayedEff || remotePlayedEff -> {
                        mergedPlayed = true
                        mergedPlayedAt = maxOf(localEp.playedAt, remoteEp.playedAt)
                            .let { if (it > 0L) it else nowMs }
                        mergedResumeMs = 0L
                    }
                    else -> {
                        mergedPlayed = false
                        mergedPlayedAt = 0L
                        mergedResumeMs = maxOf(localEp.resumePositionMs, remoteEp.resumePositionMs)
                    }
                }

                val mergedStarred = when {
                    pullOnly -> remoteEp.isStarred
                    localStatusTs > remoteStatusTs + 500L -> localEp.isStarred
                    remoteStatusTs > localStatusTs + 500L -> remoteEp.isStarred
                    else -> localEp.isStarred || remoteEp.isStarred
                }
                val mergedArchived = when {
                    pullOnly -> remoteEp.isArchived
                    localStatusTs > remoteStatusTs + 500L -> localEp.isArchived
                    remoteStatusTs > localStatusTs + 500L -> remoteEp.isArchived
                    else -> localEp.isArchived || remoteEp.isArchived
                }

                val updated = localEp.copy(
                    guid = localEp.guid.ifBlank { remoteEp.guid },
                    audioUrl = localEp.audioUrl.ifBlank { remoteEp.audioUrl },
                    artworkUrl = localEp.artworkUrl.ifBlank { remoteEp.artworkUrl },
                    descriptionHtml = localEp.descriptionHtml.ifBlank { remoteEp.descriptionHtml },
                    durationMs = if (localEp.durationMs > 5_000L) localEp.durationMs else effectiveDuration,
                    chaptersUrl = localEp.chaptersUrl.ifBlank { remoteEp.chaptersUrl },
                    chaptersJson = localEp.chaptersJson.ifBlank { remoteEp.chaptersJson },
                    transcriptUrl = localEp.transcriptUrl.ifBlank { remoteEp.transcriptUrl },
                    transcriptMimeType = localEp.transcriptMimeType.ifBlank { remoteEp.transcriptMimeType },
                    transcriptText = localEp.transcriptText.ifBlank { remoteEp.transcriptText },
                    isPlayed = mergedPlayed,
                    playedAt = mergedPlayedAt,
                    resumePositionMs = mergedResumeMs,
                    isStarred = mergedStarred,
                    isArchived = mergedArchived
                )
                if (updated != localEp) {
                    episodeStatusesUpdated++
                    workingEpisodes[matchedIdx] = updated
                }
                mergedEpStatusUpdatedAt[localKey] = maxOf(localStatusTs, remoteStatusTs)
            } else {
                val effectiveDuration = remoteEp.durationMs.coerceAtLeast(0L)
                val nearComplete = effectiveDuration >= 10_000L &&
                    remoteEp.resumePositionMs.toDouble() / effectiveDuration.toDouble() >= 0.98
                val isPlayed = remoteEp.isPlayed || nearComplete
                val inserted = remoteEp.copy(
                    podcastId = mappedPodId,
                    podcastTitle = mappedPod?.title ?: remoteEp.podcastTitle,
                    isPlayed = isPlayed,
                    playedAt = if (isPlayed) remoteEp.playedAt.takeIf { it > 0L } ?: nowMs else 0L,
                    resumePositionMs = if (isPlayed) 0L else remoteEp.resumePositionMs,
                    isDownloaded = false,
                    localFilePath = ""
                )
                workingEpisodes.add(inserted)
                remoteEpToLocalEpId[remoteEp.id] = inserted.id
                if (inserted.isPlayed || inserted.resumePositionMs > 0L || inserted.isStarred || inserted.isArchived) {
                    episodeStatusesUpdated++
                }
            }
        }

        // 3. Merge Up Next Queue & Order
        val mergedRemovedUpNext = mergeTimestampMaps(localJournal.removedFromUpNext, remoteEnv.removedFromUpNext)
        val mergedAddedUpNext = mergeTimestampMaps(localJournal.addedToUpNext, remoteEnv.addedToUpNext)

        val remoteQueueIndexByLocalId = linkedMapOf<String, Int>()
        remoteEnv.upNextQueue.sortedBy { it.order }.forEachIndexed { idx, entry ->
            val localId = remoteEpToLocalEpId[entry.episodeId]
                ?: workingEpisodes.firstOrNull { ep ->
                    val epFeed = podById[ep.podcastId]?.feedUrl.orEmpty()
                    episodeCanonicalKey(ep, epFeed) == entry.episodeKey ||
                        (entry.guid.isNotBlank() && ep.guid == entry.guid) ||
                        (entry.audioUrl.isNotBlank() && ep.audioUrl == entry.audioUrl)
                }?.id
            if (localId != null && localId !in remoteQueueIndexByLocalId) {
                remoteQueueIndexByLocalId[localId] = idx
            }
        }

        val remoteOrderWins = pullOnly || remoteEnv.upNextUpdatedAt > localJournal.upNextUpdatedAt
        val queuedCandidates = workingEpisodes.filter { ep ->
            if (ep.isPlayed || ep.isArchived) return@filter false
            val pod = podById[ep.podcastId]
            if (pod != null && !pod.isSubscribed) return@filter false
            val epKey = episodeCanonicalKey(ep, pod?.feedUrl.orEmpty())
            val inLocal = ep.inUpNext
            val inRemote = ep.id in remoteQueueIndexByLocalId ||
                remoteBackup.episodes.any { r ->
                    (remoteEpToLocalEpId[r.id] ?: r.id) == ep.id && r.inUpNext && !r.isPlayed && !r.isArchived
                }
            if (!inLocal && !inRemote) return@filter false

            val removedTs = mergedRemovedUpNext[epKey] ?: 0L
            val explicitAddedTs = mergedAddedUpNext[epKey]
            val addedTs = explicitAddedTs ?: maxOf(
                if (inLocal) localJournal.upNextUpdatedAt else 0L,
                if (inRemote) remoteEnv.upNextUpdatedAt else 0L
            )
            when {
                pullOnly -> inRemote
                removedTs > addedTs -> false
                else -> true
            }
        }

        val orderedQueue = if (remoteOrderWins) {
            queuedCandidates.sortedWith(
                compareBy<EpisodeEntity>(
                    { remoteQueueIndexByLocalId[it.id] ?: Int.MAX_VALUE },
                    { if (it.inUpNext) it.upNextOrder else Int.MAX_VALUE },
                    { -it.publishedAt }
                )
            )
        } else {
            queuedCandidates.sortedWith(
                compareBy<EpisodeEntity>(
                    { if (it.inUpNext) it.upNextOrder else Int.MAX_VALUE },
                    { remoteQueueIndexByLocalId[it.id] ?: Int.MAX_VALUE },
                    { -it.publishedAt }
                )
            )
        }
        val finalOrderByEpId = orderedQueue.mapIndexed { idx, ep -> ep.id to idx }.toMap()

        val finalEpisodes = workingEpisodes.map { ep ->
            val queueIdx = finalOrderByEpId[ep.id]
            val shouldBeInQueue = queueIdx != null
            val targetOrder = queueIdx ?: 0
            if (ep.inUpNext != shouldBeInQueue || (shouldBeInQueue && ep.upNextOrder != targetOrder)) {
                if (ep.inUpNext != shouldBeInQueue) episodeStatusesUpdated++
                ep.copy(inUpNext = shouldBeInQueue, upNextOrder = targetOrder)
            } else {
                ep
            }
        }

        // 4. Merge Playlists (custom playlists, folders, assigned podcasts, episode lists, smart settings, deletions)
        val mergedDeletedPlaylists = mergeTimestampMaps(localJournal.deletedPlaylists, remoteEnv.deletedPlaylists)
        val mergedPlaylistUpdatedAt = mergeTimestampMaps(localJournal.playlistUpdatedAt, remoteEnv.playlistUpdatedAt)
        val workingPlaylists = localPlaylists.toMutableList()
        val deletedIdsToApply = mutableSetOf<String>()

        // Apply deletions to local playlists if remote deleted more recently
        if (!pullOnly) {
            val iterator = workingPlaylists.iterator()
            while (iterator.hasNext()) {
                val pl = iterator.next()
                val deletedAt = mergedDeletedPlaylists[pl.id] ?: 0L
                val updatedAt = maxOf(
                    localJournal.playlistUpdatedAt[pl.id] ?: 0L,
                    pl.createdAt
                )
                if (deletedAt > updatedAt) {
                    deletedIdsToApply.add(pl.id)
                    iterator.remove()
                }
            }
        }

        for (remotePl in remoteBackup.playlists) {
            val remappedEpisodeCsv = remotePl.episodeIdsCsv
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { oldId -> remoteEpToLocalEpId[oldId] ?: oldId }
                .distinct()
                .joinToString(",")
            val remappedPodcastCsv = remotePl.assignedPodcastIdsCsv
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { oldPodId -> remotePodToLocalPodId[oldPodId] ?: oldPodId }
                .distinct()
                .joinToString(",")

            val normalizedRemotePl = remotePl.copy(
                episodeIdsCsv = remappedEpisodeCsv,
                assignedPodcastIdsCsv = remappedPodcastCsv
            )

            val deletedAt = mergedDeletedPlaylists[normalizedRemotePl.id] ?: 0L
            val remotePlUpdated = remoteEnv.playlistUpdatedAt[normalizedRemotePl.id]
                ?: normalizedRemotePl.createdAt.takeIf { it > 0L }
                ?: remoteEnv.syncedAt
            if (!pullOnly && deletedAt > remotePlUpdated) {
                deletedIdsToApply.add(normalizedRemotePl.id)
                continue
            }

            val existingIdx = if (normalizedRemotePl.smartRuleType.isNotBlank()) {
                workingPlaylists.indexOfFirst { it.smartRuleType == normalizedRemotePl.smartRuleType }
            } else {
                workingPlaylists.indexOfFirst { it.id == normalizedRemotePl.id }
            }

            if (existingIdx >= 0) {
                val localPl = workingPlaylists[existingIdx]
                val localPlUpdated = localJournal.playlistUpdatedAt[localPl.id] ?: localPl.createdAt
                val remotePlNewer = pullOnly || remotePlUpdated > localPlUpdated

                val mergedEpisodesCsv = if (remotePlNewer) {
                    normalizedRemotePl.episodeIdsCsv
                } else if ((localJournal.playlistUpdatedAt[localPl.id] ?: 0L) > (remoteEnv.playlistUpdatedAt[normalizedRemotePl.id] ?: 0L)) {
                    localPl.episodeIdsCsv
                } else {
                    (localPl.episodeIdList() + normalizedRemotePl.episodeIdList()).distinct().joinToString(",")
                }

                val mergedPodcastsCsv = if (remotePlNewer) {
                    normalizedRemotePl.assignedPodcastIdsCsv
                } else if ((localJournal.playlistUpdatedAt[localPl.id] ?: 0L) > (remoteEnv.playlistUpdatedAt[normalizedRemotePl.id] ?: 0L)) {
                    localPl.assignedPodcastIdsCsv
                } else {
                    (localPl.assignedPodcastIdList() + normalizedRemotePl.assignedPodcastIdList()).distinct().joinToString(",")
                }

                workingPlaylists[existingIdx] = localPl.copy(
                    name = if (remotePlNewer) normalizedRemotePl.name else localPl.name,
                    folderName = if (remotePlNewer) normalizedRemotePl.folderName else localPl.folderName,
                    isPinned = if (remotePlNewer) normalizedRemotePl.isPinned else localPl.isPinned,
                    autoDownload = if (remotePlNewer) normalizedRemotePl.autoDownload else localPl.autoDownload,
                    episodeIdsCsv = mergedEpisodesCsv,
                    assignedPodcastIdsCsv = mergedPodcastsCsv
                )
                mergedPlaylistUpdatedAt[localPl.id] = maxOf(localPlUpdated, remotePlUpdated)
            } else {
                workingPlaylists.add(normalizedRemotePl)
                mergedPlaylistUpdatedAt[normalizedRemotePl.id] = remotePlUpdated
            }
        }

        // 5. Merge Bookmarks
        val mergedBookmarks = localBookmarks.toMutableList()
        for (remoteBm in remoteBackup.bookmarks) {
            val targetEpId = remoteEpToLocalEpId[remoteBm.episodeId] ?: remoteBm.episodeId
            val exists = mergedBookmarks.any {
                it.id == remoteBm.id ||
                    (it.episodeId == targetEpId && abs(it.positionMs - remoteBm.positionMs) < 1000L && it.label == remoteBm.label)
            }
            if (!exists) {
                mergedBookmarks.add(remoteBm.copy(episodeId = targetEpId))
            }
        }

        // 6. Resolve lastPlayingEpisodeId if remote is newer
        val resolvedLastKey: String
        val resolvedLastTs: Long
        var resolvedLastLocalEpId: String? = null
        if (remoteEnv.lastPlayingEpisodeKey.isNotBlank() &&
            (pullOnly || remoteEnv.lastPlayingUpdatedAt > localJournal.lastPlayingUpdatedAt)
        ) {
            val match = finalEpisodes.firstOrNull { ep ->
                !ep.isPlayed && !ep.isArchived &&
                    episodeCanonicalKey(ep, podById[ep.podcastId]?.feedUrl.orEmpty()) == remoteEnv.lastPlayingEpisodeKey
            }
            if (match != null) {
                resolvedLastLocalEpId = match.id
                resolvedLastKey = remoteEnv.lastPlayingEpisodeKey
                resolvedLastTs = remoteEnv.lastPlayingUpdatedAt
            } else {
                resolvedLastKey = localJournal.lastPlayingEpisodeKey
                resolvedLastTs = localJournal.lastPlayingUpdatedAt
            }
        } else {
            resolvedLastKey = localJournal.lastPlayingEpisodeKey
            resolvedLastTs = localJournal.lastPlayingUpdatedAt
        }

        val updatedJournal = DriveSyncLocalJournal(
            lastSyncedAt = nowMs,
            upNextUpdatedAt = maxOf(localJournal.upNextUpdatedAt, remoteEnv.upNextUpdatedAt),
            lastPlayingEpisodeKey = resolvedLastKey,
            lastPlayingUpdatedAt = resolvedLastTs,
            deletedPlaylists = DriveSyncLocalJournal.trimMap(mergedDeletedPlaylists),
            playlistUpdatedAt = DriveSyncLocalJournal.trimMap(mergedPlaylistUpdatedAt),
            unsubscribedPodcasts = DriveSyncLocalJournal.trimMap(mergedUnsubscribed),
            podcastUpdatedAt = DriveSyncLocalJournal.trimMap(mergedPodcastUpdatedAt),
            removedFromUpNext = DriveSyncLocalJournal.trimMap(mergedRemovedUpNext),
            addedToUpNext = DriveSyncLocalJournal.trimMap(mergedAddedUpNext),
            episodeStatusUpdatedAt = DriveSyncLocalJournal.trimMap(mergedEpStatusUpdatedAt)
        )

        val canonicalJson = buildSyncPayload(
            podcasts = mergedPodcasts,
            episodes = finalEpisodes,
            playlists = workingPlaylists,
            bookmarks = mergedBookmarks,
            journal = updatedJournal,
            nowMs = nowMs
        )

        return DriveMergeResult(
            mergedPodcasts = mergedPodcasts,
            mergedEpisodes = finalEpisodes,
            mergedPlaylists = workingPlaylists,
            deletedPodcastIds = deletedPodcastIdsToApply,
            deletedPlaylistIds = deletedIdsToApply,
            mergedBookmarks = mergedBookmarks,
            updatedJournal = updatedJournal,
            resolvedLastPlayingEpisodeId = resolvedLastLocalEpId,
            summary = DriveSyncSummary(
                podcastsSynced = mergedPodcasts.count { it.isSubscribed },
                playlistsSynced = workingPlaylists.size,
                upNextCount = finalEpisodes.count { it.inUpNext && !it.isPlayed && !it.isArchived },
                episodeStatusesUpdated = episodeStatusesUpdated,
                bookmarksSynced = mergedBookmarks.size
            ),
            canonicalJsonPayload = canonicalJson
        )
    }

    private fun findMatchingPodcastIndex(
        existing: List<PodcastEntity>,
        remote: PodcastEntity
    ): Int {
        val remoteUrl = remote.feedUrl.trim().lowercase(Locale.ROOT).trimEnd('/')
        if (remoteUrl.isNotBlank() && !remoteUrl.startsWith("https://example.org/feed/")) {
            val byUrl = existing.indexOfFirst {
                it.feedUrl.trim().lowercase(Locale.ROOT).trimEnd('/') == remoteUrl
            }
            if (byUrl >= 0) return byUrl
        }
        val byId = existing.indexOfFirst { it.id == remote.id }
        if (byId >= 0) return byId

        val remoteTitle = remote.title.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (remoteTitle.isNotBlank()) {
            return existing.indexOfFirst {
                it.title.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") == remoteTitle
            }
        }
        return -1
    }

    private fun findMatchingEpisodeIndex(
        existing: List<EpisodeEntity>,
        remote: EpisodeEntity,
        mappedPodcastId: String
    ): Int {
        val cleanGuid = remote.guid.trim()
        if (cleanGuid.isNotBlank()) {
            val byGuid = existing.indexOfFirst {
                it.podcastId == mappedPodcastId && it.guid.trim() == cleanGuid
            }
            if (byGuid >= 0) return byGuid
        }
        val cleanAudio = remote.audioUrl.trim()
        if (cleanAudio.isNotBlank()) {
            val byAudio = existing.indexOfFirst {
                it.podcastId == mappedPodcastId && it.audioUrl.trim() == cleanAudio
            }
            if (byAudio >= 0) return byAudio
        }
        val byId = existing.indexOfFirst { it.id == remote.id }
        if (byId >= 0) return byId

        val cleanTitle = remote.title.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (cleanTitle.isNotBlank()) {
            val byTitle = existing.indexOfFirst { ep ->
                ep.podcastId == mappedPodcastId &&
                    ep.title.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") == cleanTitle &&
                    (ep.publishedAt == 0L || remote.publishedAt == 0L || abs(ep.publishedAt - remote.publishedAt) <= 60_000L)
            }
            if (byTitle >= 0) return byTitle
        }
        return -1
    }

    private fun mergeTimestampMaps(a: Map<String, Long>, b: Map<String, Long>): MutableMap<String, Long> {
        val out = linkedMapOf<String, Long>()
        out.putAll(a)
        b.forEach { (k, v) ->
            val cur = out[k] ?: 0L
            if (v > cur) out[k] = v
        }
        return out
    }

    private fun mapToJson(map: Map<String, Long>): JSONObject {
        val obj = JSONObject()
        DriveSyncLocalJournal.trimMap(map).forEach { (k, v) -> obj.put(k, v) }
        return obj
    }

    private fun jsonToLongMap(obj: JSONObject?): Map<String, Long> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, Long>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optLong(k, 0L)
            if (k.isNotBlank() && v > 0L) out[k] = v
        }
        return out
    }
}
