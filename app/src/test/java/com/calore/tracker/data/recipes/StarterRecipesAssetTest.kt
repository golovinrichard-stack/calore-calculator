package com.calore.tracker.data.recipes

import com.calore.tracker.data.db.JsonColumns
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.foods.GenericFoodImporter
import com.calore.tracker.data.foods.GenericFoodImporter.Companion.toEntity
import com.calore.tracker.data.recipes.StarterRecipeImporter.Companion.toStored
import com.calore.tracker.domain.Cuisines
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Recipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** M3.4: the 30 starter recipes are complete, and every ingredient is a basic food. */
class StarterRecipesAssetTest {

    private val pack = File("src/main/assets/recipes/starter-cz.json").inputStream().use { StarterRecipeImporter.parse(it) }
    private val genericFoods = File("src/main/assets/foods/generic-cz.json").inputStream().use { GenericFoodImporter.parse(it) }

    /** Basic foods as the app stores them, by key. */
    private val foodsByKey = genericFoods.foods.associate { it.key to it.toEntity(genericFoods.datasetVersion, now = 0).toDomain() }

    @Test
    fun thirtyRecipesWithUniqueKeysAndBoxCodes() {
        assertEquals(30, pack.recipes.size)
        assertEquals(30, pack.recipes.map { it.key }.toSet().size)
        assertEquals(30, pack.recipes.map { it.shortCode }.toSet().size)
        assertTrue(pack.recipes.all { it.key.startsWith("starter:cz:") })
        assertTrue(pack.recipes.all { it.shortCode.length in 1..2 && it.shortCode.all(Char::isLetter) })
        assertTrue(pack.version >= 1)
    }

    @Test
    fun everyIngredientKeyResolves() {
        for (recipe in pack.recipes) {
            assertTrue(recipe.key, recipe.ingredients.isNotEmpty())
            for (ingredient in recipe.ingredients) {
                assertNotNull("${recipe.key}: ${ingredient.food}", foodsByKey[ingredient.food])
                assertTrue("${recipe.key}: ${ingredient.food}", ingredient.grams > 0)
            }
        }
    }

    @Test
    fun everyRecipeHasPlausibleCaloriesPerServing() {
        for (recipe in pack.recipes) {
            val uuids = recipe.ingredients.map { foodsByKey.getValue(it.food).uuid }
            val stored = recipe.toStored(existing = null, foodUuids = uuids, now = 0)
            val foods = recipe.ingredients.map { foodsByKey.getValue(it.food) }.associateBy { it.uuid }
            val nutrition = Recipes.nutrition(stored.ingredients.map { it.toDomain() }, foods, recipe.servings, learnedYield = null)
            assertEquals(recipe.key, 0, nutrition.missingFoods)
            val kcal = nutrition.perServing.kcal
            assertTrue("${recipe.key}: $kcal kcal per serving", kcal in 150.0..1200.0)
            assertNotNull(recipe.key, nutrition.perServing.protein)
        }
    }

    @Test
    fun namesAndStepsInBothLanguages() {
        for (recipe in pack.recipes) {
            assertTrue(recipe.key, recipe.nameCs.isNotBlank() && recipe.nameEn.isNotBlank())
            assertNotEquals(recipe.key, recipe.nameCs, recipe.nameEn)
            assertTrue(recipe.key, recipe.stepsCs.isNotEmpty())
            assertEquals(recipe.key, recipe.stepsCs.size, recipe.stepsEn.size)
            recipe.stepsCs.zip(recipe.stepsEn).forEach { (cs, en) ->
                assertTrue(recipe.key, cs.text.isNotBlank() && en.text.isNotBlank())
                assertNotEquals(recipe.key, cs.text, en.text)
                assertEquals(recipe.key, cs.minutes, en.minutes)
                assertEquals(recipe.key, cs.passive, en.passive)
                assertEquals(recipe.key, cs.equipment, en.equipment)
            }
        }
    }

    @Test
    fun enumsAndCuisinesAreKnown() {
        val stepEquipment = Equipment.entries.map { it.name }.toSet() + "STOVE"
        for (recipe in pack.recipes) {
            assertTrue(recipe.key, recipe.mealTypes.isNotEmpty())
            assertTrue(recipe.key, recipe.mealTypes.all { JsonColumns.enumByName<MealType>(it) != null })
            assertTrue(recipe.key, recipe.equipment.all { JsonColumns.enumByName<Equipment>(it) != null })
            assertTrue(recipe.key, recipe.cuisine == null || recipe.cuisine in Cuisines.ALL)
            assertTrue(recipe.key, recipe.stepsCs.all { it.equipment == null || it.equipment in stepEquipment })
            assertTrue(recipe.key, recipe.servings in 1..50 && recipe.fridgeDays in 1..7)
        }
    }

    @Test
    fun theMixHasEveryMealAndSomethingForEveryDiet() {
        val meals = pack.recipes.flatMap { it.mealTypes }.toSet()
        assertEquals(setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK"), meals)
        for (diet in Diet.entries) {
            val fitting = pack.recipes.count { recipe ->
                val flags = recipe.ingredients.flatMap { foodsByKey.getValue(it.food).animalFlags }.toSet()
                Recipes.fitsDiet(flags, diet)
            }
            assertTrue("$diet: $fitting recipes", fitting >= 3)
        }
    }
}
