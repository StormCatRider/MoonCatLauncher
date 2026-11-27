package com.github.codeworkscreativehub.mlauncher.ui

import android.content.ClipData
import android.graphics.drawable.ColorDrawable
import android.os.UserManager
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showShortToast
import com.github.codeworkscreativehub.mlauncher.MainViewModel
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.utils.PrivateSpaceManager
import com.github.codeworkscreativehub.mlauncher.mooncat.model.ItemType
import com.github.codeworkscreativehub.mlauncher.mooncat.model.DisplayMode
import com.github.codeworkscreativehub.mlauncher.mooncat.model.FolderOpenMode
import com.github.codeworkscreativehub.mlauncher.mooncat.model.LauncherItem
import com.github.codeworkscreativehub.mlauncher.mooncat.model.LauncherLayout
import com.github.codeworkscreativehub.mlauncher.mooncat.model.LayoutSnapshot
import com.github.codeworkscreativehub.mlauncher.mooncat.model.moveFolderItem
import com.github.codeworkscreativehub.mlauncher.mooncat.model.groupHomeFavorites
import com.github.codeworkscreativehub.mlauncher.mooncat.storage.LayoutStore
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class HomeFolders(
    private val home: HomeFragment,
    private val viewModel: MainViewModel,
    private val prefs: Prefs,
    private val refresh: () -> Unit
) {
    private val store = LayoutStore(home.requireContext())
    private val users = requireNotNull(home.requireContext().getSystemService(UserManager::class.java))
    private val privateSpace = PrivateSpaceManager(home.requireContext())
    private var snapshot: LayoutSnapshot? = null
    private var saving = false
    private var dialog: AlertDialog? = null
    private var inlineRootId: String? = null
    private var inlineFolderId: String? = null

    fun load() {
        home.viewLifecycleOwner.lifecycleScope.launch {
            try {
                snapshot = withContext(Dispatchers.IO) { store.load() }
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                reportError()
            }
        }
    }

    fun folderAt(slot: Int): LauncherItem? = snapshot?.items?.firstOrNull {
        it.pageId == HOME_PAGE && it.parentFolderId == null && it.position == slot && it.type == ItemType.FOLDER
    }

    fun isGrouped(slot: Int): Boolean {
        val app = prefs.getHomeAppModel(slot)
        if (app.activityPackage.isBlank()) return false
        val serial = users.getSerialNumberForUser(app.user)
        return snapshot?.items.orEmpty().any {
            it.originalHomeSlot == slot && it.packageName == app.activityPackage &&
                it.activityName == app.activityClass && it.userSerial == serial
        }
    }

    private data class HomeDrag(val slot: Int, val app: AppListItem, val tapAction: () -> Unit)

    fun dragFavorite(row: View, slot: Int, tapAction: () -> Unit): Boolean {
        if (snapshot == null || prefs.homeLocked || saving || isGrouped(slot)) return false
        val app = prefs.getHomeAppModel(slot)
        if (app.activityPackage.isBlank() || privateSpace.isPrivateSpaceProfile(app.user)) return false
        return row.startDragAndDrop(
            ClipData.newPlainText("", ""), View.DragShadowBuilder(row), HomeDrag(slot, app, tapAction), 0
        )
    }

    private fun acceptHomeDrag(slot: Int, row: View) {
        row.setOnDragListener { _, event ->
            val drag = event.localState as? HomeDrag ?: return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DROP -> if (drag.slot == slot || prefs.homeLocked || saving) false
                    else dropFavorite(drag, slot)
                DragEvent.ACTION_DRAG_ENDED -> {
                    if (drag.slot == slot && !event.result && !prefs.homeLocked) drag.tapAction()
                    true
                }
                else -> true
            }
        }
    }

    private fun dropFavorite(drag: HomeDrag, slot: Int): Boolean {
        if (prefs.getHomeAppModel(drag.slot) != drag.app || isGrouped(drag.slot)) return false
        val source = favoriteItem(drag.slot, drag.app) ?: return false
        val existing = folderAt(slot)
        if (existing != null) {
            addChild(existing, source)
            return true
        }
        if (isGrouped(slot)) return false
        val targetApp = prefs.getHomeAppModel(slot)
        val target = favoriteItem(slot, targetApp) ?: return false
        nameFolder { name ->
            if (prefs.getHomeAppModel(drag.slot) != drag.app || prefs.getHomeAppModel(slot) != targetApp) {
                reportError()
                return@nameFolder
            }
            val folder = LauncherItem(UUID.randomUUID().toString(), name, ItemType.FOLDER, HOME_PAGE, position = slot)
            edit { current -> current.groupHomeFavorites(folder, target, source) }
        }
        return true
    }

    private fun favoriteItem(slot: Int, app: AppListItem): LauncherItem? {
        if (app.activityPackage.isBlank() || app.activityClass.isBlank() ||
            privateSpace.isPrivateSpaceProfile(app.user)) return null
        val serial = users.getSerialNumberForUser(app.user)
        if (serial < 0) return null
        return LauncherItem(
            UUID.randomUUID().toString(), prefs.getAppDisplayName(app).ifBlank { app.activityPackage },
            if (app.isShortcut) ItemType.SHORTCUT else ItemType.APPLICATION,
            HOME_PAGE, position = 0, packageName = app.activityPackage,
            activityName = app.activityClass, userSerial = serial, originalHomeSlot = slot
        )
    }

    fun close() {
        dialog?.dismiss()
        dialog = null
    }

    fun chooseSlotAction(slot: Int, selectApp: () -> Unit) {
        if (snapshot == null || saving) {
            selectApp()
            return
        }
        val app = prefs.getHomeAppModel(slot)
        val serial = if (app.activityPackage.isBlank() || isGrouped(slot)) -1 else users.getSerialNumberForUser(app.user)
        val labels = listOf(R.string.select_app, R.string.mooncat_folder_new, R.string.mooncat_display_page) +
            if (serial >= 0) listOf(R.string.mooncat_display_app) else emptyList()
        show(MaterialAlertDialogBuilder(home.requireContext()).setItems(labels.map { getLocalizedString(it) }.toTypedArray()) { _, index ->
            when (index) {
                0 -> selectApp()
                1 -> nameFolder { name ->
                    val folder = LauncherItem(UUID.randomUUID().toString(), name, ItemType.FOLDER, HOME_PAGE, position = slot)
                    edit { current ->
                        require(current.items.none {
                            it.pageId == HOME_PAGE && it.parentFolderId == null && it.position == slot
                        })
                        current.copy(items = current.items + folder)
                    }
                }
                2 -> choosePageDisplay(slot)
                3 -> chooseDisplay(R.string.mooncat_display_app, prefs.homeAppDisplayMode(app, serial)) { mode ->
                    prefs.setHomeAppDisplayMode(app, serial, mode)
                    refresh()
                }
            }
        })
    }

    fun displayMode(app: AppListItem, slot: Int): DisplayMode =
        appDisplayOverride(app) ?: pageDisplayMode(slot)

    private fun appDisplayOverride(app: AppListItem): DisplayMode? {
        if (app.activityPackage.isBlank()) return null
        val serial = users.getSerialNumberForUser(app.user)
        return if (serial < 0) null else prefs.homeAppDisplayMode(app, serial)
    }

    private fun pageDisplayMode(slot: Int): DisplayMode =
        prefs.homePageDisplayMode(home.displayPageForSlot(slot)) ?: prefs.homeDisplayMode

    private fun choosePageDisplay(slot: Int) {
        val page = home.displayPageForSlot(slot)
        chooseDisplay(R.string.mooncat_display_page, prefs.homePageDisplayMode(page)) { mode ->
            prefs.setHomePageDisplayMode(page, mode)
            refresh()
        }
    }

    private fun chooseDisplay(title: Int, selected: DisplayMode?, change: (DisplayMode?) -> Unit) {
        val labels = listOf(
            R.string.mooncat_display_inherit, R.string.mooncat_display_text_only,
            R.string.mooncat_display_icon_and_text, R.string.mooncat_display_monochrome, R.string.mooncat_display_icon_only
        )
        show(MaterialAlertDialogBuilder(home.requireContext())
            .setTitle(title)
            .setSingleChoiceItems(labels.map { getLocalizedString(it) }.toTypedArray(), (selected?.ordinal ?: -1) + 1) { dialog, index ->
                dialog.dismiss()
                if (!prefs.homeLocked) change(DisplayMode.entries.getOrNull(index - 1))
            })
    }

    private fun folderContentMode(folder: LauncherItem): DisplayMode {
        val byId = snapshot?.items.orEmpty().associateBy { it.id }
        val ancestors = generateSequence(folder) { byId[it.parentFolderId] }.toList()
        return ancestors.firstNotNullOfOrNull { it.displayMode } ?: pageDisplayMode(ancestors.last().position)
    }

    private fun renderChild(row: TextView, item: LauncherItem, inherited: DisplayMode) {
        row.contentDescription = null
        row.setCompoundDrawables(null, null, null, null)
        row.text = displayLabel(item)
        val app = resolveApp(item) ?: return
        val mode = LauncherLayout.resolveAppDisplayMode(item.displayMode, appDisplayOverride(app), inherited)
        home.renderFolderApp(row, app, mode)
    }

    fun toggle(slotFolder: LauncherItem) {
        if (slotFolder.folderOpenMode != FolderOpenMode.INLINE) {
            open(slotFolder)
            return
        }
        inlineRootId = slotFolder.id.takeUnless { it == inlineRootId }
        inlineFolderId = inlineRootId
        refresh()
    }

    fun visibleSlot(slot: Int, row: View): View {
        acceptHomeDrag(slot, row)
        val root = folderAt(slot) ?: return row
        if (root.id != inlineRootId) return row
        val folder = snapshot?.items?.firstOrNull { it.id == inlineFolderId } ?: root
        val inherited = folderContentMode(folder)
        val context = row.context
        val density = context.resources.displayMetrics.density
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), 0, 0, 0)
        }
        fun add(label: String, item: LauncherItem? = null, action: () -> Unit) {
            column.addView(TextView(context).apply {
                text = label
                textSize = prefs.appSize.toFloat()
                setTextColor(prefs.appColor)
                minHeight = (48 * density).toInt()
                gravity = android.view.Gravity.CENTER_VERTICAL
                isFocusable = true
                setOnClickListener { action() }
                if (item != null && !prefs.homeLocked) {
                    setOnLongClickListener { startItemDrag(it, item) }
                    setOnDragListener { _, event ->
                        val source = event.localState as? LauncherItem
                        if (source == null || prefs.homeLocked) return@setOnDragListener false
                        if (event.action == DragEvent.ACTION_DROP) drop(source, item, folder)
                        true
                    }
                }
                if (item != null) renderChild(this, item, inherited)
            })
        }
        if (folder.id != root.id) {
            add(getLocalizedString(R.string.mooncat_folder_up)) {
                inlineFolderId = folder.parentFolderId
                refresh()
            }
        }
        for (child in childrenOf(folder)) {
            add(displayLabel(child), child) {
                if (child.type == ItemType.FOLDER && child.folderOpenMode == FolderOpenMode.INLINE) {
                    inlineFolderId = child.id
                    refresh()
                } else select(child)
            }
        }
        if (!prefs.homeLocked) add(getLocalizedString(R.string.mooncat_folder_edit)) { editFolder(folder) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(ScrollView(context).apply {
                isFillViewport = false
                addView(column)
            }, MATCH_PARENT, (48 * density * column.childCount.coerceIn(1, 3)).toInt())
        }
    }

    private fun childrenOf(folder: LauncherItem): List<LauncherItem> =
        snapshot?.items.orEmpty().filter { it.parentFolderId == folder.id }.sortedBy { it.position }

    private fun select(child: LauncherItem) {
        if (child.type == ItemType.FOLDER) open(child)
        else {
            val app = resolveApp(child)
            if (app == null) reportError(R.string.mooncat_folder_app_missing) else viewModel.launchApp(app, home)
        }
    }

    private fun open(folder: LauncherItem) {
        val children = childrenOf(folder)
        val inherited = folderContentMode(folder)
        val entries = object : ArrayAdapter<LauncherItem>(
            home.requireContext(), android.R.layout.simple_list_item_1, children
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent) as TextView
                renderChild(row, children[position], inherited)
                return row
            }
        }
        val builder = MaterialAlertDialogBuilder(home.requireContext())
            .setTitle(folder.label)
            .setAdapter(entries) { _, index ->
                select(children[index])
            }
            .setNegativeButton(android.R.string.cancel, null)
        snapshot?.items?.firstOrNull { it.id == folder.parentFolderId }?.let { parent ->
            builder.setNeutralButton(R.string.mooncat_folder_up) { _, _ -> open(parent) }
        }
        if (!prefs.homeLocked) {
            builder.setPositiveButton(R.string.mooncat_folder_edit) { _, _ -> editFolder(folder) }
        }
        val opened = show(builder)
        if (folder.folderOpenMode == FolderOpenMode.FULL_SCREEN) {
            opened.window?.apply {
                val surface = MaterialColors.getColor(decorView, com.google.android.material.R.attr.colorSurface, prefs.backgroundColor)
                setBackgroundDrawable(ColorDrawable(surface))
                setLayout(MATCH_PARENT, MATCH_PARENT)
            }
        }
        val list = opened.listView
        if (!prefs.homeLocked && list != null) {
            list.setOnItemLongClickListener { _, row, position, _ ->
                val child = children[position]
                startItemDrag(row, child)
            }
            list.setOnDragListener { _, event ->
                val child = event.localState as? LauncherItem
                if (child == null || prefs.homeLocked) return@setOnDragListener false
                if (event.action == DragEvent.ACTION_DROP) {
                    val targetIndex = list.pointToPosition(event.x.toInt(), event.y.toInt())
                    val target = children.getOrNull(targetIndex) ?: return@setOnDragListener false
                    drop(child, target, folder)
                }
                true
            }
        }
    }

    private fun startItemDrag(row: View, item: LauncherItem): Boolean =
        !prefs.homeLocked && !saving && row.startDragAndDrop(
            ClipData.newPlainText("", ""), View.DragShadowBuilder(row), item, 0
        )

    private fun drop(source: LauncherItem, target: LauncherItem, folder: LauncherItem) {
        if (target.id == source.id) return
        if (target.type == ItemType.FOLDER) move(source, target)
        else move(source, folder, target.id)
    }

    fun editFolder(folder: LauncherItem) {
        if (prefs.homeLocked || saving) return
        val children = snapshot?.items.orEmpty().filter { it.parentFolderId == folder.id }
        val labels = arrayOf(
            getLocalizedString(R.string.select_app),
            getLocalizedString(R.string.mooncat_folder_new),
            getLocalizedString(R.string.mooncat_folder_rename),
            getLocalizedString(R.string.mooncat_folder_remove),
            getLocalizedString(R.string.mooncat_folder_move),
            getLocalizedString(R.string.mooncat_folder_reorder),
            getLocalizedString(R.string.mooncat_folder_open_mode),
            getLocalizedString(R.string.mooncat_display_folder),
            getLocalizedString(R.string.mooncat_display_app),
            getLocalizedString(R.string.mooncat_display_page)
        )
        show(MaterialAlertDialogBuilder(home.requireContext()).setTitle(folder.label).setItems(labels) { _, index ->
            when (index) {
                0 -> pickApp(folder)
                1 -> nameFolder { name ->
                    addChild(folder, LauncherItem(UUID.randomUUID().toString(), name, ItemType.FOLDER, folder.pageId, position = 0))
                }
                2 -> nameFolder(folder.label) { name ->
                    edit { current -> current.copy(items = current.items.map { if (it.id == folder.id) it.copy(label = name) else it }) }
                }
                3 -> if (children.isEmpty()) remove(folder) else removeChild(children)
                4 -> chooseChild(children, R.string.mooncat_folder_move, ::chooseDestination)
                5 -> chooseChild(children, R.string.mooncat_folder_reorder) { child ->
                    val siblings = children.sortedBy { it.position }
                    val targets = siblings.filterNot { it.id == child.id }
                    val labels = targets.map(::displayLabel) + getLocalizedString(R.string.mooncat_folder_move_end)
                    show(MaterialAlertDialogBuilder(home.requireContext())
                        .setTitle(R.string.mooncat_folder_move_before)
                        .setItems(labels.toTypedArray()) { _, position ->
                            move(child, folder, targets.getOrNull(position)?.id)
                        })
                }
                6 -> chooseOpenMode(folder)
                7 -> editItemDisplay(folder, R.string.mooncat_display_folder)
                8 -> chooseChild(children.filter { it.type != ItemType.FOLDER }, R.string.mooncat_display_app) {
                    editItemDisplay(it, R.string.mooncat_display_app)
                }
                9 -> {
                    val byId = snapshot?.items.orEmpty().associateBy { it.id }
                    choosePageDisplay(generateSequence(folder) { byId[it.parentFolderId] }.last().position)
                }
            }
        })
    }

    private fun editItemDisplay(item: LauncherItem, title: Int) {
        chooseDisplay(title, item.displayMode) { mode ->
            edit { current ->
                current.copy(items = current.items.map { if (it.id == item.id) it.copy(displayMode = mode) else it })
            }
        }
    }

    private fun chooseOpenMode(folder: LauncherItem) {
        val labels = arrayOf(R.string.mooncat_folder_inline, R.string.mooncat_folder_popup, R.string.mooncat_folder_full)
        show(MaterialAlertDialogBuilder(home.requireContext())
            .setTitle(R.string.mooncat_folder_open_mode)
            .setSingleChoiceItems(labels.map { getLocalizedString(it) }.toTypedArray(), folder.folderOpenMode.ordinal) { popup, index ->
                val mode = FolderOpenMode.entries[index]
                popup.dismiss()
                edit(afterSave = {
                    if (mode != FolderOpenMode.INLINE) {
                        if (inlineRootId == folder.id) {
                            inlineRootId = null
                            inlineFolderId = null
                        } else if (inlineFolderId == folder.id) inlineFolderId = folder.parentFolderId
                    }
                }) { current ->
                    current.copy(items = current.items.map { if (it.id == folder.id) it.copy(folderOpenMode = mode) else it })
                }
            })
    }

    private fun chooseChild(children: List<LauncherItem>, title: Int, select: (LauncherItem) -> Unit) {
        val ordered = children.sortedBy { it.position }
        show(MaterialAlertDialogBuilder(home.requireContext())
            .setTitle(title)
            .setItems(ordered.map(::displayLabel).toTypedArray()) { _, index ->
                select(ordered[index])
            })
    }

    private fun chooseDestination(child: LauncherItem) {
        val items = snapshot?.items.orEmpty()
        val destinations = items.filter {
            it.type == ItemType.FOLDER && it.id != child.parentFolderId &&
                LauncherLayout.canMoveInto(child, it.id, items)
        }
        if (destinations.isEmpty()) {
            reportError(R.string.mooncat_folder_no_destination)
            return
        }
        val byId = items.associateBy { it.id }
        val paths = destinations.map { folder ->
            generateSequence(folder) { byId[it.parentFolderId] }.toList().asReversed().joinToString(" / ") { it.label }
        }
        show(MaterialAlertDialogBuilder(home.requireContext())
            .setTitle(R.string.mooncat_folder_move)
            .setItems(paths.toTypedArray()) { _, index -> move(child, destinations[index]) })
    }

    private fun move(child: LauncherItem, destination: LauncherItem, beforeId: String? = null) {
        edit(afterSave = {
            if (inlineRootId != null && destination.folderOpenMode == FolderOpenMode.INLINE) {
                val byId = snapshot?.items.orEmpty().associateBy { it.id }
                val root = generateSequence(destination) { byId[it.parentFolderId] }.last()
                if (root.folderOpenMode == FolderOpenMode.INLINE) {
                    inlineRootId = root.id
                    inlineFolderId = destination.id
                } else open(destination)
            } else open(destination)
        }) { current ->
            current.moveFolderItem(child.id, destination.id, beforeId)
        }
    }

    private fun pickApp(folder: LauncherItem) {
        val apps = viewModel.appList.value.orEmpty().filter { !it.isShortcut && it.profileType != "PRIVATE" }
        if (apps.isEmpty()) {
            reportError(R.string.mooncat_folder_app_missing)
            return
        }
        show(MaterialAlertDialogBuilder(home.requireContext())
            .setTitle(R.string.select_app)
            .setItems(apps.map(prefs::getAppDisplayName).toTypedArray()) { _, index ->
                val app = apps[index]
                val serial = users.getSerialNumberForUser(app.user)
                if (serial < 0) {
                    reportError(R.string.mooncat_folder_app_missing)
                    return@setItems
                }
                addChild(folder, LauncherItem(
                    UUID.randomUUID().toString(), prefs.getAppDisplayName(app), ItemType.APPLICATION,
                    folder.pageId, position = 0, packageName = app.activityPackage,
                    activityName = app.activityClass, userSerial = serial
                ))
            })
    }

    private fun addChild(folder: LauncherItem, child: LauncherItem) {
        edit { current ->
            require(current.items.any { it.id == folder.id && it.type == ItemType.FOLDER })
            val position = current.items.filter { it.parentFolderId == folder.id }.maxOfOrNull { it.position } ?: -1
            current.copy(items = current.items + child.copy(parentFolderId = folder.id, position = position + 1))
        }
    }

    private fun removeChild(children: List<LauncherItem>) {
        val removable = children.filter { child -> snapshot?.items.orEmpty().none { it.parentFolderId == child.id } }
        if (removable.isEmpty()) {
            reportError(R.string.mooncat_folder_remove_empty)
            return
        }
        show(MaterialAlertDialogBuilder(home.requireContext())
            .setTitle(R.string.mooncat_folder_remove)
            .setItems(removable.map(::displayLabel).toTypedArray()) { _, index -> remove(removable[index]) })
    }

    private fun remove(item: LauncherItem) {
        edit { current ->
            require(current.items.none { it.parentFolderId == item.id })
            current.copy(items = current.items.filterNot { it.id == item.id })
        }
    }

    private fun resolveApp(item: LauncherItem): AppListItem? = viewModel.appList.value.orEmpty().firstOrNull {
        (item.type == ItemType.APPLICATION || item.type == ItemType.SHORTCUT) &&
            it.profileType != "PRIVATE" && it.activityPackage == item.packageName &&
            it.activityClass == item.activityName && users.getSerialNumberForUser(it.user) == item.userSerial
    }

    private fun displayLabel(item: LauncherItem): String =
        if (item.type == ItemType.FOLDER) item.label
        else resolveApp(item)?.let(prefs::getAppDisplayName) ?: getLocalizedString(R.string.mooncat_folder_app_missing)

    private fun nameFolder(initial: String = "", save: (String) -> Unit) {
        val input = EditText(home.requireContext()).apply {
            setSingleLine()
            setText(initial)
            filters = arrayOf(android.text.InputFilter.LengthFilter(80))
        }
        val prompt = show(MaterialAlertDialogBuilder(home.requireContext())
            .setTitle(R.string.mooncat_folder_name)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null))
        prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty()) input.error = getLocalizedString(R.string.mooncat_folder_name)
            else {
                prompt.dismiss()
                save(name)
            }
        }
    }

    private fun edit(afterSave: () -> Unit = {}, transform: (LayoutSnapshot) -> LayoutSnapshot) {
        if (snapshot == null || prefs.homeLocked || saving) return
        saving = true
        home.viewLifecycleOwner.lifecycleScope.launch {
            try {
                snapshot = withContext(Dispatchers.IO) { store.update(transform) }
                afterSave()
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                reportError()
            } finally {
                saving = false
            }
        }
    }

    private fun show(builder: MaterialAlertDialogBuilder): AlertDialog {
        close()
        return builder.show().also { dialog = it }
    }

    private fun reportError(message: Int = R.string.mooncat_folder_error) {
        home.context?.showShortToast(getLocalizedString(message))
    }

    private companion object {
        const val HOME_PAGE = "home"
    }
}
