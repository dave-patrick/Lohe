package com.lohe.app.dev.mcp

import android.content.Context
import com.lohe.app.data.ServiceLocator
import com.lohe.app.data.UserPlaylist
import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.db.entity.toSummary
import com.lohe.app.dev.DevSettingsBridge
import com.lohe.app.dev.pocketcasts.PocketCastsImporter
import com.lohe.app.dev.pocketcasts.PocketCastsLibrary
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Raised when a tool is asked for something it cannot do. */
class ToolException(message: String) : Exception(message)

/**
 * One operation the dev surface can run.
 *
 * A named type rather than a bare `suspend (JsonObject) -> JsonObject` so
 * that the operations can live in a table: a table of them can be counted,
 * listed and checked against the advertised catalogue, which a set of
 * branches in a `when` cannot.
 */
fun interface McpOperation {
    suspend operator fun invoke(args: JsonObject): JsonObject
}

/**
 * Every operation the dev surface can perform.
 *
 * One place for the behaviour, two front doors: the MCP server exposes each
 * operation as a tool, and the REST surface in `DevApi` calls the same
 * operation with the same arguments. Neither front door contains any logic of
 * its own, so the two cannot drift apart.
 */
class LoheToolkit(context: Context) {

    private val appContext = context.applicationContext
    private val playback = McpPlayback(appContext)

    private val repository get() = ServiceLocator.subscriptionRepository()
    private val playlists get() = ServiceLocator.playlistRepository()
    private val settings get() = ServiceLocator.settingsStore()

    /**
     * The operations this toolkit can run, by name.
     *
     * A table rather than a `when` so that the set of names is a value that
     * can be checked against the advertised catalogue, rather than a set of
     * branches nobody can enumerate.
     */
    private val operations: Map<String, McpOperation> = mapOf(
        "lohe_shows" to McpOperation { shows(it) },
        "lohe_show" to McpOperation { show(it) },
        "lohe_subscribe" to McpOperation { subscribe(it) },
        "lohe_unsubscribe" to McpOperation { unsubscribe(it) },
        "lohe_refresh_feeds" to McpOperation { refreshFeeds() },
        "lohe_search_catalog" to McpOperation { searchCatalog(it) },
        "lohe_episodes" to McpOperation { episodes(it) },
        "lohe_episode" to McpOperation { episode(it) },
        "lohe_mark_played" to McpOperation { markPlayed(it) },
        "lohe_star" to McpOperation { star(it) },
        "lohe_archive" to McpOperation { archive(it) },
        "lohe_delete_episode" to McpOperation { deleteEpisode(it) },
        "lohe_download" to McpOperation { download(it) },
        "lohe_downloads" to McpOperation { downloads() },
        "lohe_queue" to McpOperation { queue() },
        "lohe_enqueue" to McpOperation { enqueue(it) },
        "lohe_dequeue" to McpOperation { dequeue(it) },
        "lohe_clear_queue" to McpOperation { clearQueue() },
        "lohe_reorder_queue" to McpOperation { reorderQueue(it) },
        "lohe_playlists" to McpOperation { listPlaylists() },
        "lohe_playlist" to McpOperation { playlist(it) },
        "lohe_create_playlist" to McpOperation { createPlaylist(it) },
        "lohe_playlist_update" to McpOperation { playlistUpdate(it) },
        "lohe_playlist_add" to McpOperation { playlistAdd(it) },
        "lohe_playlist_remove" to McpOperation { playlistRemove(it) },
        "lohe_delete_playlist" to McpOperation { deletePlaylist(it) },
        "lohe_play" to McpOperation { play(it) },
        "lohe_import_pocketcasts" to McpOperation { importPocketCasts(it) },
        "lohe_bulk_deleted" to McpOperation { bulkDeleted(it) },
        "lohe_pause" to McpOperation { pause() },
        "lohe_now_playing" to McpOperation { nowPlaying() },
        "lohe_settings" to McpOperation { readSettings() },
        "lohe_set_setting" to McpOperation { writeSetting(it) },
    )

    /** The names of the operations this toolkit can run. */
    val operationNames: Set<String> get() = operations.keys

    /** Runs the operation named [operation] with [args]. */
    suspend fun execute(operation: String, args: JsonObject): JsonObject =
        operations[operation]?.invoke(args)
            ?: throw ToolException("Unknown operation: $operation")

    // --- Shows --------------------------------------------------------------

    private suspend fun shows(args: JsonObject): JsonObject {
        val folder = args.stringOrNull("folder")
        val folders = playlists.podcastFolders.value
        val shows = repository.observePodcasts().first()
            .filter { folder == null || folders[it.uuid] == folder }
            .map { podcastJson(it, folders[it.uuid]) }
        return jsonObject(
            "shows" to JsonArray(shows),
            "count" to shows.size,
        )
    }

    private suspend fun show(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        val podcast = repository.getPodcast(uuid)
            ?: throw ToolException("No show with uuid $uuid")
        val episodes = repository.allEpisodesOnce(uuid)
        val folders = playlists.podcastFolders.value
        return jsonObject(
            "show" to podcastJson(podcast, folders[uuid]),
            "episodeCount" to episodes.size,
            "unplayedCount" to episodes.count { it.playingStatus == EpisodeEntity.UNPLAYED },
            "downloadedCount" to episodes.count { it.isDownloaded },
            "episodes" to JsonArray(episodes.take(10).map { episodeJson(it, podcast.title) }),
        )
    }

    private suspend fun subscribe(args: JsonObject): JsonObject {
        val feedUrl = args.stringOrNull("feedUrl")
        val title = args.stringOrNull("title")
        if (feedUrl == null && title == null) {
            throw ToolException("feedUrl or title is required")
        }

        val resolved = feedUrl ?: run {
            val match = ServiceLocator.catalogSearcher().findFeedForTitle(title!!)
                .getOrElse { throw ToolException("Catalog lookup failed: ${it.message}") }
                ?: throw ToolException("No show in the catalog matched \"$title\"")
            match.feedUrl
        }

        val result = repository.importFeed(resolved)
        val podcast = result.getOrElse {
            throw ToolException("Could not subscribe: ${it.message}")
        }
        return jsonObject(
            "ok" to true,
            "show" to podcastJson(podcast, null),
        )
    }

    private suspend fun unsubscribe(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        val podcast = repository.getPodcast(uuid)
            ?: throw ToolException("No show with uuid $uuid")
        repository.unsubscribe(uuid)
        return jsonObject("ok" to true, "removed" to podcast.title)
    }

    private suspend fun refreshFeeds(): JsonObject {
        val summary = repository.refreshAllFeeds()
        return jsonObject(
            "ok" to true,
            "total" to summary.total,
            "refreshed" to summary.refreshed,
            "failed" to summary.failed,
            "failureTitles" to JsonArray(summary.failureTitles.map { it.toJsonElement() }),
        )
    }

    private suspend fun searchCatalog(args: JsonObject): JsonObject {
        val query = args.stringOrNull("query") ?: throw ToolException("query is required")
        val limit = (args.intOrNull("limit") ?: 15).coerceIn(1, 50)
        val results = ServiceLocator.catalogSearcher().search(query, limit)
            .getOrElse { throw ToolException("Search failed: ${it.message}") }
        return jsonObject(
            "results" to JsonArray(
                results.map {
                    jsonObject(
                        "title" to it.title,
                        "author" to it.author,
                        "feedUrl" to it.feedUrl,
                        "artworkUrl" to it.artworkUrl,
                        "genre" to it.genre,
                        "episodeCount" to it.episodeCount,
                        "subscribed" to (repository.subscriptionForFeed(it.feedUrl) != null),
                    )
                },
            ),
            "count" to results.size,
        )
    }

    // --- Episodes -----------------------------------------------------------

    private suspend fun episodes(args: JsonObject): JsonObject {
        val podcastUuid = args.stringOrNull("podcastUuid")
        val query = args.stringOrNull("query")?.lowercase()
        val state = args.stringOrNull("state")?.lowercase()
        val limit = (args.intOrNull("limit") ?: 50).coerceIn(1, 500)
        val offset = (args.intOrNull("offset") ?: 0).coerceAtLeast(0)

        val all = if (podcastUuid != null) {
            repository.allEpisodesOnce(podcastUuid)
        } else {
            ServiceLocator.database().episodeDao().allOnce()
        }

        val showTitles = repository.observePodcasts().first()
            .associate { it.uuid to it.title }

        val filtered = all
            .filter { episode ->
                when (state) {
                    null, "visible" -> !episode.isDeleted && !episode.isArchived
                    "unplayed" -> episode.playingStatus == EpisodeEntity.UNPLAYED &&
                        !episode.isDeleted && !episode.isArchived
                    "in_progress" -> episode.playingStatus == EpisodeEntity.IN_PROGRESS
                    "played" -> episode.playingStatus == EpisodeEntity.PLAYED
                    "starred" -> episode.starred
                    "archived" -> episode.isArchived
                    "deleted" -> episode.isDeleted
                    "downloaded" -> episode.isDownloaded
                    "queued" -> episode.isQueued
                    "all" -> true
                    else -> throw ToolException(
                        "Unknown state \"$state\". Use unplayed, in_progress, played, " +
                            "starred, archived, deleted, downloaded, queued, visible or all.",
                    )
                }
            }
            .filter { episode ->
                if (query == null) return@filter true
                episode.title.lowercase().contains(query) ||
                    showTitles[episode.podcastUuid]?.lowercase()?.contains(query) == true
            }
            .sortedByDescending { it.published }

        val page = filtered.drop(offset).take(limit)
        return jsonObject(
            "episodes" to JsonArray(page.map { episodeJson(it, showTitles[it.podcastUuid]) }),
            "total" to filtered.size,
            "offset" to offset,
            "limit" to limit,
        )
    }

    private suspend fun episode(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        val episode = repository.episodeOnce(uuid)
            ?: throw ToolException("No episode with uuid $uuid")
        val show = repository.getPodcast(episode.podcastUuid)
        return jsonObject(
            "episode" to episodeJson(episode, show?.title, includeDescription = true),
        )
    }

    private suspend fun markPlayed(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        val played = args.booleanOrNull("played") ?: true
        if (played) {
            repository.markPlayed(uuid)
            // A finished episode leaves the playlists it was filed into: that
            // is what the app does when the listener marks one listened.
            playlists.removeEpisodeFromAll(uuid)
        } else {
            repository.reopenEpisode(uuid)
        }
        return jsonObject("ok" to true, "uuid" to uuid, "played" to played)
    }

    private suspend fun star(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        val starred = args.booleanOrNull("starred") ?: true
        repository.setStarred(uuid, starred)
        return jsonObject("ok" to true, "uuid" to uuid, "starred" to starred)
    }

    private suspend fun archive(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        val archived = args.booleanOrNull("archived") ?: true
        repository.setArchived(uuid, archived)
        return jsonObject("ok" to true, "uuid" to uuid, "archived" to archived)
    }

    private suspend fun deleteEpisode(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        val deleted = args.booleanOrNull("deleted") ?: true
        if (deleted) {
            repository.deleteEpisode(uuid)
        } else {
            val current = repository.episodeOnce(uuid)
                ?: throw ToolException("No episode with uuid $uuid")
            ServiceLocator.database().episodeDao().upsertAll(listOf(current.copy(isDeleted = false)))
        }
        return jsonObject("ok" to true, "uuid" to uuid, "deleted" to deleted)
    }

    private suspend fun download(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        val episode = repository.episodeOnce(uuid)
            ?: throw ToolException("No episode with uuid $uuid")
        if (episode.isDownloaded) {
            return jsonObject("ok" to true, "uuid" to uuid, "alreadyDownloaded" to true)
        }
        ServiceLocator.downloadManager().start(episode.toSummary())
        return jsonObject("ok" to true, "uuid" to uuid, "started" to true)
    }

    private suspend fun downloads(): JsonObject {
        val list = repository.observeDownloads().first()
        return jsonObject(
            "downloads" to JsonArray(list.map { episodeSummaryJson(it, null) }),
            "count" to list.size,
        )
    }

    // --- Queue --------------------------------------------------------------

    private suspend fun queue(): JsonObject {
        val items = repository.queueSnapshot()
        val showTitles = repository.observePodcasts().first().associate { it.uuid to it.title }
        return jsonObject(
            "queue" to JsonArray(items.map { episodeJson(it, showTitles[it.podcastUuid]) }),
            "count" to items.size,
        )
    }

    private suspend fun enqueue(args: JsonObject): JsonObject {
        val uuids = listOfNotNull(args.stringOrNull("uuid")) + args.stringList("uuids")
        if (uuids.isEmpty()) throw ToolException("uuid or uuids is required")
        uuids.forEach { repository.enqueue(it) }
        return jsonObject("ok" to true, "queued" to uuids)
    }

    private suspend fun dequeue(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        repository.dequeue(uuid)
        return jsonObject("ok" to true, "uuid" to uuid)
    }

    private suspend fun clearQueue(): JsonObject {
        repository.clearAllQueue()
        return jsonObject("ok" to true)
    }

    private suspend fun reorderQueue(args: JsonObject): JsonObject {
        val uuids = args.stringList("uuids")
        if (uuids.isEmpty()) throw ToolException("uuids is required")
        repository.reorderQueue(uuids)
        return jsonObject("ok" to true, "order" to JsonArray(uuids.map { it.toJsonElement() }))
    }

    // --- Playlists ----------------------------------------------------------

    private suspend fun listPlaylists(): JsonObject {
        val list = playlists.playlists.value
        return jsonObject(
            "playlists" to JsonArray(list.map { playlistJson(it) }),
            "count" to list.size,
        )
    }

    private suspend fun playlist(args: JsonObject): JsonObject {
        val id = args.stringOrNull("id")
            ?: args.stringOrNull("playlistId")
            ?: throw ToolException("id is required")
        val found = playlists.playlists.value.firstOrNull { it.id == id }
            ?: throw ToolException("No playlist with id $id")
        val episodes = repository.episodesOnce(found.episodeUuids)
        val showTitles = repository.observePodcasts().first().associate { it.uuid to it.title }
        return jsonObject(
            "playlist" to playlistJson(found),
            "episodes" to JsonArray(episodes.map { episodeJson(it, showTitles[it.podcastUuid]) }),
        )
    }

    private suspend fun createPlaylist(args: JsonObject): JsonObject {
        val name = args.stringOrNull("name") ?: throw ToolException("name is required")
        val episodes = args.stringList("episodeUuids")
        val created = playlists.createPlaylist(
            name = name,
            initialEpisodes = episodes,
            shows = args.stringList("shows"),
            minDurationMinutes = args.intOrNull("minDurationMinutes") ?: 0,
            maxDurationMinutes = args.intOrNull("maxDurationMinutes") ?: 0,
        )
        return jsonObject("ok" to true, "playlist" to playlistJson(created))
    }

    /**
     * Changes the rule a playlist collects by.
     *
     * A playlist can follow a set of shows, a length window, or both: the
     * window narrows the shows when there are any, and stands on its own
     * when there are not.
     */
    private suspend fun playlistUpdate(args: JsonObject): JsonObject {
        val id = args.stringOrNull("playlistId")
            ?: args.stringOrNull("id")
            ?: throw ToolException("playlistId is required")
        val existing = playlists.playlists.value.firstOrNull { it.id == id }
            ?: throw ToolException("No playlist with id $id")

        val shows = if (args.containsKey("shows")) args.stringList("shows") else existing.shows
        playlists.setPlaylistShows(id, shows)

        val min = args.intOrNull("minDurationMinutes") ?: existing.minDurationMinutes
        val max = args.intOrNull("maxDurationMinutes") ?: existing.maxDurationMinutes
        playlists.setPlaylistDuration(id, min, max)

        val updated = playlists.playlists.value.firstOrNull { it.id == id }
            ?: throw ToolException("No playlist with id $id")
        return jsonObject("ok" to true, "playlist" to playlistJson(updated))
    }

    private suspend fun playlistAdd(args: JsonObject): JsonObject {
        val id = args.stringOrNull("playlistId")
            ?: args.stringOrNull("id")
            ?: throw ToolException("playlistId is required")
        val uuids = args.stringList("episodeUuids") + listOfNotNull(args.stringOrNull("episodeUuid"))
        if (uuids.isEmpty()) throw ToolException("episodeUuids is required")
        playlists.addEpisodes(id, uuids)
        return jsonObject("ok" to true, "playlistId" to id, "added" to uuids)
    }

    private suspend fun playlistRemove(args: JsonObject): JsonObject {
        val id = args.stringOrNull("playlistId")
            ?: args.stringOrNull("id")
            ?: throw ToolException("playlistId is required")
        val uuid = args.stringOrNull("episodeUuid") ?: throw ToolException("episodeUuid is required")
        playlists.removeEpisode(id, uuid)
        return jsonObject("ok" to true, "playlistId" to id, "removed" to uuid)
    }

    private suspend fun deletePlaylist(args: JsonObject): JsonObject {
        val id = args.stringOrNull("playlistId")
            ?: args.stringOrNull("id")
            ?: throw ToolException("playlistId is required")
        playlists.deletePlaylist(id)
        return jsonObject("ok" to true, "playlistId" to id)
    }

    // --- Playback -----------------------------------------------------------

    /**
     * Applies a Pocket Casts export to the library.
     *
     * The export is a file in the app's own files directory: it is far too
     * large to pass through a tool call, and it is written there by
     * `adb push` after `tools/pocketcasts-export.py` has run.
     */
    private suspend fun play(args: JsonObject): JsonObject {
        val uuid = args.stringOrNull("uuid") ?: throw ToolException("uuid is required")
        val episode = repository.episodeOnce(uuid)
            ?: throw ToolException("No episode with uuid $uuid")
        val show = repository.getPodcast(episode.podcastUuid)
        playback.play(episode, show)
        return jsonObject(
            "ok" to true,
            "uuid" to uuid,
            "title" to episode.title,
            "showTitle" to show?.title,
        )
    }

    private suspend fun pause(): JsonObject {
        playback.pause()
        return jsonObject("ok" to true)
    }

    private suspend fun nowPlaying(): JsonObject {
        val state = playback.state()
        val playing = (state?.get("isPlaying") as? JsonPrimitive)?.content?.toBoolean() ?: false
        return jsonObject(
            "nowPlaying" to (state ?: JsonObject(emptyMap())),
            "playing" to playing,
        )
    }

    // --- Settings -----------------------------------------------------------

    private suspend fun readSettings(): JsonObject {
        val current = settings.settings.first()
        return jsonObject(
            "settings" to jsonObject(
                "appearance" to current.appearance.name,
                "volumeBoostByDefault" to current.volumeBoostByDefault,
                "skipSilenceByDefault" to current.skipSilenceByDefault,
                "smartSpeedByDefault" to current.smartSpeedByDefault,
                "autoTrimByDefault" to current.autoTrimByDefault,
                "defaultPlaybackSpeed" to current.defaultPlaybackSpeed,
                "skipBackSeconds" to current.skipBackSeconds,
                "skipForwardSeconds" to current.skipForwardSeconds,
                "markPlayedAtEnd" to current.markPlayedAtEnd,
                "autoDownloadQueue" to current.autoDownloadQueue,
                "sleepTimerMinutes" to current.sleepTimerMinutes,
                "podcastSort" to current.podcastSort.name,
                "playlistSort" to current.playlistSort.name,
            ),
        )
    }

    private suspend fun writeSetting(args: JsonObject): JsonObject {
        val key = args.stringOrNull("key") ?: throw ToolException("key is required")
        val raw = args["value"] ?: throw ToolException("value is required")
        // The value is read as text: a JSON number, a JSON boolean and a JSON
        // string all have one honest reading once they are spelled out.
        val value = (raw as? JsonPrimitive)?.content ?: raw.toString()
        DevSettingsBridge.apply(settings, key, value)
        return jsonObject("ok" to true, "key" to key)
    }

    // --- Import -------------------------------------------------------------

    /**
     * Brings the library into line with a Pocket Casts export.
     *
     * The state arrives as the canonical JSON written by
     * `tools/pocketcasts-export.py`: the shows, the episodes and their state,
     * the playlists, the folders and the up-next queue.
     *
     * It can arrive two ways. A small export can be handed over inline as
     * `state`; a whole library is far too large for a tool call, so it is
     * written to the app's files directory and named by `fileName`.
     *
     * A library of tens of thousands of episodes is imported in three
     * phases — the shows, the episodes, then the playlists — because the
     * episodes alone are far too many to arrive at once, and because the
     * playlists can only be written once the episodes they name have been
     * matched. The phase is named by `phase`: "shows", "episodes",
     * "playlists", or "all" for a small library.
     */
    private suspend fun importPocketCasts(args: JsonObject): JsonObject {
        val state = args["state"] as? JsonObject
        val library = when {
            state != null && state.containsKey("podcasts") ->
                PocketCastsLibrary.fromJson(state)
            else ->
                // state is either null or a file reference like {"fileName":"..."}
                // read the export from the app's files directory
                PocketCastsLibrary.fromJson(readExportFile(args))
        }
        val subscribe = args.booleanOrNull("subscribeMissingShows") ?: false
        val replaceFolders = args.booleanOrNull("replaceFolders") ?: false
        val importer = PocketCastsImporter(
            repository = repository,
            playlists = playlists,
            subscribeMissingShows = subscribe,
            replaceFolders = replaceFolders,
        )
        val phase = args.stringOrNull("phase") ?: "all"
        return when (phase) {
            "shows" -> {
                val report = importer.applyShows(library)
                jsonObject(
                    "ok" to true,
                    "phase" to phase,
                    "showsMatched" to report.matched,
                    "showsSubscribed" to report.subscribed,
                    "showsUnmatched" to JsonArray(report.unmatched.map { it.toJsonElement() }),
                )
            }
            "episodes" -> {
                val report = importer.applyEpisodes(library)
                jsonObject(
                    "ok" to true,
                    "phase" to phase,
                    "episodesMatched" to report.matched,
                    "episodesUnmatched" to report.unmatched,
                    "markedPlayed" to report.played,
                    "markedInProgress" to report.inProgress,
                    "markedUnplayed" to report.unplayed,
                    "starred" to report.starred,
                    "archived" to report.archived,
                )
            }
            "playlists" -> {
                val report = importer.applyPlaylists(library)
                jsonObject(
                    "ok" to true,
                    "phase" to phase,
                    "queued" to report.queued,
                    "foldersApplied" to report.foldersApplied,
                    "foldersCleared" to report.foldersCleared,
                    "playlistsApplied" to report.playlistsApplied,
                    "playlistEpisodesApplied" to report.playlistEpisodesApplied,
                    "playlistEpisodesUnmatched" to JsonArray(
                        report.playlistEpisodesUnmatched.map { it.toJsonElement() },
                    ),
                    "playlistsSkipped" to JsonArray(report.playlistsSkipped.map { it.toJsonElement() }),
                )
            }
            "sync-played" -> {
                val report = importer.syncPlayedState(library)
                jsonObject(
                    "ok" to true,
                    "phase" to phase,
                    "matched" to report.matched,
                    "markedPlayed" to report.markedPlayed,
                    "markedUnplayed" to report.markedUnplayed,
                    "libraryShows" to library.shows.size,
                    "libraryEpisodes" to library.episodes.size,
                )
            }
            else -> {
                val report = importer.apply(library)
                jsonObject(
                    "ok" to true,
                    "phase" to phase,
                    "summary" to report.summary(),
                    "report" to jsonObject(
                        "showsMatched" to report.showsMatched,
                        "showsSubscribed" to report.showsSubscribed,
                        "showsUnmatched" to JsonArray(report.showsUnmatched.map { it.toJsonElement() }),
                        "episodesMatched" to report.episodesMatched,
                        "episodesUnmatched" to report.episodesUnmatched,
                        "markedPlayed" to report.markedPlayed,
                        "markedInProgress" to report.markedInProgress,
                        "markedUnplayed" to report.markedUnplayed,
                        "starred" to report.starred,
                        "archived" to report.archived,
                        "queued" to report.queued,
                        "foldersApplied" to report.foldersApplied,
                        "foldersCleared" to report.foldersCleared,
                        "playlistsApplied" to report.playlistsApplied,
                        "playlistEpisodesApplied" to report.playlistEpisodesApplied,
                        "playlistEpisodesUnmatched" to JsonArray(
                            report.playlistEpisodesUnmatched.map { it.toJsonElement() },
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Files a list of episodes away, or brings them back, in one go.
     *
     * The list is read either inline or from a file the host pushed into the
     * app's files directory: a repair over tens of thousands of episodes is
     * too much to carry in a tool call.
     */
    private suspend fun bulkDeleted(args: JsonObject): JsonObject {
        val uuids = args.stringList("uuids").ifEmpty { readUuidFile(args) }
        if (uuids.isEmpty()) {
            throw ToolException("uuids or fileName is required: the episodes to change")
        }
        val deleted = args.booleanOrNull("deleted") ?: false
        val dao = ServiceLocator.database().episodeDao()
        // SQLite has a limit on how many parameters a statement may carry,
        // so the list is written in chunks.
        uuids.chunked(400).forEach { chunk ->
            dao.setDeletedFor(chunk, deleted)
        }
        return jsonObject(
            "ok" to true,
            "changed" to uuids.size,
            "deleted" to deleted,
        )
    }

    /** Reads a plain list of uuids from a file the host pushed. */
    private fun readUuidFile(args: JsonObject): List<String> {
        val fileName = args.stringOrNull("fileName")
            ?: throw ToolException("uuids or fileName is required: the episodes to change")
        if (fileName.contains('/') || fileName.contains('\\')) {
            throw ToolException("fileName must be a plain file name")
        }
        val file = java.io.File(appContext.filesDir, fileName)
        if (!file.exists()) {
            throw ToolException("No file called $fileName in the app's files directory")
        }
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** Reads an export file the host pushed into the app's files directory. */
    private fun readExportFile(args: JsonObject): JsonObject {
        val fileName = args.stringOrNull("fileName")
            ?: args["state"]?.let { (it as? JsonObject)?.stringOrNull("fileName") }
            ?: throw ToolException("state or fileName is required: the Pocket Casts export")
        if (fileName.contains('/') || fileName.contains('\\')) {
            throw ToolException("fileName must be a plain file name")
        }
        val file = java.io.File(appContext.filesDir, fileName)
        if (!file.exists()) {
            throw ToolException("No export file called $fileName in the app's files directory")
        }
        val text = file.readText()
        android.util.Log.e("PocketCastsImporter", "readExportFile: fileSize=${text.length}, firstChars=${text.take(100)}")
        val root = kotlinx.serialization.json.Json.parseToJsonElement(text)
        android.util.Log.e("PocketCastsImporter", "readExportFile: parsed root=${root::class.simpleName}, keys=${(root as? kotlinx.serialization.json.JsonObject)?.keys?.toList()}")
        return root as? kotlinx.serialization.json.JsonObject
            ?: throw ToolException("The export file is not a JSON object")
    }

    // --- Shaping ------------------------------------------------------------

    private fun podcastJson(podcast: PodcastEntity, folder: String?): JsonObject = jsonObject(
        "uuid" to podcast.uuid,
        "title" to podcast.title,
        "author" to podcast.author,
        "feedUrl" to podcast.feedUrl,
        "artworkUrl" to podcast.artworkUrl,
        "description" to podcast.description,
        "folder" to folder,
        "autoDownload" to podcast.autoDownload,
        "unreadCount" to podcast.unreadCount,
        "subscribedAt" to podcast.subscribedAt,
        "lastEpisodePublished" to podcast.lastEpisodePublished?.let { isoTime(it) },
    )

    private fun playlistJson(playlist: UserPlaylist): JsonObject = jsonObject(
        "id" to playlist.id,
        "name" to playlist.name,
        "folder" to playlist.folder,
        "autoDownload" to playlist.autoDownload,
        "episodeCount" to playlist.episodeUuids.size,
        "shows" to JsonArray(playlist.shows.map { it.toJsonElement() }),
        "minDurationMinutes" to playlist.minDurationMinutes,
        "maxDurationMinutes" to playlist.maxDurationMinutes,
        "createdAt" to playlist.createdAt,
    )

    private fun episodeJson(
        episode: EpisodeEntity,
        showTitle: String?,
        includeDescription: Boolean = false,
    ): JsonObject = jsonObject(
        "uuid" to episode.uuid,
        "podcastUuid" to episode.podcastUuid,
        "showTitle" to showTitle,
        "title" to episode.title,
        "author" to episode.author,
        "published" to isoTime(episode.published),
        "publishedEpochMs" to episode.published.toEpochMillis(),
        "durationSeconds" to episode.durationSeconds,
        "state" to playbackStateName(episode.playingStatus),
        "playedUpToSeconds" to episode.playedUpToSeconds,
        "listenedSeconds" to episode.listenedSeconds,
        "starred" to episode.starred,
        "archived" to episode.isArchived,
        "deleted" to episode.isDeleted,
        "queued" to episode.isQueued,
        "queuePosition" to episode.queuePosition,
        "downloaded" to episode.isDownloaded,
        "downloadState" to downloadStateName(episode.downloadState),
        "audioUrl" to episode.audioUrl,
        "artworkUrl" to episode.artworkUrl,
        "description" to if (includeDescription) episode.description else null,
    )

    private fun episodeSummaryJson(
        episode: com.lohe.app.data.db.entity.EpisodeSummary,
        showTitle: String?,
    ): JsonObject = jsonObject(
        "uuid" to episode.uuid,
        "podcastUuid" to episode.podcastUuid,
        "showTitle" to showTitle,
        "title" to episode.title,
        "published" to isoTime(episode.published),
        "durationSeconds" to episode.durationSeconds,
        "state" to playbackStateName(episode.playingStatus),
        "downloaded" to episode.isDownloaded,
        "sizeBytes" to episode.sizeBytes,
        "localPath" to episode.localPath,
    )

    private fun playbackStateName(status: Int): String = when (status) {
        EpisodeEntity.UNPLAYED -> "unplayed"
        EpisodeEntity.IN_PROGRESS -> "in_progress"
        EpisodeEntity.PLAYED -> "played"
        else -> "unknown"
    }

    private fun downloadStateName(state: Int): String = when (state) {
        DownloadState.NONE -> "none"
        DownloadState.RUNNING -> "running"
        DownloadState.DONE -> "done"
        DownloadState.FAILED -> "failed"
        else -> "unknown"
    }

    /**
     * An epoch timestamp as an ISO instant.
     *
     * The database holds feed dates in seconds — that is what the feed
     * parser produces — and local files in milliseconds. Reading one as the
     * other turns every episode into 1970, so the scale is settled first.
     */
    private fun isoTime(epochMillis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis.toEpochMillis()))

    /**
     * The same timestamp in milliseconds.
     *
     * Feeds older than the year 5138 cannot store real millisecond dates, so
     * a timestamp under 100 billion is read as Unix seconds and scaled up.
     */
    private fun Long.toEpochMillis(): Long =
        if (this <= 0L) this
        else if (this < 100_000_000_000L) this * 1000L
        else this
}
