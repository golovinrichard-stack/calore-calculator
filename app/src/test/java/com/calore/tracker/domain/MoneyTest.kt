package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.DecimalFormatSymbols
import java.util.Locale

class MoneyTest {

    private val czech = Locale.forLanguageTag("cs-CZ")
    private val english = Locale.forLanguageTag("en")
    private val nbsp = " "

    @Test
    fun addsInHalers() {
        assertEquals(Money(13_990 + 2_550), Money(13_990) + Money(2_550))
        assertEquals("CZK", Money(1).currency)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToAddDifferentCurrencies() {
        Money(100, "CZK") + Money(100, "EUR")
    }

    @Test
    fun timesRoundsHalfUp() {
        assertEquals(Money(20_985), Money(13_990) * 1.5)
        assertEquals(Money(167), Money(333) * 0.5)
        assertEquals(Money(166), Money(333) * 0.4985)
        assertEquals(Money(1), Money(1) * 0.5)
        assertEquals(Money(0), Money(1) * 0.49)
        assertEquals("halves round away from zero", Money(-167), Money(-333) * 0.5)
        assertEquals("decimal factors are exact", Money(3), Money(10) * 0.3)
    }

    @Test
    fun formatsInCzech() {
        assertEquals("139,90${nbsp}Kč", Money(13_990).format(czech))
        assertEquals("0,05${nbsp}Kč", Money(5).format(czech))
        val group = DecimalFormatSymbols.getInstance(czech).groupingSeparator
        assertEquals("1${group}234,56${nbsp}Kč", Money(123_456).format(czech))
    }

    @Test
    fun formatsInEnglish() {
        assertEquals("CZK${nbsp}139.90", Money(13_990).format(english))
        assertEquals("CZK${nbsp}1,234.56", Money(123_456).format(english))
        assertEquals("CZK${nbsp}0.00", Money.zero().format(english))
    }
}
