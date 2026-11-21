package com.lohe.sync

import org.json.JSONArray
import org.json.JSONObject

enum class Edition(
    val label: String,
    val packageName: String,
    val authority: String,
) {
    PLAY("Google Play", "com.lohe.app", "com.lohe.app.ownersync"),
    FOSS("FOSS", "com.lohe.app.foss", "com.lohe.app.foss.ownersync"),
}

/**
 * Snapshot helper and merger for Lohe owner-sync snapshots.
 *
 * Supports both:
 * 1. v1219.0+ `lohe-backup-v2` / `driveSync` payloads (delegating full 2-way merge to
 *    `OwnerSyncProvider` + `DriveSyncEngine` while boosting the chosen primary edition's
 *    `upNextUpdatedAt` timestamp so its Up Next queue ordering wins).
 * 2. Legacy v1 `schemaVersion: 1` payloads.
 */
object SnapshotMerger {

    fun isDriveSyncV2Payload(rawJson: String): Boolean {
        return try {
            val root = JSONObject(rawJson.trim())
            root.has("driveSync") || root.optString("format") == "lohe-backup-v2"
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ensures [primaryJson]'s `driveSync.upNextUpdatedAt` is strictly newer than any existing
     * timestamp on the secondary device so the chosen primary edition's Up Next queue order
     * is preserved during a two-way smart merge.
     */
    fun withPrimaryUpNextPriority(
        primaryJson: String,
        secondaryJson: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val primaryRoot = JSONObject(primaryJson.trim())
        val secondaryUpNextTs = try {
            JSONObject(secondaryJson.trim())
                .optJSONObject("driveSync")
                ?.optLong("upNextUpdatedAt", 0L) ?: 0L
        } catch (_: Exception) {
            0L
        }
        val syncObj = primaryRoot.optJSONObject("driveSync") ?: JSONObject().also {
            primaryRoot.put("driveSync", it)
        }
        val currentTs = syncObj.optLong("upNextUpdatedAt", 0L)
        val prioritizedTs = maxOf(nowMillis, currentTs, secondaryUpNextTs) + 1_000L
        syncObj.put("upNextUpdatedAt", prioritizedTs)
        return primaryRoot.toString()
    }

    fun merge(
        playJson: String,
        fossJson: String,
        primary: Edition,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val playRoot = JSONObject(playJson.trim())
        val fossRoot = JSONObject(fossJson.trim())
        val primaryRoot = if (primary == Edition.PLAY) playRoot else fossRoot
        val secondaryRoot = if (primary == Edition.PLAY) fossRoot else playRoot

        val mergedPodcasts = mergePodcasts(
            primaryRoot.optJSONArray("podcasts") ?: JSONArray(),
            secondaryRoot.optJSONArray("podcasts") ?: JSONArray(),
        )
        val mergedEpisodes = mergeEpisodes(
            primaryRoot.optJSONArray("episodes") ?: JSONArray(),
            secondaryRoot.optJSONArray("episodes") ?: JSONArray(),
        )
        val mergedEpisodeByUuid = linkedMapOf<String, JSONObject>()
        for (i in 0 until mergedEpisodes.length()) {
            val ep = mergedEpisodes.getJSONObject(i)
            val key = ep.optString("uuid").ifBlank { ep.optString("id") }
            if (key.isNotBlank()) mergedEpisodeByUuid[key] = ep
        }
        val mergedHistory = mergeListeningHistory(
            primaryRoot.optJSONArray("listeningHistory") ?: JSONArray(),
            secondaryRoot.optJSONArray("listeningHistory") ?: JSONArray(),
            mergedEpisodeByUuid.keys,
        )
        val mergedPlaylists = mergePlaylists(
            primaryRoot.optJSONArray("playlists") ?: JSONArray(),
            secondaryRoot.optJSONArray("playlists") ?: JSONArray(),
            mergedEpisodeByUuid,
        )
        val mergedFolders = mergeFolders(
            primaryRoot.optJSONObject("podcastFolders"),
            secondaryRoot.optJSONObject("podcastFolders"),
        )

        return JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", nowMillis)
            put("sourcePackage", "com.lohe.sync")
            put("podcasts", mergedPodcasts)
            put("episodes", mergedEpisodes)
            put("listeningHistory", mergedHistory)
            put("playlists", mergedPlaylists)
            put("podcastFolders", mergedFolders)
        }.toString()
    }

    private fun mergePodcasts(primary: JSONArray, secondary: JSONArray): JSONArray {
        val byKey = linkedMapOf<String, JSONObject>()
        for (i in 0 until primary.length()) {
            val obj = primary.optJSONObject(i) ?: continue
            val key = obj.optString("uuid").ifBlank { obj.optString("id") }.ifBlank { obj.optString("feedUrl") }
            if (key.isNotBlank()) byKey[key] = JSONObject(obj.toString())
        }
        for (i in 0 until secondary.length()) {
            val sec = secondary.optJSONObject(i) ?: continue
            val key = sec.optString("uuid").ifBlank { sec.optString("id") }.ifBlank { sec.optString("feedUrl") }
            if (key.isBlank()) continue
            val pri = byKey[key]
            if (pri == null) {
                byKey[key] = JSONObject(sec.toString())
            } else {
                val priSub = pri.optBoolean("isSubscribed", false)
                val secSub = sec.optBoolean("isSubscribed", false)
                pri.put("isSubscribed", priSub || secSub)
            }
        }
        val out = JSONArray()
        byKey.values.forEach { out.put(it) }
        return out
    }

    private fun mergeEpisodes(primary: JSONArray, secondary: JSONArray): JSONArray {
        val primaryByKey = linkedMapOf<String, JSONObject>()
        val primaryQueue = mutableListOf<String>()
        val queuedWithPos = mutableListOf<Pair<Int, String>>()

        for (i in 0 until primary.length()) {
            val obj = primary.optJSONObject(i) ?: continue
            val uuid = obj.optString("uuid").ifBlank { obj.optString("id") }
            if (uuid.isBlank()) continue
            primaryByKey[uuid] = obj
            if (!obj.isNull("queuePosition") && obj.has("queuePosition")) {
                queuedWithPos.add(obj.optInt("queuePosition", i) to uuid)
            }
        }
        queuedWithPos.sortedBy { it.first }.forEach { primaryQueue.add(it.second) }

        val secondaryByKey = linkedMapOf<String, JSONObject>()
        for (i in 0 until secondary.length()) {
            val obj = secondary.optJSONObject(i) ?: continue
            val uuid = obj.optString("uuid").ifBlank { obj.optString("id") }
            if (uuid.isNotBlank()) secondaryByKey[uuid] = obj
        }

        val allKeys = LinkedHashSet<String>().apply {
            addAll(primaryByKey.keys)
            addAll(secondaryByKey.keys)
        }

        val mergedWithoutQueue = linkedMapOf<String, JSONObject>()
        for (uuid in allKeys) {
            val p = primaryByKey[uuid]
            val s = secondaryByKey[uuid]
            mergedWithoutQueue[uuid] = when {
                p != null && s != null -> mergeEpisodePair(p, s)
                p != null -> JSONObject(p.toString())
                else -> JSONObject(s!!.toString())
            }
        }

        val survivingQueue = primaryQueue.filter { uuid ->
            val ep = mergedWithoutQueue[uuid] ?: return@filter false
            val playingStatus = ep.optInt("playingStatus", 0)
            val isArchived = ep.optBoolean("isArchived", false)
            playingStatus != 2 && !isArchived
        }
        val queueIndex = survivingQueue.withIndex().associate { (idx, id) -> id to idx }

        val out = JSONArray()
        mergedWithoutQueue.forEach { (uuid, ep) ->
            val qPos = queueIndex[uuid]
            if (qPos != null) {
                ep.put("queuePosition", qPos)
            } else {
                ep.put("queuePosition", JSONObject.NULL)
            }
            out.put(ep)
        }
        return out
    }

    private fun mergeEpisodePair(primary: JSONObject, secondary: JSONObject): JSONObject {
        val pActivity = activityTimestamp(primary)
        val sActivity = activityTimestamp(secondary)
        val winner = when {
            sActivity > pActivity -> secondary
            pActivity > sActivity -> primary
            else -> {
                val pStatus = primary.optInt("playingStatus", 0)
                val sStatus = secondary.optInt("playingStatus", 0)
                when {
                    sStatus > pStatus -> secondary
                    pStatus > sStatus -> primary
                    secondary.optLong("playedUpToSeconds", 0L) > primary.optLong("playedUpToSeconds", 0L) -> secondary
                    else -> primary
                }
            }
        }
        val merged = JSONObject(primary.toString())
        val winningStatus = winner.optInt("playingStatus", 0)
        merged.put("playingStatus", winningStatus)
        merged.put("playedUpToSeconds", if (winningStatus == 2) 0L else winner.optLong("playedUpToSeconds", 0L))
        merged.put(
            "listenedSeconds",
            maxOf(primary.optLong("listenedSeconds", 0L), secondary.optLong("listenedSeconds", 0L)),
        )
        val maxPlayedAt = maxOf(primary.optLong("playedAt", 0L), secondary.optLong("playedAt", 0L))
        if (maxPlayedAt > 0L) merged.put("playedAt", maxPlayedAt)
        val maxLastPlayedAt = maxOf(primary.optLong("lastPlayedAt", 0L), secondary.optLong("lastPlayedAt", 0L))
        if (maxLastPlayedAt > 0L) merged.put("lastPlayedAt", maxLastPlayedAt)
        return merged
    }

    private fun activityTimestamp(ep: JSONObject): Long = maxOf(
        ep.optLong("lastPlayedAt", 0L),
        ep.optLong("playedAt", 0L),
        ep.optLong("archivedAt", 0L),
    )

    private fun mergeListeningHistory(
        primary: JSONArray,
        secondary: JSONArray,
        existingEpisodeUuids: Set<String>,
    ): JSONArray {
        val byKey = linkedMapOf<String, JSONObject>()
        fun ingest(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val uuid = row.optString("episodeUuid")
                if (uuid.isBlank() || uuid in existingEpisodeUuids) continue
                val existing = byKey[uuid]
                if (existing == null || row.optLong("listenedSeconds", 0L) > existing.optLong("listenedSeconds", 0L)) {
                    byKey[uuid] = JSONObject(row.toString())
                }
            }
        }
        ingest(primary)
        ingest(secondary)
        val out = JSONArray()
        byKey.values.forEach { out.put(it) }
        return out
    }

    private fun mergePlaylists(
        primary: JSONArray,
        secondary: JSONArray,
        mergedEpisodesByUuid: Map<String, JSONObject>,
    ): JSONArray {
        val byId = linkedMapOf<String, JSONObject>()
        for (i in 0 until primary.length()) {
            val pl = primary.optJSONObject(i) ?: continue
            val id = pl.optString("id")
            if (id.isNotBlank()) byId[id] = JSONObject(pl.toString())
        }
        for (i in 0 until secondary.length()) {
            val sec = secondary.optJSONObject(i) ?: continue
            val id = sec.optString("id")
            if (id.isBlank()) continue
            val pri = byId[id]
            if (pri == null) {
                byId[id] = JSONObject(sec.toString())
            } else {
                val combined = LinkedHashSet<String>()
                val priEps = pri.optJSONArray("episodeUuids") ?: JSONArray()
                val secEps = sec.optJSONArray("episodeUuids") ?: JSONArray()
                for (j in 0 until priEps.length()) combined.add(priEps.optString(j))
                for (j in 0 until secEps.length()) combined.add(secEps.optString(j))
                val arr = JSONArray()
                combined.filter { it.isNotBlank() }.forEach { arr.put(it) }
                pri.put("episodeUuids", arr)
            }
        }

        val out = JSONArray()
        byId.values.forEach { pl ->
            val includeFinished = pl.optBoolean("finished", false)
            val rawEps = pl.optJSONArray("episodeUuids") ?: JSONArray()
            val filtered = JSONArray()
            for (i in 0 until rawEps.length()) {
                val epId = rawEps.optString(i)
                val ep = mergedEpisodesByUuid[epId]
                val done = ep != null && (ep.optInt("playingStatus", 0) == 2 || ep.optBoolean("isArchived", false))
                if (includeFinished || !done) {
                    filtered.put(epId)
                }
            }
            pl.put("episodeUuids", filtered)
            out.put(pl)
        }
        return out
    }

    private fun mergeFolders(primary: JSONObject?, secondary: JSONObject?): JSONObject {
        val merged = JSONObject()
        if (secondary != null) {
            val keys = secondary.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                merged.put(k, secondary.optString(k))
            }
        }
        if (primary != null) {
            val keys = primary.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                merged.put(k, primary.optString(k))
            }
        }
        return merged
    }
}
