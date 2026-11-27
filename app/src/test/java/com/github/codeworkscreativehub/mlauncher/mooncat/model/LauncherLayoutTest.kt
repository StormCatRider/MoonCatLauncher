package com.github.codeworkscreativehub.mlauncher.mooncat.model

import org.junit.Assert.*
import org.junit.Test

class LauncherLayoutTest {
    private val home = LauncherPage("home", "Home", PageType.HOME, 0)
    private val folder = LauncherItem("folder", "Games", ItemType.FOLDER, home.id, position = 0)
    private val nested = LauncherItem("nested", "Strategy", ItemType.FOLDER, home.id, folder.id, 0)
    private val app = LauncherItem("app", "Game", ItemType.APPLICATION, home.id, position = 1)

    @Test fun defaultPagesAndDisplay() {
        assertEquals(listOf(PageType.HOME, PageType.WIDGETS), LauncherLayout.initialPages().map { it.type })
        assertEquals(DisplayMode.TEXT_ONLY, LauncherLayout.resolveDisplayMode(app, null, home))
    }

    @Test fun nestedFoldersAndCycles() {
        val items = listOf(folder, nested, app)
        assertTrue(LauncherLayout.canMoveInto(app, nested.id, items))
        assertFalse(LauncherLayout.canMoveInto(folder, nested.id, items))
        assertFalse(LauncherLayout.canMoveInto(folder, folder.id, items))
        assertFalse(LauncherLayout.canMoveInto(app, "missing", items))
        assertFalse(LauncherLayout.canMoveInto(app, app.id, items))
        assertFalse(LauncherLayout.canMoveInto(app, nested.id, items + folder))
    }

    @Test fun rejectCrossPageAndCorruptChains() {
        val other = folder.copy(pageId = "widgets")
        assertFalse(LauncherLayout.canMoveInto(app, other.id, listOf(other, app)))
        val cyclic = folder.copy(parentFolderId = nested.id)
        assertFalse(LauncherLayout.canMoveInto(app, nested.id, listOf(cyclic, nested, app)))
        assertFalse(LauncherLayout.canMoveInto(app.copy(type = ItemType.WIDGET), folder.id, listOf(folder)))
    }

    @Test fun displayOverrides() {
        val page = home.copy(displayMode = DisplayMode.ICON_ONLY)
        val parent = folder.copy(displayMode = DisplayMode.ICON_AND_TEXT)
        assertEquals(DisplayMode.ICON_ONLY, LauncherLayout.resolveDisplayMode(app, null, page))
        assertEquals(DisplayMode.ICON_AND_TEXT, LauncherLayout.resolveDisplayMode(app, parent, page))
        assertEquals(DisplayMode.MONOCHROME_AND_TEXT, LauncherLayout.resolveDisplayMode(app.copy(displayMode = DisplayMode.MONOCHROME_AND_TEXT), parent, page))
    }
}
