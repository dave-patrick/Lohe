package com.lohe.app.dev.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.UUID

/**
 * The HTTP transport of the MCP server.
 *
 * POST serves JSON-RPC; DELETE closes the logical session. No notifications
 * are pushed, so GET returns 405. The debug-only loopback server authenticates
 * every request before handing it to this transport. Loopback is also reachable
 * by other apps on the device, so authentication is required even without adb.
 */
object McpHttp {

    /** The path the MCP endpoint is mounted on. */
    const val PATH = "/mcp"


    /** The name the server introduces itself under. */
    const val SERVER_NAME = "lohe"

    /** The server version reported at initialize. */
    val SERVER_VERSION = com.lohe.app.BuildConfig.VERSION_NAME

    /**
     * The session id handed out with every response.
     *
     * The server keeps no session state — each request stands on its own — so
     * the id is a formality that satisfies the transport, not a key to
     * anything.
     */
    private val sessionId: String = UUID.randomUUID().toString()

    /** Serves a request already authenticated by DevApi. GET streams are optional in MCP. */
    suspend fun handle(
        socket: Socket,
        method: String,
        body: String,
        tools: McpTools,
    ) {
        when (method) {
            "POST" -> {
                val (status, payload) = handlePost(body, tools)
                if (payload.isEmpty()) {
                    respondEmpty(socket, status)
                } else {
                    respondJson(socket, status, payload)
                }
            }
            "GET" -> respondJson(socket, 405, McpProtocol.failure(null, -32000, "Server notifications are not supported").toString())
            "DELETE" -> respondEmpty(socket, 200)
            else -> respondJson(
                socket,
                405,
                McpProtocol.failure(null, McpProtocol.INVALID_REQUEST, "Method not allowed").toString(),
            )
        }
    }

    /** Runs a batch or a single request and works out what to answer with. */
    private suspend fun handlePost(body: String, tools: McpTools): Pair<Int, String> =
        when (val parsed = McpProtocol.parseBody(body)) {
            is McpProtocol.Body.Invalid ->
                400 to McpProtocol.failure(null, McpProtocol.PARSE_ERROR, parsed.message).toString()

            is McpProtocol.Body.Requests -> {
                val responses = parsed.requests.mapNotNull { dispatch(it, tools) }
                when {
                    // Every request in the batch was a notification: there is
                    // nothing to answer with.
                    responses.isEmpty() -> 202 to ""
                    parsed.isBatch -> 200 to JsonArray(responses).toString()
                    else -> 200 to responses.first().toString()
                }
            }
        }

    /**
     * Runs one request.
     *
     * A notification has no id and gets no answer: the null return is what
     * takes it out of the response list.
     */
    private suspend fun dispatch(request: McpProtocol.Request, tools: McpTools): JsonObject? {
        if (request.isNotification) {
            // The only notification that means anything here is the client
            // saying it is ready; the rest are ignored on purpose.
            return null
        }
        return when (request.method) {
            "initialize" -> McpProtocol.success(
                request.id,
                McpProtocol.initializeResult(
                    requestedVersion = request.params.stringOrNull("protocolVersion"),
                    serverName = SERVER_NAME,
                    serverVersion = SERVER_VERSION,
                ),
            )

            "ping" -> McpProtocol.success(request.id, JsonObject(emptyMap()))

            "tools/list" -> McpProtocol.success(request.id, tools.listResult())

            "tools/call" -> {
                val name = request.params.stringOrNull("name")
                if (name == null) {
                    McpProtocol.failure(request.id, McpProtocol.INVALID_PARAMS, "name is required")
                } else {
                    val arguments = request.params["arguments"] as? JsonObject
                        ?: JsonObject(emptyMap())
                    McpProtocol.success(request.id, tools.call(name, arguments))
                }
            }

            else -> McpProtocol.failure(
                request.id,
                McpProtocol.METHOD_NOT_FOUND,
                "Unknown method: ${request.method}",
            )
        }
    }

    /** Answers with a JSON body. */
    private fun respondJson(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        val w = OutputStreamWriter(out, Charsets.UTF_8)
        w.write("HTTP/1.1 $status ${statusText(status)}\r\n")
        w.write("Content-Type: application/json; charset=utf-8\r\n")
        w.write("Content-Length: ${bytes.size}\r\n")
        w.write("Mcp-Session-Id: $sessionId\r\n")
        w.write("Connection: close\r\n")
        w.write("\r\n")
        w.flush()
        out.write(bytes)
        out.flush()
    }

    /** Answers with no body at all. */
    private fun respondEmpty(socket: Socket, status: Int) {
        val out = socket.getOutputStream()
        val w = OutputStreamWriter(out, Charsets.UTF_8)
        w.write("HTTP/1.1 $status ${statusText(status)}\r\n")
        w.write("Content-Length: 0\r\n")
        w.write("Mcp-Session-Id: $sessionId\r\n")
        w.write("Connection: close\r\n")
        w.write("\r\n")
        w.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        202 -> "Accepted"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        else -> "OK"
    }
}
