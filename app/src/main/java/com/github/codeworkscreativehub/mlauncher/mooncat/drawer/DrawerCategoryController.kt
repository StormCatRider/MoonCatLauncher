package com.github.codeworkscreativehub.mlauncher.mooncat.drawer

import android.content.Context
import android.content.pm.PackageManager
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showLongToast
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.mooncat.productivity.Productivity
import com.github.codeworkscreativehub.mlauncher.mooncat.productivity.ProductivityDialogs
import com.github.codeworkscreativehub.mlauncher.mooncat.productivity.ProductivityStore
import com.github.codeworkscreativehub.mlauncher.ui.adapter.AppDrawerAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Drawer categories (automatic and the user's own, which act as drawer folders), the list or grid
 * view, and local results from notes and tasks while searching. Everything is computed on the device.
 */
class DrawerCategoryController(
    private val fragment: Fragment,
    private val adapter: AppDrawerAdapter,
    private val recycler: RecyclerView,
    private val bar: TextView,
    private val localResults: TextView,
    private val currentQuery: () -> String,
    private val openNotes: () -> Unit
) {
    private val context: Context = fragment.requireContext()
    private val prefs = Prefs(context).prefsNormal
    private val adapterJson = Moshi.Builder().build().adapter(CategoryData::class.java)
    private val locale get() = Locale.getDefault()

    /** Package name to ApplicationInfo.category, filled in the background. */
    private var androidCategories: Map<String, Int> = emptyMap()
    private var selected: String? = null

    var columns: Int
        get() = prefs.getInt(KEY_COLUMNS, 1).coerceIn(1, MAX_COLUMNS)
        private set(value) {
            prefs.edit().putInt(KEY_COLUMNS, value.coerceIn(1, MAX_COLUMNS)).apply()
        }

    fun load(): CategoryData = try {
        prefs.getString(KEY_DATA, null)?.let(adapterJson::fromJson)?.takeIf(DrawerCategories::isValid) ?: CategoryData()
    } catch (_: Exception) {
        CategoryData()
    }

    private fun save(data: CategoryData): Boolean {
        val compacted = DrawerCategories.compact(data, androidCategories)
        if (!DrawerCategories.isValid(compacted)) return false
        return prefs.edit().putString(KEY_DATA, adapterJson.toJson(compacted)).commit()
    }

    fun start() {
        applyColumns()
        bar.isVisible = true
        bar.setOnClickListener { showPicker() }
        bar.contentDescription = getLocalizedString(R.string.mooncat_cat_bar_description)
        localResults.setOnClickListener { showLocalResults() }
        updateBar()
        refreshAndroidCategories()
    }

    /** Called when the drawer gets a new app list. */
    fun onAppsChanged() {
        val known = androidCategories.keys
        if (adapter.allApps.any { it.activityPackage !in known }) refreshAndroidCategories() else updateBar()
    }

    private fun refreshAndroidCategories() {
        val packages = adapter.allApps.map { it.activityPackage }.toSet()
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                packages.associateWith { pkg ->
                    try {
                        pm.getApplicationInfo(pkg, 0).category
                    } catch (_: PackageManager.NameNotFoundException) {
                        -1
                    }
                }
            }
            androidCategories = androidCategories + found
            applySelection()
        }
    }

    private fun categoryOf(data: CategoryData, app: AppListItem): String =
        DrawerCategories.categoryOf(data, app.activityPackage, androidCategories[app.activityPackage] ?: -1)

    private fun applySelection() {
        val id = selected
        val data = load()
        if (id != null && !DrawerCategories.exists(data, id)) selected = null
        adapter.categoryPredicate = selected?.let { chosen -> { app: AppListItem -> categoryOf(data, app) == chosen } }
        val query = currentQuery()
        if (query.isNotEmpty()) adapter.filter.filter(query)
        updateBar()
    }

    fun name(data: CategoryData, id: String): String = when {
        id.startsWith(DrawerCategories.ID_PREFIX_USER) ->
            data.categories.firstOrNull { DrawerCategories.userId(it) == id }?.name.orEmpty()
        else -> getLocalizedString(
            when (AutoCategory.entries.firstOrNull { it.id == id } ?: AutoCategory.OTHER) {
                AutoCategory.GAMES -> R.string.mooncat_cat_games
                AutoCategory.AUDIO -> R.string.mooncat_cat_audio
                AutoCategory.VIDEO -> R.string.mooncat_cat_video
                AutoCategory.IMAGES -> R.string.mooncat_cat_images
                AutoCategory.SOCIAL -> R.string.mooncat_cat_social
                AutoCategory.NEWS -> R.string.mooncat_cat_news
                AutoCategory.MAPS -> R.string.mooncat_cat_maps
                AutoCategory.PRODUCTIVITY -> R.string.mooncat_cat_productivity
                AutoCategory.ACCESSIBILITY -> R.string.mooncat_cat_accessibility
                AutoCategory.OTHER -> R.string.mooncat_cat_other
            }
        )
    }

    private fun updateBar() {
        val data = load()
        bar.text = (selected?.let { name(data, it) } ?: getLocalizedString(R.string.mooncat_cat_all)) + "  ▾"
    }

    // ---- Category picker -------------------------------------------------------------------

    private data class Entry(val id: String?, val label: String)

    private fun showPicker() {
        val data = load()
        val counts = adapter.allApps.groupingBy { categoryOf(data, it) }.eachCount()
        val userEntries = data.categories.sortedBy { it.name.lowercase(locale) }.map {
            val id = DrawerCategories.userId(it)
            Entry(id, "▸ ${it.name} (${counts[id] ?: 0})")
        }
        val autoEntries = AutoCategory.entries.map { it.id }.filter { (counts[it] ?: 0) > 0 }
            .map { Entry(it, "${name(data, it)} (${counts[it]})") }
            .sortedBy { if (it.id == AutoCategory.OTHER.id) "￿" else it.label.lowercase(locale) }
        val entries = listOf(Entry(null, "${getLocalizedString(R.string.mooncat_cat_all)} (${adapter.allApps.size})")) +
            userEntries + autoEntries
        val checked = entries.indexOfFirst { it.id == selected }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mooncat_cat_title)
            .setSingleChoiceItems(entries.map { it.label }.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                selected = entries[which].id
                applySelection()
            }
            .setPositiveButton(R.string.mooncat_cat_edit) { _, _ -> showEditor() }
            .setNeutralButton(R.string.mooncat_cat_view) { _, _ -> chooseColumns() }
            .setNegativeButton(R.string.mooncat_prod_close, null)
            .show()
    }

    private fun chooseColumns() {
        val labels = listOf(getLocalizedString(R.string.mooncat_cat_view_list)) +
            (2..MAX_COLUMNS).map { getLocalizedString(R.string.mooncat_cat_view_grid, it) }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mooncat_cat_view)
            .setSingleChoiceItems(labels.toTypedArray(), columns - 1) { dialog, which ->
                dialog.dismiss()
                columns = which + 1
                applyColumns()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyColumns() {
        val count = columns
        adapter.columns = count
        recycler.layoutManager = if (count <= 1) LinearLayoutManager(context) else GridLayoutManager(context, count).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = if (adapter.isFullSpan(position)) count else 1
            }
        }
    }

    // ---- Editing ---------------------------------------------------------------------------

    private fun showEditor() {
        val data = load()
        val mine = data.categories.sortedBy { it.name.lowercase(locale) }
        val labels = mine.map { "▸ ${it.name}" } + getLocalizedString(R.string.mooncat_cat_move_app)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mooncat_cat_edit)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < mine.size) categoryActions(mine[which]) else pickAppToMove()
            }
            .setPositiveButton(R.string.mooncat_cat_new) { _, _ -> askName(R.string.mooncat_cat_new, "") { name -> createCategory(name) } }
            .setNegativeButton(R.string.mooncat_prod_close, null)
            .show()
    }

    private fun createCategory(name: String) {
        val result = DrawerCategories.add(load(), name, System.currentTimeMillis())
        if (result == null) {
            context.showLongToast(getLocalizedString(R.string.mooncat_cat_name_invalid, DrawerCategories.MAX_CATEGORIES))
            return
        }
        save(result.first)
        chooseMembers(result.second)
    }

    private fun categoryActions(category: UserCategory) {
        val actions = listOf(
            getLocalizedString(R.string.mooncat_cat_choose_apps),
            getLocalizedString(R.string.mooncat_cat_rename),
            getLocalizedString(R.string.mooncat_prod_task_delete)
        )
        MaterialAlertDialogBuilder(context)
            .setTitle(category.name)
            .setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> chooseMembers(category)
                    1 -> askName(R.string.mooncat_cat_rename, category.name) { name ->
                        val renamed = DrawerCategories.rename(load(), category.id, name)
                        if (renamed == null) context.showLongToast(getLocalizedString(R.string.mooncat_cat_name_invalid, DrawerCategories.MAX_CATEGORIES))
                        else { save(renamed); applySelection() }
                    }
                    else -> MaterialAlertDialogBuilder(context)
                        .setMessage(getLocalizedString(R.string.mooncat_cat_delete_confirm, category.name))
                        .setPositiveButton(R.string.mooncat_prod_task_delete) { _, _ ->
                            save(DrawerCategories.delete(load(), category.id)); applySelection()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> showEditor() }
            .show()
    }

    /** One entry per package, since categories belong to apps rather than to profiles or shortcuts. */
    private fun appChoices(): List<Pair<String, String>> {
        val aliases = Prefs(context)
        return adapter.allApps.filterNot { it.isShortcut }
            .distinctBy { it.activityPackage }
            .map { it.activityPackage to (aliases.getAppAlias(it.settingsKey).takeIf(String::isNotBlank) ?: it.activityLabel) }
            .sortedBy { it.second.lowercase(locale) }
    }

    private fun chooseMembers(category: UserCategory) {
        val data = load()
        val id = DrawerCategories.userId(category)
        val apps = appChoices()
        val checked = BooleanArray(apps.size) { data.assignments[apps[it].first] == id }
        MaterialAlertDialogBuilder(context)
            .setTitle(getLocalizedString(R.string.mooncat_cat_choose_apps_for, category.name))
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val members = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }.toSet()
                save(DrawerCategories.setMembers(load(), id, members))
                selected = id
                applySelection()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickAppToMove() {
        val apps = appChoices()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mooncat_cat_move_app)
            .setItems(apps.map { it.second }.toTypedArray()) { _, which -> pickCategoryFor(apps[which]) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> showEditor() }
            .show()
    }

    private fun pickCategoryFor(app: Pair<String, String>) {
        val data = load()
        val auto = AutoCategory.fromAndroid(androidCategories[app.first] ?: -1).id
        val options: List<Pair<String?, String>> =
            listOf(null to getLocalizedString(R.string.mooncat_cat_automatic, name(data, auto))) +
                data.categories.sortedBy { it.name.lowercase(locale) }.map { DrawerCategories.userId(it) to "▸ ${it.name}" } +
                AutoCategory.entries.map { it.id to name(data, it.id) }
        val current = data.assignments[app.first]
        MaterialAlertDialogBuilder(context)
            .setTitle(app.second)
            .setSingleChoiceItems(options.map { it.second }.toTypedArray(), options.indexOfFirst { it.first == current }.coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss()
                save(DrawerCategories.assign(load(), app.first, options[which].first))
                applySelection()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun askName(title: Int, initial: String, onDone: (String) -> Unit) {
        val builder = MaterialAlertDialogBuilder(context).setTitle(title)
        val input = EditText(builder.context).apply {
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = getLocalizedString(R.string.mooncat_cat_name_hint)
            setText(initial)
            setSelection(text.length)
        }
        val pad = (20 * context.resources.displayMetrics.density).toInt()
        val box = LinearLayout(builder.context).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dialog = builder.setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> onDone(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); true } else false
        }
        input.requestFocus()
    }

    // ---- Notes and tasks in search ---------------------------------------------------------

    private sealed class LocalResult(val label: String) {
        class Note(label: String) : LocalResult(label)
        class Task(label: String) : LocalResult(label)
    }

    private fun localMatches(query: String): List<LocalResult> {
        val notes = LocalSearch.search(query, Prefs(context).loadMessages()) { it.text }.map { LocalResult.Note("✎ " + it.text) }
        val tasks = LocalSearch.search(query, Productivity.sorted(ProductivityStore(context).load().tasks)) { it.text }
            .map { LocalResult.Task((if (it.done) "☑ " else "☐ ") + it.text) }
        return (tasks + notes).take(LocalSearch.MAX_RESULTS)
    }

    fun onQueryChanged(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < LocalSearch.MIN_QUERY || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            localResults.isVisible = false
            return
        }
        val count = localMatches(trimmed).size
        localResults.isVisible = count > 0
        if (count > 0) localResults.text = getLocalizedString(R.string.mooncat_search_local_results, count) + "  ›"
    }

    private fun showLocalResults() {
        val results = localMatches(currentQuery().trim())
        if (results.isEmpty()) return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mooncat_search_local_title)
            .setItems(results.map { it.label.take(120) }.toTypedArray()) { _, which ->
                when (results[which]) {
                    is LocalResult.Note -> openNotes()
                    is LocalResult.Task -> ProductivityDialogs(fragment.requireActivity()).showTasks()
                }
            }
            .setNegativeButton(R.string.mooncat_prod_close, null)
            .show()
    }

    companion object {
        const val KEY_DATA = "MOONCAT_DRAWER_CATEGORIES"
        const val KEY_COLUMNS = "MOONCAT_DRAWER_COLUMNS"
        const val MAX_COLUMNS = 4
    }
}
