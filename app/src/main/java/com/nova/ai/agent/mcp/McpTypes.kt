package com.nova.ai.agent.mcp

import com.nova.ai.agent.AgentTool
import com.nova.ai.agent.PermissionCategory
import com.nova.ai.agent.RiskLevel
import com.nova.ai.agent.ToolContext
import com.nova.ai.agent.ToolResult
import com.nova.ai.provider.ToolDefinition

/**
 * Minimal MCP (Model Context Protocol) types. Servers are not connected yet; these types define
 * the seam so a future implementation plugs into [com.nova.ai.agent.ToolRegistry] without
 * touching [com.nova.ai.agent.AgentOrchestrator].
 */

/** A configured MCP server. [transport] is e.g. "stdio" or "sse". */
interface McpServer {
    val name: String
    val transport: String

    /** Tools exposed by this server, in provider [ToolDefinition] form. */
    suspend fun listTools(): List<ToolDefinition>
}

/**
 * Adapts one MCP server tool to the [AgentTool] interface.
 *
 * Permission mapping: MCP tools always use [PermissionCategory.NETWORK] with [RiskLevel.HIGH]
 * until per-tool risk metadata exists, so every MCP call goes through approval by default.
 */
class McpToolAdapter(
    val serverName: String,
    val definition: ToolDefinition,
) : AgentTool {
    override val name: String = "mcp_${serverName}_${definition.name}"
    override val description: String = "[MCP:$serverName] ${definition.description}"
    override val parametersSchemaJson: String = definition.parametersSchemaJson
    override val permissionCategory: PermissionCategory = PermissionCategory.NETWORK
    override val risk: RiskLevel = RiskLevel.HIGH

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        return ToolResult(
            success = false,
            output = "",
            error = "MCP server '$serverName' is not connected yet.",
        )
    }
}
