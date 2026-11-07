package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Algorithms A8. */
class MealTargetsTest {

    @Test
    fun lunchGetsItsShareOfTheDay() {
        val daily = NutritionGoals(kcal = 2400, proteinG = 160, carbsG = 270, fatG = 70)
        val lunch = MealTargets.forMeal(daily, sharePct = 30.0)
        assertEquals(720.0, lunch.kcal, 1e-9)
        assertEquals(48.0, lunch.proteinG, 1e-9)
    }

    @Test
    fun skippedBreakfastIsSpreadOverTheOtherMeals() {
        val split = Member.DEFAULT_MEAL_SPLIT
        val withoutBreakfast = MealTargets.normalizedSplit(split, eaten = setOf(MealType.LUNCH, MealType.DINNER, MealType.SNACK))
        assertEquals(37.5, withoutBreakfast.getValue(MealType.LUNCH), 1e-9)
        assertEquals(37.5, withoutBreakfast.getValue(MealType.DINNER), 1e-9)
        assertEquals(25.0, withoutBreakfast.getValue(MealType.SNACK), 1e-9)
        assertEquals(false, MealType.BREAKFAST in withoutBreakfast)
    }

    @Test
    fun aZeroShareCountsAsSkipped() {
        val split = mapOf(MealType.BREAKFAST to 0, MealType.LUNCH to 30, MealType.DINNER to 30, MealType.SNACK to 20)
        assertEquals(37.5, MealTargets.normalizedSplit(split).getValue(MealType.LUNCH), 1e-9)
    }
}
