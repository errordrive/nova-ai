package com.nova.ai.agent

import android.content.Context
import com.nova.ai.agent.permissions.PermissionEngine
import com.nova.ai.agent.tools.parseArgs
import com.nova.ai.agent.tools.str
import com.nova.ai.data.datastore.SettingsStore
import com.nova.ai.data.local.entity.MessageEntity
import com.nova.ai.domain.repository.AgentProfileRepository
import com.nova.ai.domain.repository.ChatRepository
import com.nova.ai.domain.repository.WorkspaceRepository
import com.nova.ai.provider.AIProvider
import com.nova.ai.provider.ChatChunk
import com.nova.ai.provider.ChatMessage
import com.nova.ai.provider.ChatRequest
import com.nova.ai.provider.ChatRole
import com.nova.ai.provider.ProviderError
import com.nova.ai.provider.ProviderRegistry
import com.nova.ai.provider.ToolCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the agent loop: streams model output, executes tool calls through the permission engine,
 * pauses for user approval when required, and feeds tool results back to the model.
 *
 * Flow per step:
 * 1. Build a [ChatRequest] with the profile's tools and stream it.
 * 2. Accumulate text ([AgentEvent.ThinkingDelta]) and tool-call fragments.
 * 3. No tool calls -> [AgentEvent.Completed]; otherwise execute each call:
 *    [AgentEvent.ToolCallStarted] -> permission check -> optional [AgentEvent.ApprovalRequested]
 *    (UI resolves via [submitApproval]) -> execute -> [AgentEvent.ToolCallResult].
 * 4. Tool results are appended as TOOL messages and the loop continues until [AgentEvent.Completed]
 *    or `maxSteps` is exhausted.
 *
 * Approvals time out after 10 minutes and are treated as denied. Cancellation emits
 * [AgentEvent.Cancelled] and rethrows.
 *
 * Persistence: agent runs are recorded through [AuditLogger] only; `AgentSessionEntity` /
 * `ToolCallEntity` rows are intentionally not written (the audit trail covers tool calls).
 * Conversation history is read from and the final answer written back to [ChatRepository] when
 * the spec carries a `conversationId`.
 *
 * Assumptions about sibling modules (must match their actual signatures):
 * - `AIProvider.chatStream(ChatRequest): Flow<ChatChunk>`, `AIProvider.supportsTools: Boolean`
 * - `ChatChunk` variants: `TextDelta(text)`, `ToolCallDelta(id, name, argumentsFragment)`, `Done`, `Error(error)`
 * - `ProviderRegistry.providerFor(providerId: String?): AIProvider`
 * - `ChatMessage(role, content, toolCalls = ..., toolCallId = ...)` with provider `ToolCall(id, name, arguments)`
 * - `AgentProfileEntity(id, name, systemPrompt, providerId?, modelId?, enabledToolsJson, maxSteps)`
 * - `AgentProfileRepository`: `getProfile(id)`, `ensureDefaults()`, `observeProfiles()`
 * - `WorkspaceEntity(id, name, uriString, type, isDefault)`; `WorkspaceRepository.observeWorkspaces()`
 * - `ChatRepository.getMessages(conversationId): List<MessageEntity>` with `MessageEntity(role, content, toolCallId?)`,
 *   and `addMessage(conversationId, role, content)`
 * - `SettingsStore.settings: Flow<AppSettings>` with `AppSettings.defaultModelId`
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val toolRegistry: ToolRegistry,
    private val permissionEngine: PermissionEngine,
    private val auditLogger: AuditLogger,
    private val profileRepository: AgentProfileRepository,
    private val chatRepository: ChatRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val settingsStore: SettingsStore,
    @ApplicationContext private val context: Context,
) {

    private val pendingApprovals = ConcurrentHashMap<String, CompletableDeferred<ApprovalDecision>>()

    /**
     * Resolves a pending approval. Returns false when no approval is waiting for [callId]
     * (already resolved or timed out).
     */
    fun submitApproval(callId: String, decision: ApprovalDecision): Boolean {
        val deferred = pendingApprovals[callId] ?: return false
        return deferred.complete(decision)
    }

    /** Starts an agent run for [spec], emitting [AgentEvent]s. */
    fun runAgent(spec: AgentSessionSpec): Flow<AgentEvent> = flow {
        val sessionId = UUID.randomUUID().toString()
        emit(AgentEvent.Started(sessionId))
        try {
            val profile = profileRepository.getProfile(spec.profileId)
            if (profile == null) {
                emit(AgentEvent.Failed("Agent profile not found: ${spec.profileId}"))
                return@flow
            }
            val provider: AIProvider = try {
                providerRegistry.providerFor(profile.providerId)
            } catch (e: Exception) {
                emit(AgentEvent.Failed("No AI provider available: ${e.message}"))
                return@flow
            }
            val settings = settingsStore.settings.first()
            val modelId = profile.modelId ?: settings.defaultModelId ?: "default"
            val maxSteps = spec.maxStepsOverride ?: profile.maxSteps

            val ctx = buildToolContext(spec)

            val messages = mutableListOf(ChatMessage(role = ChatRole.SYSTEM, content = profile.systemPrompt))
            if (spec.conversationId != null) {
                chatRepository.getMessages(spec.conversationId).takeLast(40).forEach { m ->
                    val role = when (m.role.lowercase()) {
                        "user" -> ChatRole.USER
                        "assistant" -> ChatRole.ASSISTANT
                        "system" -> ChatRole.SYSTEM
                        "tool" -> ChatRole.TOOL
                        else -> ChatRole.USER
                    }
                    messages += ChatMessage(role = role, content = m.content, toolCallId = m.toolCallId)
                }
            }
            messages += ChatMessage(role = ChatRole.USER, content = spec.userRequest)

            val tools = if (provider.supportsTools) {
                toolRegistry.definitionsForProfile(profile.enabledToolsJson).ifEmpty { null }
            } else {
                null
            }

            var steps = 0
            while (steps < maxSteps) {
                ensureActive()
                steps++
                val request = ChatRequest(
                    modelId = modelId,
                    messages = messages.toList(),
                    systemPrompt = null, // already the first message
                    temperature = 0.7,
                    maxTokens = 2048,
                    tools = tools,
                )

                // Stream one model turn.
                val text = StringBuilder()
                val argBuilders = LinkedHashMap<String, StringBuilder>()
                val callNames = mutableMapOf<String, String>()
                var streamError: ProviderError? = null
                provider.chatStream(request).collect { chunk ->
                    when (chunk) {
                        is ChatChunk.TextDelta -> {
                            text.append(chunk.text)
                            emit(AgentEvent.ThinkingDelta(chunk.text))
                        }
                        is ChatChunk.ToolCallDelta -> {
                            val id = chunk.id.ifBlank { "call_${UUID.randomUUID()}" }
                            argBuilders.getOrPut(id) { StringBuilder() }.append(chunk.argumentsFragment)
                            if (!chunk.name.isNullOrBlank()) callNames[id] = chunk.name
                        }
                        is ChatChunk.Done -> Unit
                        is ChatChunk.Error -> streamError = chunk.error
                    }
                }
                if (streamError != null) {
                    emit(AgentEvent.Failed(streamError.message ?: "Provider error"))
                    return@flow
                }

                val toolCalls = argBuilders.mapNotNull { (id, args) ->
                    val name = callNames[id] ?: return@mapNotNull null
                    Triple(id, name, args.toString())
                }
                if (toolCalls.isEmpty()) {
                    val finalText = text.toString()
                    emit(AgentEvent.Completed(finalText, steps))
                    if (spec.conversationId != null) {
                        chatRepository.addMessage(
                            MessageEntity(
                                conversationId = spec.conversationId,
                                role = "assistant",
                                content = finalText,
                            ),
                        )
                    }
                    return@flow
                }

                messages += ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = text.toString(),
                    toolCalls = toolCalls.map { (id, name, argsJson) -> ToolCall(id, name, argsJson) },
                )

                for ((callId, toolName, argsJson) in toolCalls) {
                    ensureActive()
                    emit(AgentEvent.ToolCallStarted(callId, toolName, argsJson))
                    val args = parseArgs(argsJson)
                    val path = args.str("path") ?: args.str("from")
                    val tool = toolRegistry.get(toolName)
                    if (tool == null) {
                        val message = "Unknown tool: $toolName"
                        auditLogger.log(toolName, argsJson, path, "UNKNOWN_TOOL", message, false, "agent")
                        emit(AgentEvent.ToolCallResult(callId, toolName, false, message))
                        messages += ChatMessage(
                            role = ChatRole.TOOL,
                            content = "Error: $message",
                            toolCallId = callId,
                        )
                        continue
                    }

                    val decision = permissionEngine.check(tool, args, path)
                    auditLogger.log(
                        toolName = tool.name,
                        argsJson = argsJson,
                        path = path,
                        permissionState = decision.javaClass.simpleName,
                        resultSummary = "permission check",
                        approved = false,
                        actor = "agent",
                    )
                    when (decision) {
                        is PermissionDecision.Deny -> {
                            emit(AgentEvent.ToolCallResult(callId, tool.name, false, "Denied: ${decision.reason}"))
                            messages += ChatMessage(
                                role = ChatRole.TOOL,
                                content = "Permission denied: ${decision.reason}",
                                toolCallId = callId,
                            )
                            continue
                        }
                        is PermissionDecision.Allow -> Unit
                        is PermissionDecision.Ask -> {
                            val deferred = CompletableDeferred<ApprovalDecision>()
                            pendingApprovals[callId] = deferred
                            emit(
                                AgentEvent.ApprovalRequested(
                                    callId = callId,
                                    toolName = tool.name,
                                    argsJson = argsJson,
                                    path = path,
                                    risk = tool.risk,
                                    diffPreview = decision.diffPreview,
                                )
                            )
                            val verdict = try {
                                withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() }
                            } finally {
                                pendingApprovals.remove(callId)
                            }
                            if (verdict == ApprovalDecision.APPROVE_ALWAYS) {
                                permissionEngine.setLevel(tool.permissionCategory, PermissionLevel.ALWAYS_ALLOW)
                            }
                            if (verdict != ApprovalDecision.APPROVE_ONCE && verdict != ApprovalDecision.APPROVE_ALWAYS) {
                                auditLogger.log(
                                    toolName = tool.name,
                                    argsJson = argsJson,
                                    path = path,
                                    permissionState = "ASK",
                                    resultSummary = "denied by user",
                                    approved = false,
                                    actor = "user",
                                )
                                emit(AgentEvent.ApprovalDenied(callId, tool.name))
                                messages += ChatMessage(
                                    role = ChatRole.TOOL,
                                    content = "User denied this tool call.",
                                    toolCallId = callId,
                                )
                                continue
                            }
                        }
                    }

                    val result = try {
                        tool.execute(args, ctx)
                    } catch (e: Exception) {
                        ToolResult(false, "", "Execution failed: ${e.message}")
                    }
                    auditLogger.log(
                        toolName = tool.name,
                        argsJson = argsJson,
                        path = path,
                        permissionState = "EXECUTED",
                        resultSummary = (result.output.ifBlank { result.error ?: "" }),
                        approved = true,
                        actor = "agent",
                    )
                    emit(
                        AgentEvent.ToolCallResult(
                            callId = callId,
                            toolName = tool.name,
                            success = result.success,
                            output = result.output.ifBlank { result.error ?: "" },
                        )
                    )
                    val toolContent = if (result.success) {
                        result.output.ifBlank { "(no output)" }
                    } else {
                        "Error: ${result.error ?: result.output}"
                    }
                    messages += ChatMessage(role = ChatRole.TOOL, content = toolContent, toolCallId = callId)
                }
            }
            emit(AgentEvent.Failed("Max steps ($maxSteps) reached without final answer"))
        } catch (e: CancellationException) {
            emit(AgentEvent.Cancelled)
            throw e
        } catch (e: Exception) {
            emit(AgentEvent.Failed(e.message ?: "Unknown error"))
        }
    }

    private suspend fun buildToolContext(spec: AgentSessionSpec): ToolContext {
        val entities = workspaceRepository.observeWorkspaces().first()
        var roots = entities.map { e ->
            WorkspaceRoot(
                id = e.id,
                name = e.name,
                uri = e.uriString,
                ioFile = if (e.type == "internal") File(e.uriString) else null,
            )
        }
        if (roots.isEmpty()) {
            val dir = File(context.filesDir, "workspace").apply { mkdirs() }
            roots = listOf(WorkspaceRoot("internal", "Internal workspace", dir.absolutePath, dir))
        }
        val activeRoot = spec.workspaceId?.let { id -> roots.find { it.id == id } }
            ?: entities.find { it.isDefault }?.let { e -> roots.find { it.id == e.id } }
            ?: roots.first()
        return ToolContext(workspaceRoots = roots, activeRoot = activeRoot, auditLogger = auditLogger)
    }

    private companion object {
        /** Approvals time out after 10 minutes and are treated as denied. */
        const val APPROVAL_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
