package com.calore.tracker.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

const val UNKNOWN_VALUE = "—"

/** Whole kilocalories with locale grouping, e.g. "1,234". */
fun formatKcal(kcal: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%,d", kcal.roundToInt())

/** A count with locale grouping, e.g. "4,321" or "4 321", for use in plurals. */
fun formatCount(count: Int, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%,d", count)

/**
 * Grams for display: one decimal for small amounts where it matters (4.5 g),
 * whole numbers otherwise (0 g, 4 g, 12 g). Missing values show as a dash, not zero.
 */
fun formatGrams(grams: Double?, locale: Locale = Locale.getDefault()): String {
    if (grams == null || !grams.isFinite()) return UNKNOWN_VALUE
    val hasTenths = abs(grams) < 10.0 && (abs(grams) * 10).roundToInt() % 10 != 0
    return if (hasTenths) {
        String.format(locale, "%.1f", grams)
    } else {
        String.format(locale, "%,d", grams.roundToInt())
    }
}

/** Body weight with one decimal, e.g. "72.4". */
fun formatKg(kg: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.1f", kg)

/** How full a progress bar should be: 0 to 1, and 0 when there is no goal to measure against. */
fun progressFraction(value: Double, goal: Int): Float {
    if (goal <= 0 || !value.isFinite()) return 0f
    return (value / goal).toFloat().coerceIn(0f, 1f)
}

/**
 * A number to pre-fill an editable text field: no grouping, at most two decimals,
 * trailing zeros dropped ("12", "12.5"). Missing values become an empty field.
 */
fun formatEditable(value: Double?): String {
    if (value == null || !value.isFinite()) return ""
    return BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
