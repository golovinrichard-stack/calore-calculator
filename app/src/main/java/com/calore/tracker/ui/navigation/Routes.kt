package com.calore.tracker.ui.navigation

import android.net.Uri
import com.calore.tracker.domain.MealType
import java.time.LocalDate

object Routes {
    const val ARG_DATE = "date"
    const val ARG_MEAL = "meal"
    const val ARG_FOOD_ID = "foodId"
    const val ARG_NAME = "name"

    /** Food id value meaning "create a new food" in the custom food route. */
    const val NEW_FOOD_ID = -1L

    const val DIARY = "diary"
    const val WEIGHT = "weight"
    const val GOALS = "goals"
    const val CALCULATOR = "calculator"
    const val ABOUT = "about"
    const val BACKUP = "backup"

    const val ADD_FOOD = "add_food/{$ARG_DATE}/{$ARG_MEAL}"
    const val FOOD_DETAIL = "food/{$ARG_FOOD_ID}/{$ARG_DATE}/{$ARG_MEAL}"
    const val CUSTOM_FOOD = "custom_food/{$ARG_DATE}/{$ARG_MEAL}?$ARG_FOOD_ID={$ARG_FOOD_ID}&$ARG_NAME={$ARG_NAME}"

    fun addFood(date: LocalDate, meal: MealType) = "add_food/$date/${meal.name}"

    fun foodDetail(foodId: Long, date: LocalDate, meal: MealType) = "food/$foodId/$date/${meal.name}"

    fun customFood(date: LocalDate, meal: MealType, foodId: Long = NEW_FOOD_ID, name: String = "") =
        "custom_food/$date/${meal.name}?$ARG_FOOD_ID=$foodId&$ARG_NAME=${Uri.encode(name)}"
}
