package com.calore.tracker.domain

/** Product barcodes (M2.4): EAN-13, EAN-8, UPC-A and UPC-E, with their check digit. */
object Barcodes {
    private val LENGTHS = setOf(8, 12, 13)

    /**
     * Digits of a typed or scanned code, or null when it isn't a valid product barcode. Spaces
     * and dashes are ignored. UPC-A (12 digits) becomes EAN-13 with a leading 0, the form Open
     * Food Facts uses.
     */
    fun normalize(text: String): String? {
        val digits = text.filterNot { it == ' ' || it == '-' }
        if (digits.isEmpty() || !digits.all { it in '0'..'9' } || digits.length !in LENGTHS) return null
        if (!hasValidCheckDigit(digits)) return null
        return if (digits.length == 12) "0$digits" else digits
    }

    /** GS1 check digit: weights 3 and 1 from the right, excluding the check digit itself. */
    fun hasValidCheckDigit(digits: String): Boolean {
        val body = digits.dropLast(1)
        val sum = body.reversed().mapIndexed { index, c -> (c - '0') * if (index % 2 == 0) 3 else 1 }.sum()
        val check = (10 - sum % 10) % 10
        return check == digits.last() - '0'
    }
}
