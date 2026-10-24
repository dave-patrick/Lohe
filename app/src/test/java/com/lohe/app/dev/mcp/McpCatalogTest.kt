package com.lohe.app.dev.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the shape of the advertised tool catalogue.
 *
 * A tool that is described badly is worse than one that is missing: the
 * model will call it with the wrong arguments and be told so, in a way that
 * looks like the app's fault. So the schemas are held to a shape here.
 */
class McpCatalogTest {

    @Test
    fun `every tool is named and described`() {
        McpCatalog.tools.forEach { tool ->
            assertTrue("a tool with no name", tool.name.isNotBlank())
            assertTrue("${tool.name} has no description", tool.description.isNotBlank())
        }
    }

    @Test
    fun `tool names are unique`() {
        val names = McpCatalog.tools.map { it.name }

        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `tool names are namespaced`() {
        McpCatalog.tools.forEach { tool ->
            assertTrue("${tool.name} is not namespaced", tool.name.startsWith("lohe_"))
        }
    }

    @Test
    fun `every schema is an object schema`() {
        McpCatalog.tools.forEach { tool ->
            val schema = tool.inputSchema
            assertEquals("${tool.name} is not an object schema", "object", schema["type"]?.jsonPrimitive?.content)
            assertTrue("${tool.name} has no properties block", schema["properties"] is JsonArray ||
                schema["properties"] is kotlinx.serialization.json.JsonObject)
        }
    }

    @Test
    fun `every required argument is a declared argument`() {
        McpCatalog.tools.forEach { tool ->
            val properties = tool.inputSchema["properties"]?.jsonObject ?: return@forEach
            val required = (tool.inputSchema["required"] as? JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                .orEmpty()
            required.forEach { key ->
                assertTrue(
                    "${tool.name} requires $key but does not declare it",
                    properties.containsKey(key),
                )
            }
        }
    }

    @Test
    fun `the catalogue lists every tool the server offers`() {
        // A spot-check on the shape of the list rather than a count: the
        // count moves whenever a tool is added.
        val names = McpCatalog.tools.map { it.name }

        listOf(
            "lohe_shows", "lohe_episodes", "lohe_playlists", "lohe_queue",
            "lohe_mark_played", "lohe_play", "lohe_now_playing", "lohe_settings",
        ).forEach { expected ->
            assertTrue("$expected is missing", names.contains(expected))
        }
    }
}
