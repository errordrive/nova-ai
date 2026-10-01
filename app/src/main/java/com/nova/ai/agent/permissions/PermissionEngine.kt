package com.nova.ai.agent.permissions

import com.nova.ai.agent.AgentTool
import com.nova.ai.agent.DiffUtil
import com.nova.ai.agent.DiffResult
import com.nova.ai.agent.PathValidator
import com.nova.ai.agent.PermissionCategory
import com.nova.ai.agent.PermissionDecision
import com.nova.ai.agent.PermissionLevel
import com.nova.ai.agent.RiskLevel
import com.nova.ai.agent.terminal.TerminalTool
import com.nova.ai.agent.tools.bool
import com.nova.ai.agent.tools.str
import com.nova.ai.data.local.entity.PermissionRuleEntity
import com.nova.ai.domain.repository.PermissionRepository
import com.nova.ai.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a tool call may run, needs user approval, or is denied.
 *
 * Rules (in order):
 * 1. Stored level `DENY` for the tool's [PermissionCategory] always denies.
 * 2. Dangerous operations always ask, even when the level would allow them.
 * 3. Level `ASK_EVERY_TIME` asks.
 * 4. Levels `ALLOW_SESSION` / `ALWAYS_ALLOW` allow.
 *
 * Unknown or unparsable stored levels fall back to [PermissionLevel.ASK_EVERY_TIME].
 *
 * Note: besides [PermissionRepository], this also reads [WorkspaceRepository] on a best-effort
 * basis to build diff previews for edit/write approvals from the first internal workspace root.
 */
@Singleton
class PermissionEngine @Inject constructor(
    private val permissionRepository: PermissionRepository,
    private val workspaceRepository: WorkspaceRepository,
) {

    /**
     * Checks a planned tool call. [path] is the raw relative path argument when the tool takes one.
     */
    suspend fun check(tool: AgentTool, args: Map<String, Any?>, path: String?): PermissionDecision {
        val level = storedLevel(tool.permissionCategory)
        if (level == PermissionLevel.DENY) {
            return PermissionDecision.Deny("${tool.permissionCategory.name} is disabled in Permissions")
        }
        val diffPreview = if (path != null &&
            (tool.permissionCategory == PermissionCategory.FILE_EDIT ||
                tool.permissionCategory == PermissionCategory.FILE_WRITE)
        ) {
            buildDiffPreview(tool, args, path)
        } else {
            null
        }
        if (isDangerous(tool, args, path)) {
            return PermissionDecision.Ask(
                "Dangerous operation requires explicit approval: ${tool.name}.",
                diffPreview,
            )
        }
        if (level == PermissionLevel.ASK_EVERY_TIME) {
            return PermissionDecision.Ask(
                "Permission for ${tool.name} is set to ask every time.",
                diffPreview,
            )
        }
        return PermissionDecision.Allow
    }

    /**
     * Heuristic used for UI hints and the always-ask gate. Public so the UI can pre-warn.
     */
    fun isDangerous(tool: AgentTool, args: Map<String, Any?>, path: String?): Boolean {
        if (tool.risk == RiskLevel.CRITICAL) return true
        if (tool.permissionCategory == PermissionCategory.FILE_DELETE && args.bool("recursive", false)) return true
        if (tool is TerminalTool) {
            val command = args.str("command").orEmpty()
            if (SHELL_DANGEROUS.containsMatchIn(command)) return true
        }
        return false
    }

    /** Persists a new [PermissionLevel] for a category (e.g. after "Always allow"). */
    suspend fun setLevel(category: PermissionCategory, level: PermissionLevel) {
        permissionRepository.upsertRule(
            PermissionRuleEntity(category = category.name, level = level.name),
        )
    }

    private suspend fun storedLevel(category: PermissionCategory): PermissionLevel {
        val stored = try {
            permissionRepository.getRule(category.name)?.level
        } catch (e: Exception) {
            null
        }
        return stored?.let { runCatching { PermissionLevel.valueOf(it) }.getOrNull() }
            ?: PermissionLevel.ASK_EVERY_TIME
    }

    /**
     * Best-effort diff preview: reads the current file (if any) from the first internal workspace
     * root and diffs it against what the tool would write. Returns null when anything is unknown.
     */
    private suspend fun buildDiffPreview(tool: AgentTool, args: Map<String, Any?>, path: String): DiffResult? {
        return try {
            val rootFile = workspaceRepository.observeWorkspaces().first()
                .firstOrNull { it.type == "internal" }
                ?.let { File(it.uriString) }
                ?: return null
            val file = PathValidator.resolve(rootFile, path)
            val oldText = if (file.isFile) file.readText() else ""
            val newText = when (tool.name) {
                "write_file" -> args.str("content") ?: return null
                "edit_file" -> {
                    val old = args.str("oldText") ?: return null
                    val replacement = args.str("newText") ?: return null
                    if (args.bool("replaceAll", false)) oldText.replace(old, replacement)
                    else oldText.replaceFirst(old, replacement)
                }
                else -> return null
            }
            DiffUtil.diff(oldText, newText)
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        /** Shell patterns that are always treated as dangerous: recursive delete, mkfs, fork bombs, raw disk writes. */
        val SHELL_DANGEROUS = Regex("""rm\s+-[a-z]*r|mkfs(\.|$)|\{\s*:\s*:\s*&\s*\}|:\(\)|dd\s+[^|;&]*of=/dev/""")
    }
}
