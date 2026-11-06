package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodTest {

    private fun food(name: String, brand: String?) = Food(
        id = 1,
        name = name,
        brand = brand,
        barcode = null,
        per100g = Nutrients(kcal = 100.0),
        servingGrams = null,
        source = FoodSource.OPEN_FOOD_FACTS,
        uuid = newId(),
    )

    @Test
    fun diaryNameAddsBrand() {
        assertEquals("Greek yogurt (Fage)", food("Greek yogurt", "Fage").diaryName)
    }

    @Test
    fun diaryNameSkipsBrandAlreadyInName() {
        assertEquals("Hanaa Whole Oats", food("Hanaa Whole Oats", "Hanaa").diaryName)
        assertEquals("nutella", food("nutella", "Nutella").diaryName)
    }

    @Test
    fun diaryNameWithoutBrandIsJustTheName() {
        assertEquals("Apple", food("Apple", null).diaryName)
    }

    @Test
    fun basicFoodsShowTheNameInTheAppLanguage() {
        val chicken = food("Chicken breast, raw", null).copy(nameCs = "Kuřecí prsa, syrová", nameEn = "Chicken breast, raw")
        assertEquals("Kuřecí prsa, syrová", chicken.displayName(java.util.Locale.forLanguageTag("cs-CZ")))
        assertEquals("Chicken breast, raw", chicken.displayName(java.util.Locale.ENGLISH))
        assertEquals("Chicken breast, raw", chicken.displayName(java.util.Locale.GERMAN))
    }

    @Test
    fun foodsWithoutLocalizedNamesUseTheirName() {
        assertEquals("Hanaa Whole Oats", food("Hanaa Whole Oats", "Hanaa").displayName(java.util.Locale.forLanguageTag("cs")))
    }
}
