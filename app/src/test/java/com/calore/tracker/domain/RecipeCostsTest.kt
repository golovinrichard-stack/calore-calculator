package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5.8: food prices and recipe and box costs (A18). */
class RecipeCostsTest {

    @Test
    fun theFoodsPriceIsThePreferredProductsOrTheCheapest() {
        val prices = RecipeCosts.pricePerGramByFood(
            listOf(
                ProductOption("a", "lidl", "chicken", 500.0, preferred = true, pricePerGram = 0.30),
                ProductOption("b", "tesco", "chicken", 500.0, pricePerGram = 0.20),
                ProductOption("c", "tesco", "rice", 1000.0, pricePerGram = 0.05),
                ProductOption("d", "lidl", "rice", 1000.0, pricePerGram = 0.04),
                ProductOption("e", "lidl", "salt", 1000.0),
            ),
        )
        assertEquals(mapOf("chicken" to 0.30, "rice" to 0.04), prices)
    }

    @Test
    fun recipeCostPerServingAndPer100gProtein() {
        val ingredients = listOf(
            RecipeIngredient("1", "r", 0, "chicken", 500.0),
            RecipeIngredient("2", "r", 1, "rice", 300.0),
            RecipeIngredient("3", "r", 2, "herbs", 5.0, optional = true),
        )
        val nutrition = RecipeNutrition(Nutrients(kcal = 1700.0, protein = 120.0), Nutrients(kcal = 425.0), null, 800.0, 0)
        val cost = RecipeCosts.cost(ingredients, nutrition, servings = 4, prices = mapOf("chicken" to 0.2798, "rice" to 0.04))
        assertEquals(139.9 + 12.0, cost.totalHalers, 1e-9)
        assertEquals(151.9 / 4, cost.perServingHalers, 1e-9)
        assertEquals(151.9 / 120 * 100, cost.per100gProteinHalers!!, 1e-9)
        assertTrue("the optional herbs don't count", cost.complete)
        assertFalse(RecipeCosts.cost(ingredients, nutrition, 4, mapOf("chicken" to 0.28)).complete)
    }

    @Test
    fun boxCostFromItsBatches() {
        val batch = Batch("b", null, "Chili", 0, 3200.0, 3000.0, Nutrients(kcal = 120.0), costHalers = 24_000)
        val box = Box("x", "C1", null, Storage.FRIDGE, BoxState.READY, 1.0, java.time.LocalDate.of(2026, 11, 11))
        assertEquals(4000.0, BoxView(box, listOf(BoxPart("p", "x", "b", 500.0) to batch)).costHalers!!, 1e-9)
        assertEquals(null, BoxView(box, listOf(BoxPart("p", "x", "b", 500.0) to batch.copy(costHalers = null))).costHalers)
    }
}
