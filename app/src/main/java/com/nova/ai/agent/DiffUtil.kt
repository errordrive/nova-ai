package com.nova.ai.agent

/**
 * Minimal line-based diff used for approval previews.
 *
 * Multiset-aware: duplicate lines are matched by count, so a line appearing twice in the old
 * text and once in the new text counts as one removal. Result lists are capped at 200 lines
 * each to keep approval dialogs cheap.
 */
object DiffUtil {
    private const val MAX_LINES = 200

    /**
     * Computes [DiffResult] between [oldText] and [newText].
     */
    fun diff(oldText: String, newText: String): DiffResult {
        val oldLines = if (oldText.isEmpty()) emptyList() else oldText.lines()
        val newLines = if (newText.isEmpty()) emptyList() else newText.lines()
        val oldCounts = oldLines.groupingBy { it }.eachCount()
        val newCounts = newLines.groupingBy { it }.eachCount()

        val removed = mutableListOf<String>()
        for ((line, count) in oldCounts) {
            val surplus = count - (newCounts[line] ?: 0)
            repeat(surplus.coerceAtLeast(0)) { removed.add(line) }
        }
        val added = mutableListOf<String>()
        for ((line, count) in newCounts) {
            val surplus = count - (oldCounts[line] ?: 0)
            repeat(surplus.coerceAtLeast(0)) { added.add(line) }
        }
        return DiffResult(
            added = added.take(MAX_LINES),
            removed = removed.take(MAX_LINES),
            oldLineCount = oldLines.size,
            newLineCount = newLines.size,
        )
    }
}
