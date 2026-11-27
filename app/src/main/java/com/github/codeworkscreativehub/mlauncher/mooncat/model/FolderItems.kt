package com.github.codeworkscreativehub.mlauncher.mooncat.model

internal fun LayoutSnapshot.groupHomeFavorites(
    folder: LauncherItem,
    target: LauncherItem,
    source: LauncherItem
): LayoutSnapshot {
    require(folder.type == ItemType.FOLDER && folder.parentFolderId == null)
    require(target.originalHomeSlot == folder.position && source.originalHomeSlot != target.originalHomeSlot)
    require(listOf(target, source).all {
        it.type in setOf(ItemType.APPLICATION, ItemType.SHORTCUT) &&
            it.originalHomeSlot != null && it.originalHomeSlot >= 0 && it.pageId == folder.pageId
    })
    require(items.none {
        it.pageId == folder.pageId && it.parentFolderId == null && it.position == folder.position
    })
    return copy(items = items + listOf(
        folder,
        target.copy(parentFolderId = folder.id, position = 0),
        source.copy(parentFolderId = folder.id, position = 1)
    ))
}

internal fun LayoutSnapshot.moveFolderItem(
    itemId: String,
    destinationId: String,
    beforeId: String? = null
): LayoutSnapshot {
    val item = items.firstOrNull { it.id == itemId }
    require(item != null && item.parentFolderId != null)
    require(LauncherLayout.canMoveInto(item, destinationId, items))
    if (beforeId == item.id && item.parentFolderId == destinationId) return this

    val destination = items.filter { it.parentFolderId == destinationId && it.id != item.id }
        .sortedBy { it.position }.toMutableList()
    val insertion = if (beforeId == null) destination.size else destination.indexOfFirst { it.id == beforeId }
    require(insertion >= 0)
    destination.add(insertion, item.copy(parentFolderId = destinationId))
    val updated = destination.mapIndexed { index, child -> child.copy(position = index) }.associateBy { it.id }
    return copy(items = items.map { updated[it.id] ?: it })
}
