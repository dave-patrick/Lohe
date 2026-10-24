package com.lohe.app.dev.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The JSON-RPC 2.0 envelope of the Model Context Protocol.
 *
 * Only the parts Lohe speaks are modelled: `initialize`, `ping`, `tools/list`
 * and `tools/call`, plus the notifications a client sends alongside them.
 * Everything here is plain Kotlin with no Android dependency, so the wire
 * format can be exercised by ordinary JVM tests.
 */
object McpProtocol {

    /** The protocol revision this server implements. */
    const val LATEST_VERSION = "2025-06-18"

    /**
     * Revisions this server can speak.
     *
     * A client asks for the revision it knows; answering with the same one
     * keeps the conversation on a dialect both sides understand. An unknown
     * revision gets [LATEST_VERSION], which is what the specification asks
     * for when the server supports something newer than the client.
     */
    val SUPPORTED_VERSIONS = setOf("2024-11-05", "2025-03-26", "2025-06-18")

    // JSON-RPC error codes.
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** One request from the client. A request with no id is a notification. */
    data class Request(
        val id: JsonElement?,
        val method: String,
        val params: JsonObject,
    ) {
        val isNotification: Boolean get() = id == null
    }

    /** What a request body turned out to hold. */
    sealed interface Body {
        /** One request, or a batch of them. */
        data class Requests(val requests: List<Request>, val isBatch: Boolean) : Body

        /** The body could not be read as a request. */
        data class Invalid(val message: String) : Body
    }

    /**
     * Parses a request body.
     *
     * A body may be a single request or a batch — an array of requests. A
     * body that is neither comes back as [Body.Invalid] rather than as an
     * exception, so the caller can answer with a parse error instead of
     * bringing the server down.
     */
    fun parseBody(body: String): Body {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return Body.Invalid("Could not parse the request body as JSON.")
        return when (element) {
            is JsonArray -> Body.Requests(element.mapNotNull { request(it) }, isBatch = true)
            is JsonObject -> {
                val parsed = request(element)
                    ?: return Body.Invalid("The request is missing a method.")
                Body.Requests(listOf(parsed), isBatch = false)
            }
            else -> Body.Invalid("The request body is neither an object nor an array.")
        }
    }

    /** The requests a body carries; empty when the body could not be read. */
    fun parseRequests(body: String): List<Request> =
        (parseBody(body) as? Body.Requests)?.requests.orEmpty()


    private fun request(element: JsonElement): Request? {
        val obj = element as? JsonObject ?: return null
        val method = (obj["method"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val id = obj["id"]?.takeUnless { it is JsonPrimitive && it.content == "null" }
        val params = obj["params"] as? JsonObject ?: JsonObject(emptyMap())
        return Request(id = id, method = method, params = params)
    }

    /** A successful response to the request with [id]. */
    fun success(id: JsonElement?, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("result", result)
    }

    /** A failed response. The id is echoed even when it is null. */
    fun failure(id: JsonElement?, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", message)
            },
        )
    }

    /**
     * The result of a tool call.
     *
     * A tool that fails returns its explanation as ordinary content with
     * `isError` set, rather than as a protocol error: the failure is the
     * answer, and the model is meant to read it and try something else.
     */
    fun toolResult(text: String, isError: Boolean = false): JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                )
            },
        )
        if (isError) put("isError", true)
    }

    /** The result of `initialize`. */
    fun initializeResult(requestedVersion: String?, serverName: String, serverVersion: String): JsonObject {
        val version = requestedVersion
            ?.takeIf { it in SUPPORTED_VERSIONS }
            ?: LATEST_VERSION
        return buildJsonObject {
            put("protocolVersion", version)
            put(
                "capabilities",
                buildJsonObject {
                    put(
                        "tools",
                        buildJsonObject {
                            put("listChanged", false)
                        },
                    )
                },
            )
            put(
                "serverInfo",
                buildJsonObject {
                    put("name", serverName)
                    put("version", serverVersion)
                },
            )
        }
    }
}
