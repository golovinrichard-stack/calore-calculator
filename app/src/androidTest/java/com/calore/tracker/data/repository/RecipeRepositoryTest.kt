package com.calore.tracker.data.repository


import java.time.LocalDate
import com.calore.tracker.domain.Recipes
import com.calore.tracker.domain.EntryType
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
import com.calore.tracker.domain.RecipeStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Recipes are saved with their ingredients and steps, and deleted softly (M3.1). */
@RunWith(AndroidJUnit4::class)
class RecipeRepositoryTest {

    private lateinit var db: CaloreDatabase
    private lateinit var recipes: RecipeRepository
    private lateinit var foods: FoodRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-06T18:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CaloreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recipes = RecipeRepository(db.recipeDao(), db.foodDao(), clock)
        foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(userAgent = "test"), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ingredient(foodUuid: String, grams: Double) =
        RecipeIngredient(id = "", recipeId = "", position = 0, foodUuid = foodUuid, grams = grams)

    @Test
    fun saveLoadEditAndDelete() = runTest {
        val pasta = foods.getFood(foods.saveCustom(null, "Těstoviny", null, Nutrients(350.0, 12.0, 70.0, 2.0, null, null), null))!!
        val tuna = foods.getFood(foods.saveCustom(null, "Tuňák", null, Nutrients(116.0, 26.0, 0.0, 1.0, null, null), null))!!

        val id = recipes.save(
            Recipe(id = "", name = "Těstoviny s tuňákem", shortCode = "T", servings = 2, mealTypes = setOf(MealType.LUNCH)),
            listOf(ingredient(pasta.uuid, 200.0), ingredient(tuna.uuid, 100.0)),
            listOf(RecipeStep(id = "", recipeId = "", position = 0, text = "Uvařte těstoviny.", minutes = 10)),
        )

        val saved = recipes.details(id)!!
        assertEquals(listOf(0, 1), saved.ingredients.map { it.position })
        assertEquals(2, saved.foods.size)
        assertEquals((700.0 + 116.0) / 2, saved.nutrition.perServing.kcal, 1e-9)
        assertEquals(setOf(MealType.LUNCH), saved.recipe.mealTypes)

        // Editing replaces the ingredient list completely.
        recipes.save(saved.recipe.copy(servings = 4), listOf(ingredient(pasta.uuid, 400.0)), saved.steps)
        val edited = recipes.details(id)!!
        assertEquals(1, edited.ingredients.size)
        assertEquals(4, edited.recipe.servings)
        assertEquals(saved.recipe.createdAt, edited.recipe.createdAt)
        assertEquals(listOf("T"), recipes.shortCodes().toList())
        assertEquals(1, recipes.recipesUsing(pasta.uuid))
        assertEquals(0, recipes.recipesUsing(tuna.uuid))

        recipes.delete(id)
        assertNull(recipes.details(id))
        assertTrue(recipes.observeAll().first().isEmpty())
        recipes.restore(id)
        assertEquals(1, recipes.observeAll().first().size)
    }

    @Test
    fun aDeletedFoodLeavesAGapInsteadOfBreakingTheRecipe() = runTest {
        val bread = foods.getFood(foods.saveCustom(null, "Chléb", null, Nutrients(kcal = 250.0), null))!!
        val ham = foods.getFood(foods.saveCustom(null, "Šunka", null, Nutrients(kcal = 110.0), null))!!
        val id = recipes.save(
            Recipe(id = "", name = "Chléb se šunkou", shortCode = "C", servings = 1),
            listOf(ingredient(bread.uuid, 100.0), ingredient(ham.uuid, 50.0)),
            emptyList(),
        )
        foods.deleteFood(ham.id)

        val details = recipes.observe(id).first()!!
        assertEquals(1, details.nutrition.missingFoods)
        assertEquals(250.0, details.nutrition.total.kcal, 1e-9)
    }

    @Test
    fun savingAStarterRecipeMarksItEdited() = runTest {
        val milk = foods.getFood(foods.saveCustom(null, "Mléko", null, Nutrients(kcal = 64.0), null))!!
        val id = recipes.save(
            Recipe(id = "", key = "starter:cz:test", name = "Kakao", shortCode = "K", servings = 1),
            listOf(ingredient(milk.uuid, 250.0)),
            emptyList(),
        )
        assertNotEquals("", id)
        assertTrue(recipes.details(id)!!.recipe.userEdited)
    }

    @Test
    fun aDishIsLoggedAsExactlyServingsTimesAServingAndEditsScale() = runTest {
        val beef = foods.getFood(foods.saveCustom(null, "Hovězí", null, Nutrients(215.0, 18.6, 0.0, 15.0, null, null), null))!!
        val rice = foods.getFood(foods.saveCustom(null, "Rýže", null, Nutrients(365.0, 7.1, 80.0, 0.7, null, null), null))!!
        val id = recipes.save(
            Recipe(id = "", name = "Chili", shortCode = "CH", servings = 6),
            listOf(ingredient(beef.uuid, 800.0), ingredient(rice.uuid, 420.0)),
            emptyList(),
        )
        val details = recipes.details(id)!!
        val diary = DiaryRepository(db.diaryDao(), clock)
        val day = LocalDate.of(2026, 11, 6)

        diary.logDish(details.recipe, details.nutrition, servings = 1.5, grams = null, meal = MealType.LUNCH, date = day)
        val entry = diary.entriesFor(day).first().single()
        val serving = details.nutrition.perServing
        assertEquals(EntryType.DISH, entry.entryType)
        assertEquals(id, entry.recipeId)
        assertEquals(1.5, entry.servings!!, 0.0)
        assertEquals(serving.kcal * 1.5, entry.nutrients.kcal, 0.0)
        assertEquals(serving.protein!! * 1.5, entry.nutrients.protein!!, 0.0)
        assertEquals("Chili", entry.name)

        diary.changeServings(entry, 3.0)
        val edited = diary.entriesFor(day).first().single()
        assertEquals(3.0, edited.servings!!, 0.0)
        assertEquals(serving.kcal * 3.0, edited.nutrients.kcal, 1e-9)

        assertNull("no grams without a learned yield", diary.logDish(details.recipe, details.nutrition, null, 300.0, MealType.DINNER, day))
    }

    @Test
    fun aSwapSavedAsACopyKeepsTheOriginal() = runTest {
        val beef = foods.getFood(foods.saveCustom(null, "Hovězí", null, Nutrients(kcal = 215.0), null))!!
        val turkey = foods.getFood(foods.saveCustom(null, "Krůtí", null, Nutrients(kcal = 148.0), null))!!
        val id = recipes.save(
            Recipe(id = "", key = "starter:cz:x", name = "Ragú", nameCs = "Ragú", nameEn = "Ragù", shortCode = "R", servings = 4),
            listOf(ingredient(beef.uuid, 500.0)),
            listOf(RecipeStep(id = "", recipeId = "", position = 0, text = "Vařte.", textCs = "Vařte.", textEn = "Cook.")),
        )
        val original = recipes.details(id)!!
        val copyId = recipes.saveCopy(original, Recipes.swapped(original.ingredients, 0, turkey.uuid), "Ragú (kopie)")

        assertNotEquals(id, copyId)
        assertEquals(beef.uuid, recipes.details(id)!!.ingredients.single().foodUuid)
        val copy = recipes.details(copyId)!!
        assertEquals(turkey.uuid, copy.ingredients.single().foodUuid)
        assertEquals(500.0, copy.ingredients.single().grams, 0.0)
        assertEquals("Ragú (kopie)", copy.recipe.name)
        assertNull("a copy is the user's own recipe", copy.recipe.key)
        assertEquals("Cook.", copy.steps.single().textEn)
        assertEquals(148.0 * 5 / 4, copy.nutrition.perServing.kcal, 1e-9)
    }
}
