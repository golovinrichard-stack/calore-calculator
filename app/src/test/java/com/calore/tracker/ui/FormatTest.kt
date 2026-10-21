package com.calore.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {

    @Test
    fun kcalIsRoundedAndGrouped() {
        assertEquals("1,235", formatKcal(1234.6, Locale.US))
        assertEquals("0", formatKcal(0.2, Locale.US))
    }

    @Test
    fun smallGramsKeepOneDecimal() {
        assertEquals("4.5", formatGrams(4.5, Locale.US))
        assertEquals("0.4", formatGrams(0.36, Locale.US))
        assertEquals("4,5", formatGrams(4.5, Locale.GERMANY))
    }

    @Test
    fun wholeSmallGramsDropTheDecimal() {
        assertEquals("0", formatGrams(0.0, Locale.US))
        assertEquals("0", formatGrams(0.04, Locale.US))
        assertEquals("4", formatGrams(4.0, Locale.US))
        assertEquals("4", formatGrams(4.04, Locale.US))
        assertEquals("10", formatGrams(9.96, Locale.US))
    }

    @Test
    fun largeGramsAreWhole() {
        assertEquals("12", formatGrams(12.4, Locale.US))
        assertEquals("1,000", formatGrams(999.6, Locale.US))
    }

    @Test
    fun missingGramsShowDash() {
        assertEquals(UNKNOWN_VALUE, formatGrams(null, Locale.US))
        assertEquals(UNKNOWN_VALUE, formatGrams(Double.NaN, Locale.US))
    }

    @Test
    fun progressIsClampedAndSafeWithoutGoal() {
        assertEquals(0.5f, progressFraction(1000.0, 2000), 1e-6f)
        assertEquals(1f, progressFraction(2500.0, 2000), 0f)
        assertEquals(0f, progressFraction(-10.0, 2000), 0f)
        assertEquals(0f, progressFraction(100.0, 0), 0f)
        assertEquals(0f, progressFraction(Double.NaN, 2000), 0f)
    }

    @Test
    fun editableValuesDropTrailingZeros() {
        assertEquals("12", formatEditable(12.0))
        assertEquals("12.5", formatEditable(12.5))
        assertEquals("1.8", formatEditable(1.7999999523162842))
        assertEquals("0", formatEditable(0.0))
        assertEquals("", formatEditable(null))
        assertEquals("", formatEditable(Double.NaN))
    }
}
