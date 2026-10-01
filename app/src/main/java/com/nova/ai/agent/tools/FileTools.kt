package com.nova.ai.agent.tools

import com.nova.ai.agent.AgentTool
import com.nova.ai.agent.PathValidator
import com.nova.ai.agent.PermissionCategory
import com.nova.ai.agent.RiskLevel
import com.nova.ai.agent.ToolContext
import com.nova.ai.agent.ToolResult
import java.io.File

/**
 * The 8 built-in file tools. Every tool takes paths relative to the active workspace root and
 * resolves them through [PathValidator]; traversal attempts become tool failures, not crashes.
 *
 * Note: [WriteFileTool] uses [PermissionCategory.FILE_WRITE] for both create and overwrite.
 * [PermissionCategory.FILE_CREATE] exists for parity/future split but is not used yet.
 */

private const val SAF_ONLY_MESSAGE =
    "No local workspace available (SAF-only workspace); file tools need the internal workspace"

/** Shared helpers for file tools. */
abstract class BaseFileTool : AgentTool {
    protected fun activeDir(ctx: ToolContext): File? = ctx.activeRoot.ioFile

    /** Resolves [path] or returns a failure [ToolResult] when the root/path is unusable. */
    protected fun resolveTarget(ctx: ToolContext, path: String): Result<File> {
        val root = activeDir(ctx) ?: return Result.failure(IllegalStateException(SAF_ONLY_MESSAGE))
        return try {
            Result.success(PathValidator.resolve(root, path.ifBlank { "." }))
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Security: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }
}

/** Lists directory entries as "name (dir|file, N bytes)", capped at 500 entries. */
class ListDirectoryTool : BaseFileTool() {
    override val name = "list_directory"
    override val description = "List files and folders in a workspace directory. Path is relative to the workspace root; empty means the root."
    override val parametersSchemaJson = """
        {"type":"object","properties":{"path":{"type":"string","description":"Directory path relative to workspace root. Defaults to the root."}},"required":[]}
    """.trimIndent()
    override val permissionCategory = PermissionCategory.FILE_READ
    override val risk = RiskLevel.LOW

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val dir = resolveTarget(ctx, args.str("path").orEmpty()).getOrElse {
            return ToolResult(false, "", it.message ?: "Invalid path")
        }
        if (!dir.exists()) return ToolResult(false, "", "Directory does not exist: ${args.str("path")}")
        if (!dir.isDirectory) return ToolResult(false, "", "Not a directory: ${args.str("path")}")
        val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.take(500)
            ?.map { f -> "${f.name} (${if (f.isDirectory) "dir" else "file, ${f.length()} bytes"})" }
            ?: emptyList()
        return ToolResult(true, entries.joinToString("\n").ifBlank { "(empty directory)" })
    }
}

/** Searches file names, optionally grepping file contents (text files up to 1 MB, 200 files walked). */
class SearchFilesTool : BaseFileTool() {
    override val name = "search_files"
    override val description = "Search for files by name, optionally also searching inside file contents."
    override val parametersSchemaJson = """
        {"type":"object","properties":{
          "path":{"type":"string","description":"Directory to search, relative to workspace root. Defaults to the root."},
          "query":{"type":"string","description":"Search text matched against file names (and contents when contentSearch is true)."},
          "contentSearch":{"type":"boolean","description":"Also grep inside text files up to 1 MB. Default false."},
          "maxResults":{"type":"integer","description":"Maximum results. Default 50."}
        },"required":["query"]}
    """.trimIndent()
    override val permissionCategory = PermissionCategory.FILE_READ
    override val risk = RiskLevel.LOW

    companion object {
        private const val MAX_WALKED = 200
        private const val MAX_CONTENT_BYTES = 1024L * 1024L
    }

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val query = args.str("query")?.takeIf { it.isNotBlank() }
            ?: return ToolResult(false, "", "Missing required argument: query")
        val dir = resolveTarget(ctx, args.str("path").orEmpty()).getOrElse {
            return ToolResult(false, "", it.message ?: "Invalid path")
        }
        if (!dir.isDirectory) return ToolResult(false, "", "Not a directory: ${args.str("path")}")
        val contentSearch = args.bool("contentSearch", false)
        val maxResults = args.int("maxResults", 50).coerceIn(1, 500)
        val root = activeDir(ctx) ?: return ToolResult(false, "", SAF_ONLY_MESSAGE)

        val results = mutableListOf<String>()
        var walked = 0
        for (file in dir.walkTopDown().maxDepth(20)) {
            if (!file.isFile) continue
            if (walked >= MAX_WALKED) break
            walked++
            val rel = try {
                file.relativeTo(root).path.replace('\\', '/')
            } catch (e: IllegalArgumentException) {
                file.name
            }
            var matched = false
            if (file.name.contains(query, ignoreCase = true)) {
                results.add(rel)
                matched = true
            }
            if (!matched && contentSearch && file.length() <= MAX_CONTENT_BYTES) {
                try {
                    file.bufferedReader().useLines { lines ->
                        var lineNo = 0
                        for (line in lines) {
                            lineNo++
                            if (line.contains(query, ignoreCase = true)) {
                                results.add("$rel:$lineNo: ${line.trim().take(160)}")
                                if (results.size >= maxResults) break
                            }
                        }
                    }
                } catch (e: Exception) {
                    // unreadable/binary file: skip
                }
            }
            if (results.size >= maxResults) break
        }
        return ToolResult(true, results.joinToString("\n").ifBlank { "(no matches)" })
    }
}

/** Reads a slice of a text file with line numbers. Files larger than 2 MB are rejected. */
class ReadFileTool : BaseFileTool() {
    override val name = "read_file"
    override val description = "Read a text file with line numbers. Use offset/limit to page through large files."
    override val parametersSchemaJson = """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File path relative to workspace root."},
          "offset":{"type":"integer","description":"First line (0-based). Default 0."},
          "limit":{"type":"integer","description":"Maximum lines. Default 200."}
        },"required":["path"]}
    """.trimIndent()
    override val permissionCategory = PermissionCategory.FILE_READ
    override val risk = RiskLevel.LOW

    companion object {
        private const val MAX_BYTES = 2L * 1024L * 1024L
    }

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val path = args.str("path")?.takeIf { it.isNotBlank() }
            ?: return ToolResult(false, "", "Missing required argument: path")
        val file = resolveTarget(ctx, path).getOrElse {
            return ToolResult(false, "", it.message ?: "Invalid path")
        }
        if (!file.isFile) return ToolResult(false, "", "Not a file: $path")
        if (file.length() > MAX_BYTES) {
            return ToolResult(false, "", "File is ${file.length()} bytes (over 2 MB). Read it in slices with offset/limit.")
        }
        val offset = args.int("offset", 0).coerceAtLeast(0)
        val limit = args.int("limit", 200).coerceIn(1, 1000)
        val slice = try {
            file.readLines().drop(offset).take(limit)
        } catch (e: Exception) {
            return ToolResult(false, "", "Could not read file: ${e.message}")
        }
        val numbered = slice.mapIndexed { i, line -> "${offset + i + 1}: $line" }
        return ToolResult(true, numbered.joinToString("\n").ifBlank { "(empty file)" })
    }
}

/**
 * Creates or overwrites a file (parent directories are created).
 * Permission category [PermissionCategory.FILE_WRITE] covers both create and overwrite.
 */
class WriteFileTool : BaseFileTool() {
    override val name = "write_file"
    override val description = "Create a new file or overwrite an existing one. Parent directories are created automatically."
    override val parametersSchemaJson = """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File path relative to workspace root."},
          "content":{"type":"string","description":"Full file content."}
        },"required":["path","content"]}
    """.trimIndent()
    override val permissionCategory = PermissionCategory.FILE_WRITE
    override val risk = RiskLevel.MEDIUM

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val path = args.str("path")?.takeIf { it.isNotBlank() }
            ?: return ToolResult(false, "", "Missing required argument: path")
        val content = args.str("content") ?: return ToolResult(false, "", "Missing required argument: content")
        val file = resolveTarget(ctx, path).getOrElse {
            return ToolResult(false, "", it.message ?: "Invalid path")
        }
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult(true, "Wrote ${content.toByteArray().size} bytes to $path")
        } catch (e: Exception) {
            ToolResult(false, "", "Could not write file: ${e.message}")
        }
    }
}

/** Replaces text in a file; refuses ambiguous matches unless replaceAll is set. */
class EditFileTool : BaseFileTool() {
    override val name = "edit_file"
    override val description = "Replace text inside a file. Fails when oldText is missing or matches more than once unless replaceAll is true."
    override val parametersSchemaJson = """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File path relative to workspace root."},
          "oldText":{"type":"string","description":"Exact text to replace."},
          "newText":{"type":"string","description":"Replacement text."},
          "replaceAll":{"type":"boolean","description":"Replace every occurrence. Default false."}
        },"required":["path","oldText","newText"]}
    """.trimIndent()
    override val permissionCategory = PermissionCategory.FILE_EDIT
    override val risk = RiskLevel.MEDIUM

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val path = args.str("path")?.takeIf { it.isNotBlank() }
            ?: return ToolResult(false, "", "Missing required argument: path")
        val oldText = args.str("oldText") ?: return ToolResult(false, "", "Missing required argument: oldText")
        val newText = args.str("newText") ?: return ToolResult(false, "", "Missing required argument: newText")
        if (oldText.isEmpty()) return ToolResult(false, "", "oldText must not be empty")
        val replaceAll = args.bool("replaceAll", false)
        val file = resolveTarget(ctx, path).getOrElse {
            return ToolResult(false, "", it.message ?: "Invalid path")
        }
        if (!file.isFile) return ToolResult(false, "", "Not a file: $path")
        val current = try {
            file.readText()
        } catch (e: Exception) {
            return ToolResult(false, "", "Could not read file: ${e.message}")
        }
        var count = 0
        var idx = current.indexOf(oldText)
        while (idx >= 0) {
            count++
            idx = current.indexOf(oldText, idx + oldText.length)
        }
        if (count == 0) return ToolResult(false, "", "oldText not found in $path")
        if (count > 1 && !replaceAll) {
            return ToolResult(false, "", "oldText matches $count times in $path; be more specific or set replaceAll=true")
        }
        val updated = if (replaceAll) current.replace(oldText, newText) else current.replaceFirst(oldText, newText)
        return try {
            file.writeText(updated)
            val applied = if (replaceAll) count else 1
            ToolResult(true, "Replaced $applied occurrence(s) in $path")
        } catch (e: Exception) {
            ToolResult(false, "", "Could not write file: ${e.message}")
        }
    }
}

/** Deletes a file; directories require recursive=true (treated as dangerous by the permission engine). */
class DeleteFileTool : BaseFileTool() {
    override val name = "delete_file"
    override val description = "Delete a file. Deleting a directory requires recursive=true."
    override val parametersSchemaJson = """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File or directory path relative to workspace root."},
          "recursive":{"type":"boolean","description":"Allow deleting directories with all contents. Default false."}
        },"required":["path"]}
    """.trimIndent()
    override val permissionCategory = PermissionCategory.FILE_DELETE
    override val risk = RiskLevel.HIGH

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val path = args.str("path")?.takeIf { it.isNotBlank() }
            ?: return ToolResult(false, "", "Missing required argument: path")
        val recursive = args.bool("recursive", false)
        val target = resolveTarget(ctx, path).getOrElse {
            return ToolResult(false, "", it.message ?: "Invalid path")
        }
        if (!target.exists()) return ToolResult(false, "", "Does not exist: $path")
        if (target.isDirectory && !recursive) {
            return ToolResult(false, "", "$path is a directory; set recursive=true to delete it with all contents")
        }
        return try {
            if (target.isDirectory) {
                var count = 0
                target.walkBottomUp().forEach { count++; it.delete() }
                ToolResult(true, "Deleted directory and $count entr${if (count == 1) "y" else "ies"}: $path")
            } else {
                if (target.delete()) ToolResult(true, "Deleted file: $path")
                else ToolResult(false, "", "Could not delete: $path")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Could not delete: ${e.message}")
        }
    }
}

/** Renames or moves a file/directory inside the workspace. */
class RenameFileTool : BaseFileTool() {
    override val name = "rename_file"
    override val description = "Rename or move a file or directory inside the workspace."
    override val parametersSchemaJson = """
        {"type":"object","properties":{
          "from":{"type":"string","description":"Existing path relative to workspace root."},
          "to":{"type":"string","description":"New path relative to workspace root."}
        },"required":["from","to"]}
    """.trimIndent()
    override val permissionCategory = PermissionCategory.FILE_EDIT
    override val risk = RiskLevel.MEDIUM

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val from = args.str("from")?.takeIf { it.isNotBlank() }
            ?: return ToolResult(false, "", "Missing required argument: from")
        val to = args.str("to")?.takeIf { it.isNotBlank() }
            ?: return ToolResult(false, "", "Missing required argument: to")
        val src = resolveTarget(ctx, from).getOrElse {
            return ToolResult(false, "", it.message ?: "Invalid path")
        }
        val dst = resolveTarget(ctx, to).getOrElse {
            return ToolResult(false, "", it.message ?: "Invalid path")
        }
        if (!src.exists()) return ToolResult(false, "", "Does not exist: $from")
        if (dst.exists()) return ToolResult(false, "", "Destination already exists: $to")
        return try {
            dst.parentFile?.mkdirs()
            if (src.renameTo(dst)) ToolResult(true, "Renamed $from to $to")
            else ToolResult(false, "", "Could not rename $from to $to")
        } catch (e: Exception) {
            ToolResult(false, "", "Could not rename: ${e.message}")
        }
    }
}

/** Creates a directory (including parents). */
class CreateDirectoryTool : BaseFileTool() {
    override val name = "create_directory"
    override val description = "Create a directory inside the workspace, including any missing parents."
    override val parametersSchemaJson = """
        {"type":"object","properties":{
          "path":{"type":"string","description":"Directory path relative to workspace root."}
        },"required":["path"]}
    """.trimIndent()
    override val permissionCategory = PermissionCategory.FOLDER_CREATE
    override val risk = RiskLevel.LOW

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val path = args.str("path")?.takeIf { it.isNotBlank() }
            ?: return ToolResult(false, "", "Missing required argument: path")
        val dir = resolveTarget(ctx, path).getOrElse {
            return ToolResult(false, "", it.message ?: "Invalid path")
        }
        return try {
            if (dir.isDirectory) ToolResult(true, "Directory already exists: $path")
            else if (dir.mkdirs()) ToolResult(true, "Created directory: $path")
            else ToolResult(false, "", "Could not create directory: $path")
        } catch (e: Exception) {
            ToolResult(false, "", "Could not create directory: ${e.message}")
        }
    }
}
