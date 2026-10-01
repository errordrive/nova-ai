package com.nova.ai.agent

import com.nova.ai.agent.terminal.TerminalTool
import com.nova.ai.agent.tools.CreateDirectoryTool
import com.nova.ai.agent.tools.DeleteFileTool
import com.nova.ai.agent.tools.EditFileTool
import com.nova.ai.agent.tools.ListDirectoryTool
import com.nova.ai.agent.tools.ReadFileTool
import com.nova.ai.agent.tools.RenameFileTool
import com.nova.ai.agent.tools.SearchFilesTool
import com.nova.ai.agent.tools.WriteFileTool
import com.nova.ai.provider.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry of [AgentTool] implementations available to the agent loop.
 *
 * MCP servers plug in here later via [com.nova.ai.agent.mcp.McpToolAdapter] without touching
 * [AgentOrchestrator] (see `agent/mcp/MCP_DESIGN.md`).
 */
@Singleton
class ToolRegistry @Inject constructor() {

    private val tools: List<AgentTool> = listOf(
        ListDirectoryTool(),
        SearchFilesTool(),
        ReadFileTool(),
        WriteFileTool(),
        EditFileTool(),
        DeleteFileTool(),
        RenameFileTool(),
        CreateDirectoryTool(),
        TerminalTool(),
    )

    private val byName: Map<String, AgentTool> = tools.associateBy { it.name }

    /** All registered tool names, in registration order. */
    fun toolNames(): List<String> = tools.map { it.name }

    /** Returns the tool registered as [name], or null. */
    fun get(name: String): AgentTool? = byName[name]

    /**
     * Provider-facing tool definitions, optionally restricted to [enabledTools].
     * A null [enabledTools] means every registered tool.
     */
    fun definitions(enabledTools: Set<String>? = null): List<ToolDefinition> {
        return tools
            .filter { enabledTools == null || it.name in enabledTools }
            .map { tool ->
                ToolDefinition(
                    name = tool.name,
                    description = tool.description,
                    parametersSchemaJson = tool.parametersSchemaJson,
                )
            }
    }

    /**
     * Parses a profile's `enabledToolsJson` (JSON array of tool names) into definitions.
     * Blank, empty or unparsable JSON falls back to all tools.
     */
    fun definitionsForProfile(enabledToolsJson: String): List<ToolDefinition> {
        if (enabledToolsJson.isBlank()) return definitions()
        val names = try {
            Json.parseToJsonElement(enabledToolsJson).jsonArray.map { it.jsonPrimitive.contentOrNull }
        } catch (e: Exception) {
            return definitions()
        }.filterNotNull().toSet()
        if (names.isEmpty()) return definitions()
        return definitions(names)
    }
}
