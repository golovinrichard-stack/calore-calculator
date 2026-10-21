package com.calore.tracker.domain

/**
 * Parses a number typed by the user. Accepts both "1.5" and "1,5" because many locales
 * use a decimal comma. Returns null for blank, malformed, NaN or infinite input.
 */
fun parseDecimal(text: String): Double? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Parses a value and accepts it only when it falls inside [range]. */
fun parseDecimalIn(text: String, range: ClosedFloatingPointRange<Double>): Double? =
    parseDecimal(text)?.takeIf { it in range }

/** Parses a whole number inside [range]. Decimal input is rejected rather than truncated. */
fun parseWholeIn(text: String, range: IntRange): Int? =
    text.trim().toIntOrNull()?.takeIf { it in range }

/** Parses a portion size in grams: strictly positive and not absurdly large. */
fun parsePortionGrams(text: String): Double? =
    parseDecimal(text)?.takeIf { it > 0.0 && it <= Limits.MAX_PORTION_GRAMS }
