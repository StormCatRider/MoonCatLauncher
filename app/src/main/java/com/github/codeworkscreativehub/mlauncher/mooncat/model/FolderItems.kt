package com.github.codeworkscreativehub.mlauncher.mooncat.model

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
