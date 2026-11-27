package com.github.codeworkscreativehub.mlauncher.mooncat.model

import com.squareup.moshi.Moshi
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

    @Test fun nestedFoldersRoundTripWithProfileIdentity() {
        val child = app.copy(
            parentFolderId = nested.id,
            packageName = "example.game",
            activityName = "example.game.Main",
            userSerial = 42
        )
        val snapshot = LayoutSnapshot(items = listOf(folder, nested, child))
        val adapter = Moshi.Builder().build().adapter(LayoutSnapshot::class.java)
        assertTrue(snapshot.isValid())
        assertEquals(snapshot, adapter.fromJson(adapter.toJson(snapshot)))
        assertFalse(snapshot.copy(items = listOf(folder, child)).isValid())
    }
    @Test fun movesSubtreeAndReordersWithoutDroppingItems() {
        val sibling = folder.copy(id = "other", position = 2)
        val child = app.copy(parentFolderId = nested.id, packageName = "example.app", activityName = "Main")
        val peer = child.copy(id = "peer", position = 2)
        val snapshot = LayoutSnapshot(items = listOf(folder, nested, sibling, child, peer))
        val moved = snapshot.moveFolderItem(nested.id, sibling.id)
        assertTrue(moved.isValid())
        assertEquals(snapshot.items.map { it.id }, moved.items.map { it.id })
        assertEquals(sibling.id, moved.items.first { it.id == nested.id }.parentFolderId)
        assertEquals(child, moved.items.first { it.id == child.id })
        val reordered = moved.moveFolderItem(peer.id, nested.id, child.id)
        assertEquals(listOf(peer.id, child.id), reordered.items.filter { it.parentFolderId == nested.id }
            .sortedBy { it.position }.map { it.id })
    }

    @Test fun rejectsMovesThatWouldCreateCyclesOrUseUnrelatedAnchors() {
        val descendant = nested.copy(id = "descendant", parentFolderId = nested.id)
        val snapshot = LayoutSnapshot(items = listOf(folder, nested, descendant))
        assertThrows(IllegalArgumentException::class.java) { snapshot.moveFolderItem(nested.id, descendant.id) }
        assertThrows(IllegalArgumentException::class.java) { snapshot.moveFolderItem(descendant.id, folder.id, folder.id) }
    }
}
