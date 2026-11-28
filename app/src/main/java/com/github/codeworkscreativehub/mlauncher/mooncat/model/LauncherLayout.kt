package com.github.codeworkscreativehub.mlauncher.mooncat.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class DisplayMode { TEXT_ONLY, ICON_AND_TEXT, MONOCHROME_AND_TEXT, ICON_ONLY }
@JsonClass(generateAdapter = false)
enum class FolderOpenMode { INLINE, POPUP, FULL_SCREEN }
@JsonClass(generateAdapter = false)
enum class PageType { HOME, WIDGETS, MIXED }
@JsonClass(generateAdapter = false)
enum class ItemType { APPLICATION, FOLDER, SHORTCUT, WIDGET }

@JsonClass(generateAdapter = true)
data class LauncherPage(
    val id: String,
    val name: String,
    val type: PageType,
    val position: Int,
    val displayMode: DisplayMode? = null
)

@JsonClass(generateAdapter = true)
data class LauncherItem(
    val id: String,
    val label: String,
    val type: ItemType,
    val pageId: String,
    val parentFolderId: String? = null,
    val position: Int,
    val packageName: String? = null,
    val activityName: String? = null,
    val userSerial: Long? = null,
    val displayMode: DisplayMode? = null,
    val folderOpenMode: FolderOpenMode = FolderOpenMode.INLINE,
    val originalHomeSlot: Int? = null,
    // Requires biometric or screen-lock confirmation before the folder opens. Folders only.
    val locked: Boolean = false
)

object LauncherLayout {
    fun resolveAppDisplayMode(item: DisplayMode?, app: DisplayMode?, inherited: DisplayMode): DisplayMode =
        item ?: app ?: inherited

    fun initialPages() = listOf(
        LauncherPage("home", "home", PageType.HOME, 0),
        LauncherPage("widgets", "widgets", PageType.WIDGETS, 1)
    )

    fun resolveDisplayMode(item: LauncherItem, folder: LauncherItem?, page: LauncherPage, global: DisplayMode = DisplayMode.TEXT_ONLY): DisplayMode =
        item.displayMode ?: folder?.displayMode ?: page.displayMode ?: global

    // Reject missing parents, cross-page nesting and cycles, including existing corrupt chains.
    fun canMoveInto(item: LauncherItem, targetFolderId: String, items: Collection<LauncherItem>): Boolean {
        if (item.type == ItemType.WIDGET) return false
        val byId = items.associateBy { it.id }
        if (byId.size != items.size) return false
        var current: String? = targetFolderId
        val seen = mutableSetOf(item.id)
        while (current != null) {
            if (!seen.add(current)) return false
            val parent = byId[current] ?: return false
            if (parent.type != ItemType.FOLDER || parent.pageId != item.pageId) return false
            current = parent.parentFolderId
        }
        return true
    }
}
