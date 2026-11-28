package com.github.codeworkscreativehub.mlauncher.mooncat.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileIndexTest {
    private fun dir(id: String) = FileEntry(id, id, "vnd.android.document/directory", true)
    private fun file(id: String) = FileEntry(id, "$id.pdf", "application/pdf", false)

    private val tree = mapOf(
        "root" to listOf(file("a"), dir("docs"), dir("broken")),
        "docs" to listOf(file("b"), dir("deep")),
        "deep" to listOf(file("c"), dir("root")), // a link back to the root must not loop
        "broken" to emptyList()
    )

    private fun lister(id: String): List<FileEntry> {
        if (id == "broken") throw SecurityException("permission revoked")
        return tree[id].orEmpty()
    }

    @Test
    fun collectsFilesFromAllLevelsAndSurvivesErrorsAndCycles() {
        val names = FileIndex.walk("root", ::lister).map { it.name }
        assertEquals(listOf("a.pdf", "b.pdf", "c.pdf"), names)
    }

    @Test
    fun respectsDepthAndFileLimits() {
        assertEquals(listOf("a.pdf"), FileIndex.walk("root", ::lister, maxDepth = 0).map { it.name })
        assertEquals(listOf("a.pdf", "b.pdf"), FileIndex.walk("root", ::lister, maxDepth = 1).map { it.name })
        assertEquals(2, FileIndex.walk("root", ::lister, maxFiles = 2).size)
        assertEquals(listOf("a.pdf"), FileIndex.walk("root", ::lister, maxListings = 1).map { it.name })
    }
}
