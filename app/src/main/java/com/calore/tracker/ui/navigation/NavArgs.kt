package com.calore.tracker.ui.navigation

import androidx.lifecycle.SavedStateHandle
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.parseIsoDate
import java.time.Clock
import java.time.LocalDate

/** The diary date a screen was opened for; falls back to today if the argument is missing or bad. */
fun SavedStateHandle.dateArg(clock: Clock): LocalDate =
    parseIsoDate(get<String>(Routes.ARG_DATE)) ?: LocalDate.now(clock)

fun SavedStateHandle.mealArg(): MealType {
    val name = get<String>(Routes.ARG_MEAL)
    return MealType.entries.firstOrNull { it.name == name } ?: MealType.SNACK
}
