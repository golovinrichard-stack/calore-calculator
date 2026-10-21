package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodDraftTest {

    private fun FoodDraftResult.valid(): FoodDraftResult.Valid {
        assertTrue("expected valid but was $this", this is FoodDraftResult.Valid)
        return this as FoodDraftResult.Valid
    }

    private fun FoodDraftResult.invalidFields(): Set<FoodField> {
        assertTrue("expected invalid but was $this", this is FoodDraftResult.Invalid)
        return (this as FoodDraftResult.Invalid).fields
    }

    @Test
    fun nameAndCaloriesAreEnough() {
        val result = FoodDraft(name = "  Apple ", kcal = "52").validate().valid()

        assertEquals("Apple", result.name)
        assertNull(result.brand)
        assertEquals(52.0, result.per100g.kcal, 0.0)
        assertNull("blank macros are unknown, not zero", result.per100g.protein)
        assertNull(result.servingGrams)
    }

    @Test
    fun fullDraftParsesEveryField() {
        val draft = FoodDraft(
            name = "Greek yogurt",
            brand = " Fage ",
            kcal = "97",
            protein = "9",
            carbs = "3,9",
            fat = "5",
            fiber = "0",
            sugar = "3.9",
            servingGrams = "170",
        )

        val result = draft.validate().valid()

        assertEquals("Fage", result.brand)
        assertEquals(Nutrients(97.0, 9.0, 3.9, 5.0, 0.0, 3.9), result.per100g)
        assertEquals(170.0, result.servingGrams!!, 0.0)
    }

    @Test
    fun missingNameAndCaloriesAreReported() {
        val fields = FoodDraft(name = " ", kcal = "").validate().invalidFields()

        assertEquals(setOf(FoodField.NAME, FoodField.KCAL), fields)
    }

    @Test
    fun overlongNameIsRejected() {
        val name = "x".repeat(Limits.MAX_NAME_LENGTH + 1)

        assertEquals(setOf(FoodField.NAME), FoodDraft(name = name, kcal = "10").validate().invalidFields())
    }

    @Test
    fun impossibleCaloriesAreRejected() {
        assertEquals(setOf(FoodField.KCAL), FoodDraft(name = "x", kcal = "1200").validate().invalidFields())
        assertEquals(setOf(FoodField.KCAL), FoodDraft(name = "x", kcal = "-1").validate().invalidFields())
    }

    @Test
    fun macrosOver100gPer100gAreRejected() {
        val fields = FoodDraft(name = "x", kcal = "500", protein = "50", carbs = "40", fat = "20")
            .validate()
            .invalidFields()

        assertEquals(setOf(FoodField.PROTEIN, FoodField.CARBS, FoodField.FAT), fields)
    }

    @Test
    fun singleNutrientAbove100IsRejected() {
        val fields = FoodDraft(name = "x", kcal = "10", sugar = "101").validate().invalidFields()

        assertEquals(setOf(FoodField.SUGAR), fields)
    }

    @Test
    fun zeroServingIsRejectedButBlankIsFine() {
        assertEquals(
            setOf(FoodField.SERVING),
            FoodDraft(name = "x", kcal = "10", servingGrams = "0").validate().invalidFields(),
        )
        assertNull(FoodDraft(name = "x", kcal = "10", servingGrams = " ").validate().valid().servingGrams)
    }
}
