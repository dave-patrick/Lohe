package com.lohe.app.dev.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One advertised tool. */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

/**
 * The tool catalogue: what the MCP server advertises.
 *
 * The names here are the names [LoheToolkit] dispatches on. The toolkit
 * checks at startup that every advertised tool is implemented, so a tool
 * that is described but not wired up fails loudly rather than answering
 * "no such tool" to a caller that was promised it.
 */
object McpCatalog {

    val tools: List<McpTool> = listOf(
        McpTool(
            name = "lohe_shows",
            description = "List the podcasts subscribed to in the library. " +
                "Optionally filter to one folder.",
            inputSchema = schema(
                properties = mapOf(
                    "folder" to stringProp("Only shows filed in this folder."),
                ),
            ),
        ),
        McpTool(
            name = "lohe_show",
            description = "One podcast with its recent episodes and counts of unplayed " +
                "and downloaded episodes.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The show's uuid."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_subscribe",
            description = "Subscribe to a podcast by feed URL, or by title via the " +
                "iTunes catalog. The feed is fetched and its episodes are imported.",
            inputSchema = schema(
                properties = mapOf(
                    "feedUrl" to stringProp("The RSS feed URL."),
                    "title" to stringProp("Show title to look up in the catalog."),
                ),
            ),
        ),
        McpTool(
            name = "lohe_unsubscribe",
            description = "Unsubscribe from a podcast. Its episodes leave the library " +
                "with it; listening time is kept in the history.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The show's uuid."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_refresh_feeds",
            description = "Re-fetch every subscribed feed. Slow: it is a network round " +
                "trip per show.",
            inputSchema = schema(),
        ),
        McpTool(
            name = "lohe_search_catalog",
            description = "Search the iTunes podcast catalog for shows to subscribe to.",
            inputSchema = schema(
                properties = mapOf(
                    "query" to stringProp("What to search for."),
                    "limit" to intProp("How many results to return (1-50, default 15)."),
                ),
                required = listOf("query"),
            ),
        ),
        McpTool(
            name = "lohe_episodes",
            description = "List episodes. Filter by show, by state (unplayed, " +
                "in_progress, played, starred, archived, deleted, downloaded, queued) " +
                "and by a text query over the episode and show titles.",
            inputSchema = schema(
                properties = mapOf(
                    "podcastUuid" to stringProp("Only episodes of this show."),
                    "query" to stringProp("Text to look for in episode or show titles."),
                    "state" to enumProp(
                        "Episode state to filter by.",
                        listOf(
                            "visible", "unplayed", "in_progress", "played", "starred",
                            "archived", "deleted", "downloaded", "queued", "all",
                        ),
                    ),
                    "limit" to intProp("How many episodes to return (1-500, default 50)."),
                    "offset" to intProp("How many episodes to skip (default 0)."),
                ),
            ),
        ),
        McpTool(
            name = "lohe_episode",
            description = "One episode in full, including its description.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The episode's uuid."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_mark_played",
            description = "Mark an episode as listened, or take that back.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The episode's uuid."),
                    "played" to boolProp("True to mark listened, false to un-mark."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_star",
            description = "Star or unstar an episode.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The episode's uuid."),
                    "starred" to boolProp("True to star, false to unstar."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_archive",
            description = "File an episode away, or bring it back. Archived episodes " +
                "stay in the library but leave the episode lists.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The episode's uuid."),
                    "archived" to boolProp("True to archive, false to restore."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_delete_episode",
            description = "Soft-delete an episode (it leaves the lists and the queue) " +
                "or restore it.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The episode's uuid."),
                    "deleted" to boolProp("True to delete, false to restore."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_download",
            description = "Download an episode for offline listening.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The episode's uuid."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_downloads",
            description = "List the episodes downloaded for offline listening.",
            inputSchema = schema(),
        ),
        McpTool(
            name = "lohe_queue",
            description = "The Up Next queue, in order.",
            inputSchema = schema(),
        ),
        McpTool(
            name = "lohe_enqueue",
            description = "Add episodes to the Up Next queue.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("One episode uuid."),
                    "uuids" to arrayProp("Several episode uuids."),
                ),
            ),
        ),
        McpTool(
            name = "lohe_dequeue",
            description = "Take one episode out of the Up Next queue.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The episode's uuid."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_clear_queue",
            description = "Empty the Up Next queue.",
            inputSchema = schema(),
        ),
        McpTool(
            name = "lohe_reorder_queue",
            description = "Rewrite the order of the Up Next queue.",
            inputSchema = schema(
                properties = mapOf(
                    "uuids" to arrayProp("The episode uuids, in the order wanted."),
                ),
                required = listOf("uuids"),
            ),
        ),
        McpTool(
            name = "lohe_playlists",
            description = "List the playlists.",
            inputSchema = schema(),
        ),
        McpTool(
            name = "lohe_playlist",
            description = "One playlist and the episodes in it.",
            inputSchema = schema(
                properties = mapOf(
                    "id" to stringProp("The playlist's id."),
                ),
                required = listOf("id"),
            ),
        ),
        McpTool(
            name = "lohe_create_playlist",
            description = "Create a playlist, optionally with episodes already in it, " +
                "following a set of shows, and/or bounded by episode length.",
            inputSchema = schema(
                properties = mapOf(
                    "name" to stringProp("The playlist's name."),
                    "episodeUuids" to arrayProp("Episodes to put in it."),
                    "shows" to arrayProp("Show uuids the playlist follows."),
                    "minDurationMinutes" to intProp("Only episodes longer than this many minutes."),
                    "maxDurationMinutes" to intProp("Only episodes no longer than this many minutes."),
                ),
                required = listOf("name"),
            ),
        ),
        McpTool(
            name = "lohe_playlist_update",
            description = "Change a playlist's rule: the shows it follows and the " +
                "length window it collects within. A playlist with no shows " +
                "collects from every show.",
            inputSchema = schema(
                properties = mapOf(
                    "playlistId" to stringProp("The playlist's id."),
                    "shows" to arrayProp("Show uuids the playlist follows. Empty means every show."),
                    "minDurationMinutes" to intProp("Only episodes longer than this many minutes."),
                    "maxDurationMinutes" to intProp("Only episodes no longer than this many minutes."),
                ),
                required = listOf("playlistId"),
            ),
        ),
        McpTool(
            name = "lohe_playlist_add",
            description = "Add episodes to a playlist.",
            inputSchema = schema(
                properties = mapOf(
                    "playlistId" to stringProp("The playlist's id."),
                    "episodeUuids" to arrayProp("The episodes to add."),
                ),
                required = listOf("playlistId", "episodeUuids"),
            ),
        ),
        McpTool(
            name = "lohe_playlist_remove",
            description = "Take one episode out of a playlist.",
            inputSchema = schema(
                properties = mapOf(
                    "playlistId" to stringProp("The playlist's id."),
                    "episodeUuid" to stringProp("The episode to remove."),
                ),
                required = listOf("playlistId", "episodeUuid"),
            ),
        ),
        McpTool(
            name = "lohe_delete_playlist",
            description = "Delete a playlist. The episodes stay in the library.",
            inputSchema = schema(
                properties = mapOf(
                    "playlistId" to stringProp("The playlist's id."),
                ),
                required = listOf("playlistId"),
            ),
        ),
        McpTool(
            name = "lohe_play",
            description = "Play an episode now.",
            inputSchema = schema(
                properties = mapOf(
                    "uuid" to stringProp("The episode's uuid."),
                ),
                required = listOf("uuid"),
            ),
        ),
        McpTool(
            name = "lohe_pause",
            description = "Pause playback.",
            inputSchema = schema(),
        ),
        McpTool(
            name = "lohe_now_playing",
            description = "What is playing: the episode, the position, and whether it " +
                "is playing.",
            inputSchema = schema(),
        ),
        McpTool(
            name = "lohe_settings",
            description = "Read the app's playback and appearance settings.",
            inputSchema = schema(),
        ),
        McpTool(
            name = "lohe_set_setting",
            description = "Change one setting. Keys: appearance, volumeBoostDefault, " +
                "skipSilenceDefault, smartSpeedDefault, autoTrimDefault, " +
                "defaultPlaybackSpeed, skipBackSeconds, skipForwardSeconds, " +
                "markPlayedAtEnd, autoArchiveHours, autoDownloadQueue, sleepTimerMinutes, podcastSort, " +
                "playlistSort.",
            inputSchema = schema(
                properties = mapOf(
                    "key" to stringProp("The setting to change."),
                    "value" to stringProp("The new value, as text."),
                ),
                required = listOf("key", "value"),
            ),
        ),
        McpTool(
            name = "lohe_import_pocketcasts",
            description = "Bring the library into line with a Pocket Casts export: " +
                "the same episodes, in the same state, in the same playlists. " +
                "The state is the canonical JSON written by " +
                "tools/pocketcasts-export.py. A whole library is far too large " +
                "to hand over inline, so it can be named as a file in the " +
                "app's own files directory instead. Phases: shows, episodes, " +
                "playlists, sync-played (repairs played/unplayed state for " +
                "episodes that exist in both apps).",
            inputSchema = schema(
                properties = mapOf(
                    "state" to objectProp(
                        "The Pocket Casts export: shows, episodes, playlists, " +
                            "folders and upNext.",
                    ),
                    "fileName" to stringProp(
                        "The export file in the app's files directory, for an " +
                            "export too large to pass inline.",
                    ),
                    "subscribeMissingShows" to boolProp(
                        "Fetch the feeds of shows Pocket Casts has and Lohe does not.",
                    ),
                    "replaceFolders" to boolProp(
                        "Clear Lohe's folder assignments for shows Pocket Casts files " +
                            "nowhere (default false: the folders are the user's own " +
                            "arrangement).",
                    ),
                ),
            ),
        ),
        McpTool(
            name = "lohe_bulk_deleted",
            description = "File a list of episodes away, or bring them back, in one " +
                "go. The list is read from a file in the app's files directory, " +
                "because a repair over tens of thousands of episodes is too much " +
                "to carry in a tool call.",
            inputSchema = schema(
                properties = mapOf(
                    "fileName" to stringProp(
                        "A file in the app's files directory with one episode " +
                            "uuid per line.",
                    ),
                    "uuids" to arrayProp("The episodes to change, instead of a file."),
                    "deleted" to boolProp(
                        "True to file them away, false to bring them back.",
                    ),
                ),
                required = listOf("deleted"),
            ),
        ),
    )

    /** The tool with this name, or null when there is none. */
    fun find(name: String): McpTool? = tools.firstOrNull { it.name == name }

    /** The catalogue as the MCP `tools/list` result. */
    fun listResult(): JsonObject = buildJsonObject {
        put(
            "tools",
            JsonArray(
                tools.map { tool ->
                    buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("inputSchema", tool.inputSchema)
                    }
                },
            ),
        )
    }

    private fun schema(
        properties: Map<String, JsonObject> = emptyMap(),
        required: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(properties))
        if (required.isNotEmpty()) {
            put("required", JsonArray(required.map { it.toJsonElement() }))
        }
    }

    private fun stringProp(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun intProp(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun boolProp(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    private fun arrayProp(description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", buildJsonObject { put("type", "string") })
    }

    private fun enumProp(description: String, values: List<String>): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        put("enum", JsonArray(values.map { it.toJsonElement() }))
    }

    private fun objectProp(description: String): JsonObject = buildJsonObject {
        put("type", "object")
        put("description", description)
    }
}
