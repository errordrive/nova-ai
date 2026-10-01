package com.nova.ai.agent.terminal

import com.nova.ai.agent.AgentTool
import com.nova.ai.agent.PermissionCategory
import com.nova.ai.agent.RiskLevel
import com.nova.ai.agent.ToolContext
import com.nova.ai.agent.ToolResult
import com.nova.ai.agent.tools.str
import javax.inject.Inject

/**
 * Shell execution tool. Phase 4 stub: always reports unavailability.
 *
 * Kept registered (CRITICAL risk, TERMINAL_EXEC category) so profiles, the permission engine and
 * the approval UI can already treat it as the most dangerous tool. Real execution via Termux is
 * designed in `TERMUX_PLAN.md`.
 */
class TerminalTool @Inject constructor() : AgentTool {
    override val name = "run_terminal"
    override val description = "Execute a shell command (Phase 4: Termux integration — currently a stub)."
    override val parametersSchemaJson = """
        {"type":"object","properties":{
          "command":{"type":"string","description":"Shell command to execute."}
        },"required":["command"]}
    """.trimIndent()
    override val permissionCategory = PermissionCategory.TERMINAL_EXEC
    override val risk = RiskLevel.CRITICAL

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val command = args.str("command")
        ctx.auditLogger.log(
            toolName = name,
            argsJson = """{"command":${command?.let { "\"$it\"" } ?: "null"}}""",
            path = null,
            permissionState = "STUB",
            resultSummary = "terminal execution not available yet",
            approved = false,
            actor = "agent",
        )
        return ToolResult(
            success = false,
            output = "",
            error = "Terminal execution is not available yet (Phase 4: Termux integration).",
        )
    }
}
