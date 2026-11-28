package com.github.codeworkscreativehub.mlauncher.mooncat.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemeColorsTest {
    private val keys = setOf("BACKGROUND_COLOR", "APP_COLOR")

    @Test fun roundTripKeepsAlpha() {
        val colors = mapOf("BACKGROUND_COLOR" to 0xFF15182E.toInt(), "APP_COLOR" to 0x80C4B5FD.toInt())
        assertEquals(colors, ThemeColors.parse(ThemeColors.format(colors), keys))
    }

    @Test fun unknownKeysAreNeverImported() {
        val parsed = ThemeColors.parse("""{"APP_COLOR":"#FFFFFFFF","HOME_LOCKED":"#FF000000"}""", keys)
        assertEquals(setOf("APP_COLOR"), parsed.keys)
    }

    @Test fun invalidFilesAreRejectedWhole() {
        val bad = listOf(
            """{"APP_COLOR":"#FFF"}""",
            """{"APP_COLOR":"red"}""",
            """{"APP_COLOR":12}""",
            """{"APP_COLOR":"#FFFFFFFF","BACKGROUND_COLOR":null}""",
            """{"HOME_LOCKED":"#FF000000"}""",
            """[1,2]""",
            "not json",
            "{\"APP_COLOR\":\"" + "#".repeat(ThemeColors.MAX_FILE_CHARS) + "\"}"
        )
        for (json in bad) {
            assertThrows(json, ThemeColors.InvalidThemeException::class.java) { ThemeColors.parse(json, keys) }
        }
    }
}
