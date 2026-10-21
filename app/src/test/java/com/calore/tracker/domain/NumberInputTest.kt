package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputTest {

    @Test
    fun acceptsDotAndCommaDecimals() {
        assertEquals(1.5, parseDecimal("1.5")!!, 0.0)
        assertEquals(1.5, parseDecimal("1,5")!!, 0.0)
        assertEquals(2.0, parseDecimal("  2 ")!!, 0.0)
    }

    @Test
    fun rejectsBlankAndMalformed() {
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("   "))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("1.2.3"))
        assertNull(parseDecimal("1,2,3"))
    }

    @Test
    fun rejectsNonFiniteValues() {
        assertNull(parseDecimal("NaN"))
        assertNull(parseDecimal("Infinity"))
        assertNull(parseDecimal("-Infinity"))
        assertNull(parseDecimal("1e400"))
    }

    @Test
    fun rangeIsInclusive() {
        assertEquals(0.0, parseDecimalIn("0", 0.0..100.0)!!, 0.0)
        assertEquals(100.0, parseDecimalIn("100", 0.0..100.0)!!, 0.0)
        assertNull(parseDecimalIn("100.01", 0.0..100.0))
        assertNull(parseDecimalIn("-0.01", 0.0..100.0))
    }

    @Test
    fun portionMustBePositiveAndBounded() {
        assertEquals(150.0, parsePortionGrams("150")!!, 0.0)
        assertEquals(Limits.MAX_PORTION_GRAMS, parsePortionGrams("10000")!!, 0.0)
        assertNull(parsePortionGrams("0"))
        assertNull(parsePortionGrams("-5"))
        assertNull(parsePortionGrams("10000.5"))
    }

    @Test
    fun wholeNumbersRejectDecimalsAndOverflow() {
        assertEquals(30, parseWholeIn("30", 15..100))
        assertEquals(15, parseWholeIn(" 15 ", 15..100))
        assertNull(parseWholeIn("30.5", 15..100))
        assertNull(parseWholeIn("14", 15..100))
        assertNull(parseWholeIn("99999999999", 15..100))
        assertNull(parseWholeIn("", 15..100))
    }
}
