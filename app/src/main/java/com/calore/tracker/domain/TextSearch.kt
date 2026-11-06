package com.calore.tracker.domain

import java.text.Normalizer

/** Text for search matching: lower case and without accents, so "cocka" finds "Čočka". */
fun searchKey(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

/** True when every word of [query] appears in one of [texts] (accents and case ignored). */
fun matchesSearch(query: String, vararg texts: String?): Boolean {
    val words = searchKey(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val haystack = texts.filterNotNull().joinToString(" ") { searchKey(it) }
    return words.all { it in haystack }
}

private val COMBINING_MARKS = Regex("""\p{Mn}+""")
