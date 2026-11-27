package com.github.codeworkscreativehub.mlauncher.mooncat.model

data class CustomTheme(
    val name: String,
    val background: String = "#15182E",
    val foreground: String = "#FFFFFF",
    val accent: String = "#C4B5FD",
    val fontSizeSp: Int = 20,
    val spacingDp: Int = 12,
    val iconSizeDp: Int = 24,
    val animations: Boolean = true,
    val schemaVersion: Int = 1
) {
    fun isValid(): Boolean =
        schemaVersion == 1 &&
        name.isNotBlank() && name.length <= 80 &&
        listOf(background, foreground, accent).all { COLOR.matches(it) } &&
        fontSizeSp in 12..48 && spacingDp in 0..48 && iconSizeDp in 16..64

    companion object {
        private val COLOR = Regex("#[0-9a-fA-F]{6}")
        fun lunar() = CustomTheme("Lunar")
        fun light() = CustomTheme("Light", "#FFFFFF", "#15182E", "#59469D")
        fun amoled() = CustomTheme("AMOLED", "#000000", "#FFFFFF")
    }
}
