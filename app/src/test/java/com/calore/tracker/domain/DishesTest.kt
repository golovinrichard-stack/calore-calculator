package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M3.5: logging 1.5 servings adds exactly 1.5 × the per-serving values. */
class DishesTest {

    private fun food(uuid: String, per100g: Nutrients) = Food(
        id = 0, name = uuid, brand = null, barcode = null, per100g = per100g, servingGrams = null,
        source = FoodSource.CUSTOM, uuid = uuid,
    )

    private val beef = food("beef", Nutrients(kcal = 215.0, protein = 18.6, carbs = 0.0, fat = 15.0, ironMg = 2.0))
    private val rice = food("rice", Nutrients(kcal = 365.0, protein = 7.13, carbs = 80.0, fat = 0.66, fiber = 1.3))
    private val ingredients = listOf(
        RecipeIngredient(id = "a", recipeId = "r", position = 0, foodUuid = "beef", grams = 800.0),
        RecipeIngredient(id = "b", recipeId = "r", position = 1, foodUuid = "rice", grams = 420.0),
    )
    private val foods = mapOf("beef" to beef, "rice" to rice)

    @Test
    fun oneAndAHalfServingsIsExactlyOneAndAHalfTimesAServing() {
        val nutrition = Recipes.nutrition(ingredients, foods, servings = 6, learnedYield = null)
        val logged = Dishes.forServings(nutrition, 1.5)
        val serving = nutrition.perServing
        assertEquals(serving.kcal * 1.5, logged.kcal, 0.0)
        assertEquals(serving.protein!! * 1.5, logged.protein!!, 0.0)
        assertEquals(serving.carbs!! * 1.5, logged.carbs!!, 0.0)
        assertEquals(serving.fat!! * 1.5, logged.fat!!, 0.0)
        assertEquals(serving.ironMg!! * 1.5, logged.ironMg!!, 0.0)
        assertEquals((1720.0 + 1533.0) / 6 * 1.5, logged.kcal, 1e-9)
    }

    @Test
    fun gramsOnlyOnceTheYieldIsKnown() {
        assertNull(Dishes.forGrams(Recipes.nutrition(ingredients, foods, 6, learnedYield = null), 400.0))
        // 1,220 g raw × 2.0 = 2,440 g cooked; 3,253 kcal in the pot.
        val cooked = Recipes.nutrition(ingredients, foods, 6, learnedYield = 2.0)
        assertEquals(3253.0 * 400.0 / 2440.0, Dishes.forGrams(cooked, 400.0)!!.kcal, 1e-9)
    }

    @Test
    fun servingsInput() {
        assertEquals(1.5, Dishes.parseServings("1,5")!!, 0.0)
        assertEquals(0.5, Dishes.parseServings("0.5")!!, 0.0)
        assertNull(Dishes.parseServings("0"))
        assertNull(Dishes.parseServings("25"))
        assertNull(Dishes.parseServings(""))
    }
}
