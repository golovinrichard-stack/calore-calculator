package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodesTest {

    @Test
    fun validEan13AndEan8() {
        assertEquals("8593807736873", Barcodes.normalize("8593807736873"))
        assertEquals("85936126", Barcodes.normalize("85936126"))
        assertEquals("spaces and dashes are ignored", "8593807736873", Barcodes.normalize("859 3807-736873"))
    }

    @Test
    fun upcABecomesEan13() {
        assertEquals("0036000291452", Barcodes.normalize("036000291452"))
    }

    @Test
    fun wrongCheckDigitLengthOrCharactersAreRejected() {
        assertNull(Barcodes.normalize("8593807736874"))
        assertNull(Barcodes.normalize("12345"))
        assertNull(Barcodes.normalize("85938077368730"))
        assertNull(Barcodes.normalize("ABCDEFGH"))
        assertNull(Barcodes.normalize(""))
        assertFalse(Barcodes.hasValidCheckDigit("40170727"))
        assertTrue(Barcodes.hasValidCheckDigit("40170725"))
    }
}
