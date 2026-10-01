package com.nova.ai.agent

import java.io.File

/**
 * Resolves user-supplied relative paths against a workspace root, blocking directory traversal.
 *
 * Security notes:
 * - Absolute paths (Unix-style or Windows drive letters) are rejected outright.
 * - The candidate is resolved through [File.getCanonicalFile]; symlinks inside the workspace that
 *   point outside are therefore caught by the containment check, not just ".." segments.
 * - The canonical target must equal the canonical root or live strictly beneath it.
 * - Callers must catch [SecurityException] and surface it as a tool failure, never as a crash.
 */
object PathValidator {

    /**
     * Resolves [relativePath] against [root].
     *
     * @throws IllegalArgumentException if [relativePath] is blank.
     * @throws SecurityException if the path is absolute or escapes [root].
     */
    @Throws(SecurityException::class)
    fun resolve(root: File, relativePath: String): File {
        require(relativePath.isNotBlank()) { "Path must not be blank" }
        val normalized = relativePath.trim().replace('\\', '/')
        if (normalized.startsWith("/")) {
            throw SecurityException("Path escapes workspace: $relativePath (absolute paths are not allowed)")
        }
        if (normalized.length >= 2 && normalized[1] == ':') {
            throw SecurityException("Path escapes workspace: $relativePath (drive-letter paths are not allowed)")
        }
        val rootCanonical = root.canonicalFile
        val target = File(rootCanonical, normalized).canonicalFile
        val rootPath = rootCanonical.path
        val inside = target.path == rootPath || target.path.startsWith(rootPath + File.separator)
        if (!inside) {
            throw SecurityException("Path escapes workspace: $relativePath")
        }
        return target
    }
}
