package com.nova.ai.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * An LLM provider configuration: an OpenAI-compatible endpoint, a hosted API, or a local server.
 *
 * API keys are never stored here; they live in
 * [com.nova.ai.data.security.SecureCredentialStore], keyed by provider id.
 *
 * @property type ProviderType enum name stored as String, e.g. "OPENAI".
 * @property authMethod One of "bearer", "none", "header".
 * @property modelId Default model id used when a conversation does not pick one.
 * @property customHeadersJson JSON object of extra HTTP headers sent with every request.
 */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String,
    val baseUrl: String,
    val authMethod: String,
    val modelId: String,
    val customHeadersJson: String = "{}",
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Cached model listing for one provider, so model pickers work offline.
 *
 * @property id Composite key "providerId:modelId"; callers must pass it explicitly.
 */
@Entity(tableName = "models_cache")
data class ModelCacheEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val cachedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A chat or agent conversation thread.
 *
 * @property mode One of "chat", "agent".
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val mode: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A single message inside a conversation.
 *
 * @property role One of "system", "user", "assistant", "tool".
 * @property toolCallsJson JSON array of tool calls requested by an assistant message.
 * @property toolCallId Set on role="tool" messages to link back to the requesting call.
 * @property attachmentsJson JSON array of attached files (uris, names, mime types).
 */
@Entity(
    tableName = "messages",
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String,
    val content: String,
    val toolCallsJson: String = "[]",
    val toolCallId: String? = null,
    val attachmentsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A workspace root the agent is allowed to operate in.
 *
 * @property uriString Document-tree or file uri of the workspace root.
 * @property type One of "internal" (app-private storage) or "saf" (Storage Access Framework).
 */
@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uriString: String,
    val type: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Permission rule controlling what an agent may do without asking.
 *
 * @property category Tool category, e.g. "FILE_READ".
 * @property level One of "DENY", "ASK_EVERY_TIME", "ALLOW_SESSION", "ALWAYS_ALLOW".
 * @property scopePath Optional path prefix the rule is limited to; null means global.
 */
@Entity(tableName = "permission_rules")
data class PermissionRuleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: String,
    val level: String,
    val scopePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * One run of an agent profile.
 *
 * @property status One of "running", "completed", "failed", "cancelled".
 */
@Entity(tableName = "agent_sessions")
data class AgentSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val conversationId: String? = null,
    val status: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A single tool invocation inside an agent session.
 *
 * @property endedAt Epoch millis when the call finished; 0 while still running.
 */
@Entity(
    tableName = "tool_calls",
    indices = [Index("sessionId")]
)
data class ToolCallEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val toolName: String,
    val argumentsJson: String,
    val resultSummary: String = "",
    val success: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Immutable audit trail entry for every privileged agent action.
 *
 * @property permissionState One of "ALLOW", "ASK", "DENY": the rule outcome at execution time.
 * @property actor One of "agent", "user".
 */
@Entity(tableName = "audit_records")
data class AuditRecordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String,
    val argumentsJson: String,
    val path: String? = null,
    val permissionState: String,
    val resultSummary: String,
    val approved: Boolean,
    val actor: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A reusable agent profile: persona, model, enabled tools, and step budget.
 */
@Entity(tableName = "agent_profiles")
data class AgentProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val systemPrompt: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val enabledToolsJson: String = "[]",
    val maxSteps: Int = 25,
    val createdAt: Long = System.currentTimeMillis()
)
