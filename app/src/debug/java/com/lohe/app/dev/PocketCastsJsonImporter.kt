package com.lohe.app.dev

import androidx.room.withTransaction
import com.lohe.app.data.importer.ImportResultSummary
import com.lohe.app.data.importer.ImportSourceFormat
import com.lohe.app.data.importer.PodcastAppImporter
import com.lohe.app.data.local.EpisodeEntity
import com.lohe.app.data.local.LoheDatabase
import com.lohe.app.data.local.PodcastEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

class PocketCastsJsonImporter(
    private val database: LoheDatabase
) {
    private val dao = database.loheDao()

    suspend fun importPocketCastsJson(rawJson: String): Result<ImportResultSummary> {
        return runCatching {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
                throw IllegalArgumentException("Invalid Pocket Casts JSON payload")
            }
            val root = JSONObject(trimmed)
            val parsedPodcasts = mutableListOf<PodcastEntity>()
            val parsedEpisodes = mutableListOf<EpisodeEntity>()

            val podcastsArray = root.optJSONArray("podcasts")
                ?: root.optJSONArray("subscriptions")
                ?: root.optJSONArray("feeds")
                ?: root.optJSONArray("lists")
                ?: JSONArray()

            val uuidToPodcast = mutableMapOf<String, PodcastEntity>()

            for (i in 0 until podcastsArray.length()) {
                val obj = podcastsArray.optJSONObject(i) ?: continue
                val rawUuid = obj.optStringFirst("uuid", "id", "podcastUuid", "podcast_uuid")
                val title = obj.optStringFirst("title", "name")
                val feedUrl = obj.optStringFirst("url", "feedUrl", "feed_url", "rssUrl", "xmlUrl")
                val author = obj.optStringFirst("author", "podcastAuthor").ifBlank { "Pocket Casts Import" }
                val description = obj.optStringFirst("description").ifBlank { "Imported from Pocket Casts JSON" }
                val artworkUrl = obj.optStringFirst("thumbnailUrl", "artworkUrl", "imageUrl", "thumbnail_url")
                    .ifBlank { "drawable://lohe_logo_square" }

                if (title.isBlank() && feedUrl.isBlank() && rawUuid.isBlank()) continue

                val podId = when {
                    rawUuid.isNotBlank() -> rawUuid
                    feedUrl.isNotBlank() -> "pc_pod_${PodcastAppImporter.normalizeUrl(feedUrl).hashCode()}"
                    else -> "pc_pod_${title.hashCode()}"
                }

                val podEntity = PodcastEntity(
                    id = podId,
                    title = title.ifBlank { "Pocket Casts Show #${i + 1}" },
                    author = author,
                    description = description,
                    feedUrl = feedUrl,
                    artworkUrl = artworkUrl,
                    category = "Imported",
                    isSubscribed = true
                )
                parsedPodcasts.add(podEntity)
                if (rawUuid.isNotBlank()) {
                    uuidToPodcast[rawUuid] = podEntity
                }
                uuidToPodcast[podId] = podEntity

                // Parse nested episodes inside a podcast object if present
                val nestedEps = obj.optJSONArray("episodes")
                if (nestedEps != null) {
                    for (j in 0 until nestedEps.length()) {
                        val epObj = nestedEps.optJSONObject(j) ?: continue
                        parseEpisodeObject(epObj, podEntity, uuidToPodcast)?.let { parsedEpisodes.add(it) }
                    }
                }
            }

            val topLevelEpisodes = root.optJSONArray("episodes")
                ?: root.optJSONArray("history")
                ?: root.optJSONArray("items")
                ?: JSONArray()

            for (i in 0 until topLevelEpisodes.length()) {
                val epObj = topLevelEpisodes.optJSONObject(i) ?: continue
                parseEpisodeObject(epObj, null, uuidToPodcast)?.let { parsedEpisodes.add(it) }
            }

            applyInSingleTransaction(parsedPodcasts, parsedEpisodes)
        }
    }

    private fun parseEpisodeObject(
        obj: JSONObject,
        defaultParent: PodcastEntity?,
        uuidToPodcast: Map<String, PodcastEntity>
    ): EpisodeEntity? {
        val guid = obj.optStringFirst("guid", "uuid", "id", "episodeUuid", "episode_uuid")
        val title = obj.optStringFirst("title", "name")
        val audioUrl = obj.optStringFirst(
            "url",
            "downloadUrl",
            "download_url",
            "audioUrl",
            "enclosureUrl",
            "enclosure_url",
            "mediaUrl"
        )
        if (guid.isBlank() && title.isBlank() && audioUrl.isBlank()) return null

        val parentKey = obj.optStringFirst("podcastUuid", "podcast_uuid", "podcastId", "podcast_id")
        val parentPod = (if (parentKey.isNotBlank()) uuidToPodcast[parentKey] else null) ?: defaultParent
        val podcastId = parentPod?.id ?: parentKey.ifBlank { "pc_pod_unassigned" }
        val podcastTitle = obj.optStringFirst("podcastTitle", "podcast_title")
            .ifBlank { parentPod?.title ?: "Pocket Casts Show" }

        val publishedAt = parseTimestampMs(
            obj,
            "publishedAt",
            "published_at",
            "publishedDate",
            "published",
            "pubDate"
        )

        val (durationMs, positionMs) = parseDurationAndPositionMs(obj)

        val playingStatus = obj.optInt("playingStatus", obj.optInt("playing_status", 0))
        val explicitPlayed = obj.optBoolean("isPlayed", false) ||
            obj.optBoolean("played", false) ||
            playingStatus >= 3 ||
            obj.optString("status", "").equals("played", ignoreCase = true) ||
            obj.optString("status", "").equals("completed", ignoreCase = true)

        val isStarred = obj.optBoolean("starred", false) || obj.optBoolean("isStarred", false)
        val inUpNext = obj.optBoolean("inUpNext", false) || obj.optBoolean("upNext", false)
        val playedAt = parseTimestampMs(obj, "playedAt", "played_at", "lastPlayedAt", "last_played_at")

        val isMarkedPlayed = explicitPlayed ||
            PodcastAppImporter.isPositionConsideredPlayed(positionMs, durationMs)

        val epId = if (guid.isNotBlank()) {
            "pc_ep_${(podcastId + "_" + guid).hashCode()}"
        } else {
            "pc_ep_${(podcastId + "_" + audioUrl + "_" + title).hashCode()}"
        }

        return EpisodeEntity(
            id = epId,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            guid = guid,
            title = title.ifBlank { "Pocket Casts Episode" },
            descriptionHtml = obj.optStringFirst("description", "descriptionHtml", "showNotes"),
            audioUrl = audioUrl,
            artworkUrl = parentPod?.artworkUrl ?: "drawable://lohe_logo_square",
            publishedAt = publishedAt,
            durationMs = durationMs,
            resumePositionMs = positionMs,
            isPlayed = isMarkedPlayed,
            playedAt = playedAt,
            isStarred = isStarred,
            inUpNext = inUpNext
        )
    }

    private fun parseDurationAndPositionMs(obj: JSONObject): Pair<Long, Long> {
        val hasExplicitDurationMs = obj.has("durationMs") || obj.has("duration_ms")
        val hasExplicitPosMs = obj.has("positionMs") ||
            obj.has("position_ms") ||
            obj.has("resumePositionMs") ||
            obj.has("playedUpToMs") ||
            obj.has("played_up_to_ms")

        val rawDuration = when {
            obj.has("durationMs") -> obj.optDouble("durationMs", 0.0)
            obj.has("duration_ms") -> obj.optDouble("duration_ms", 0.0)
            else -> obj.optDouble("duration", 0.0)
        }.coerceAtLeast(0.0)

        val rawPos = when {
            obj.has("positionMs") -> obj.optDouble("positionMs", 0.0)
            obj.has("position_ms") -> obj.optDouble("position_ms", 0.0)
            obj.has("resumePositionMs") -> obj.optDouble("resumePositionMs", 0.0)
            obj.has("playedUpToMs") -> obj.optDouble("playedUpToMs", 0.0)
            obj.has("played_up_to_ms") -> obj.optDouble("played_up_to_ms", 0.0)
            obj.has("playedUpTo") -> obj.optDouble("playedUpTo", 0.0)
            obj.has("played_up_to") -> obj.optDouble("played_up_to", 0.0)
            else -> obj.optDouble("position", 0.0)
        }.coerceAtLeast(0.0)

        // Determine if duration / playedUpTo are in seconds (Pocket Casts standard) or milliseconds
        val durationIsSeconds = !hasExplicitDurationMs && rawDuration > 0.0 && rawDuration < 100_000.0
        val durationMs = if (durationIsSeconds) {
            (rawDuration * 1000.0).toLong()
        } else {
            rawDuration.toLong()
        }

        val posIsSeconds = !hasExplicitPosMs && (
            durationIsSeconds ||
                (rawPos > 0.0 && rawPos < 10_000.0 && rawDuration == 0.0)
            )
        val positionMs = if (posIsSeconds) {
            (rawPos * 1000.0).toLong()
        } else {
            rawPos.toLong()
        }

        return durationMs to positionMs
    }

    private fun parseTimestampMs(obj: JSONObject, vararg keys: String): Long {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val numeric = obj.optLong(key, -1L)
            if (numeric > 0L) {
                return if (numeric in 1_000_000L..9_999_999_999L) numeric * 1000L else numeric
            }
            val str = obj.optString(key, "").trim()
            if (str.isNotEmpty()) {
                val parsedIso = runCatching { Instant.parse(str).toEpochMilli() }.getOrNull()
                if (parsedIso != null && parsedIso > 0L) return parsedIso
            }
        }
        return 0L
    }

    private suspend fun applyInSingleTransaction(
        podcasts: List<PodcastEntity>,
        episodes: List<EpisodeEntity>
    ): ImportResultSummary {
        var showsAdded = 0
        var episodesUpdated = 0
        var itemsSkipped = 0

        database.withTransaction {
            val existingPodcasts = dao.getAllPodcasts().first().toMutableList()
            val existingEpisodes = dao.getAllEpisodes().first().toMutableList()
            val importedPodIdToLocal = mutableMapOf<String, PodcastEntity>()

            for (importedPod in podcasts) {
                val normRss = PodcastAppImporter.normalizeUrl(importedPod.feedUrl)
                val normTitle = PodcastAppImporter.normalizeTitle(importedPod.title)

                val matchedPod = existingPodcasts.firstOrNull { existing ->
                    (normRss.isNotBlank() && PodcastAppImporter.normalizeUrl(existing.feedUrl) == normRss) ||
                        existing.id == importedPod.id
                } ?: existingPodcasts.firstOrNull { existing ->
                    normTitle.isNotBlank() && PodcastAppImporter.normalizeTitle(existing.title) == normTitle
                }

                if (matchedPod != null) {
                    if (!matchedPod.isSubscribed) {
                        val updated = matchedPod.copy(isSubscribed = true)
                        dao.updatePodcast(updated)
                        val idx = existingPodcasts.indexOfFirst { it.id == matchedPod.id }
                        if (idx >= 0) existingPodcasts[idx] = updated
                        importedPodIdToLocal[importedPod.id] = updated
                        showsAdded++
                    } else {
                        importedPodIdToLocal[importedPod.id] = matchedPod
                        itemsSkipped++
                    }
                } else {
                    dao.insertPodcast(importedPod)
                    existingPodcasts.add(importedPod)
                    importedPodIdToLocal[importedPod.id] = importedPod
                    showsAdded++
                }
            }

            for (importedEp in episodes) {
                val mappedParent = importedPodIdToLocal[importedEp.podcastId]
                val targetPodcastId = mappedParent?.id ?: importedEp.podcastId
                val targetPodcastTitle = mappedParent?.title ?: importedEp.podcastTitle

                val matchedExisting = findMatchingEpisode(importedEp, targetPodcastId, existingEpisodes)
                val effectiveDuration = maxOf(
                    matchedExisting?.durationMs ?: 0L,
                    importedEp.durationMs.coerceAtLeast(0L)
                )
                val importedPos = importedEp.resumePositionMs.coerceAtLeast(0L)
                val countsAsPlayed = importedEp.isPlayed ||
                    PodcastAppImporter.isPositionConsideredPlayed(importedPos, effectiveDuration)
                val importedPlayedAt = importedEp.playedAt.coerceAtLeast(0L)

                if (matchedExisting != null) {
                    val mergedIsPlayed = matchedExisting.isPlayed || countsAsPlayed
                    val mergedPositionMs = maxOf(matchedExisting.resumePositionMs, importedPos)
                    val mergedPlayedAt = maxOf(matchedExisting.playedAt, importedPlayedAt)
                    val mergedStarred = matchedExisting.isStarred || importedEp.isStarred
                    val mergedInUpNext = matchedExisting.inUpNext || importedEp.inUpNext

                    val movedForward = (!matchedExisting.isPlayed && mergedIsPlayed) ||
                        (mergedPositionMs > matchedExisting.resumePositionMs) ||
                        (mergedPlayedAt > matchedExisting.playedAt) ||
                        (!matchedExisting.isStarred && mergedStarred) ||
                        (!matchedExisting.inUpNext && mergedInUpNext)

                    val updated = matchedExisting.copy(
                        guid = matchedExisting.guid.ifBlank { importedEp.guid.trim() },
                        audioUrl = matchedExisting.audioUrl.ifBlank { importedEp.audioUrl.trim() },
                        durationMs = if (matchedExisting.durationMs > 0L) matchedExisting.durationMs else effectiveDuration,
                        isPlayed = mergedIsPlayed,
                        resumePositionMs = mergedPositionMs,
                        playedAt = mergedPlayedAt,
                        isStarred = mergedStarred,
                        inUpNext = mergedInUpNext
                    )

                    if (movedForward) {
                        dao.updateEpisode(updated)
                        val idx = existingEpisodes.indexOfFirst { it.id == matchedExisting.id }
                        if (idx >= 0) existingEpisodes[idx] = updated
                        episodesUpdated++
                    } else {
                        itemsSkipped++
                    }
                } else {
                    val toInsert = importedEp.copy(
                        podcastId = targetPodcastId,
                        podcastTitle = targetPodcastTitle,
                        durationMs = effectiveDuration,
                        resumePositionMs = importedPos,
                        isPlayed = countsAsPlayed,
                        playedAt = importedPlayedAt
                    )
                    dao.insertEpisode(toInsert)
                    existingEpisodes.add(toInsert)
                    if (countsAsPlayed || importedPos > 0L || importedPlayedAt > 0L || importedEp.isStarred || importedEp.inUpNext) {
                        episodesUpdated++
                    } else {
                        itemsSkipped++
                    }
                }
            }
        }

        return ImportResultSummary(
            format = ImportSourceFormat.LOHE_JSON,
            showsAdded = showsAdded,
            episodesUpdated = episodesUpdated,
            itemsSkipped = itemsSkipped
        )
    }

    private fun findMatchingEpisode(
        imported: EpisodeEntity,
        targetPodcastId: String,
        existingEpisodes: List<EpisodeEntity>
    ): EpisodeEntity? {
        // 1. GUID first
        val guid = imported.guid.trim()
        if (guid.isNotBlank()) {
            existingEpisodes.firstOrNull {
                it.podcastId == targetPodcastId && (it.guid.trim() == guid || it.id.trim() == guid)
            }?.let { return it }
            existingEpisodes.firstOrNull {
                it.guid.trim() == guid || it.id.trim() == guid
            }?.let { return it }
        }

        // 2. Enclosure URL second
        val url = PodcastAppImporter.normalizeUrl(imported.audioUrl)
        if (url.isNotBlank()) {
            existingEpisodes.firstOrNull {
                it.podcastId == targetPodcastId &&
                    it.audioUrl.isNotBlank() &&
                    PodcastAppImporter.normalizeUrl(it.audioUrl) == url
            }?.let { return it }
            existingEpisodes.firstOrNull {
                it.audioUrl.isNotBlank() &&
                    PodcastAppImporter.normalizeUrl(it.audioUrl) == url
            }?.let { return it }
        }

        // 3. Title + publish date third
        val title = PodcastAppImporter.normalizeTitle(imported.title)
        val pub = imported.publishedAt
        if (title.isNotBlank() && pub > 0L) {
            existingEpisodes.firstOrNull {
                it.publishedAt > 0L &&
                    PodcastAppImporter.normalizeTitle(it.title) == title &&
                    abs(it.publishedAt - pub) <= 86_400_000L
            }?.let { return it }
        }

        return null
    }

    private fun JSONObject.optStringFirst(vararg keys: String): String {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = optString(key, "").trim()
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) {
                return value
            }
        }
        return ""
    }
}
