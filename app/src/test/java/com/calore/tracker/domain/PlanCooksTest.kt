package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** M5.4: from the planned slots to what to cook (A15 step 4). */
class PlanCooksTest {

    private val sunday = LocalDate.of(2026, 11, 8)
    private val days = listOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY)
    private val prepped = setOf(MealType.LUNCH, MealType.DINNER)

    /** Chili: 3,600 kcal from 3,200 g raw, 3,000 g cooked → 120 kcal per 100 g. */
    private val chili = Recipe(id = "chili", name = "Chili", shortCode = "CH", servings = 6, learnedYield = 3000.0 / 3200.0)
    private val chiliNutrition = RecipeNutrition(Nutrients(kcal = 3600.0), Nutrients(kcal = 600.0), Nutrients(kcal = 120.0), 3200.0, 0)

    private fun slot(day: Long, meal: MealType, member: String, kcal: Double, kind: SlotKind = SlotKind.BOX) =
        PlanSlot("$day$meal$member", "p", sunday.plusDays(day), meal, member, kind, recipeId = "chili", target = MealTarget(kcal, 40.0, 50.0, 20.0))

    @Test
    fun boxSlotsAddUpToTheirCookingDay() {
        val slots = listOf(
            slot(1, MealType.LUNCH, "me", 600.0),
            slot(2, MealType.LUNCH, "me", 600.0),
            slot(1, MealType.LUNCH, "eva", 450.0),
            slot(2, MealType.LUNCH, "eva", 450.0),
        )
        val cook = PlanCooks.compute(slots, mapOf("chili" to (chili to chiliNutrition)), days, prepped).single()
        assertEquals(sunday, cook.date)
        assertEquals(4, cook.portions)
        assertEquals(500.0 + 500.0 + 375.0 + 375.0, cook.targetCookedGrams, 1e-9)
        assertEquals(1750.0 / 3000.0, cook.scale, 1e-9)
    }

    @Test
    fun slotsAfterTheNextCookingDayGoToThatCook() {
        val slots = listOf(slot(1, MealType.LUNCH, "me", 600.0), slot(4, MealType.LUNCH, "me", 600.0))
        val cooks = PlanCooks.compute(slots, mapOf("chili" to (chili to chiliNutrition)), days, prepped)
        assertEquals(listOf(sunday, sunday.plusDays(3)), cooks.map { it.date })
    }

    @Test
    fun cookFreshIsCookedOnItsOwnDayAndWithoutATargetOneServing() {
        val fresh = slot(5, MealType.DINNER, "me", 0.0, SlotKind.COOK_FRESH).copy(target = null)
        val cook = PlanCooks.compute(listOf(fresh), mapOf("chili" to (chili to chiliNutrition)), days, prepped).single()
        assertEquals(sunday.plusDays(5), cook.date)
        assertEquals(500.0, cook.targetCookedGrams, 1e-9)
    }

    @Test
    fun withoutAYieldTheRawWeightCounts() {
        val unweighed = chili.copy(learnedYield = null)
        assertEquals(112.5, PlanCooks.kcalPer100gCooked(chiliNutrition, null)!!, 1e-9)
        val cook = PlanCooks.compute(listOf(slot(1, MealType.LUNCH, "me", 450.0)), mapOf("chili" to (unweighed to chiliNutrition)), days, prepped).single()
        assertEquals(400.0, cook.targetCookedGrams, 1e-9)
        assertEquals(0.125, cook.scale, 1e-9)
    }
}
