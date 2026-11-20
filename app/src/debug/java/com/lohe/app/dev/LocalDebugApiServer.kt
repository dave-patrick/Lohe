package com.lohe.app.dev

import android.content.Context
import com.lohe.app.data.local.EpisodeEntity
import com.lohe.app.data.local.LoheDatabase
import com.lohe.app.data.local.LohePreferencesRepository
import com.lohe.app.data.local.PodcastEntity
import com.lohe.app.playback.LohePlayerController
import com.lohe.app.playback.PlaybackUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class LocalDebugApiServer(
    private val context: Context,
    private val databaseProvider: () -> LoheDatabase,
    private val preferencesProvider: () -> LohePreferencesRepository,
    private val playerControllerProvider: () -> LohePlayerController?,
    private val eventRecorder: (String, String) -> Unit = { _, _ -> }
) {
    companion object {
        const val TOKEN_FILE_NAME = "debug-api-token"
        const val DEFAULT_PORT = 8765
        const val MAX_REQUEST_BODY_BYTES = 1024 * 1024 // 1 MiB
        private const val MAX_HEADER_LINE_BYTES = 8 * 1024
        private const val MAX_HEADER_COUNT = 64

        fun tokenFile(context: Context): File {
            val dir = context.noBackupFilesDir.apply { if (!exists()) mkdirs() }
            return File(dir, TOKEN_FILE_NAME)
        }

        @Synchronized
        fun getOrCreateInstallationToken(context: Context): String {
            val file = tokenFile(context)
            if (file.exists()) {
                val existing = runCatching { file.readText().trim() }.getOrDefault("")
                if (existing.isNotEmpty()) {
                    return existing
                }
            }
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val generated = bytes.joinToString("") { "%02x".format(it) }
            file.writeText(generated)
            return generated
        }
    }

    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var workerExecutor: ExecutorService? = null
    private val running = AtomicBoolean(false)

    val lastRequestBodyBytesRead = AtomicInteger(0)

    // Fallback playback state when headless tests run without a UI-attached LohePlayerController
    @Volatile
    private var headlessPlaybackState = PlaybackUiState()

    val boundPort: Int
        get() = serverSocket?.localPort ?: -1

    val isRunning: Boolean
        get() = running.get() && serverSocket != null && serverSocket?.isClosed == false

    @Synchronized
    fun start(preferredPort: Int = DEFAULT_PORT): Int {
        if (isRunning) return boundPort
        getOrCreateInstallationToken(context)

        val loopback = InetAddress.getByName("127.0.0.1")
        val socket = try {
            ServerSocket(preferredPort, 50, loopback)
        } catch (_: Exception) {
            ServerSocket(0, 50, loopback)
        }
        serverSocket = socket
        running.set(true)

        val workers = Executors.newCachedThreadPool()
        workerExecutor = workers

        val acceptor = Executors.newSingleThreadExecutor()
        acceptExecutor = acceptor
        acceptor.execute {
            while (running.get()) {
                try {
                    val client = socket.accept()
                    workers.execute {
                        handleClientSocket(client)
                    }
                } catch (_: SocketException) {
                    break
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }
        eventRecorder("debug_api", "Local debug API listening on 127.0.0.1:${socket.localPort}")
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { acceptExecutor?.shutdownNow() }
        acceptExecutor = null
        runCatching { workerExecutor?.shutdownNow() }
        workerExecutor = null
    }

    private fun handleClientSocket(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 10_000
            val input = client.getInputStream()
            val output = client.getOutputStream()
            lastRequestBodyBytesRead.set(0)

            try {
                val requestLine = readHttpHeaderLine(input) ?: return
                if (requestLine.isBlank()) return
                val parts = requestLine.trim().split(Regex("\\s+"))
                if (parts.size < 2) {
                    writeJsonResponse(output, 400, "Bad Request", JSONObject().put("error", "Malformed request line"))
                    return
                }
                val method = parts[0].uppercase(Locale.ROOT)
                val fullTarget = parts[1]
                val path = fullTarget.substringBefore('?').trimEnd('/').ifEmpty { "/" }

                // Read HTTP headers byte-by-byte without buffering into the request body
                val headers = mutableMapOf<String, String>()
                var headerLines = 0
                while (true) {
                    val line = readHttpHeaderLine(input) ?: break
                    if (line.isEmpty()) break
                    headerLines++
                    if (headerLines > MAX_HEADER_COUNT) {
                        writeJsonResponse(output, 431, "Request Header Fields Too Large", JSONObject().put("error", "Too many headers"))
                        return
                    }
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex).trim().lowercase(Locale.ROOT)
                        val value = line.substring(colonIndex + 1).trim()
                        headers[key] = value
                    }
                }

                // 1. Authenticate EVERY request BEFORE reading the request body
                val expectedToken = getOrCreateInstallationToken(context)
                if (!isAuthorized(headers, expectedToken)) {
                    writeJsonResponse(
                        output,
                        401,
                        "Unauthorized",
                        JSONObject().put("error", "Missing or invalid debug API token")
                    )
                    return
                }

                // 2. MCP GET returns 405 (checked before reading body)
                if ((path == "/mcp" || path == "/api/mcp") && method != "POST") {
                    writeJsonResponse(
                        output = output,
                        statusCode = 405,
                        statusText = "Method Not Allowed",
                        body = JSONObject().put("error", "MCP endpoint only accepts POST"),
                        extraHeaders = mapOf("Allow" to "POST")
                    )
                    return
                }

                // 3. Enforce 1 MiB request size limit via Content-Length before reading body
                val contentLengthHeader = headers["content-length"]
                val declaredLength = contentLengthHeader?.toLongOrNull() ?: 0L
                if (declaredLength > MAX_REQUEST_BODY_BYTES) {
                    writeJsonResponse(
                        output,
                        413,
                        "Payload Too Large",
                        JSONObject().put("error", "Request body exceeds 1 MiB limit")
                    )
                    return
                }
                if (declaredLength < 0L) {
                    writeJsonResponse(
                        output,
                        400,
                        "Bad Request",
                        JSONObject().put("error", "Invalid Content-Length")
                    )
                    return
                }

                // 4. Read request body only for POST/PUT (capped strictly at 1 MiB)
                val requestBodyText = if (method == "POST" || method == "PUT") {
                    val bodyBytes = readRequestBodyBounded(input, contentLengthHeader)
                    if (bodyBytes == null) {
                        writeJsonResponse(
                            output,
                            413,
                            "Payload Too Large",
                            JSONObject().put("error", "Request body exceeds 1 MiB limit")
                        )
                        return
                    }
                    String(bodyBytes, Charsets.UTF_8)
                } else {
                    ""
                }

                // 5. Route authenticated request
                routeRequest(method, path, requestBodyText, output)
            } catch (e: HeaderLineTooLongException) {
                runCatching {
                    writeJsonResponse(output, 431, "Request Header Fields Too Large", JSONObject().put("error", e.message))
                }
            } catch (e: Exception) {
                runCatching {
                    writeJsonResponse(
                        output,
                        500,
                        "Internal Server Error",
                        JSONObject().put("error", e.localizedMessage ?: "Server error")
                    )
                }
            }
        }
    }

    private class HeaderLineTooLongException(message: String) : Exception(message)

    /**
     * Reads a single CRLF-terminated (or LF-terminated) line from [input] one byte at a time
     * so we never read past the `\r\n\r\n` boundary into the request body.
     */
    private fun readHttpHeaderLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream(128)
        var sawByte = false
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sawByte) buffer.toString(Charsets.ISO_8859_1.name()) else null
            }
            sawByte = true
            if (b == '\n'.code) {
                break
            }
            if (b != '\r'.code) {
                buffer.write(b)
                if (buffer.size() > MAX_HEADER_LINE_BYTES) {
                    throw HeaderLineTooLongException("Header line exceeds limit")
                }
            }
        }
        return buffer.toString(Charsets.ISO_8859_1.name())
    }

    /**
     * Reads request body up to [MAX_REQUEST_BODY_BYTES]. Returns null if the body exceeds 1 MiB.
     */
    private fun readRequestBodyBounded(input: InputStream, contentLengthHeader: String?): ByteArray? {
        if (contentLengthHeader != null) {
            val length = contentLengthHeader.toIntOrNull() ?: return null
            if (length > MAX_REQUEST_BODY_BYTES) return null
            if (length == 0) return ByteArray(0)

            val buffer = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val read = input.read(buffer, offset, length - offset)
                if (read == -1) break
                offset += read
                lastRequestBodyBytesRead.addAndGet(read)
            }
            return if (offset == length) buffer else buffer.copyOf(offset)
        } else {
            // No Content-Length header: read up to MAX_REQUEST_BODY_BYTES + 1
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (input.available() > 0) {
                val read = input.read(chunk)
                if (read <= 0) break
                out.write(chunk, 0, read)
                lastRequestBodyBytesRead.addAndGet(read)
                if (out.size() > MAX_REQUEST_BODY_BYTES) {
                    return null
                }
            }
            return out.toByteArray()
        }
    }

    private fun isAuthorized(headers: Map<String, String>, expectedToken: String): Boolean {
        if (expectedToken.isBlank()) return false
        val authHeader = headers["authorization"]?.trim().orEmpty()
        val xDebugHeader = (headers["x-debug-token"] ?: headers["x-lohe-debug-token"])?.trim().orEmpty()

        val providedToken = when {
            authHeader.startsWith("Bearer ", ignoreCase = true) ->
                authHeader.substring(7).trim()
            authHeader.isNotEmpty() ->
                authHeader
            xDebugHeader.isNotEmpty() ->
                xDebugHeader
            else -> ""
        }
        if (providedToken.isEmpty()) return false

        return MessageDigest.isEqual(
            providedToken.toByteArray(Charsets.UTF_8),
            expectedToken.toByteArray(Charsets.UTF_8)
        )
    }

    private fun routeRequest(
        method: String,
        path: String,
        bodyText: String,
        output: OutputStream
    ) {
        when {
            // MCP JSON-RPC over POST
            (path == "/mcp" || path == "/api/mcp") && method == "POST" -> {
                val rpcResponse = handleMcpJsonRpc(bodyText)
                writeJsonResponse(output, 200, "OK", rpcResponse)
            }

            // REST: Read Library
            method == "GET" && path in setOf("/library", "/api/library", "/podcasts", "/api/podcasts") -> {
                val libraryJson = runBlocking { buildLibraryJson() }
                writeJsonResponse(output, 200, "OK", libraryJson)
            }

            // REST: Read Queue
            method == "GET" && path in setOf("/queue", "/api/queue", "/up-next", "/api/up-next") -> {
                val queueJson = runBlocking { buildQueueJson() }
                writeJsonResponse(output, 200, "OK", queueJson)
            }

            // REST: Read Playback State
            method == "GET" && path in setOf("/playback", "/api/playback", "/playback-state", "/api/playback-state") -> {
                val playbackJson = runBlocking { buildPlaybackStateJson() }
                writeJsonResponse(output, 200, "OK", playbackJson)
            }

            // REST: Read Settings
            method == "GET" && path in setOf("/settings", "/api/settings") -> {
                val settingsJson = runBlocking { buildSettingsJson() }
                writeJsonResponse(output, 200, "OK", settingsJson)
            }

            // REST: Control Playback
            method == "POST" && (
                path in setOf("/playback", "/api/playback", "/playback/control", "/api/playback/control") ||
                    path.startsWith("/playback/") ||
                    path.startsWith("/api/playback/")
                ) -> {
                val subAction = path.substringAfterLast('/')
                    .takeIf { it !in setOf("playback", "control") }
                val params = if (bodyText.isNotBlank()) {
                    runCatching { JSONObject(bodyText) }.getOrNull() ?: JSONObject()
                } else {
                    JSONObject()
                }
                if (!subAction.isNullOrBlank() && !params.has("action")) {
                    params.put("action", subAction)
                }
                val resultJson = runBlocking { executePlaybackControl(params) }
                writeJsonResponse(output, 200, "OK", resultJson)
            }

            // REST: Import Pocket Casts JSON export
            method == "POST" && path in setOf(
                "/import/pocketcasts",
                "/api/import/pocketcasts",
                "/import/pocket-casts",
                "/api/import/pocket-casts",
                "/import"
            ) -> {
                val payload = extractPocketCastsPayload(bodyText)
                val importResult = runBlocking {
                    PocketCastsJsonImporter(databaseProvider()).importPocketCastsJson(payload)
                }
                importResult.onSuccess { summary ->
                    val json = JSONObject().apply {
                        put("ok", true)
                        put("showsAdded", summary.showsAdded)
                        put("episodesUpdated", summary.episodesUpdated)
                        put("itemsSkipped", summary.itemsSkipped)
                    }
                    writeJsonResponse(output, 200, "OK", json)
                }.onFailure { err ->
                    writeJsonResponse(
                        output,
                        400,
                        "Bad Request",
                        JSONObject().put("ok", false).put("error", err.localizedMessage ?: "Invalid Pocket Casts JSON")
                    )
                }
            }

            else -> {
                writeJsonResponse(
                    output,
                    404,
                    "Not Found",
                    JSONObject().put("error", "Unknown endpoint: $method $path")
                )
            }
        }
    }

    private fun handleMcpJsonRpc(bodyText: String): JSONObject {
        val request = try {
            JSONObject(bodyText)
        } catch (_: Exception) {
            return JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", JSONObject.NULL)
                put(
                    "error",
                    JSONObject().apply {
                        put("code", -32700)
                        put("message", "Parse error: invalid JSON")
                    }
                )
            }
        }

        val idValue: Any = if (request.has("id") && !request.isNull("id")) request.get("id") else JSONObject.NULL
        val method = request.optString("method", "").trim()
        val params = request.optJSONObject("params") ?: JSONObject()

        return try {
            val resultObj: JSONObject = when (method) {
                "initialize" -> JSONObject().apply {
                    put("protocolVersion", "2024-11-05")
                    put(
                        "capabilities",
                        JSONObject().apply {
                            put("tools", JSONObject())
                        }
                    )
                    put(
                        "serverInfo",
                        JSONObject().apply {
                            put("name", "lohe-debug-api")
                            put("version", "0.12.0")
                        }
                    )
                }

                "notifications/initialized", "ping" -> JSONObject().put("ok", true)

                "tools/list" -> buildMcpToolsListResult()

                "tools/call" -> {
                    val toolName = params.optString("name", "").trim()
                    val toolArgs = params.optJSONObject("arguments") ?: params
                    val toolData = runBlocking { dispatchMcpTool(toolName, toolArgs) }
                    wrapMcpToolCallResult(toolData)
                }

                // Support direct JSON-RPC method calls as well
                "get_library", "read_library", "library", "library/get",
                "get_queue", "read_queue", "queue", "queue/get",
                "get_playback_state", "read_playback_state", "playback_state", "playback", "get_playback", "playback/get",
                "get_settings", "read_settings", "settings", "settings/get",
                "control_playback", "playback_control", "playback/control", "play", "pause", "toggle", "seek",
                "import_pocket_casts", "import_pocketcasts", "pocket_casts_import", "import/pocketcasts" -> {
                    val effectiveArgs = if (method in setOf("play", "pause", "toggle", "seek") && !params.has("action")) {
                        JSONObject(params.toString()).put("action", method)
                    } else {
                        params
                    }
                    val toolData = runBlocking { dispatchMcpTool(method, effectiveArgs) }
                    wrapMcpToolCallResult(toolData)
                }

                else -> {
                    return JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("id", idValue)
                        put(
                            "error",
                            JSONObject().apply {
                                put("code", -32601)
                                put("message", "Method not found: $method")
                            }
                        )
                    }
                }
            }

            JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", idValue)
                put("result", resultObj)
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", idValue)
                put(
                    "error",
                    JSONObject().apply {
                        put("code", -32602)
                        put("message", e.localizedMessage ?: "Invalid params")
                    }
                )
            }
        }
    }

    private suspend fun dispatchMcpTool(toolName: String, args: JSONObject): JSONObject {
        return when (toolName.lowercase(Locale.ROOT)) {
            "get_library", "read_library", "library", "library/get" ->
                buildLibraryJson()

            "get_queue", "read_queue", "queue", "queue/get" ->
                buildQueueJson()

            "get_playback_state", "read_playback_state", "playback_state", "playback", "get_playback", "playback/get" ->
                buildPlaybackStateJson()

            "get_settings", "read_settings", "settings", "settings/get" ->
                buildSettingsJson()

            "control_playback", "playback_control", "playback/control", "play", "pause", "toggle", "seek" ->
                executePlaybackControl(args)

            "import_pocket_casts", "import_pocketcasts", "pocket_casts_import", "import/pocketcasts" -> {
                val payload = when {
                    args.has("json") && args.opt("json") is JSONObject -> args.getJSONObject("json").toString()
                    args.has("json") -> args.optString("json")
                    args.has("payload") && args.opt("payload") is JSONObject -> args.getJSONObject("payload").toString()
                    args.has("payload") -> args.optString("payload")
                    args.has("data") && args.opt("data") is JSONObject -> args.getJSONObject("data").toString()
                    args.has("data") -> args.optString("data")
                    else -> args.toString()
                }
                val summary = PocketCastsJsonImporter(databaseProvider())
                    .importPocketCastsJson(payload)
                    .getOrThrow()
                JSONObject().apply {
                    put("ok", true)
                    put("showsAdded", summary.showsAdded)
                    put("episodesUpdated", summary.episodesUpdated)
                    put("itemsSkipped", summary.itemsSkipped)
                }
            }

            else -> throw IllegalArgumentException("Unknown MCP tool: $toolName")
        }
    }

    private fun wrapMcpToolCallResult(data: JSONObject): JSONObject {
        val wrapper = JSONObject(data.toString())
        wrapper.put(
            "content",
            JSONArray().put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", data.toString())
                }
            )
        )
        wrapper.put("isError", false)
        return wrapper
    }

    private fun buildMcpToolsListResult(): JSONObject {
        val tools = JSONArray().apply {
            put(
                mcpToolDescriptor(
                    name = "get_library",
                    description = "Read subscribed podcasts, active episodes, and custom playlists"
                )
            )
            put(
                mcpToolDescriptor(
                    name = "get_queue",
                    description = "Read the ordered Up Next playback queue"
                )
            )
            put(
                mcpToolDescriptor(
                    name = "get_playback_state",
                    description = "Read current playback state, episode, position, and audio DSP settings"
                )
            )
            put(
                mcpToolDescriptor(
                    name = "get_settings",
                    description = "Read user preferences and app settings"
                )
            )
            put(
                mcpToolDescriptor(
                    name = "control_playback",
                    description = "Control playback (play, pause, toggle, seek, set_speed, skip_forward, skip_back)"
                )
            )
            put(
                mcpToolDescriptor(
                    name = "import_pocket_casts",
                    description = "Import a Pocket Casts JSON export into Lohe"
                )
            )
        }
        return JSONObject().put("tools", tools)
    }

    private fun mcpToolDescriptor(name: String, description: String): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("inputSchema", JSONObject().put("type", "object"))
        }
    }

    private suspend fun buildLibraryJson(): JSONObject {
        val dao = databaseProvider().loheDao()
        val podcasts = dao.getSubscribedPodcasts().first()
        val episodes = dao.getActiveEpisodes().first()
        val playlists = dao.getCustomPlaylists().first()

        return JSONObject().apply {
            put("podcasts", JSONArray().apply { podcasts.forEach { put(it.toJson()) } })
            put("episodes", JSONArray().apply { episodes.forEach { put(it.toJson()) } })
            put(
                "playlists",
                JSONArray().apply {
                    playlists.forEach { pl ->
                        put(
                            JSONObject().apply {
                                put("id", pl.id)
                                put("name", pl.name)
                                put("folderName", pl.folderName)
                                put("smartRuleType", pl.smartRuleType)
                                put("isPinned", pl.isPinned)
                            }
                        )
                    }
                }
            )
        }
    }

    private suspend fun buildQueueJson(): JSONObject {
        val dao = databaseProvider().loheDao()
        val upNext = dao.getUpNextEpisodes().first()
        val arr = JSONArray().apply {
            upNext.forEach { put(it.toJson()) }
        }
        return JSONObject().apply {
            put("queue", arr)
            put("episodes", arr)
            put("count", upNext.size)
        }
    }

    private suspend fun buildPlaybackStateJson(): JSONObject {
        val controller = playerControllerProvider()
        val state = controller?.state?.value ?: headlessPlaybackState
        val currentEp = state.currentEpisode
            ?: databaseProvider().loheDao().getInProgressEpisodes().first().firstOrNull()

        return JSONObject().apply {
            put("isPlaying", state.isPlaying)
            put("positionMs", if (state.positionMs > 0L) state.positionMs else (currentEp?.resumePositionMs ?: 0L))
            put("durationMs", if (state.durationMs > 0L) state.durationMs else (currentEp?.durationMs ?: 0L))
            put("playbackSpeed", state.playbackSpeed.toDouble())
            put("skipSilenceEnabled", state.skipSilenceEnabled)
            put("volumeBoostDb", state.volumeBoostDb)
            put("sleepTimerRemainingSeconds", state.sleepTimerRemainingSeconds)
            put("sleepAtEndOfEpisode", state.sleepAtEndOfEpisode)
            put("currentEpisode", currentEp?.toJson() ?: JSONObject.NULL)
        }
    }

    private suspend fun buildSettingsJson(): JSONObject {
        val settings = preferencesProvider().settingsFlow.first()
        return JSONObject().apply {
            put("themePreference", settings.themePreference.name)
            put("reducedMotion", settings.reducedMotion)
            put("upNextIndicatorEnabled", settings.upNextIndicatorEnabled)
            put("queueIndicatorStyle", settings.queueIndicatorStyle.name)
            put("queueIndicatorSize", settings.queueIndicatorSize.name)
            put("queuePresetColor", settings.queuePresetColor.name)
            put("playbackSpeed", settings.playbackSpeed.toDouble())
            put("skipBackSeconds", settings.skipBackSeconds)
            put("skipForwardSeconds", settings.skipForwardSeconds)
            put("skipSilenceEnabled", settings.skipSilenceEnabled)
            put("volumeBoostDb", settings.volumeBoostDb)
            put("continuousPlayback", settings.continuousPlayback)
            put("markPlayedWhenFinished", settings.markPlayedWhenFinished)
            put("resumeOnHeadsetConnect", settings.resumeOnHeadsetConnect)
            put("autoRewindSeconds", settings.autoRewindSeconds)
            put("autoArchivePolicy", settings.autoArchivePolicy)
            put("keepStarredExcluded", settings.keepStarredExcluded)
            put("episodeLimitPerShow", settings.episodeLimitPerShow)
            put("autoDownloadWifi", settings.autoDownloadWifi)
            put("autoDownloadUpNext", settings.autoDownloadUpNext)
            put("autoDownloadQueueDepth", settings.autoDownloadQueueDepth)
            put("backgroundSyncEnabled", settings.backgroundSyncEnabled)
            put("backgroundSyncIntervalHours", settings.backgroundSyncIntervalHours)
        }
    }

    private suspend fun executePlaybackControl(params: JSONObject): JSONObject {
        val dao = databaseProvider().loheDao()
        val prefs = preferencesProvider()
        val controller = playerControllerProvider()
        val action = params.optString("action", params.optString("command", "play"))
            .trim()
            .lowercase(Locale.ROOT)
        val requestedEpisodeId = params.optString("episodeId", params.optString("episode_id", "")).trim()

        val targetEpisode: EpisodeEntity? = when {
            requestedEpisodeId.isNotEmpty() -> dao.getEpisodeById(requestedEpisodeId)
            controller?.state?.value?.currentEpisode != null -> controller.state.value.currentEpisode
            headlessPlaybackState.currentEpisode != null -> headlessPlaybackState.currentEpisode
            else -> dao.getInProgressEpisodes().first().firstOrNull()
                ?: dao.getUpNextEpisodes().first().firstOrNull()
                ?: dao.getActiveEpisodes().first().firstOrNull()
        }

        when (action) {
            "play", "resume", "start" -> {
                if (targetEpisode != null) {
                    val seekPos = if (params.has("positionMs")) {
                        params.optLong("positionMs", targetEpisode.resumePositionMs)
                    } else {
                        targetEpisode.resumePositionMs
                    }
                    val epWithPos = targetEpisode.copy(resumePositionMs = seekPos)
                    controller?.loadAndPlayEpisode(epWithPos, autoPlay = true)
                    headlessPlaybackState = headlessPlaybackState.copy(
                        currentEpisode = epWithPos,
                        isPlaying = true,
                        positionMs = seekPos,
                        durationMs = epWithPos.durationMs
                    )
                    prefs.setLastPlayingEpisodeId(epWithPos.id)
                } else {
                    if (controller != null && !controller.state.value.isPlaying) {
                        controller.togglePlayPause()
                    }
                    headlessPlaybackState = headlessPlaybackState.copy(isPlaying = true)
                }
            }

            "pause", "stop" -> {
                if (controller != null && controller.state.value.isPlaying) {
                    controller.togglePlayPause()
                }
                headlessPlaybackState = headlessPlaybackState.copy(isPlaying = false)
            }

            "toggle", "toggle_play_pause", "play_pause" -> {
                if (controller != null) {
                    controller.togglePlayPause()
                    headlessPlaybackState = controller.state.value
                } else {
                    headlessPlaybackState = headlessPlaybackState.copy(
                        currentEpisode = targetEpisode,
                        isPlaying = !headlessPlaybackState.isPlaying
                    )
                }
            }

            "seek", "seek_to" -> {
                val posMs = params.optLong("positionMs", params.optLong("position_ms", params.optLong("position", 0L)))
                    .coerceAtLeast(0L)
                controller?.seekTo(posMs)
                val activeEp = controller?.state?.value?.currentEpisode ?: targetEpisode
                if (activeEp != null) {
                    dao.updatePlaybackPosition(activeEp.id, posMs, activeEp.durationMs)
                }
                headlessPlaybackState = headlessPlaybackState.copy(
                    currentEpisode = activeEp,
                    positionMs = posMs
                )
            }

            "skip_forward", "skip-forward", "forward" -> {
                val sec = params.optInt("seconds", 30)
                controller?.skipBySeconds(sec)
                val currentPos = controller?.state?.value?.positionMs ?: headlessPlaybackState.positionMs
                val newPos = (currentPos + sec * 1000L).coerceAtLeast(0L)
                val activeEp = controller?.state?.value?.currentEpisode ?: targetEpisode
                if (activeEp != null) {
                    dao.updatePlaybackPosition(activeEp.id, newPos, activeEp.durationMs)
                }
                headlessPlaybackState = headlessPlaybackState.copy(
                    currentEpisode = activeEp,
                    positionMs = newPos
                )
            }

            "skip_back", "skip-back", "rewind", "back" -> {
                val sec = params.optInt("seconds", 15)
                controller?.skipBySeconds(-abs(sec))
                val currentPos = controller?.state?.value?.positionMs ?: headlessPlaybackState.positionMs
                val newPos = (currentPos - abs(sec) * 1000L).coerceAtLeast(0L)
                val activeEp = controller?.state?.value?.currentEpisode ?: targetEpisode
                if (activeEp != null) {
                    dao.updatePlaybackPosition(activeEp.id, newPos, activeEp.durationMs)
                }
                headlessPlaybackState = headlessPlaybackState.copy(
                    currentEpisode = activeEp,
                    positionMs = newPos
                )
            }

            "speed", "set_speed", "playback_speed" -> {
                val speed = params.optDouble("speed", params.optDouble("playbackSpeed", 1.0)).toFloat()
                    .coerceIn(0.5f, 3.0f)
                controller?.setPlaybackSpeed(speed)
                prefs.setPlaybackSpeed(speed)
                headlessPlaybackState = headlessPlaybackState.copy(playbackSpeed = speed)
            }

            else -> throw IllegalArgumentException("Unsupported playback action: $action")
        }

        eventRecorder("debug_api", "Executed playback control action=$action")
        val updatedState = buildPlaybackStateJson()
        return JSONObject().apply {
            put("ok", true)
            put("action", action)
            put("playback", updatedState)
            put("isPlaying", updatedState.optBoolean("isPlaying"))
            put("positionMs", updatedState.optLong("positionMs"))
            put("playbackSpeed", updatedState.optDouble("playbackSpeed"))
        }
    }

    private fun extractPocketCastsPayload(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return trimmed
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return trimmed
        return when {
            obj.has("json") && obj.opt("json") is JSONObject -> obj.getJSONObject("json").toString()
            obj.has("json") -> obj.optString("json")
            obj.has("payload") && obj.opt("payload") is JSONObject -> obj.getJSONObject("payload").toString()
            obj.has("payload") -> obj.optString("payload")
            else -> trimmed
        }
    }

    private fun PodcastEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("description", description)
        put("feedUrl", feedUrl)
        put("artworkUrl", artworkUrl)
        put("category", category)
        put("isSubscribed", isSubscribed)
    }

    private fun EpisodeEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("podcastId", podcastId)
        put("podcastTitle", podcastTitle)
        put("guid", guid)
        put("title", title)
        put("audioUrl", audioUrl)
        put("publishedAt", publishedAt)
        put("durationMs", durationMs)
        put("resumePositionMs", resumePositionMs)
        put("isPlayed", isPlayed)
        put("playedAt", playedAt)
        put("isStarred", isStarred)
        put("inUpNext", inUpNext)
        put("upNextOrder", upNextOrder)
    }

    private fun writeJsonResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        body: JSONObject,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val payloadBytes = body.toString().toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${payloadBytes.size}\r\n")
            append("Connection: close\r\n")
            for ((k, v) in extraHeaders) {
                append("$k: $v\r\n")
            }
            append("\r\n")
        }.toByteArray(Charsets.ISO_8859_1)

        output.write(headers)
        output.write(payloadBytes)
        output.flush()
    }
}
