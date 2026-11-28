package com.github.codeworkscreativehub.mlauncher.mooncat.backup

import android.content.Context
import android.content.SharedPreferences
import com.github.codeworkscreativehub.mlauncher.BuildConfig
import com.github.codeworkscreativehub.mlauncher.data.PREFS_FILENAME
import com.github.codeworkscreativehub.mlauncher.mooncat.storage.LayoutStore

/** Reads and writes the launcher data that an encrypted backup contains. Call off the main thread. */
class BackupRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    private val layouts = LayoutStore(context)

    fun collect(): BackupPayload = BackupPayload(
        createdAtMillis = System.currentTimeMillis(),
        appVersion = BuildConfig.VERSION_NAME.take(64),
        preferences = prefs.all.mapNotNull { (key, value) -> PrefEntry.of(key, value) }.sortedBy { it.key },
        layout = layouts.loadStored()
    )

    /**
     * Replaces settings and layout with a payload that [BackupCodec.decrypt] already validated.
     * The preference file is replaced in a single commit, so a failed write leaves it unchanged.
     */
    fun apply(payload: BackupPayload) {
        check(payload.isValid())
        val decoded = payload.preferences.map { it.key to it.decoded() }
        val editor = prefs.edit().clear()
        for ((key, value) in decoded) {
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
            }
        }
        check(editor.commit()) { "Settings write failed" }
        val layout = payload.layout
        if (layout == null) check(layouts.clear()) { "Layout write failed" } else layouts.replace(layout)
    }
}
