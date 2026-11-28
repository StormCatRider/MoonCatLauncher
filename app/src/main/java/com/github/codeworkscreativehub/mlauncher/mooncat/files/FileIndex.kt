package com.github.codeworkscreativehub.mlauncher.mooncat.files

/** One entry returned by a folder listing. [id] identifies it to the document provider. */
data class FileEntry(val id: String, val name: String, val mimeType: String, val isDirectory: Boolean)

/**
 * Builds a name-only index of the folders the user authorized. Only names, ids and types are kept,
 * in memory; file contents are never read. Limits keep a huge folder from slowing the drawer.
 */
object FileIndex {
    const val MAX_FOLDERS = 5
    const val MAX_DEPTH = 6
    const val MAX_FILES = 5000
    const val MAX_LISTINGS = 1500
    /** How long an index stays fresh before the drawer rebuilds it in the background. */
    const val MAX_AGE_MILLIS = 10 * 60 * 1000L

    /**
     * Walks [rootId] breadth first. [list] returns the children of a directory and may throw;
     * a failing directory is skipped so one unreadable folder does not hide the others.
     */
    fun walk(
        rootId: String,
        list: (String) -> List<FileEntry>,
        maxDepth: Int = MAX_DEPTH,
        maxFiles: Int = MAX_FILES,
        maxListings: Int = MAX_LISTINGS
    ): List<FileEntry> {
        val files = mutableListOf<FileEntry>()
        val visited = mutableSetOf(rootId)
        var level = listOf(rootId)
        var depth = 0
        var listings = 0
        while (level.isNotEmpty() && depth <= maxDepth && files.size < maxFiles) {
            val next = mutableListOf<String>()
            for (dir in level) {
                if (files.size >= maxFiles || listings >= maxListings) return files
                listings++
                val children = try {
                    list(dir)
                } catch (_: Exception) {
                    emptyList()
                }
                for (child in children) {
                    if (child.isDirectory) {
                        if (visited.add(child.id)) next += child.id
                    } else if (child.name.isNotBlank()) {
                        files += child
                        if (files.size >= maxFiles) return files
                    }
                }
            }
            level = next
            depth++
        }
        return files
    }
}
