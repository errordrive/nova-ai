package com.nova.ai.agent

import java.io.File

/**
 * Core models for the Nova agent runtime: permissions, sessions, events and tool plumbing.
 */

/** Permission categories gate every tool execution. */
enum class PermissionCategory {
    FILE_READ,
    FILE_CREATE,
    FILE_WRITE,
    FILE_EDIT,
    FILE_DELETE,
    FOLDER_CREATE,
    FOLDER_DELETE,
    TERMINAL_EXEC,
    NETWORK,
    DOWNLOAD,
}

/** How a permission category is granted. */
enum class PermissionLevel {
    DENY,
    ASK_EVERY_TIME,
    ALLOW_SESSION,
    ALWAYS_ALLOW,
}

/** Risk attached to a single tool execution, used for approval UI hints. */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

/** Outcome of [com.nova.ai.agent.permissions.PermissionEngine.check]. */
sealed interface PermissionDecision {
    /** Execute immediately. */
    data object Allow : PermissionDecision

    /** Pause the agent loop and ask the user; [diffPreview] is attached for edit/write tools when available. */
    data class Ask(val reason: String, val diffPreview: DiffResult? = null) : PermissionDecision

    /** Block the tool call. */
    data class Deny(val reason: String) : PermissionDecision
}

/**
 * Line-based diff of a file change, used for approval previews.
 *
 * @param added lines present in the new text but not the old (multiset-aware), capped at 200.
 * @param removed lines present in the old text but not the new (multiset-aware), capped at 200.
 */
data class DiffResult(
    val added: List<String>,
    val removed: List<String>,
    val oldLineCount: Int,
    val newLineCount: Int,
)

/** User verdict on a pending approval request. */
enum class ApprovalDecision {
    APPROVE_ONCE,
    APPROVE_ALWAYS,
    DENY,
}

/** Parameters for starting one agent run. */
data class AgentSessionSpec(
    val profileId: String,
    val userRequest: String,
    val workspaceId: String? = null,
    val conversationId: String? = null,
    val maxStepsOverride: Int? = null,
)

/** Stream of events emitted by [AgentOrchestrator.runAgent]. */
sealed interface AgentEvent {
    data class Started(val sessionId: String) : AgentEvent
    data class ThinkingDelta(val text: String) : AgentEvent
    data class ToolCallStarted(val callId: String, val toolName: String, val argsJson: String) : AgentEvent
    data class ApprovalRequested(
        val callId: String,
        val toolName: String,
        val argsJson: String,
        val path: String?,
        val risk: RiskLevel,
        val diffPreview: DiffResult?,
    ) : AgentEvent
    data class ToolCallResult(val callId: String, val toolName: String, val success: Boolean, val output: String) : AgentEvent
    data class ApprovalDenied(val callId: String, val toolName: String) : AgentEvent
    data class Completed(val text: String, val stepsUsed: Int) : AgentEvent
    data class Failed(val error: String) : AgentEvent
    data object Cancelled : AgentEvent
}

/** A workspace root the agent may operate in. [ioFile] is null for SAF-only roots. */
data class WorkspaceRoot(
    val id: String,
    val name: String,
    val uri: String,
    val ioFile: File?,
)

/** Result of one tool execution. Exactly one of [output]/[error] is expected to be meaningful. */
data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
)

/** Context handed to every tool execution. */
data class ToolContext(
    val workspaceRoots: List<WorkspaceRoot>,
    val activeRoot: WorkspaceRoot,
    val auditLogger: AuditLogger,
)

/** A capability the agent can invoke. Implementations must be side-effect safe per their [risk]. */
interface AgentTool {
    val name: String
    val description: String

    /** JSON Schema (draft 2020-12 style object schema) describing the tool arguments. */
    val parametersSchemaJson: String
    val permissionCategory: PermissionCategory
    val risk: RiskLevel

    suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult
}
