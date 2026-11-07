package com.calore.tracker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * An amount of money in the currency's smallest unit: hellers (haléře) for Czech crowns,
 * 1/100 Kč. Never a floating-point amount, so sums are exact.
 */
data class Money(val halers: Long, val currency: String = DEFAULT_CURRENCY) {

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Can't add $currency and ${other.currency}" }
        return Money(Math.addExact(halers, other.halers), currency)
    }

    /** Scales the amount, for example a price per pack times 1.5 packs. Halves round away from zero. */
    operator fun times(factor: Double): Money {
        require(factor.isFinite()) { "Factor must be finite" }
        val scaled = BigDecimal.valueOf(halers)
            .multiply(BigDecimal.valueOf(factor))
            .setScale(0, RoundingMode.HALF_UP)
        return Money(scaled.longValueExact(), currency)
    }

    /**
     * The amount with two decimals, grouping and the currency symbol where the [locale] puts it:
     * `139,90 Kč` in Czech, `CZK 139.90` in English. The space before or after the symbol is
     * non-breaking, so the amount never wraps onto two lines.
     */
    fun format(locale: Locale = Locale.getDefault()): String {
        val currencyInfo = Currency.getInstance(currency)
        val digits = currencyInfo.defaultFractionDigits.coerceAtLeast(0)
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            isGroupingUsed = true
        }.format(BigDecimal.valueOf(halers, MINOR_DIGITS))
        val symbol = currencyInfo.getSymbol(locale)
        val pattern = (NumberFormat.getCurrencyInstance(locale) as? DecimalFormat)?.toPattern().orEmpty()
        val symbolFirst = pattern.isEmpty() || pattern.startsWith(CURRENCY_SIGN)
        return if (symbolFirst) "$symbol$NBSP$number" else "$number$NBSP$symbol"
    }

    companion object {
        const val DEFAULT_CURRENCY = "CZK"

        /** Hellers per crown, cents per euro. */
        private const val MINOR_DIGITS = 2
        private const val CURRENCY_SIGN = "¤"
        private const val NBSP = " "

        fun zero(currency: String = DEFAULT_CURRENCY) = Money(0, currency)
    }
}
