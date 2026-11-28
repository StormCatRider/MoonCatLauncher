package com.github.codeworkscreativehub.mlauncher.mooncat.theme

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showLongToast
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.utils.AppReloader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/** "My themes": presets and named local themes with a preview before applying. */
class ThemeDialogs(private val context: Context) {
    private val prefs = Prefs(context)
    private val adapter = Moshi.Builder().build().adapter<List<SavedTheme>>(
        Types.newParameterizedType(List::class.java, SavedTheme::class.java)
    )

    private fun loadSaved(): List<SavedTheme> = try {
        prefs.prefsNormal.getString(KEY, null)?.let(adapter::fromJson)
            ?.takeIf(ThemeLibrary::isValid).orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun store(themes: List<SavedTheme>): Boolean {
        check(ThemeLibrary.isValid(themes))
        return prefs.prefsNormal.edit().putString(KEY, adapter.toJson(themes)).commit()
    }

    /** The colors in effect now, including defaults the user never changed. */
    private fun currentColors(): Map<String, Int> {
        val stored = prefs.prefsNormal.all
        return ThemeLibrary.ALL_KEYS.mapNotNull { key -> (stored[key] as? Int)?.let { key to it } }.toMap() + mapOf(
            "BACKGROUND_COLOR" to prefs.backgroundColor,
            "APP_COLOR" to prefs.appColor,
            "DATE_COLOR" to prefs.dateColor,
            "CLOCK_COLOR" to prefs.clockColor,
            "BATTERY_COLOR" to prefs.batteryColor,
            "DAILY_WORD_COLOR" to prefs.dailyWordColor,
            "ALARM_CLOCK_COLOR" to prefs.alarmClockColor,
            "NOTES_BACKGROUND_COLOR" to prefs.notesBackgroundColor,
            "BUBBLE_BACKGROUND_COLOR" to prefs.bubbleBackgroundColor,
            "INPUT_MESSAGE_COLOR" to prefs.inputMessageColor
        )
    }

    private fun displayName(theme: SavedTheme, preset: Boolean): String = if (!preset) theme.name else when (theme.name) {
        "Lunar" -> getLocalizedString(R.string.mooncat_theme_preset_lunar)
        "Light" -> getLocalizedString(R.string.mooncat_theme_preset_light)
        else -> getLocalizedString(R.string.mooncat_theme_preset_amoled)
    }

    fun showLibrary() {
        val presets = ThemeLibrary.presets()
        val saved = loadSaved().sortedBy { it.name.lowercase() }
        val entries = presets.map { it to true } + saved.map { it to false }
        val labels = entries.map { (theme, preset) ->
            if (preset) "☾ " + displayName(theme, true) else displayName(theme, false)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mooncat_theme_library)
            .setItems(labels.toTypedArray()) { _, index -> preview(entries[index].first, entries[index].second) }
            .setPositiveButton(R.string.mooncat_theme_save_current) { _, _ -> saveCurrent() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun preview(theme: SavedTheme, preset: Boolean) {
        val colors = currentColors() + theme.colors
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(displayName(theme, preset))
            .setView(previewView(colors))
            .setPositiveButton(R.string.mooncat_theme_apply) { _, _ -> apply(theme) }
            .setNegativeButton(android.R.string.cancel, null)
        if (preset) {
            builder.setNeutralButton(R.string.mooncat_theme_duplicate) { _, _ -> duplicate(theme, displayName(theme, true)) }
        } else {
            builder.setNeutralButton(R.string.mooncat_theme_manage) { _, _ -> manage(theme) }
        }
        builder.show()
    }

    private fun previewView(colors: Map<String, Int>): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun line(text: String, key: String, size: Float) = TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(colors.getValue(key))
            setPadding(0, dp(2), 0, dp(2))
        }
        val screen = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(colors.getValue("BACKGROUND_COLOR"))
                setStroke(dp(1), colors.getValue("APP_COLOR") and 0x33FFFFFF)
            }
            addView(line("21:30", "CLOCK_COLOR", 34f))
            addView(line(getLocalizedString(R.string.mooncat_theme_preview_date), "DATE_COLOR", 14f))
            addView(line("⏰ 06:30", "ALARM_CLOCK_COLOR", 13f))
            addView(View(context), LinearLayout.LayoutParams(1, dp(12)))
            for (app in listOf(R.string.mooncat_theme_preview_app1, R.string.mooncat_theme_preview_app2, R.string.mooncat_theme_preview_app3)) {
                addView(line(getLocalizedString(app), "APP_COLOR", 18f))
            }
            addView(TextView(context).apply {
                text = getLocalizedString(R.string.mooncat_theme_preview_note)
                textSize = 13f
                setTextColor(colors.getValue("BUBBLE_MESSAGE_COLOR".takeIf(colors::containsKey) ?: "APP_COLOR"))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(colors.getValue("BUBBLE_BACKGROUND_COLOR"))
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
                gravity = Gravity.END
            })
        }
        return LinearLayout(context).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(screen, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    /** Applying replaces every themable color, so keys absent from the theme return to defaults. */
    private fun apply(theme: SavedTheme) {
        val written = prefs.prefsNormal.edit().apply {
            ThemeLibrary.ALL_KEYS.forEach(::remove)
            theme.colors.forEach { (key, color) -> putInt(key, color) }
        }.commit()
        if (written) AppReloader.restartApp(context) else context.showLongToast(getLocalizedString(R.string.mooncat_theme_error))
    }

    private fun saveCurrent() = askName("", R.string.mooncat_theme_save_current) { name ->
        val saved = loadSaved()
        if (saved.any { it.name.equals(name, ignoreCase = true) }) {
            MaterialAlertDialogBuilder(context)
                .setMessage(getLocalizedString(R.string.mooncat_theme_replace).format(name))
                .setPositiveButton(R.string.mooncat_theme_replace_confirm) { _, _ -> save(saved, SavedTheme(name, currentColors())) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else save(saved, SavedTheme(name, currentColors()))
    }

    private fun duplicate(theme: SavedTheme, label: String) {
        val saved = loadSaved()
        askName(ThemeLibrary.copyName(saved, label), R.string.mooncat_theme_duplicate) { name ->
            if (saved.any { it.name.equals(name, ignoreCase = true) }) {
                context.showLongToast(getLocalizedString(R.string.mooncat_theme_name_taken))
            } else save(saved, theme.copy(name = name))
        }
    }

    private fun save(saved: List<SavedTheme>, theme: SavedTheme) {
        if (saved.size >= ThemeLibrary.MAX_THEMES && saved.none { it.name.equals(theme.name, ignoreCase = true) }) {
            context.showLongToast(getLocalizedString(R.string.mooncat_theme_limit).format(ThemeLibrary.MAX_THEMES))
            return
        }
        val ok = try {
            store(ThemeLibrary.upsert(saved, theme))
        } catch (_: Exception) {
            false
        }
        context.showLongToast(getLocalizedString(if (ok) R.string.mooncat_theme_saved else R.string.mooncat_theme_error))
    }

    private fun manage(theme: SavedTheme) {
        val actions = arrayOf(
            getLocalizedString(R.string.mooncat_theme_duplicate),
            getLocalizedString(R.string.mooncat_theme_rename),
            getLocalizedString(R.string.mooncat_theme_delete)
        )
        MaterialAlertDialogBuilder(context)
            .setTitle(theme.name)
            .setItems(actions) { _, index ->
                when (index) {
                    0 -> duplicate(theme, theme.name)
                    1 -> askName(theme.name, R.string.mooncat_theme_rename) { name ->
                        val ok = try {
                            store(ThemeLibrary.rename(loadSaved(), theme.name, name))
                        } catch (_: Exception) {
                            false
                        }
                        context.showLongToast(getLocalizedString(if (ok) R.string.mooncat_theme_saved else R.string.mooncat_theme_name_taken))
                    }
                    2 -> MaterialAlertDialogBuilder(context)
                        .setMessage(getLocalizedString(R.string.mooncat_theme_delete_confirm).format(theme.name))
                        .setPositiveButton(R.string.mooncat_theme_delete) { _, _ ->
                            store(loadSaved().filterNot { it.name == theme.name })
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    private fun askName(initial: String, title: Int, done: (String) -> Unit) {
        val padding = (20 * context.resources.displayMetrics.density).toInt()
        val input = EditText(context).apply {
            isSingleLine = true
            setText(initial)
            setSelection(text.length)
            filters = arrayOf(android.text.InputFilter.LengthFilter(ThemeLibrary.MAX_NAME_LENGTH))
            hint = getLocalizedString(R.string.mooncat_theme_name)
        }
        val container = LinearLayout(context).apply {
            setPadding(padding, 0, padding, 0)
            addView(input, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = ThemeLibrary.normalizeName(input.text.toString())
            if (name == null) {
                input.error = getLocalizedString(R.string.mooncat_theme_name)
            } else {
                dialog.dismiss()
                done(name)
            }
        }
    }

    private companion object {
        const val KEY = "MOONCAT_SAVED_THEMES"
    }
}
