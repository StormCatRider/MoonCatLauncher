package com.github.codeworkscreativehub.mlauncher.ui

import android.os.UserManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showShortToast
import com.github.codeworkscreativehub.mlauncher.MainViewModel
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.mooncat.model.ItemType
import com.github.codeworkscreativehub.mlauncher.mooncat.model.LauncherItem
import com.github.codeworkscreativehub.mlauncher.mooncat.model.LayoutSnapshot
import com.github.codeworkscreativehub.mlauncher.mooncat.storage.LayoutStore
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
    private var snapshot: LayoutSnapshot? = null
    private var saving = false
    private var dialog: AlertDialog? = null

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

    fun close() {
        dialog?.dismiss()
    }

    fun chooseSlotAction(slot: Int, selectApp: () -> Unit) {
        if (snapshot == null || saving) {
            selectApp()
            return
        }
        val labels = arrayOf(getLocalizedString(R.string.select_app), getLocalizedString(R.string.mooncat_folder_new))
        show(MaterialAlertDialogBuilder(home.requireContext()).setItems(labels) { _, index ->
            if (index == 0) selectApp() else nameFolder { name ->
                val folder = LauncherItem(UUID.randomUUID().toString(), name, ItemType.FOLDER, HOME_PAGE, position = slot)
                edit { current ->
                    require(current.items.none {
                        it.pageId == HOME_PAGE && it.parentFolderId == null && it.position == slot
                    })
                    current.copy(items = current.items + folder)
                }
            }
        })
    }

    fun open(folder: LauncherItem) {
        val children = snapshot?.items.orEmpty().filter { it.parentFolderId == folder.id }.sortedBy { it.position }
        val entries = children.map(::displayLabel)
        val builder = MaterialAlertDialogBuilder(home.requireContext())
            .setTitle(folder.label)
            .setItems(entries.toTypedArray()) { _, index ->
                val child = children[index]
                if (child.type == ItemType.FOLDER) {
                    open(child)
                } else {
                    val app = resolveApp(child)
                    if (app == null) reportError(R.string.mooncat_folder_app_missing)
                    else viewModel.launchApp(app, home)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
        snapshot?.items?.firstOrNull { it.id == folder.parentFolderId }?.let { parent ->
            builder.setNeutralButton(R.string.mooncat_folder_up) { _, _ -> open(parent) }
        }
        if (!prefs.homeLocked) {
            builder.setPositiveButton(R.string.mooncat_folder_edit) { _, _ -> editFolder(folder) }
        }
        show(builder)
    }

    private fun editFolder(folder: LauncherItem) {
        if (prefs.homeLocked || saving) return
        val children = snapshot?.items.orEmpty().filter { it.parentFolderId == folder.id }
        val labels = arrayOf(
            getLocalizedString(R.string.select_app),
            getLocalizedString(R.string.mooncat_folder_new),
            getLocalizedString(R.string.mooncat_folder_rename),
            getLocalizedString(R.string.mooncat_folder_remove)
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
            }
        })
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
        item.type == ItemType.APPLICATION && it.profileType != "PRIVATE" && it.activityPackage == item.packageName &&
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

    private fun edit(transform: (LayoutSnapshot) -> LayoutSnapshot) {
        if (snapshot == null || prefs.homeLocked || saving) return
        saving = true
        home.viewLifecycleOwner.lifecycleScope.launch {
            try {
                snapshot = withContext(Dispatchers.IO) { store.update(transform) }
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
