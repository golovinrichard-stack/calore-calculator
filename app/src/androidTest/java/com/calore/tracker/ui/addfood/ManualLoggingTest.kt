package com.calore.tracker.ui.addfood

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.R
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.data.repository.DiaryRepository
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.ui.fooddetail.FoodDetailScreen
import com.calore.tracker.ui.fooddetail.FoodDetailViewModel
import com.calore.tracker.ui.navigation.Routes
import com.calore.tracker.ui.theme.CaloreTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** M2.6: the manual flow still works: search → pick → grams → log. */
@RunWith(AndroidJUnit4::class)
class ManualLoggingTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 11, 6)
    private lateinit var db: CaloreDatabase
    private lateinit var foods: FoodRepository
    private lateinit var diary: DiaryRepository
    private var riceId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(userAgent = "test"), clock)
        diary = DiaryRepository(db.diaryDao(), clock)
        riceId = runBlocking { foods.saveCustom(null, "Jasmine rice", null, Nutrients(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3), null) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun args(vararg extra: Pair<String, Any>) =
        SavedStateHandle(mapOf(Routes.ARG_DATE to day.toString(), Routes.ARG_MEAL to MealType.LUNCH.name) + extra)

    @Test
    fun searchPickGramsLog() {
        val addFood = AddFoodViewModel(args(), foods, clock, diary)
        var chosenId by mutableStateOf<Long?>(null)
        var logged = false
        val detail = FoodDetailViewModel(args(Routes.ARG_FOOD_ID to riceId), foods, diary, clock)
        compose.setContent {
            CaloreTheme(dynamicColor = false) {
                val id = chosenId
                if (id == null) {
                    AddFoodScreen(
                        onBack = {},
                        onFoodChosen = { foodId, _, _ -> chosenId = foodId },
                        onCreateCustom = { _, _, _ -> },
                        viewModel = addFood,
                    )
                } else {
                    FoodDetailScreen(
                        onBack = {},
                        onLogged = { logged = true },
                        onEdit = { _, _, _ -> },
                        onDeleted = {},
                        viewModel = detail,
                    )
                }
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("rice")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Jasmine rice")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Jasmine rice").performClick()
        assertEquals("the picked food is the one in the database", riceId, chosenId)

        val amount = context.getString(R.string.amount_label)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(amount)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(amount).performTextClearance()
        compose.onNodeWithText(amount).performTextInput("150")
        val addButton = context.getString(R.string.add_to_meal, context.getString(R.string.meal_lunch))
        compose.onNodeWithText(addButton).performClick()
        compose.waitUntil(5_000) { logged }

        val entry = runBlocking { diary.entriesFor(day).first().single() }
        assertEquals(MealType.LUNCH, entry.meal)
        assertEquals(150.0, entry.grams, 0.0)
        assertEquals(195.0, entry.nutrients.kcal, 1e-9)
    }
}
