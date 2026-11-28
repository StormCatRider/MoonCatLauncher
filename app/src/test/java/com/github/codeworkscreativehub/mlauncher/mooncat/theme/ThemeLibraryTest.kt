package com.github.codeworkscreativehub.mlauncher.mooncat.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeLibraryTest {
    private val lunar = ThemeLibrary.presets().first()

    @Test fun presetsUseBrandColorsAndCoverEveryKey() {
        assertEquals(listOf("Lunar", "Light", "AMOLED"), ThemeLibrary.presets().map { it.name })
        assertEquals(ThemeLibrary.ALL_KEYS, lunar.colors.keys)
        assertEquals(0xFF15182E.toInt(), lunar.colors["BACKGROUND_COLOR"])
        assertEquals(0xFFFFFFFF.toInt(), lunar.colors["APP_COLOR"])
        assertEquals(0xFFC4B5FD.toInt(), lunar.colors["ALARM_CLOCK_COLOR"])
        assertTrue(ThemeLibrary.isValid(ThemeLibrary.presets()))
    }

    @Test fun presetsSurviveTheExportFormat() {
        assertEquals(lunar.colors, ThemeColors.parse(ThemeColors.format(lunar.colors), ThemeLibrary.ALL_KEYS))
    }

    @Test fun namesAreTrimmedBoundedAndUniqueIgnoringCase() {
        assertEquals("Noche", ThemeLibrary.normalizeName("  Noche "))
        assertNull(ThemeLibrary.normalizeName("   "))
        assertNull(ThemeLibrary.normalizeName("a".repeat(ThemeLibrary.MAX_NAME_LENGTH + 1)))
        assertNull(ThemeLibrary.normalizeName("bad\nname"))
        val themes = ThemeLibrary.upsert(emptyList(), lunar)
        val replaced = ThemeLibrary.upsert(themes, lunar.copy(name = "LUNAR"))
        assertEquals(listOf("LUNAR"), replaced.map { it.name })
        assertThrows(IllegalArgumentException::class.java) {
            ThemeLibrary.rename(themes + lunar.copy(name = "Otro"), "Otro", "lunar")
        }
    }

    @Test fun copiesGetFreeNamesWithinTheLimit() {
        val themes = listOf(lunar, lunar.copy(name = "Lunar (2)"))
        assertEquals("Lunar (3)", ThemeLibrary.copyName(themes, "Lunar"))
        val long = "x".repeat(ThemeLibrary.MAX_NAME_LENGTH)
        assertTrue(ThemeLibrary.copyName(emptyList(), long).length <= ThemeLibrary.MAX_NAME_LENGTH)
    }

    @Test fun unknownKeysAndLimitsAreRejected() {
        assertFalse(ThemeLibrary.isValid(lunar.copy(colors = mapOf("HOME_LOCKED" to 1))))
        assertFalse(ThemeLibrary.isValid(lunar.copy(colors = emptyMap())))
        val full = (1..ThemeLibrary.MAX_THEMES).map { lunar.copy(name = "T$it") }
        assertThrows(IllegalArgumentException::class.java) { ThemeLibrary.upsert(full, lunar.copy(name = "Nuevo")) }
    }
}
