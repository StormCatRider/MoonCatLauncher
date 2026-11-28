package com.github.codeworkscreativehub.mlauncher.mooncat.theme

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Validated theme color files: a flat JSON object of color keys to "#AARRGGBB" strings.
 * Only known color keys are accepted, so an imported file can never overwrite other settings.
 */
object ThemeColors {
    const val MAX_FILE_CHARS = 64 * 1024
    private val HEX = Regex("^#[0-9A-Fa-f]{8}$")
    private val adapter = Moshi.Builder().build().adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )
    private val writer = Moshi.Builder().build().adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    ).indent("  ")

    class InvalidThemeException(message: String) : Exception(message)

    /**
     * Parses and validates a whole theme before anything is written.
     * Unknown keys are ignored; known keys with invalid values reject the entire file.
     */
    fun parse(json: String, allowedKeys: Set<String>): Map<String, Int> {
        if (json.length > MAX_FILE_CHARS) throw InvalidThemeException("Theme file too large")
        val raw = try {
            adapter.fromJson(json)
        } catch (error: Exception) {
            throw InvalidThemeException("Not a JSON object")
        } ?: throw InvalidThemeException("Empty theme")
        val colors = LinkedHashMap<String, Int>()
        for ((key, value) in raw) {
            if (key !in allowedKeys) continue
            if (value !is String || !HEX.matches(value)) throw InvalidThemeException("Invalid color for $key")
            colors[key] = value.substring(1).toLong(16).toInt()
        }
        if (colors.isEmpty()) throw InvalidThemeException("No theme colors found")
        return colors
    }

    fun format(colors: Map<String, Int>): String =
        writer.toJson(colors.toSortedMap().mapValues { (_, color) -> String.format("#%08X", color) })
}
