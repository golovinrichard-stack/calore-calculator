package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** M3.1: recipe nutrition, allergen union and the A15 diet filter. */
class RecipesTest {

    private fun food(uuid: String, per100g: Nutrients, allergens: Set<Allergen> = emptySet(), flags: Set<AnimalFlag> = emptySet()) =
        Food(0, uuid, null, null, per100g, null, FoodSource.CUSTOM, uuid, allergens = allergens, animalFlags = flags)

    private val chicken = food("chicken", Nutrients(kcal = 120.0, protein = 22.5, carbs = 0.0, fat = 2.6), flags = setOf(AnimalFlag.MEAT))
    private val rice = food("rice", Nutrients(kcal = 365.0, protein = 7.1, carbs = 80.0, fat = 0.7, fiber = 1.3))
    private val cream = food("cream", Nutrients(kcal = 131.0, protein = 3.1), setOf(Allergen.MILK), setOf(AnimalFlag.DAIRY))
    private val foods = listOf(chicken, rice, cream).associateBy { it.uuid }

    private fun ingredient(food: String, grams: Double, optional: Boolean = false) =
        RecipeIngredient(id = newId(), recipeId = "r", position = 0, foodUuid = food, grams = grams, optional = optional)

    @Test
    fun totalsPerServingAndPer100gCooked() {
        val ingredients = listOf(ingredient("chicken", 500.0), ingredient("rice", 300.0))
        val result = Recipes.nutrition(ingredients, foods, servings = 4, learnedYield = 1.5)

        // 600 + 1,095 kcal; 112.5 + 21.3 g protein.
        assertEquals(1695.0, result.total.kcal, 1e-9)
        assertEquals(133.8, result.total.protein!!, 1e-9)
        assertEquals(423.75, result.perServing.kcal, 1e-9)
        assertEquals(800.0, result.rawGrams, 0.0)
        // 800 g raw × 1.5 = 1,200 g cooked.
        assertEquals(141.25, result.per100gCooked!!.kcal, 1e-9)
    }

    @Test
    fun unknownValuesStayUnknown() {
        val result = Recipes.nutrition(listOf(ingredient("chicken", 100.0)), foods, servings = 1, learnedYield = null)
        assertNull("no ingredient knows fibre", result.total.fiber)
        assertNull("no cooked weight yet", result.per100gCooked)

        val mixed = Recipes.nutrition(listOf(ingredient("chicken", 100.0), ingredient("rice", 100.0)), foods, 1, null)
        assertEquals("the known part is summed", 1.3, mixed.total.fiber!!, 1e-9)
    }

    @Test
    fun optionalAndMissingIngredientsAreNotCounted() {
        val result = Recipes.nutrition(
            listOf(ingredient("rice", 100.0), ingredient("cream", 50.0, optional = true), ingredient("gone", 80.0)),
            foods,
            servings = 1,
            learnedYield = null,
        )
        assertEquals(365.0, result.total.kcal, 1e-9)
        assertEquals(1, result.missingFoods)
        assertEquals(100.0, result.rawGrams, 0.0)
    }

    @Test
    fun allergensAreTheUnionIncludingOptionalIngredients() {
        val ingredients = listOf(ingredient("rice", 100.0), ingredient("cream", 20.0, optional = true))
        assertEquals(setOf(Allergen.MILK), Recipes.allergens(ingredients, foods))
        assertTrue(Recipes.allergens(listOf(ingredient("rice", 100.0)), foods).isEmpty())
        assertEquals(setOf(AnimalFlag.MEAT, AnimalFlag.DAIRY), Recipes.animalFlags(listOf(ingredient("chicken", 1.0), ingredient("cream", 1.0)), foods))
    }

    @Test
    fun dietCompatibilityForEveryDiet() {
        val none = emptySet<AnimalFlag>()
        val dairy = setOf(AnimalFlag.DAIRY, AnimalFlag.EGG)
        val fish = setOf(AnimalFlag.FISH)
        val shellfish = setOf(AnimalFlag.SHELLFISH)
        val beef = setOf(AnimalFlag.MEAT)
        val pork = setOf(AnimalFlag.MEAT, AnimalFlag.PORK)

        assertTrue(listOf(none, dairy, fish, beef, pork).all { Recipes.fitsDiet(it, Diet.OMNIVORE) })

        assertTrue(Recipes.fitsDiet(none, Diet.VEGAN))
        assertFalse(Recipes.fitsDiet(dairy, Diet.VEGAN))

        assertTrue(Recipes.fitsDiet(dairy, Diet.VEGETARIAN))
        assertFalse(Recipes.fitsDiet(fish, Diet.VEGETARIAN))
        assertFalse(Recipes.fitsDiet(shellfish, Diet.VEGETARIAN))
        assertFalse(Recipes.fitsDiet(beef, Diet.VEGETARIAN))

        assertTrue(Recipes.fitsDiet(fish, Diet.PESCATARIAN))
        assertTrue(Recipes.fitsDiet(shellfish, Diet.PESCATARIAN))
        assertFalse(Recipes.fitsDiet(beef, Diet.PESCATARIAN))

        assertTrue(Recipes.fitsDiet(beef, Diet.NO_PORK))
        assertFalse(Recipes.fitsDiet(pork, Diet.NO_PORK))
    }

    @Test
    fun shortCodesFromTheName() {
        assertEquals("C", Recipes.suggestShortCode("Chili con carne"))
        assertEquals("CC", Recipes.suggestShortCode("Chili con carne", taken = setOf("C")))
        assertEquals("KR", Recipes.suggestShortCode("Kuřecí rizoto", taken = setOf("K")))
        assertEquals("R", Recipes.suggestShortCode("   "))
    }

    @Test
    fun namesAndStepsInTheAppLanguage() {
        val recipe = Recipe(id = "r", name = "x", nameCs = "Guláš", nameEn = "Goulash", shortCode = "G", servings = 4)
        assertEquals("Guláš", recipe.displayName(Locale.forLanguageTag("cs")))
        assertEquals("Goulash", recipe.displayName(Locale.ENGLISH))
        val step = RecipeStep(id = "s", recipeId = "r", position = 0, text = "", textCs = "Osmahněte cibuli.", textEn = "Fry the onion.")
        assertEquals("Fry the onion.", step.displayText(Locale.ENGLISH))
    }
}
