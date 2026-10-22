package com.lohe.app.dev.pocketcasts

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A Pocket Casts library, in the shape Lohe can act on.
 *
 * The export that produces this is normalised on the Pocket Casts side: the
 * two apps describe the same library in different dialects — one in podcast
 * feeds, the other in iTunes catalog ids — and the translation happens once,
 * at the edge, rather than being smeared through the importer.
 */
data class PocketCastsLibrary(
    val shows: List<PcShow> = emptyList(),
    val episodes: List<PcEpisode> = emptyList(),
    val playlists: List<PcPlaylist> = emptyList(),
    val folders: List<PcFolder> = emptyList(),
    val upNext: List<String> = emptyList(),
) {

    /** The episodes belonging to one show. */
    fun episodesOf(showUuid: String): List<PcEpisode> =
        episodes.filter { it.podcastUuid == showUuid }

    companion object {

        /** Reads the canonical export written by `tools/pocketcasts-export.py`. */
        fun fromJson(root: JsonObject): PocketCastsLibrary {
            val shows = root.array("podcasts").mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val uuid = obj.string("uuid") ?: return@mapNotNull null
                PcShow(
                    uuid = uuid,
                    title = obj.string("title") ?: uuid,
                    feedUrl = obj.string("feedUrl"),
                )
            }

            val episodes = root.array("episodes").mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val uuid = obj.string("uuid") ?: return@mapNotNull null
                PcEpisode(
                    uuid = uuid,
                    podcastUuid = obj.string("podcastUuid") ?: "",
                    title = obj.string("title"),
                    mediaUrl = obj.string("mediaUrl"),
                    publishedMs = obj.long("publishedMs"),
                    durationSeconds = obj.double("durationSeconds")?.toInt(),
                    status = obj.string("status") ?: "unplayed",
                    playedUpToSeconds = obj.long("playedUpToSeconds") ?: 0L,
                    starred = obj.bool("starred") ?: false,
                    archived = obj.bool("archived") ?: (obj.bool("deleted") ?: false),
                    deleted = obj.bool("deleted") ?: false,
                )
            }

            val playlists = root.array("playlists").mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj.string("name") ?: return@mapNotNull null
                PcPlaylist(
                    name = name,
                    episodeUuids = obj.strings("episodeUuids"),
                    podcastUuids = obj.strings("podcastUuids"),
                    minDurationMinutes = obj.int("minDurationMinutes") ?: 0,
                    maxDurationMinutes = obj.int("maxDurationMinutes") ?: 0,
                    kind = obj.string("kind") ?: "rule",
                    finished = obj.bool("finished") ?: false,
                    starred = obj.bool("starred") ?: false,
                    downloaded = obj.bool("downloaded") ?: false,
                    notDownloaded = obj.bool("notDownloaded") ?: false,
                    allPodcasts = obj.bool("allPodcasts") ?: false,
                )
            }

            val folders = root.array("folders").mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj.string("name") ?: return@mapNotNull null
                PcFolder(name = name, podcastUuids = obj.strings("podcastUuids"))
            }

            return PocketCastsLibrary(
                shows = shows,
                episodes = episodes,
                playlists = playlists,
                folders = folders,
                upNext = root.strings("upNext"),
            )
        }

        private fun JsonObject.array(key: String): List<kotlinx.serialization.json.JsonElement> =
            (this[key] as? JsonArray)?.toList() ?: emptyList()

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content.isNotBlank() }?.content

        private fun JsonObject.long(key: String): Long? =
            (this[key] as? JsonPrimitive)?.longOrNull

        private fun JsonObject.int(key: String): Int? =
            (this[key] as? JsonPrimitive)?.intOrNull

        private fun JsonObject.double(key: String): Double? =
            (this[key] as? JsonPrimitive)?.doubleOrNull

        private fun JsonObject.bool(key: String): Boolean? =
            (this[key] as? JsonPrimitive)?.booleanOrNull

        private fun JsonObject.strings(key: String): List<String> =
            array(key).mapNotNull { (it as? JsonPrimitive)?.content }
    }
}

/** One subscribed show, as Pocket Casts knows it. */
data class PcShow(
    val uuid: String,
    val title: String,
    val feedUrl: String?,
)

/** One episode and the state the listener has put it in. */
data class PcEpisode(
    val uuid: String,
    val podcastUuid: String,
    val title: String?,
    val mediaUrl: String?,
    val publishedMs: Long?,
    val durationSeconds: Int?,
    /** "played", "in_progress" or "unplayed". */
    val status: String,
    val playedUpToSeconds: Long,
    val starred: Boolean,
    /**
     * Filed away in Pocket Casts.
     *
     * The API calls this "deleted", but the account has auto-archive on, so
     * what it really means is "the listener has finished with this and it is
     * out of the way" — which is Lohe's *archived*, not Lohe's *deleted*.
     */
    val archived: Boolean = false,
    /** Left as it is: Pocket Casts' own deleted flag, for reference. */
    val deleted: Boolean = false,
)

/** A playlist: a name and the episodes in it, in order. */
data class PcPlaylist(
    val name: String,
    val episodeUuids: List<String> = emptyList(),
    /**
     * The shows a rule-based playlist follows.
     *
     * Pocket Casts has two kinds of playlist: one with episodes picked by
     * hand, and one that collects by a rule — these shows, this length. The
     * second kind carries no episode list at all, and copying it across as an
     * empty list would leave Lohe with an empty playlist.
     */
    val podcastUuids: List<String> = emptyList(),
    /** Episodes longer than this many minutes, or zero for no bound. */
    val minDurationMinutes: Int = 0,
    /** Episodes no longer than this many minutes, or zero for no bound. */
    val maxDurationMinutes: Int = 0,
    /**
     * "rule", "list", or "state".
     *
     * A "state" playlist collects by the state of the library — played,
     * unplayed, downloaded, starred — rather than by a set of shows. Lohe has
     * no way of saying that, so it is left alone rather than copied as an
     * empty playlist that shows nothing.
     */
    val kind: String = "rule",
    /** Collects the episodes that have been listened to. */
    val finished: Boolean = false,
    /** Collects the episodes that have been starred. */
    val starred: Boolean = false,
    /** Collects the episodes that have been downloaded. */
    val downloaded: Boolean = false,
    /** Collects the episodes that have not been downloaded. */
    val notDownloaded: Boolean = false,
    /** Covers every show in the library rather than a set of them. */
    val allPodcasts: Boolean = false,
) {
    /**
     * Whether this playlist collects by a rule rather than by a list.
     *
     * A rule is worth copying as a rule: it keeps collecting new episodes as
     * they arrive, which a snapshot of episode ids would not.
     */
    val isRule: Boolean
        get() = kind == "rule" &&
            episodeUuids.isEmpty() &&
            (podcastUuids.isNotEmpty() || minDurationMinutes > 0 || maxDurationMinutes > 0)

    /** Whether this playlist filters by the state of the library. */
    val isStateFilter: Boolean get() = kind == "state"
}

/** A folder: a name and the shows filed under it. */
data class PcFolder(
    val name: String,
    val podcastUuids: List<String>,
)
