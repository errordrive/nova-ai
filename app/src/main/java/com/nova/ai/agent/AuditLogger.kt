package com.nova.ai.agent

import com.nova.ai.data.local.entity.AuditRecordEntity
import com.nova.ai.domain.repository.AuditRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes audit records for every tool call attempt and outcome.
 *
 * The audit trail is the persistence story for agent runs: tool call rows cover what ran, so
 * [AgentOrchestrator] intentionally does not persist `AgentSessionEntity`/`ToolCallEntity` rows.
 */
@Singleton
class AuditLogger @Inject constructor(
    private val auditRepository: AuditRepository,
) {
    /**
     * Records one audit event.
     *
     * @param permissionState short label such as Allow, Ask, Deny, EXECUTED or UNKNOWN_TOOL.
     * @param resultSummary short outcome text; truncated to 500 chars before insert.
     * @param approved whether the call was approved to run.
     * @param actor who is responsible: "agent" for autonomous steps, "user" for approval verdicts.
     */
    suspend fun log(
        toolName: String,
        argsJson: String,
        path: String?,
        permissionState: String,
        resultSummary: String,
        approved: Boolean,
        actor: String,
    ) {
        auditRepository.log(
            AuditRecordEntity(
                timestamp = System.currentTimeMillis(),
                toolName = toolName,
                argumentsJson = argsJson,
                path = path,
                permissionState = permissionState,
                resultSummary = resultSummary.take(500),
                approved = approved,
                actor = actor,
            )
        )
    }
}
