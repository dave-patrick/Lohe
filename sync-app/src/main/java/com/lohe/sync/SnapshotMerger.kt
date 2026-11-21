package com.lohe.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class Edition(
    val label: String,
    val packageName: String,
    val authority: String,
) {
    PLAY("Google Play", "com.lohe.app", "com.lohe.app.ownersync"),
    FOSS("FOSS", "com.lohe.app.foss", "com.lohe.app.foss.ownersync"),
}

/**
 * Pure JSON merger for Lohe owner-sync snapshots.
 *
 * Rules for two-way Smart Merge:
 * - Podcasts: union by UUID; newer metadata wins, while subscription & auto-queue settings
 *   prefer [primary] when both sides contain the podcast.
 * - Episodes: union by UUID; the side with the latest activity timestamp
 *   (`max(lastPlayedAt, playedAt, archivedAt)`) wins playback/archive status. On ties,
 *   further playback progress (`PLAYED > IN_PROGRESS > UNPLAYED`, then higher resume
 *   position) wins. `listenedSeconds` always takes the maximum.
 * - Up Next: follows the exact ordered queue of [primary], omitting episodes that are
 *   now played or archived after the episode merge.
 * - Playlists: union by ID (primary order first, then unique playlists from secondary).
 *   Shared playlists preserve primary's ordering and append newly added episodes from
 *   secondary, dropping episodes that became played/archived when `!finished`.
 * - Listening history: union by `episodeUuid`, taking max `listenedSeconds` and dropping
 *   rows whose episode exists in the merged episode list.
 * - Podcast folders: union with [primary] taking precedence on conflicts.
 */
object SnapshotMerger {

    private val json = Json { ignoreUnknownKeys = true }

    fun merge(
        playJson: String,
        fossJson: String,
        primary: Edition,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val playRoot = json.parseToJsonElement(playJson).jsonObject
        val fossRoot = json.parseToJsonElement(fossJson).jsonObject
        val primaryRoot = if (primary == Edition.PLAY) playRoot else fossRoot
        val secondaryRoot = if (primary == Edition.PLAY) fossRoot else playRoot

        val mergedPodcasts = mergePodcasts(
            primaryRoot["podcasts"]?.jsonArray.orEmpty(),
            secondaryRoot["podcasts"]?.jsonArray.orEmpty(),
        )
        val mergedEpisodes = mergeEpisodes(
            primaryRoot["episodes"]?.jsonArray.orEmpty(),
            secondaryRoot["episodes"]?.jsonArray.orEmpty(),
        )
        val mergedEpisodeByUuid = mergedEpisodes.associateBy { it.str("uuid").orEmpty() }
        val mergedHistory = mergeListeningHistory(
            primaryRoot["listeningHistory"]?.jsonArray.orEmpty(),
            secondaryRoot["listeningHistory"]?.jsonArray.orEmpty(),
            mergedEpisodeByUuid.keys,
        )
        val mergedPlaylists = mergePlaylists(
            primaryRoot["playlists"]?.jsonArray.orEmpty(),
            secondaryRoot["playlists"]?.jsonArray.orEmpty(),
            mergedEpisodeByUuid,
        )
        val mergedFolders = mergeFolders(
            primaryRoot["podcastFolders"]?.jsonObject,
            secondaryRoot["podcastFolders"]?.jsonObject,
        )

        val out = buildJsonObject {
            put("schemaVersion", 1)
            put("exportedAt", nowMillis)
            put("sourcePackage", "com.lohe.sync")
            put("podcasts", JsonArray(mergedPodcasts))
            put("episodes", JsonArray(mergedEpisodes))
            put("listeningHistory", JsonArray(mergedHistory))
            put("playlists", JsonArray(mergedPlaylists))
            put("podcastFolders", mergedFolders)
        }
        return json.encodeToString(JsonObject.serializer(), out)
    }

    private fun mergePodcasts(
        primary: List<JsonElement>,
        secondary: List<JsonElement>,
    ): List<JsonObject> {
        val byUuid = LinkedHashMap<String, JsonObject>()
        for (el in primary) {
            val obj = el.jsonObject
            val uuid = obj.str("uuid") ?: continue
            byUuid[uuid] = obj
        }
        for (el in secondary) {
            val sec = el.jsonObject
            val uuid = sec.str("uuid") ?: continue
            val pri = byUuid[uuid]
            if (pri == null) {
                byUuid[uuid] = sec
            } else {
                val priLatest = pri.lng("lastEpisodePublished") ?: 0L
                val secLatest = sec.lng("lastEpisodePublished") ?: 0L
                val meta = if (secLatest > priLatest) sec else pri
                byUuid[uuid] = buildJsonObject {
                    for ((k, v) in pri) put(k, v)
                    // Take fresher feed metadata if secondary has newer episodes.
                    put("title", meta["title"] ?: pri["title"] ?: JsonPrimitive(""))
                    meta["author"]?.let { put("author", it) }
                    meta["description"]?.let { put("description", it) }
                    meta["artworkUrl"]?.let { put("artworkUrl", it) }
                    put("feedUrl", meta["feedUrl"] ?: pri["feedUrl"] ?: JsonPrimitive(""))
                    put("lastEpisodePublished", JsonPrimitive(maxOf(priLatest, secLatest).takeIf { it > 0L }))
                }
            }
        }
        return byUuid.values.toList()
    }

    private fun mergeEpisodes(
        primary: List<JsonElement>,
        secondary: List<JsonElement>,
    ): List<JsonObject> {
        val primaryMap = LinkedHashMap<String, JsonObject>()
        for (el in primary) {
            val obj = el.jsonObject
            val uuid = obj.str("uuid") ?: continue
            primaryMap[uuid] = obj
        }
        val secondaryMap = LinkedHashMap<String, JsonObject>()
        for (el in secondary) {
            val obj = el.jsonObject
            val uuid = obj.str("uuid") ?: continue
            secondaryMap[uuid] = obj
        }

        val allUuids = LinkedHashSet<String>()
        allUuids.addAll(primaryMap.keys)
        allUuids.addAll(secondaryMap.keys)

        // Capture primary's ordered Up Next queue before merging status.
        val primaryQueueOrder = primaryMap.values
            .mapNotNull { obj ->
                val uuid = obj.str("uuid") ?: return@mapNotNull null
                val pos = obj.intVal("queuePosition") ?: return@mapNotNull null
                uuid to pos
            }
            .sortedBy { it.second }
            .map { it.first }

        val mergedByUuid = LinkedHashMap<String, JsonObject>(allUuids.size)
        for (uuid in allUuids) {
            val pri = primaryMap[uuid]
            val sec = secondaryMap[uuid]
            val merged = when {
                pri != null && sec == null -> pri.withoutQueue()
                pri == null && sec != null -> sec.withoutQueue()
                pri != null && sec != null -> mergeSingleEpisode(pri, sec)
                else -> continue
            }
            mergedByUuid[uuid] = merged
        }

        // Re-apply primary's Up Next order, omitting episodes that are now played/archived/deleted.
        var nextPos = 0
        for (uuid in primaryQueueOrder) {
            val ep = mergedByUuid[uuid] ?: continue
            val isPlayed = (ep.intVal("playingStatus") ?: 0) == 2
            val isArchived = ep.boolVal("isArchived") == true
            val isDeleted = ep.boolVal("isDeleted") == true
            if (!isPlayed && !isArchived && !isDeleted) {
                mergedByUuid[uuid] = ep.withField("queuePosition", JsonPrimitive(nextPos++))
            }
        }

        return mergedByUuid.values.toList()
    }

    private fun mergeSingleEpisode(pri: JsonObject, sec: JsonObject): JsonObject {
        val priActivity = episodeActivityAt(pri)
        val secActivity = episodeActivityAt(sec)

        val statusWinner = when {
            priActivity > secActivity -> pri
            secActivity > priActivity -> sec
            else -> {
                val priStatus = pri.intVal("playingStatus") ?: 0
                val secStatus = sec.intVal("playingStatus") ?: 0
                when {
                    priStatus > secStatus -> pri
                    secStatus > priStatus -> sec
                    (pri.lng("playedUpToSeconds") ?: 0L) >= (sec.lng("playedUpToSeconds") ?: 0L) -> pri
                    else -> sec
                }
            }
        }

        val maxListened = maxOf(
            pri.lng("listenedSeconds") ?: 0L,
            sec.lng("listenedSeconds") ?: 0L,
        )
        val starred = if (priActivity != secActivity) {
            statusWinner.boolVal("starred") == true
        } else {
            (pri.boolVal("starred") == true) || (sec.boolVal("starred") == true)
        }
        val duration = pri.intVal("durationSeconds")?.takeIf { it > 0 }
            ?: sec.intVal("durationSeconds")?.takeIf { it > 0 }

        return buildJsonObject {
            // Start with primary metadata, filling missing fields from secondary.
            for ((k, v) in pri) put(k, v)
            for (metaKey in listOf("description", "chapters", "artworkUrl", "author", "fileType", "sizeBytes")) {
                if (pri[metaKey] == null || pri[metaKey] is JsonNull) {
                    sec[metaKey]?.let { put(metaKey, it) }
                }
            }
            if (duration != null) put("durationSeconds", duration)

            // Apply winning playback / archive status.
            put("playingStatus", statusWinner["playingStatus"] ?: JsonPrimitive(0))
            put("playedAt", statusWinner["playedAt"] ?: JsonNull)
            put("playedUpToSeconds", statusWinner["playedUpToSeconds"] ?: JsonPrimitive(0L))
            put("isArchived", statusWinner["isArchived"] ?: JsonPrimitive(false))
            put("archivedAt", statusWinner["archivedAt"] ?: JsonNull)
            put("isDeleted", statusWinner["isDeleted"] ?: JsonPrimitive(false))
            put("lastPlayedAt", statusWinner["lastPlayedAt"] ?: JsonNull)
            put("listenedSeconds", maxListened)
            put("starred", starred)
            put("queuePosition", JsonNull)
        }
    }

    private fun episodeActivityAt(ep: JsonObject): Long = maxOf(
        ep.lng("lastPlayedAt") ?: 0L,
        ep.lng("playedAt") ?: 0L,
        ep.lng("archivedAt") ?: 0L,
    )

    private fun mergeListeningHistory(
        primary: List<JsonElement>,
        secondary: List<JsonElement>,
        presentEpisodeUuids: Set<String>,
    ): List<JsonObject> {
        val byUuid = LinkedHashMap<String, JsonObject>()
        for (el in primary + secondary) {
            val obj = el.jsonObject
            val uuid = obj.str("episodeUuid") ?: continue
            if (uuid in presentEpisodeUuids) continue
            val existing = byUuid[uuid]
            if (existing == null || (obj.lng("listenedSeconds") ?: 0L) > (existing.lng("listenedSeconds") ?: 0L)) {
                byUuid[uuid] = obj
            }
        }
        return byUuid.values.toList()
    }

    private fun mergePlaylists(
        primary: List<JsonElement>,
        secondary: List<JsonElement>,
        mergedEpisodes: Map<String, JsonObject>,
    ): List<JsonObject> {
        val primaryMap = LinkedHashMap<String, JsonObject>()
        for (el in primary) {
            val obj = el.jsonObject
            val id = obj.str("id") ?: continue
            primaryMap[id] = obj
        }
        val secondaryMap = LinkedHashMap<String, JsonObject>()
        for (el in secondary) {
            val obj = el.jsonObject
            val id = obj.str("id") ?: continue
            secondaryMap[id] = obj
        }

        val allIds = LinkedHashSet<String>()
        allIds.addAll(primaryMap.keys)
        allIds.addAll(secondaryMap.keys)

        return allIds.mapNotNull { id ->
            val pri = primaryMap[id]
            val sec = secondaryMap[id]
            when {
                pri != null && sec == null -> filterPlaylistEpisodes(pri, mergedEpisodes)
                pri == null && sec != null -> filterPlaylistEpisodes(sec, mergedEpisodes)
                pri != null && sec != null -> {
                    val combinedEpisodes = LinkedHashSet<String>()
                    combinedEpisodes.addAll(pri.stringList("episodeUuids"))
                    combinedEpisodes.addAll(sec.stringList("episodeUuids"))
                    val combinedExcluded = LinkedHashSet<String>()
                    combinedExcluded.addAll(pri.stringList("excludedUuids"))
                    combinedExcluded.addAll(sec.stringList("excludedUuids"))
                    val combinedAssigned = LinkedHashSet<String>()
                    combinedAssigned.addAll(pri.stringList("assignedPodcastUuids"))
                    combinedAssigned.addAll(sec.stringList("assignedPodcastUuids"))
                    val merged = buildJsonObject {
                        for ((k, v) in pri) put(k, v)
                        put("episodeUuids", buildJsonArray { combinedEpisodes.forEach { add(JsonPrimitive(it)) } })
                        put("excludedUuids", buildJsonArray { combinedExcluded.forEach { add(JsonPrimitive(it)) } })
                        put("assignedPodcastUuids", buildJsonArray { combinedAssigned.forEach { add(JsonPrimitive(it)) } })
                    }
                    filterPlaylistEpisodes(merged, mergedEpisodes)
                }
                else -> null
            }
        }
    }

    private fun filterPlaylistEpisodes(
        playlist: JsonObject,
        mergedEpisodes: Map<String, JsonObject>,
    ): JsonObject {
        val finished = playlist.boolVal("finished") == true
        val filtered = playlist.stringList("episodeUuids").filter { uuid ->
            val ep = mergedEpisodes[uuid] ?: return@filter true
            val isPlayed = (ep.intVal("playingStatus") ?: 0) == 2
            val isArchived = ep.boolVal("isArchived") == true
            if (finished) !isArchived else (!isPlayed && !isArchived)
        }
        return playlist.withField(
            "episodeUuids",
            buildJsonArray { filtered.forEach { add(JsonPrimitive(it)) } },
        )
    }

    private fun mergeFolders(primary: JsonObject?, secondary: JsonObject?): JsonObject =
        buildJsonObject {
            secondary?.forEach { (k, v) -> put(k, v) }
            primary?.forEach { (k, v) -> put(k, v) }
        }

    private fun JsonObject.withoutQueue(): JsonObject = withField("queuePosition", JsonNull)

    private fun JsonObject.withField(key: String, value: JsonElement): JsonObject = buildJsonObject {
        for ((k, v) in this@withField) put(k, v)
        put(key, value)
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.lng(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.intVal(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolVal(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
}
