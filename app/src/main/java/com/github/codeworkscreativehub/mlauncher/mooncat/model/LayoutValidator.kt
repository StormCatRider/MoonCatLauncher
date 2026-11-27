package com.github.codeworkscreativehub.mlauncher.mooncat.model

data class LayoutSnapshot(
    val pages: List<LauncherPage> = LauncherLayout.initialPages(),
    val items: List<LauncherItem> = emptyList(),
    val schemaVersion: Int = 1
) {
    fun isValid(): Boolean {
        if (schemaVersion != 1 || pages.isEmpty() || pages.size > 100 || items.size > 10000) return false
        val pageIds = pages.map { it.id }.toSet()
        if (pageIds.size != pages.size || pages.none { it.type == PageType.HOME }) return false
        if (pages.any { it.id.isBlank() || it.name.isBlank() || it.position < 0 }) return false
        if (pages.map { it.position }.distinct().size != pages.size) return false
        if (items.map { it.id }.distinct().size != items.size) return false
        val byId = items.associateBy { it.id }
        for (item in items) {
            if (item.id.isBlank() || item.label.isBlank() || item.pageId !in pageIds || item.position < 0) return false
            if (item.type == ItemType.APPLICATION && (item.packageName.isNullOrBlank() || item.activityName.isNullOrBlank())) return false
            var parent = item.parentFolderId
            if (parent != null && item.type == ItemType.WIDGET) return false
            val seen = mutableSetOf(item.id)
            while (parent != null) {
                if (!seen.add(parent)) return false
                val folder = byId[parent] ?: return false
                if (folder.type != ItemType.FOLDER || folder.pageId != item.pageId) return false
                parent = folder.parentFolderId
            }
        }
        return true
    }
}
