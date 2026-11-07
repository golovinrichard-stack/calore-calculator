package com.calore.tracker.ui.recipes

import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
import com.calore.tracker.domain.RecipeStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** M3.2: the builder's rules (validation, live nutrition, both languages of starter recipes). */
class RecipeFormTest {

    private val czech = Locale.forLanguageTag("cs")

    private fun food(uuid: String, kcal: Double, protein: Double? = null, allergens: Set<Allergen> = emptySet()) = Food(
        id = 1,
        name = uuid,
        brand = null,
        barcode = null,
        per100g = Nutrients(kcal = kcal, protein = protein),
        servingGrams = null,
        source = FoodSource.CUSTOM,
        uuid = uuid,
        allergens = allergens,
    )

    private val pasta = food("pasta", kcal = 350.0, protein = 12.0, allergens = setOf(Allergen.GLUTEN))
    private val tuna = food("tuna", kcal = 100.0, protein = 20.0, allergens = setOf(Allergen.FISH))

    private fun row(food: Food, grams: String) = IngredientDraft(id = "", foodUuid = food.uuid, food = food, gramsText = grams)

    private val valid = RecipeForm(
        name = "Těstoviny s tuňákem",
        servingsText = "2",
        shortCode = "T",
        ingredients = listOf(row(pasta, "200"), row(tuna, "100")),
    )

    @Test
    fun aNameAnIngredientAndPositiveGramsAreRequired() {
        assertTrue(valid.invalidFields().isEmpty())
        assertEquals(setOf(RecipeField.NAME), valid.copy(name = " ").invalidFields())
        assertEquals(setOf(RecipeField.INGREDIENTS), valid.copy(ingredients = emptyList()).invalidFields())
        val zeroGrams = valid.copy(ingredients = listOf(row(pasta, "0"), row(tuna, "100")))
        assertEquals(setOf(RecipeField.INGREDIENTS), zeroGrams.invalidFields())
        assertEquals(setOf(0), zeroGrams.badGramsRows)
        assertEquals(setOf(RecipeField.SERVINGS), valid.copy(servingsText = "0").invalidFields())
        assertEquals(setOf(RecipeField.SHORT_CODE), valid.copy(shortCode = "C12").invalidFields())
        assertEquals(setOf(RecipeField.FREEZER_MONTHS), valid.copy(freezable = true, freezerMonthsText = "").invalidFields())
        assertNull(valid.copy(name = "").toRecipe(czech))
    }

    @Test
    fun nutritionUpdatesLiveAndSkipsRowsWithoutGrams() {
        val nutrition = valid.nutrition()
        assertEquals(800.0, nutrition.total.kcal, 1e-9)
        assertEquals(400.0, nutrition.perServing.kcal, 1e-9)
        assertEquals(22.0, nutrition.perServing.protein!!, 1e-9)

        val typing = valid.copy(ingredients = listOf(row(pasta, "200"), row(tuna, "")))
        assertEquals(700.0, typing.nutrition().total.kcal, 1e-9)
        assertEquals("allergens count even before grams are typed", setOf(Allergen.GLUTEN, Allergen.FISH), typing.allergens())
    }

    @Test
    fun theBoxCodeFollowsTheNameUntilTheUserTypesOne() {
        val form = RecipeForm().withName("Chili con carne", takenCodes = setOf("C"))
        assertEquals("CC", form.shortCode)
        val typed = form.withShortCode("x9y")
        assertEquals("XY", typed.shortCode)
        assertEquals("XY", typed.withName("Guláš", emptySet()).shortCode)
    }

    @Test
    fun savingBuildsTheRecipeWithPositionsAndTags() {
        val saved = valid.copy(
            tagsText = "rychlé, ryba, rychlé, ",
            mealTypes = setOf(MealType.LUNCH),
            steps = listOf(StepDraft(id = "", text = "Uvařte těstoviny.", minutesText = "10", passive = true), StepDraft(id = "", text = "  ")),
            reheatNote = "  ",
        ).toRecipe(czech)
        assertNotNull(saved)
        saved!!
        assertEquals("Těstoviny s tuňákem", saved.recipe.name)
        assertEquals(2, saved.recipe.servings)
        assertEquals(listOf("rychlé", "ryba"), saved.recipe.tags)
        assertNull(saved.recipe.reheatNote)
        assertEquals(listOf(0, 1), saved.ingredients.map { it.position })
        assertEquals(listOf(200.0, 100.0), saved.ingredients.map { it.grams })
        assertEquals("blank steps are dropped", 1, saved.steps.size)
        assertEquals(10, saved.steps.single().minutes)
        assertTrue(saved.steps.single().passive)
    }

    @Test
    fun aStarterRecipeKeepsBothLanguagesUntilTheTextChanges() {
        val recipe = Recipe(
            id = "r1", key = "starter:cz:test", name = "Kuře", nameCs = "Kuře", nameEn = "Chicken",
            shortCode = "K", servings = 2,
        )
        val steps = listOf(
            RecipeStep(id = "s1", recipeId = "r1", position = 0, text = "Opečte.", textCs = "Opečte.", textEn = "Fry it."),
            RecipeStep(id = "s2", recipeId = "r1", position = 1, text = "Podávejte.", textCs = "Podávejte.", textEn = "Serve."),
        )
        val details = RecipeDetails(
            recipe = recipe,
            ingredients = listOf(RecipeIngredient(id = "i1", recipeId = "r1", position = 0, foodUuid = "pasta", grams = 200.0)),
            steps = steps,
            foods = mapOf("pasta" to pasta),
        )
        val english = RecipeForm.from(details, Locale.ENGLISH)
        assertEquals("Chicken", english.name)
        assertEquals("200", english.ingredients.single().gramsText)

        val unchanged = english.toRecipe(Locale.ENGLISH)!!
        assertEquals("Kuře", unchanged.recipe.nameCs)
        assertEquals("Chicken", unchanged.recipe.nameEn)
        assertEquals("Fry it.", unchanged.steps[0].textEn)

        val renamed = english.copy(
            name = "My chicken",
            steps = english.steps.mapIndexed { i, step -> if (i == 1) step.copy(text = "Serve hot.") else step },
        ).toRecipe(Locale.ENGLISH)!!
        assertEquals("My chicken", renamed.recipe.name)
        assertNull(renamed.recipe.nameCs)
        assertNull(renamed.recipe.nameEn)
        assertEquals("the untouched step keeps Czech", "Opečte.", renamed.steps[0].textCs)
        assertEquals("Serve hot.", renamed.steps[1].text)
        assertNull(renamed.steps[1].textCs)
        assertEquals("ids are kept, so the rows are updated", listOf("s1", "s2"), renamed.steps.map { it.id })
    }
}
