package com.lohe.app.dev.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the MCP wire format.
 *
 * The protocol layer is the part a client talks to, and a mistake in it is
 * invisible until something else breaks: a client that cannot parse the
 * handshake simply never talks to the server. So the shapes are pinned here
 * rather than left to be discovered on a device.
 */
class McpProtocolTest {

    @Test
    fun `a single request is parsed`() {
        val body = """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"cursor":"abc"}}
        """.trimIndent()

        val parsed = McpProtocol.parseBody(body) as McpProtocol.Body.Requests

        assertEquals(false, parsed.isBatch)
        assertEquals(1, parsed.requests.size)
        val request = parsed.requests.first()
        assertEquals("tools/list", request.method)
        assertEquals(JsonPrimitive(1), request.id)
        assertEquals("abc", request.params.stringOrNull("cursor"))
    }

    @Test
    fun `a batch is parsed as a batch`() {
        val body = """
            [{"jsonrpc":"2.0","id":1,"method":"ping"},
             {"jsonrpc":"2.0","id":2,"method":"tools/list"}]
        """.trimIndent()

        val parsed = McpProtocol.parseBody(body) as McpProtocol.Body.Requests

        assertEquals(true, parsed.isBatch)
        assertEquals(2, parsed.requests.size)
    }

    @Test
    fun `a request without an id is a notification`() {
        val body = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""

        val parsed = McpProtocol.parseBody(body) as McpProtocol.Body.Requests
        val request = parsed.requests.first()

        assertTrue(request.isNotification)
        assertNull(request.id)
    }

    @Test
    fun `a request with a null id is a notification`() {
        val body = """{"jsonrpc":"2.0","id":null,"method":"notifications/initialized"}"""

        val parsed = McpProtocol.parseBody(body) as McpProtocol.Body.Requests

        assertTrue(parsed.requests.first().isNotification)
    }

    @Test
    fun `a body that is not JSON is refused rather than thrown`() {
        val parsed = McpProtocol.parseBody("not json at all")

        assertTrue(parsed is McpProtocol.Body.Invalid)
    }

    @Test
    fun `a body without a method is refused`() {
        val parsed = McpProtocol.parseBody("""{"jsonrpc":"2.0","id":1}""")

        assertTrue(parsed is McpProtocol.Body.Invalid)
    }

    @Test
    fun `initialize answers with the version the client asked for`() {
        val result = McpProtocol.initializeResult(
            requestedVersion = "2025-03-26",
            serverName = "lohe",
            serverVersion = "0.8.0",
        )

        assertEquals("2025-03-26", result["protocolVersion"]?.jsonPrimitive?.content)
        assertEquals("lohe", result["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
        assertNotNull(result["capabilities"]?.jsonObject?.get("tools"))
    }

    @Test
    fun `initialize answers with the server's own version when it is newer`() {
        val result = McpProtocol.initializeResult(
            requestedVersion = "1999-01-01",
            serverName = "lohe",
            serverVersion = "0.8.0",
        )

        assertEquals(McpProtocol.LATEST_VERSION, result["protocolVersion"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a successful response carries the id and the result`() {
        val response = McpProtocol.success(JsonPrimitive(7), JsonObject(mapOf("ok" to JsonPrimitive(true))))

        assertEquals("2.0", response["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(7), response["id"])
        assertEquals(true, response["result"]?.jsonObject?.get("ok")?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun `a failed response carries the code and the message`() {
        val response = McpProtocol.failure(JsonPrimitive(7), McpProtocol.METHOD_NOT_FOUND, "nope")

        val error = response["error"]?.jsonObject
        assertEquals(McpProtocol.METHOD_NOT_FOUND, error?.get("code")?.jsonPrimitive?.content?.toInt())
        assertEquals("nope", error?.get("message")?.jsonPrimitive?.content)
    }

    @Test
    fun `a tool result is content, not an error`() {
        val result = McpProtocol.toolResult("hello")

        val content = result["content"] as JsonArray
        assertEquals(1, content.size)
        val block = content[0].jsonObject
        assertEquals("text", block["type"]?.jsonPrimitive?.content)
        assertEquals("hello", block["text"]?.jsonPrimitive?.content)
        assertNull(result["isError"])
    }

    @Test
    fun `a failed tool result is flagged`() {
        val result = McpProtocol.toolResult("it went wrong", isError = true)

        assertEquals(true, result["isError"]?.jsonPrimitive?.content?.toBoolean())
    }
}
