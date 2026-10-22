package com.lohe.app.dev

import android.content.Context
import android.util.Log
import com.lohe.app.dev.mcp.LoheToolkit
import com.lohe.app.dev.mcp.McpHttp
import com.lohe.app.dev.mcp.McpTools
import com.lohe.app.dev.mcp.ToolException
import com.lohe.app.dev.mcp.jsonObject
import com.lohe.app.dev.mcp.toJsonElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * The debug-only control surface for Lohe.
 *
 * It exists so an agent (or a developer) can drive the app's data and playback
 * without tapping through the UI: settings, episode state, playlists, queue,
 * and a few playback commands. It is started only when `BuildConfig.DEBUG` is
 * true and binds to localhost, so it is reachable through `adb forward`.
 *
 * Two front doors sit on the same port:
 *
 *  - `POST /mcp` speaks the Model Context Protocol, so an MCP client can use
 *    the app as a set of tools. This is the richer of the two.
 *  - The REST endpoints below, for `curl` and for anything that would rather
 *    not speak JSON-RPC.
 *
 * Both call the same operations, so they cannot drift apart.
 *
 * Endpoints (all JSON, all require `Authorization: Bearer lohe-dev`):
 *
 *   GET  /health
 *   GET  /shows?folder=
 *   GET  /episodes?show=<uuid>&state=&query=&limit=&offset=
 *   GET  /playlists
 *   GET  /playlist/{id}
 *   GET  /settings
 *   GET  /queue
 *   GET  /downloads
 *   GET  /now
 *   POST /settings           {"key":..., "value":...}
 *   POST /episode/{uuid}/played     {"played": bool}
 *   POST /episode/{uuid}/deleted     {"deleted": bool}
 *   POST /episode/{uuid}/starred     {"starred": bool}
 *   POST /episode/{uuid}/archived    {"archived": bool}
 *   POST /playlist                  {"name":...}
 *   POST /playlist/{id}/add         {"episodeUuid":...}
 *   POST /playlist/{id}/remove      {"episodeUuid":...}
 *   POST /playlist/{id}/delete
 *   POST /queue/add                 {"episodeUuid":...}
 *   POST /queue/clear
 *   POST /play                      {"episodeUuid":...}
 *   POST /subscribe                 {"feedUrl":...}
 *   POST /import/pocketcasts        the Pocket Casts export itself as the body
 */
object DevApi {

    private const val TAG = "DevApi"
    private const val PORT = 8765
    private const val TOKEN = "lohe-dev"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false
    @Volatile private var server: ServerSocket? = null

    /** The operations both front doors run. */
    @Volatile private var toolkit: LoheToolkit? = null

    /** The MCP tool catalogue, built from the same operations. */
    @Volatile private var mcpTools: McpTools? = null

    fun start(context: Context) {
        if (!com.lohe.app.BuildConfig.DEBUG) return
        if (started) return
        started = true
        val appContext = context.applicationContext
        val built = LoheToolkit(appContext)
        toolkit = built
        mcpTools = McpTools(built)
        scope.launch {
            try {
                val s = ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"))
                server = s
                Log.i(TAG, "listening on 127.0.0.1:$PORT (MCP at ${McpHttp.PATH})")
                while (true) {
                    val client = s.accept()
                    scope.launch { handle(appContext, client) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "server failed", t)
                started = false
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        started = false
    }

    private suspend fun handle(context: Context, socket: Socket) {
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1]
            val path = rawPath.substringBefore('?')
            val query = parseQuery(rawPath.substringAfter('?', ""))

            // Read headers.
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            // Read body.
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            } else ""

            // The MCP endpoint speaks its own dialect: JSON-RPC in, JSON-RPC
            // out, with its own idea of what a response looks like.
            if (path == McpHttp.PATH) {
                val tools = mcpTools ?: return
                McpHttp.handle(s, method, headers, body, tools)
                return
            }

            // Auth check (skip for health? no, keep uniform).
            val auth = headers["authorization"] ?: ""
            if (auth != "Bearer $TOKEN") {
                respond(s, 401, errorJson("unauthorized"))
                return
            }

            try {
                val (status, json) = route(method, path, query, body)
                respond(s, status, json)
            } catch (t: Throwable) {
                Log.e(TAG, "route failed", t)
                respond(s, 500, errorJson(t.message ?: "error"))
            }
        }
    }

    private suspend fun route(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String,
    ): Pair<Int, String> {
        val toolkit = toolkit ?: return 503 to errorJson("the dev surface is not running")

        // Health
        if (method == "GET" && path == "/health") {
            return 200 to buildJsonObject {
                put("ok", true)
                put("port", PORT)
                put("mcp", McpHttp.PATH)
            }.toString()
        }

        // Shows
        if (method == "GET" && path == "/shows") {
            return call(toolkit, "lohe_shows", jsonObject("folder" to query["folder"]))
        }

        // Episodes
        if (method == "GET" && path == "/episodes") {
            return call(
                toolkit,
                "lohe_episodes",
                jsonObject(
                    "podcastUuid" to query["show"],
                    "state" to query["state"],
                    "query" to query["query"],
                    "limit" to query["limit"]?.toIntOrNull(),
                    "offset" to query["offset"]?.toIntOrNull(),
                ),
            )
        }

        // Playlists
        if (method == "GET" && path == "/playlists") {
            return call(toolkit, "lohe_playlists", JsonObject(emptyMap()))
        }

        // Single playlist
        val playlistMatch = Regex("^/playlist/([^/]+)$").matchEntire(path)
        if (method == "GET" && playlistMatch != null) {
            val id = URLDecoder.decode(playlistMatch.groupValues[1], "UTF-8")
            return call(toolkit, "lohe_playlist", jsonObject("id" to id))
        }

        // Settings
        if (method == "GET" && path == "/settings") {
            return call(toolkit, "lohe_settings", JsonObject(emptyMap()))
        }

        if (method == "POST" && path == "/settings") {
            val o = JSONObject(body)
            return call(
                toolkit,
                "lohe_set_setting",
                jsonObject("key" to o.optString("key"), "value" to o.optString("value")),
            )
        }

        // Episode state
        val epPlayed = Regex("^/episode/([^/]+)/played$").matchEntire(path)
        if (method == "POST" && epPlayed != null) {
            val uuid = URLDecoder.decode(epPlayed.groupValues[1], "UTF-8")
            return call(
                toolkit,
                "lohe_mark_played",
                jsonObject("uuid" to uuid, "played" to JSONObject(body).optBoolean("played", true)),
            )
        }

        val epDeleted = Regex("^/episode/([^/]+)/deleted$").matchEntire(path)
        if (method == "POST" && epDeleted != null) {
            val uuid = URLDecoder.decode(epDeleted.groupValues[1], "UTF-8")
            return call(
                toolkit,
                "lohe_delete_episode",
                jsonObject("uuid" to uuid, "deleted" to JSONObject(body).optBoolean("deleted", true)),
            )
        }

        val epStarred = Regex("^/episode/([^/]+)/starred$").matchEntire(path)
        if (method == "POST" && epStarred != null) {
            val uuid = URLDecoder.decode(epStarred.groupValues[1], "UTF-8")
            return call(
                toolkit,
                "lohe_star",
                jsonObject("uuid" to uuid, "starred" to JSONObject(body).optBoolean("starred", true)),
            )
        }

        val epArchived = Regex("^/episode/([^/]+)/archived$").matchEntire(path)
        if (method == "POST" && epArchived != null) {
            val uuid = URLDecoder.decode(epArchived.groupValues[1], "UTF-8")
            return call(
                toolkit,
                "lohe_archive",
                jsonObject("uuid" to uuid, "archived" to JSONObject(body).optBoolean("archived", true)),
            )
        }

        // Playlist ops
        if (method == "POST" && path == "/playlist") {
            val o = JSONObject(body)
            return call(toolkit, "lohe_create_playlist", jsonObject("name" to o.optString("name")))
        }

        val plAdd = Regex("^/playlist/([^/]+)/add$").matchEntire(path)
        if (method == "POST" && plAdd != null) {
            val id = URLDecoder.decode(plAdd.groupValues[1], "UTF-8")
            val uuid = JSONObject(body).getString("episodeUuid")
            return call(
                toolkit,
                "lohe_playlist_add",
                jsonObject("playlistId" to id, "episodeUuids" to JsonArray(listOf(uuid.toJsonElement()))),
            )
        }

        val plRemove = Regex("^/playlist/([^/]+)/remove$").matchEntire(path)
        if (method == "POST" && plRemove != null) {
            val id = URLDecoder.decode(plRemove.groupValues[1], "UTF-8")
            val uuid = JSONObject(body).getString("episodeUuid")
            return call(
                toolkit,
                "lohe_playlist_remove",
                jsonObject("playlistId" to id, "episodeUuid" to uuid),
            )
        }

        val plDelete = Regex("^/playlist/([^/]+)/delete$").matchEntire(path)
        if (method == "POST" && plDelete != null) {
            val id = URLDecoder.decode(plDelete.groupValues[1], "UTF-8")
            return call(toolkit, "lohe_delete_playlist", jsonObject("playlistId" to id))
        }

        // Queue
        if (method == "POST" && path == "/queue/add") {
            val uuid = JSONObject(body).getString("episodeUuid")
            return call(toolkit, "lohe_enqueue", jsonObject("uuid" to uuid))
        }
        if (method == "POST" && path == "/queue/clear") {
            return call(toolkit, "lohe_clear_queue", JsonObject(emptyMap()))
        }
        if (method == "GET" && path == "/queue") {
            return call(toolkit, "lohe_queue", JsonObject(emptyMap()))
        }

        // Downloads
        if (method == "GET" && path == "/downloads") {
            return call(toolkit, "lohe_downloads", JsonObject(emptyMap()))
        }

        // Playback
        if (method == "POST" && path == "/play") {
            val uuid = JSONObject(body).getString("episodeUuid")
            return call(toolkit, "lohe_play", jsonObject("uuid" to uuid))
        }

        // Subscriptions
        if (method == "POST" && path == "/subscribe") {
            val o = JSONObject(body)
            return call(
                toolkit,
                "lohe_subscribe",
                jsonObject("feedUrl" to o.optString("feedUrl"), "title" to o.optString("title")),
            )
        }

        // Now playing
        if (method == "GET" && path == "/now") {
            return call(toolkit, "lohe_now_playing", JsonObject(emptyMap()))
        }

        // Import from Pocket Casts: the body is the export itself.
        //
        // A library of tens of thousands of episodes is imported in three
        // phases — the shows, the episodes, then the playlists — because the
        // episodes alone are far too many to arrive in one body, and because
        // the playlists can only be written once the episodes they name have
        // been matched.
        if (method == "POST" && path.startsWith("/import/pocketcasts")) {
            val phase = path.substringAfter("/import/pocketcasts").trim('/').ifEmpty { "all" }
            if (phase !in setOf("all", "shows", "episodes", "playlists", "sync-played")) {
                return 400 to errorJson("unknown phase \"$phase\"")
            }
            val state = kotlinx.serialization.json.Json.parseToJsonElement(body)
                as? JsonObject
                ?: return 400 to errorJson("the body was not a JSON object")
            return call(
                toolkit,
                "lohe_import_pocketcasts",
                jsonObject(
                    "state" to state,
                    "phase" to phase,
                    "subscribeMissingShows" to query["subscribeMissingShows"],
                    "replaceFolders" to query["replaceFolders"],
                ),
            )
        }

        // A bulk repair of the deleted flag.
        if (method == "POST" && path == "/episodes/deleted") {
            val o = JSONObject(body)
            val uuids = o.optJSONArray("uuids")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotEmpty() } }
            }.orEmpty()
            return call(
                toolkit,
                "lohe_bulk_deleted",
                jsonObject(
                    "uuids" to JsonArray(uuids.map { it.toJsonElement() }),
                    "fileName" to o.optString("fileName"),
                    "deleted" to o.optBoolean("deleted", false),
                ),
            )
        }

        return 404 to errorJson("not found")
    }

    /**
     * Runs one operation and turns a refusal into a status code.
     *
     * A refusal that names something that is not there is a 404; anything
     * else is a 400 — the request was understood, the operation just cannot
     * be carried out.
     */
    private suspend fun call(
        toolkit: LoheToolkit,
        operation: String,
        args: JsonObject,
    ): Pair<Int, String> = try {
        200 to toolkit.execute(operation, args).toString()
    } catch (e: ToolException) {
        val message = e.message ?: "That did not work."
        val status = if (message.startsWith("No ")) 404 else 400
        status to errorJson(message)
    } catch (e: IllegalArgumentException) {
        400 to errorJson(e.message ?: "That did not work.")
    }

    private fun errorJson(message: String): String = buildJsonObject {
        put("error", message)
    }.toString()

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        return q.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx < 0) part to "" else
                URLDecoder.decode(part.substring(0, idx), "UTF-8") to
                    URLDecoder.decode(part.substring(idx + 1), "UTF-8")
        }.toMap()
    }

    /**
     * Writes the response.
     *
     * A client that hangs up mid-answer is ordinary — a request that takes
     * too long, a script that gives up — and it must not take the app with
     * it. The write is therefore contained: a broken pipe ends this one
     * exchange and nothing else.
     */
    private fun respond(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        try {
            val out = socket.getOutputStream()
            val w = OutputStreamWriter(out, Charsets.UTF_8)
            w.write("HTTP/1.1 $status ${statusText(status)}\r\n")
            w.write("Content-Type: application/json; charset=utf-8\r\n")
            w.write("Content-Length: ${bytes.size}\r\n")
            w.write("Connection: close\r\n")
            w.write("\r\n")
            w.flush()
            out.write(bytes)
            out.flush()
        } catch (e: IOException) {
            Log.i(TAG, "the client hung up before the answer was written: ${e.message}")
        }
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        202 -> "Accepted"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        503 -> "Service Unavailable"
        else -> "OK"
    }
}
