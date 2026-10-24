package com.lohe.app.dev.mcp

import kotlinx.serialization.json.JsonObject

/**
 * The MCP front door: it advertises the catalogue and runs what the
 * catalogue advertises.
 *
 * The catalogue lives in [McpCatalog] and the behaviour in [LoheToolkit];
 * this class is the seam between them, and it checks at construction that
 * the two agree — a tool that is advertised but not implemented fails the
 * debug build at startup rather than answering "no such tool" later.
 */
class McpTools(private val toolkit: LoheToolkit) {

    init {
        val missing = McpCatalog.tools.map { it.name }.filterNot { it in toolkit.operationNames }
        check(missing.isEmpty()) {
            "The tool catalogue advertises tools the toolkit does not implement: $missing"
        }
    }

    /** The catalogue as the MCP `tools/list` result. */
    fun listResult(): JsonObject = McpCatalog.listResult()

    /**
     * Runs a tool.
     *
     * A tool that fails does not fail the request: the explanation comes back
     * as the tool's own answer, flagged with `isError`, so the caller can
     * read it and try something else.
     */
    suspend fun call(name: String, arguments: JsonObject): JsonObject {
        val tool = McpCatalog.find(name) ?: return McpProtocol.toolResult(
            "No such tool: $name",
            isError = true,
        )
        return try {
            val result = toolkit.execute(tool.name, arguments)
            McpProtocol.toolResult(result.pretty())
        } catch (e: ToolException) {
            McpProtocol.toolResult(e.message ?: "That did not work.", isError = true)
        } catch (e: Exception) {
            McpProtocol.toolResult(
                e.message ?: e::class.simpleName ?: "That did not work.",
                isError = true,
            )
        }
    }
}
