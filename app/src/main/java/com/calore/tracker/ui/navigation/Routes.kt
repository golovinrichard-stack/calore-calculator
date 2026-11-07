package com.calore.tracker.ui.navigation

import android.net.Uri
import com.calore.tracker.domain.MealType
import java.time.LocalDate

object Routes {
    const val ARG_DATE = "date"
    const val ARG_MEAL = "meal"
    const val ARG_FOOD_ID = "foodId"
    const val ARG_NAME = "name"
    const val ARG_BARCODE = "barcode"
    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_RECIPE_IDS = "recipeIds"
    const val ARG_MEMBER_ID = "memberId"
    const val ARG_PLAN_DATE = "planDate"

    /** Where a picked food's row id goes back to the screen below (the recipe editor). */
    const val RESULT_PICKED_FOOD_ID = "picked_food_id"

    /** Food id value meaning "create a new food" in the custom food route. */
    const val NEW_FOOD_ID = -1L

    const val DIARY = "diary"
    const val ME = "me"
    const val WEIGHT = "weight"
    const val GOALS = "goals"
    const val SETTINGS = "settings"
    const val SETUP = "setup"
    const val PROFILE = "profile"
    const val BODY_FAT = "body_fat"
    const val BODY_FAT_TUTORIAL = "body_fat_tutorial"
    const val CHECKIN = "checkin"
    const val CALCULATOR = "calculator"
    const val ABOUT = "about"
    const val BACKUP = "backup"
    const val PLAN = "plan"
    const val POTS = "pots"
    const val KITCHEN = "kitchen"
    const val WASTE = "waste"
    const val COOK = "cook?$ARG_RECIPE_ID={$ARG_RECIPE_ID}&$ARG_PLAN_DATE={$ARG_PLAN_DATE}"
    const val COOK_MODE = "cook_mode/{$ARG_RECIPE_IDS}"
    const val PEOPLE = "people"
    const val SHOPS = "shops"
    const val SHOP = "shop"
    const val STATS = "stats"
    const val PERSON = "person?$ARG_MEMBER_ID={$ARG_MEMBER_ID}"
    const val RECIPE = "recipe/{$ARG_RECIPE_ID}"
    const val RECIPE_EDIT = "recipe_edit?$ARG_RECIPE_ID={$ARG_RECIPE_ID}"
    const val SCANNER_PICK = "scanner_pick"
    const val CUSTOM_FOOD_PICK = "custom_food_pick?$ARG_NAME={$ARG_NAME}&$ARG_BARCODE={$ARG_BARCODE}"

    const val ADD_FOOD = "add_food/{$ARG_DATE}/{$ARG_MEAL}"
    const val FOOD_DETAIL = "food/{$ARG_FOOD_ID}/{$ARG_DATE}/{$ARG_MEAL}"
    const val CUSTOM_FOOD =
        "custom_food/{$ARG_DATE}/{$ARG_MEAL}?$ARG_FOOD_ID={$ARG_FOOD_ID}&$ARG_NAME={$ARG_NAME}&$ARG_BARCODE={$ARG_BARCODE}"
    const val SCANNER = "scanner/{$ARG_DATE}/{$ARG_MEAL}"

    fun recipe(id: String) = "recipe/$id"

    fun person(id: String? = null) = if (id == null) "person" else "person?$ARG_MEMBER_ID=$id"

    fun cookMode(recipeIds: List<String>) = "cook_mode/${recipeIds.joinToString(",")}"

    fun cook(recipeId: String? = null) = if (recipeId == null) "cook" else "cook?$ARG_RECIPE_ID=$recipeId"

    /** M5.4: cook everything the plan needs on [date]. */
    fun cookFromPlan(date: LocalDate) = "cook?$ARG_PLAN_DATE=$date"

    fun recipeEdit(id: String? = null) = if (id == null) "recipe_edit" else "recipe_edit?$ARG_RECIPE_ID=$id"

    fun customFoodPick(name: String = "", barcode: String = "") =
        "custom_food_pick?$ARG_NAME=${Uri.encode(name)}&$ARG_BARCODE=${Uri.encode(barcode)}"

    fun scanner(date: LocalDate, meal: MealType) = "scanner/$date/${meal.name}"

    fun addFood(date: LocalDate, meal: MealType) = "add_food/$date/${meal.name}"

    fun foodDetail(foodId: Long, date: LocalDate, meal: MealType) = "food/$foodId/$date/${meal.name}"

    fun customFood(date: LocalDate, meal: MealType, foodId: Long = NEW_FOOD_ID, name: String = "", barcode: String = "") =
        "custom_food/$date/${meal.name}?$ARG_FOOD_ID=$foodId&$ARG_NAME=${Uri.encode(name)}&$ARG_BARCODE=${Uri.encode(barcode)}"
}
