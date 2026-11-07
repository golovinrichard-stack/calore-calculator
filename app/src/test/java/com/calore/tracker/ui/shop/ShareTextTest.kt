package com.calore.tracker.ui.shop

import org.junit.Assert.assertEquals
import org.junit.Test

/** M5.10: the list as plain text, grouped by shop. */
class ShareTextTest {

    @Test
    fun groupedByShopWithBullets() {
        val text = formatShareText(
            "Nákupní seznam",
            listOf("Lidl" to listOf("Kuřecí prsní řízky 500 g × 3", "Rýže 1 000 g"), "Bez obchodu" to listOf("Toaletní papír")),
        )
        assertEquals("Nákupní seznam\n\nLidl:\n• Kuřecí prsní řízky 500 g × 3\n• Rýže 1 000 g\n\nBez obchodu:\n• Toaletní papír", text)
    }
}
