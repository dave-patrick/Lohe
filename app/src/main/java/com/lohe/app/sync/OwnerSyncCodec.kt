package com.lohe.app.sync

import com.lohe.app.data.UserPlaylist
import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Portable, device-independent snapshot of Lohe's library state:
 * podcasts, episodes (playback/played/archived/starred/Up Next), listening history,
 * custom playlists, and podcast folder assignments.
 *
 * Intentionally excludes private device-local download paths so snapshots are safe
 * for both local Play <-> FOSS sync and future cloud drive sync backends.
 */
data class OwnerSyncSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long,
    val sourcePackage: String,
    val podcasts: List<PodcastEntity>,
    val episodes: List<EpisodeEntity>,
    val listeningHistory: List<ListeningHistoryEntity>,
    val playlists: List<UserPlaylist>,
    val podcastFolders: Map<String, String>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

object OwnerSyncCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeSnapshot(snapshot: OwnerSyncSnapshot): String {
        val root = buildJsonObject {
            put("schemaVersion", snapshot.schemaVersion)
            put("exportedAt", snapshot.exportedAt)
            put("sourcePackage", snapshot.sourcePackage)
            put("podcasts", buildJsonArray { snapshot.podcasts.forEach { add(encodePodcast(it)) } })
            put("episodes", buildJsonArray { snapshot.episodes.forEach { add(encodeEpisode(it)) } })
            put("listeningHistory", buildJsonArray { snapshot.listeningHistory.forEach { add(encodeHistory(it)) } })
            put("playlists", buildJsonArray { snapshot.playlists.forEach { add(encodePlaylist(it)) } })
            put(
                "podcastFolders",
                buildJsonObject {
                    snapshot.podcastFolders.forEach { (uuid, folder) -> put(uuid, folder) }
                },
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun decodeSnapshot(rawJson: String): OwnerSyncSnapshot {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val version = root.intVal("schemaVersion") ?: 1
        require(version == OwnerSyncSnapshot.CURRENT_SCHEMA_VERSION) {
            "Unsupported owner-sync schemaVersion: $version"
        }
        val exportedAt = root.lng("exportedAt") ?: System.currentTimeMillis()
        val sourcePackage = root.str("sourcePackage").orEmpty()
        val podcasts = root["podcasts"]?.jsonArray?.mapNotNull { decodePodcast(it.jsonObject) }.orEmpty()
        val episodes = root["episodes"]?.jsonArray?.mapNotNull { decodeEpisode(it.jsonObject) }.orEmpty()
        val history = root["listeningHistory"]?.jsonArray?.mapNotNull { decodeHistory(it.jsonObject) }.orEmpty()
        val playlists = root["playlists"]?.jsonArray?.mapNotNull { decodePlaylist(it.jsonObject) }.orEmpty()
        val folders = buildMap {
            root["podcastFolders"]?.jsonObject?.forEach { (k, v) ->
                val value = (v as? JsonPrimitive)?.contentOrNull?.trim()
                if (k.isNotBlank() && !value.isNullOrEmpty()) put(k, value)
            }
        }
        return OwnerSyncSnapshot(
            schemaVersion = version,
            exportedAt = exportedAt,
            sourcePackage = sourcePackage,
            podcasts = podcasts,
            episodes = episodes,
            listeningHistory = history,
            playlists = playlists,
            podcastFolders = folders,
        )
    }

    private fun encodePodcast(p: PodcastEntity): JsonObject = buildJsonObject {
        put("uuid", p.uuid)
        put("title", p.title)
        putNullable("author", p.author)
        putNullable("description", p.description)
        putNullable("artworkUrl", p.artworkUrl)
        put("feedUrl", p.feedUrl)
        putNullableLong("lastEpisodePublished", p.lastEpisodePublished)
        put("subscribedAt", p.subscribedAt)
        put("unreadCount", p.unreadCount)
        put("autoDownload", p.autoDownload)
        put("autoAddNewEpisodesToUpNext", p.autoAddNewEpisodesToUpNext)
        put("autoAddNewEpisodesAtTop", p.autoAddNewEpisodesAtTop)
        put("isSubscribed", p.isSubscribed)
        putNullable("feedEtag", p.feedEtag)
        putNullable("feedLastModified", p.feedLastModified)
    }

    private fun decodePodcast(o: JsonObject): PodcastEntity? {
        val uuid = o.str("uuid")?.takeIf { it.isNotBlank() } ?: return null
        val feedUrl = o.str("feedUrl") ?: return null
        return PodcastEntity(
            uuid = uuid,
            title = o.str("title").orEmpty(),
            author = o.str("author"),
            description = o.str("description"),
            artworkUrl = o.str("artworkUrl"),
            feedUrl = feedUrl,
            lastEpisodePublished = o.lng("lastEpisodePublished"),
            subscribedAt = o.lng("subscribedAt") ?: System.currentTimeMillis(),
            unreadCount = o.intVal("unreadCount") ?: 0,
            autoDownload = o.boolVal("autoDownload") ?: false,
            autoAddNewEpisodesToUpNext = o.boolVal("autoAddNewEpisodesToUpNext") ?: false,
            autoAddNewEpisodesAtTop = o.boolVal("autoAddNewEpisodesAtTop") ?: false,
            isSubscribed = o.boolVal("isSubscribed") ?: true,
            feedEtag = o.str("feedEtag"),
            feedLastModified = o.str("feedLastModified"),
        )
    }

    private fun encodeEpisode(e: EpisodeEntity): JsonObject = buildJsonObject {
        put("uuid", e.uuid)
        put("podcastUuid", e.podcastUuid)
        put("title", e.title)
        put("audioUrl", e.audioUrl)
        put("published", e.published)
        putNullableInt("durationSeconds", e.durationSeconds)
        putNullableLong("sizeBytes", e.sizeBytes)
        putNullable("fileType", e.fileType)
        putNullable("author", e.author)
        putNullable("artworkUrl", e.artworkUrl)
        putNullable("description", e.description)
        put("playingStatus", e.playingStatus)
        putNullableLong("playedAt", e.playedAt)
        put("playedUpToSeconds", e.playedUpToSeconds)
        put("listenedSeconds", e.listenedSeconds)
        put("starred", e.starred)
        put("isDeleted", e.isDeleted)
        put("isArchived", e.isArchived)
        putNullableLong("archivedAt", e.archivedAt)
        putNullableInt("queuePosition", e.queuePosition)
        putNullableLong("lastPlayedAt", e.lastPlayedAt)
        putNullable("chapters", e.chapters)
    }

    private fun decodeEpisode(o: JsonObject): EpisodeEntity? {
        val uuid = o.str("uuid")?.takeIf { it.isNotBlank() } ?: return null
        val podcastUuid = o.str("podcastUuid")?.takeIf { it.isNotBlank() } ?: return null
        val audioUrl = o.str("audioUrl") ?: return null
        return EpisodeEntity(
            uuid = uuid,
            podcastUuid = podcastUuid,
            title = o.str("title").orEmpty(),
            audioUrl = audioUrl,
            published = o.lng("published") ?: 0L,
            durationSeconds = o.intVal("durationSeconds"),
            sizeBytes = o.lng("sizeBytes"),
            fileType = o.str("fileType"),
            author = o.str("author"),
            artworkUrl = o.str("artworkUrl"),
            description = o.str("description"),
            playingStatus = (o.intVal("playingStatus") ?: 0).coerceIn(0, 2),
            playedAt = o.lng("playedAt"),
            playedUpToSeconds = (o.lng("playedUpToSeconds") ?: 0L).coerceAtLeast(0L),
            listenedSeconds = (o.lng("listenedSeconds") ?: 0L).coerceAtLeast(0L),
            starred = o.boolVal("starred") ?: false,
            isDeleted = o.boolVal("isDeleted") ?: false,
            isArchived = o.boolVal("isArchived") ?: false,
            archivedAt = o.lng("archivedAt"),
            queuePosition = o.intVal("queuePosition"),
            lastPlayedAt = o.lng("lastPlayedAt"),
            localPath = null,
            downloadState = DownloadState.NONE,
            chapters = o.str("chapters"),
        )
    }

    private fun encodeHistory(h: ListeningHistoryEntity): JsonObject = buildJsonObject {
        put("episodeUuid", h.episodeUuid)
        put("listenedSeconds", h.listenedSeconds)
        putNullableInt("durationSeconds", h.durationSeconds)
        putNullable("title", h.title)
    }

    private fun decodeHistory(o: JsonObject): ListeningHistoryEntity? {
        val uuid = o.str("episodeUuid")?.takeIf { it.isNotBlank() } ?: return null
        return ListeningHistoryEntity(
            episodeUuid = uuid,
            listenedSeconds = (o.lng("listenedSeconds") ?: 0L).coerceAtLeast(0L),
            durationSeconds = o.intVal("durationSeconds"),
            title = o.str("title"),
        )
    }

    private fun encodePlaylist(pl: UserPlaylist): JsonObject = buildJsonObject {
        put("id", pl.id)
        put("name", pl.name)
        put("episodeUuids", buildJsonArray { pl.episodeUuids.forEach { add(JsonPrimitive(it)) } })
        put("folder", pl.folder)
        put("autoDownload", pl.autoDownload)
        put("shows", buildJsonArray { pl.shows.forEach { add(JsonPrimitive(it)) } })
        put("minDurationMinutes", pl.minDurationMinutes)
        put("maxDurationMinutes", pl.maxDurationMinutes)
        put("finished", pl.finished)
        put("createdAt", pl.createdAt)
        put("snapshot", pl.snapshot)
        put("pinned", pl.pinned)
        put("excludedUuids", buildJsonArray { pl.excludedUuids.forEach { add(JsonPrimitive(it)) } })
        put("assignedPodcastUuids", buildJsonArray { pl.assignedPodcastUuids.forEach { add(JsonPrimitive(it)) } })
    }

    private fun decodePlaylist(o: JsonObject): UserPlaylist? {
        val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val name = o.str("name")?.takeIf { it.isNotBlank() } ?: "Untitled Playlist"
        return UserPlaylist(
            id = id,
            name = name,
            episodeUuids = o.strList("episodeUuids"),
            folder = o.str("folder").orEmpty(),
            autoDownload = o.boolVal("autoDownload") ?: true,
            shows = o.strList("shows"),
            minDurationMinutes = (o.intVal("minDurationMinutes") ?: 0).coerceAtLeast(0),
            maxDurationMinutes = (o.intVal("maxDurationMinutes") ?: 0).coerceAtLeast(0),
            finished = o.boolVal("finished") ?: false,
            createdAt = o.lng("createdAt") ?: System.currentTimeMillis(),
            snapshot = o.boolVal("snapshot") ?: false,
            pinned = o.boolVal("pinned") ?: false,
            excludedUuids = o.strList("excludedUuids"),
            assignedPodcastUuids = o.strList("assignedPodcastUuids"),
        )
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInt(key: String, value: Int?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableLong(key: String, value: Long?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.lng(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.intVal(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolVal(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
}
