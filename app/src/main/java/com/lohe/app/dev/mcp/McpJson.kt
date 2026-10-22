package com.lohe.app.dev.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }

/** The object as indented JSON — what a tool hands back to the model. */
fun JsonObject.pretty(): String = prettyJson.encodeToString(JsonObject.serializer(), this)


/**
 * Small JSON helpers shared by the MCP layer.
 *
 * The MCP payloads are built by hand rather than through a data class per
 * shape: the shapes are shallow, they differ from tool to tool, and a
 * hand-written object keeps the wire format obvious next to the code that
 * fills it.
 */

/**
 * Builds a JSON object, skipping null values.
 *
 * The value is taken with a star projection so that a mixed call — a string
 * here, a number there, a list further along — infers one type for the lot
 * rather than failing to.
 */
fun jsonObject(vararg pairs: Pair<String, *>): JsonObject = buildJsonObject {
    for (pair in pairs) {
        val key = pair.first
        val value = pair.second ?: continue
        when (value) {
            is JsonElement -> put(key, value)
            is String -> put(key, value)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Double -> put(key, value)
            is Boolean -> put(key, value)
            is Iterable<*> -> put(key, JsonArray(value.map { it.toJsonElement() }))
            else -> put(key, value.toString())
        }
    }
}

/** Wraps a plain value as a JSON element. */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}

/** A string argument, or null when it is absent, blank or not a string. */
fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** An integer argument, or null when it is absent or not a number. */
fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.trim()?.toIntOrNull()

/** A boolean argument, or null when it is absent or not a boolean. */
fun JsonObject.booleanOrNull(key: String): Boolean? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (primitive.isString) return primitive.content.trim().toBooleanStrictOrNull()
    return primitive.content.toBooleanStrictOrNull()
}

/** A list-of-strings argument; an empty list when it is absent. */
fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        .orEmpty()
