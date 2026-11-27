package com.github.codeworkscreativehub.mlauncher.mooncat.storage

import android.content.Context
import com.github.codeworkscreativehub.mlauncher.mooncat.model.LayoutSnapshot
import com.squareup.moshi.Moshi

class LayoutStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mooncat_layout", Context.MODE_PRIVATE)
    private val adapter = Moshi.Builder().build().adapter(LayoutSnapshot::class.java)

    fun update(transform: (LayoutSnapshot) -> LayoutSnapshot): LayoutSnapshot = synchronized(writeLock) {
        val updated = transform(load())
        check(save(updated)) { "Layout write failed" }
        updated
    }

    // Missing data is a fresh installation; malformed data is an error, not a silent reset.
    fun load(): LayoutSnapshot {
        val raw = prefs.getString(KEY, null) ?: return LayoutSnapshot()
        require(raw.length <= MAX_LENGTH) { "Layout exceeds size limit" }
        val snapshot = requireNotNull(adapter.fromJson(raw)) { "Missing layout" }
        require(snapshot.isValid()) { "Invalid layout" }
        return snapshot
    }

    // Call from a background dispatcher; commit reports whether the write succeeded.
    private fun save(snapshot: LayoutSnapshot): Boolean {
        require(snapshot.isValid()) { "Invalid layout" }
        val raw = adapter.toJson(snapshot)
        require(raw.length <= MAX_LENGTH) { "Layout exceeds size limit" }
        return prefs.edit().putString(KEY, raw).commit()
    }

    companion object {
        private val writeLock = Any()
        private const val KEY = "snapshot_v1"
        private const val MAX_LENGTH = 2_000_000
    }
}
