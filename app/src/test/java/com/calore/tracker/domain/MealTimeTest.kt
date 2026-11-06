package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MealTimeTest {

    @Test
    fun theMealFollowsTheTimeOfDay() {
        assertEquals(MealType.BREAKFAST, mealAt(LocalTime.of(6, 0)))
        assertEquals(MealType.BREAKFAST, mealAt(LocalTime.of(10, 29)))
        assertEquals(MealType.LUNCH, mealAt(LocalTime.of(10, 30)))
        assertEquals(MealType.LUNCH, mealAt(LocalTime.of(14, 59)))
        assertEquals(MealType.SNACK, mealAt(LocalTime.of(15, 0)))
        assertEquals(MealType.DINNER, mealAt(LocalTime.of(17, 30)))
        assertEquals(MealType.DINNER, mealAt(LocalTime.of(23, 59)))
        assertEquals(MealType.BREAKFAST, mealAt(LocalTime.MIDNIGHT))
    }
}
