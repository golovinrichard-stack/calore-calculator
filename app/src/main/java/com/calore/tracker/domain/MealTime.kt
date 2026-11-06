package com.calore.tracker.domain

import java.time.LocalTime

/** The meal a portion most likely belongs to at this time of day, as the default when logging from a recipe. */
fun mealAt(time: LocalTime): MealType = when {
    time.isBefore(LocalTime.of(10, 30)) -> MealType.BREAKFAST
    time.isBefore(LocalTime.of(15, 0)) -> MealType.LUNCH
    time.isBefore(LocalTime.of(17, 30)) -> MealType.SNACK
    else -> MealType.DINNER
}
