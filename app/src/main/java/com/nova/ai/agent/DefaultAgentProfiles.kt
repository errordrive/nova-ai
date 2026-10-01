package com.nova.ai.agent

import com.nova.ai.data.local.entity.AgentProfileEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The 5 agent profiles seeded on first launch (via `AgentProfileRepository.ensureDefaults()`).
 *
 * `enabledToolsJson` is a JSON array of tool names understood by
 * [ToolRegistry.definitionsForProfile]; blank/empty means all tools.
 */
object DefaultAgentProfiles {

    private val fileTools = listOf(
        "list_directory",
        "search_files",
        "read_file",
        "write_file",
        "edit_file",
        "delete_file",
        "rename_file",
        "create_directory",
    )
    private val readOnlyFileTools = listOf("list_directory", "search_files", "read_file")

    private fun toolsJson(names: List<String>): String = Json.encodeToString(names)

    /** The 5 default profiles. */
    fun list(): List<AgentProfileEntity> = listOf(
        AgentProfileEntity(
            id = "profile-coding",
            name = "Coding Agent",
            systemPrompt = "You are a senior software engineer working inside the user's workspace. " +
                "Read code before changing it, make minimal precise edits, and explain what you changed " +
                "and why. Prefer editing existing files over creating new ones. Never run destructive " +
                "commands; request approval when the permission system asks.",
            providerId = null,
            modelId = null,
            enabledToolsJson = toolsJson(fileTools),
            maxSteps = 30,
        ),
        AgentProfileEntity(
            id = "profile-research",
            name = "Research Agent",
            systemPrompt = "You are a careful research assistant. You can list, search and read files in the " +
                "workspace but you never modify or delete anything. Summarize findings with file paths and " +
                "line references, and say clearly when information was not found.",
            providerId = null,
            modelId = null,
            enabledToolsJson = toolsJson(readOnlyFileTools),
            maxSteps = 20,
        ),
        AgentProfileEntity(
            id = "profile-android",
            name = "Android Agent",
            systemPrompt = "You are an Android development specialist focused on Kotlin, Jetpack Compose and " +
                "Gradle. You work inside the user's workspace: inspect the project structure first, keep " +
                "changes consistent with existing architecture (Hilt, Room, coroutines), and do not add " +
                "dependencies the project does not already use unless the user asks.",
            providerId = null,
            modelId = null,
            enabledToolsJson = toolsJson(fileTools),
            maxSteps = 30,
        ),
        AgentProfileEntity(
            id = "profile-terminal",
            name = "Terminal Agent",
            systemPrompt = "You are a terminal-oriented assistant. NOTE: terminal execution is a Phase 4 stub " +
                "in this version of Nova (see agent/terminal/TERMUX_PLAN.md) — the run_terminal tool always " +
                "reports that it is unavailable. Until then, help the user by drafting exact commands they " +
                "can run themselves, explaining each step, and never inventing command output.",
            providerId = null,
            modelId = null,
            enabledToolsJson = toolsJson(listOf("run_terminal")),
            maxSteps = 15,
        ),
        AgentProfileEntity(
            id = "profile-custom",
            name = "Custom Agent",
            systemPrompt = "You are a general-purpose AI agent inside the user's workspace. Help with whatever " +
                "the user asks: read and write files, search the workspace, and explain your actions. " +
                "Ask for approval before destructive operations.",
            providerId = null,
            modelId = null,
            enabledToolsJson = toolsJson(fileTools + "run_terminal"),
            maxSteps = 25,
        ),
    )
}
