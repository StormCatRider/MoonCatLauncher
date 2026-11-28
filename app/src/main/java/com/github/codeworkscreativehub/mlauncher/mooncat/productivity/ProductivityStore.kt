package com.github.codeworkscreativehub.mlauncher.mooncat.productivity

import android.content.Context
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.squareup.moshi.Moshi
import java.time.LocalDateTime

/**
 * Tasks, focus apps, schedules and the Pomodoro state, kept in the launcher preferences so they
 * travel with the encrypted backup and disappear with "clear data". Nothing leaves the device.
 */
class ProductivityStore(context: Context) {
    private val prefs = Prefs(context).prefsNormal
    private val adapter = Moshi.Builder().build().adapter(ProductivityData::class.java)

    fun load(): ProductivityData = try {
        prefs.getString(KEY, null)?.let(adapter::fromJson)?.takeIf(Productivity::isValid) ?: ProductivityData()
    } catch (_: Exception) {
        ProductivityData()
    }

    fun save(data: ProductivityData): Boolean {
        if (!Productivity.isValid(data)) return false
        return prefs.edit().putString(KEY, adapter.toJson(data)).commit()
    }

    fun update(change: (ProductivityData) -> ProductivityData): ProductivityData {
        val next = change(load())
        return if (save(next)) next else load()
    }

    fun isPaused(packageName: String): Boolean =
        Productivity.isPaused(load(), packageName, LocalDateTime.now(), System.currentTimeMillis())

    companion object {
        const val KEY = "MOONCAT_PRODUCTIVITY"
    }
}
