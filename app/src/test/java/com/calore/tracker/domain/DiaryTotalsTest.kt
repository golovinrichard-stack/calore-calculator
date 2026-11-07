package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiaryTotalsTest {

    private val day = LocalDate.of(2026, 10, 21)

    private fun entry(id: Long, meal: MealType, nutrients: Nutrients) = DiaryEntry(
        id = id,
        date = day,
        meal = meal,
        foodId = null,
        name = "food $id",
        grams = 100.0,
        nutrients = nutrients,
        loggedAt = id,
        uuid = newId(),
    )

    @Test
    fun emptyDayHasAllMealsAndZeroTotals() {
        val summary = DiaryTotals.summarize(emptyList())

        assertEquals(MealType.entries, summary.meals.map { it.meal })
        assertTrue(summary.meals.all { it.entries.isEmpty() })
        assertEquals(Nutrients.ZERO, summary.totals)
    }

    @Test
    fun totalsAddUpAcrossMeals() {
        val entries = listOf(
            entry(1, MealType.BREAKFAST, Nutrients(300.0, protein = 10.0, carbs = 50.0, fat = 5.0)),
            entry(2, MealType.DINNER, Nutrients(600.0, protein = 40.0, carbs = 30.0, fat = 30.0)),
            entry(3, MealType.BREAKFAST, Nutrients(100.0, protein = null, carbs = 20.0, fat = 1.0)),
        )

        val summary = DiaryTotals.summarize(entries)

        assertEquals(1000.0, summary.totals.kcal, 1e-9)
        assertEquals(50.0, summary.totals.protein!!, 1e-9)
        assertEquals(100.0, summary.totals.carbs!!, 1e-9)
        assertEquals(36.0, summary.totals.fat!!, 1e-9)

        val breakfast = summary.meals.first { it.meal == MealType.BREAKFAST }
        assertEquals(listOf(1L, 3L), breakfast.entries.map { it.id })
        assertEquals(400.0, breakfast.totals.kcal, 1e-9)

        val lunch = summary.meals.first { it.meal == MealType.LUNCH }
        assertEquals(0.0, lunch.totals.kcal, 0.0)
    }

    @Test
    fun mealSumsMatchDayTotal() {
        val entries = MealType.entries.mapIndexed { i, meal -> entry(i.toLong(), meal, Nutrients(100.0 * (i + 1))) }

        val summary = DiaryTotals.summarize(entries)

        assertEquals(summary.totals.kcal, summary.meals.sumOf { it.totals.kcal }, 1e-9)
    }
}
