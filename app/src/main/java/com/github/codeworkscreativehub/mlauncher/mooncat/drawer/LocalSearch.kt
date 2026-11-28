package com.github.codeworkscreativehub.mlauncher.mooncat.drawer

import java.text.Normalizer

/** Matching for notes and tasks in the drawer search. Runs on the device over local data only. */
object LocalSearch {
    const val MIN_QUERY = 2
    const val MAX_RESULTS = 50

    private val marks = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase().replace(Regex("\\s+"), " ").trim()

    /** Every word of the query must appear in the text, ignoring case and accents. */
    fun matches(query: String, text: String): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || normalize(query).length < MIN_QUERY) return false
        val target = normalize(text)
        return words.all { target.contains(it) }
    }

    fun <T> search(query: String, items: List<T>, text: (T) -> String): List<T> =
        items.asSequence().filter { matches(query, text(it)) }.take(MAX_RESULTS).toList()
}
