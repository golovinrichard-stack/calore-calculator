package com.calore.tracker.ui.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.domain.DiaryEntry
import com.calore.tracker.domain.DiaryTotals
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.ui.theme.CaloreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TodayContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 21)

    private fun state(entries: List<DiaryEntry>) = TodayUiState(
        date = day,
        today = day,
        summary = DiaryTotals.summarize(entries),
        goals = NutritionGoals(kcal = 2000, proteinG = 150, carbsG = 200, fatG = 70),
        isLoading = false,
    )

    @Test
    fun showsRemainingCaloriesAndEntries() {
        Locale.setDefault(Locale.US)
        val entries = listOf(
            DiaryEntry(1, day, MealType.BREAKFAST, null, "Porridge", 250.0, Nutrients(400.0, 15.0, 60.0, 9.0), 1, "uuid-1"),
            DiaryEntry(2, day, MealType.LUNCH, null, "Chicken salad", 300.0, Nutrients(450.0, 40.0, 10.0, 25.0), 2, "uuid-2"),
        )

        compose.setContent {
            CaloreTheme(dynamicColor = false) {
                TodayContent(state(entries), PaddingValues(), onAddFood = {}, onEntryClick = {})
            }
        }

        compose.onNodeWithText("1,150").assertIsDisplayed()
        compose.onNodeWithText("kcal left").assertIsDisplayed()
        compose.onNodeWithText("Porridge").assertIsDisplayed()
        compose.onNodeWithText("250 g · 400 kcal").assertIsDisplayed()
        compose.onNodeWithText("55 / 150 g").assertIsDisplayed()
    }

    @Test
    fun overGoalIsShown() {
        Locale.setDefault(Locale.US)
        val entries = listOf(
            DiaryEntry(1, day, MealType.DINNER, null, "Pizza", 800.0, Nutrients(2200.0), 1, "uuid-1"),
        )

        compose.setContent {
            CaloreTheme(dynamicColor = false) {
                TodayContent(state(entries), PaddingValues(), onAddFood = {}, onEntryClick = {})
            }
        }

        compose.onNodeWithText("200").assertIsDisplayed()
        compose.onNodeWithText("kcal over").assertIsDisplayed()
    }

    @Test
    fun addButtonReportsItsMeal() {
        var tapped: MealType? = null
        compose.setContent {
            CaloreTheme(dynamicColor = false) {
                TodayContent(state(emptyList()), PaddingValues(), onAddFood = { tapped = it }, onEntryClick = {})
            }
        }

        compose.onNodeWithContentDescription("Add food to Lunch").performClick()

        assertEquals(MealType.LUNCH, tapped)
    }

    @Test
    fun hideNumbersShowsFoodsWithoutCalories() {
        Locale.setDefault(Locale.US)
        val entries = listOf(
            DiaryEntry(1, day, MealType.BREAKFAST, null, "Porridge", 250.0, Nutrients(400.0, 15.0, 60.0, 9.0), 1, "uuid-1"),
        )

        compose.setContent {
            CaloreTheme(dynamicColor = false) {
                TodayContent(state(entries).copy(hideNumbers = true), PaddingValues(), onAddFood = {}, onEntryClick = {})
            }
        }

        compose.onNodeWithText("Porridge").assertIsDisplayed()
        compose.onAllNodes(androidx.compose.ui.test.hasText("kcal", substring = true)).assertCountEquals(0)
        compose.onAllNodes(androidx.compose.ui.test.hasText("1,600")).assertCountEquals(0)
    }
}
