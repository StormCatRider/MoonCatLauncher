package com.github.codeworkscreativehub.mlauncher.mooncat.theme

import com.github.codeworkscreativehub.mlauncher.mooncat.model.CustomTheme
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SavedTheme(val name: String, val colors: Map<String, Int>)

/** Named local themes: validation and preset generation, independent of Android storage. */
object ThemeLibrary {
    const val MAX_THEMES = 50
    const val MAX_NAME_LENGTH = 40

    /** Color keys grouped by role, so a three-color preset can fill every themable element. */
    val BACKGROUND_KEYS = listOf("BACKGROUND_COLOR", "NOTES_BACKGROUND_COLOR")
    val TEXT_KEYS = listOf(
        "APP_COLOR", "DATE_COLOR", "CLOCK_COLOR", "BATTERY_COLOR", "DAILY_WORD_COLOR",
        "BUBBLE_MESSAGE_COLOR", "INPUT_MESSAGE_COLOR"
    )
    val ACCENT_KEYS = listOf(
        "ALARM_CLOCK_COLOR", "BUBBLE_TIMEDATE_COLOR", "BUBBLE_CATEGORY_COLOR", "INPUT_MESSAGEHINT_COLOR"
    )
    const val BUBBLE_KEY = "BUBBLE_BACKGROUND_COLOR"
    val ALL_KEYS: Set<String> = (BACKGROUND_KEYS + TEXT_KEYS + ACCENT_KEYS + BUBBLE_KEY).toSet()

    fun presets(): List<SavedTheme> =
        listOf(CustomTheme.lunar(), CustomTheme.light(), CustomTheme.amoled()).map(::fromCustomTheme)

    fun fromCustomTheme(theme: CustomTheme): SavedTheme {
        require(theme.isValid())
        val background = opaque(theme.background)
        val text = opaque(theme.foreground)
        val accent = opaque(theme.accent)
        val colors = BACKGROUND_KEYS.associateWith { background } +
            TEXT_KEYS.associateWith { text } +
            ACCENT_KEYS.associateWith { accent } +
            (BUBBLE_KEY to blend(background, accent))
        return SavedTheme(theme.name, colors)
    }

    fun normalizeName(name: String): String? =
        name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH && it.none(Char::isISOControl) }

    fun isValid(theme: SavedTheme): Boolean =
        normalizeName(theme.name) == theme.name && theme.colors.isNotEmpty() && theme.colors.keys.all { it in ALL_KEYS }

    fun isValid(themes: List<SavedTheme>): Boolean =
        themes.size <= MAX_THEMES && themes.all(::isValid) &&
            themes.map { it.name.lowercase() }.distinct().size == themes.size

    /** Adds or replaces a theme by case-insensitive name. */
    fun upsert(themes: List<SavedTheme>, theme: SavedTheme): List<SavedTheme> {
        require(isValid(theme))
        val others = themes.filterNot { it.name.equals(theme.name, ignoreCase = true) }
        require(others.size < MAX_THEMES) { "Too many themes" }
        return others + theme
    }

    fun rename(themes: List<SavedTheme>, from: String, to: String): List<SavedTheme> {
        val name = requireNotNull(normalizeName(to))
        require(themes.none { !it.name.equals(from, ignoreCase = true) && it.name.equals(name, ignoreCase = true) })
        return themes.map { if (it.name == from) it.copy(name = name) else it }
    }

    /** Picks "Name (2)", "Name (3)"… so duplicates never collide with existing names. */
    fun copyName(themes: List<SavedTheme>, base: String): String {
        val taken = themes.map { it.name.lowercase() }.toSet()
        var index = 2
        while (true) {
            val suffix = " ($index)"
            val candidate = base.take(MAX_NAME_LENGTH - suffix.length).trimEnd() + suffix
            if (candidate.lowercase() !in taken) return candidate
            index++
        }
    }

    private fun opaque(hex: String): Int = (0xFF000000 or hex.substring(1).toLong(16)).toInt()

    private fun blend(a: Int, b: Int): Int {
        fun channel(shift: Int) = ((((a shr shift) and 0xFF) * 3 + ((b shr shift) and 0xFF)) / 4) shl shift
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }
}
