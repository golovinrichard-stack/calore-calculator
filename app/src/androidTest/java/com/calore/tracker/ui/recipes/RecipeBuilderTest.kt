package com.calore.tracker.ui.recipes

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.R
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.ui.navigation.Routes
import com.calore.tracker.ui.theme.CaloreTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** M3.2: create a recipe in the builder, edit it, and delete it. */
@RunWith(AndroidJUnit4::class)
class RecipeBuilderTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var foods: FoodRepository
    private lateinit var recipes: RecipeRepository
    private lateinit var members: MemberRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb")
        }
        foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(userAgent = "test"), clock)
        recipes = RecipeRepository(db.recipeDao(), db.foodDao(), clock)
        members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), AppPrefs(store("a")), clock)
        runBlocking { foods.saveCustom(null, "Jasmine rice", null, Nutrients(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3), null) }
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    /** The screen on display, with its view model (built outside composition). */
    private sealed interface Screen {
        data class Editor(val viewModel: RecipeEditViewModel) : Screen
        data class Detail(val viewModel: RecipeDetailViewModel) : Screen
    }

    private fun editor(id: String? = null) = Screen.Editor(
        RecipeEditViewModel(SavedStateHandle(id?.let { mapOf(Routes.ARG_RECIPE_ID to it) } ?: emptyMap()), recipes, foods),
    )

    @Test
    fun createEditAndDeleteARecipe() {
        var screen by mutableStateOf<Screen>(editor())
        var savedId: String? = null
        var deleted = false
        compose.setContent {
            CaloreTheme(dynamicColor = false) {
                when (val current = screen) {
                    is Screen.Editor -> RecipeEditScreen(
                        onBack = {},
                        onSaved = { id, _ -> savedId = id },
                        onScanIngredient = {},
                        onCreateFood = {},
                        viewModel = current.viewModel,
                    )
                    is Screen.Detail -> RecipeDetailScreen(
                        onBack = {},
                        onEdit = {},
                        onDeleted = { deleted = true },
                        viewModel = current.viewModel,
                    )
                }
            }
        }

        // Create: a name, one ingredient from the food picker, save.
        val nameLabel = context.getString(R.string.recipe_name)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(nameLabel)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText(nameLabel)).performTextInput("Rice bowl")
        compose.onNodeWithText(context.getString(R.string.recipe_add_ingredient)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(context.getString(R.string.search_hint))).performTextInput("jasmine")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Jasmine rice")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Jasmine rice").performClick()
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        compose.waitUntil(5_000) { savedId != null }

        val id = savedId!!
        val created = runBlocking { recipes.details(id)!! }
        assertEquals("Rice bowl", created.recipe.name)
        assertEquals("R", created.recipe.shortCode)
        assertEquals(100.0, created.ingredients.single().grams, 0.0)
        assertEquals(130.0 / 4, created.nutrition.perServing.kcal, 1e-9)

        // Edit: two servings instead of four.
        savedId = null
        screen = editor(id)
        val servingsLabel = context.getString(R.string.recipe_servings_label)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Rice bowl")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText(servingsLabel)).performTextClearance()
        compose.onNode(hasSetTextAction() and hasText(servingsLabel)).performTextInput("2")
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        compose.waitUntil(5_000) { savedId != null }
        assertEquals(2, runBlocking { recipes.details(id)!!.recipe.servings })

        // Delete from the detail screen.
        screen = Screen.Detail(RecipeDetailViewModel(SavedStateHandle(mapOf(Routes.ARG_RECIPE_ID to id)), recipes, members))
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Rice bowl")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(context.getString(R.string.more_options)).performClick()
        compose.onNodeWithText(context.getString(R.string.recipe_delete)).performClick()
        compose.onNodeWithText(context.getString(R.string.delete)).performClick()
        compose.waitUntil(5_000) { deleted }
        assertNull(runBlocking { recipes.details(id) })
        assertEquals(0, runBlocking { recipes.observeAll().first().size })
    }
}
