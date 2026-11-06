package com.calore.tracker.data.recipes

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.foods.GenericFoodImporter
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.repository.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

/** M3.4: the real starter pack imports completely, and never overwrites the user's changes. */
@RunWith(AndroidJUnit4::class)
class StarterRecipeImporterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var prefs: AppPrefs
    private lateinit var recipes: RecipeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "p-${UUID.randomUUID()}.preferences_pb") })
        recipes = RecipeRepository(db.recipeDao(), db.foodDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun foodsImporter() = GenericFoodImporter(db.foodDao(), prefs, clock) { context.assets.open(GenericFoodImporter.ASSET_PATH) }

    private fun recipesImporter(text: String? = null) = StarterRecipeImporter(db.recipeDao(), db.foodDao(), prefs, clock) {
        text?.byteInputStream() ?: context.assets.open(StarterRecipeImporter.ASSET_PATH)
    }

    @Test
    fun allThirtyImportWithNutritionAndBothLanguages() = runTest {
        foodsImporter().importIfNewer()
        assertEquals(30, recipesImporter().importIfNewer())
        assertEquals(1, prefs.settings.first().starterRecipesVersion)

        val all = recipes.observeAll().first()
        assertEquals(30, all.size)
        for (details in all) {
            assertEquals(details.recipe.key, 0, details.nutrition.missingFoods)
            assertTrue(details.recipe.key, details.nutrition.perServing.kcal in 150.0..1200.0)
        }
        val chili = all.single { it.recipe.key == "starter:cz:chili" }
        assertEquals("Chili con carne s rýží", chili.recipe.displayName(Locale.forLanguageTag("cs")))
        assertEquals("Chili con carne with rice", chili.recipe.displayName(Locale.ENGLISH))
        val step = recipes.details(chili.recipe.id)!!.steps.first()
        assertTrue(step.displayText(Locale.ENGLISH) != step.displayText(Locale.forLanguageTag("cs")))

        assertEquals("same version: nothing to do", 0, recipesImporter().importIfNewer())
    }

    @Test
    fun aNewerPackSkipsEditedAndDeletedRecipes() = runTest {
        foodsImporter().importIfNewer()
        recipesImporter().importIfNewer()
        val all = recipes.observeAll().first()
        val edited = recipes.details(all.single { it.recipe.key == "starter:cz:chili" }.recipe.id)!!
        recipes.save(edited.recipe.copy(servings = 8), edited.ingredients, edited.steps)
        val deleted = all.single { it.recipe.key == "starter:cz:hard_boiled_eggs" }.recipe.id
        recipes.delete(deleted)

        val asset = context.assets.open(StarterRecipeImporter.ASSET_PATH).use { it.readBytes().toString(Charsets.UTF_8) }
        val newer = asset.replaceFirst("\"version\": 1", "\"version\": 2")
        assertEquals("all but the edited and the deleted one", 28, recipesImporter(newer).importIfNewer())

        assertEquals(8, recipes.details(edited.recipe.id)!!.recipe.servings)
        assertNull("stays deleted", recipes.details(deleted))
        assertEquals(29, recipes.observeAll().first().size)
    }

    @Test
    fun withoutBasicFoodsNothingIsImportedAndTheNextStartTriesAgain() = runTest {
        assertEquals(0, recipesImporter().importIfNewer())
        assertEquals(0, prefs.settings.first().starterRecipesVersion)
        foodsImporter().importIfNewer()
        assertEquals(30, recipesImporter().importIfNewer())
    }
}
