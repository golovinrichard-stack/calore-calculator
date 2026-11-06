package com.calore.tracker.domain

import java.time.LocalDate

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

enum class FoodSource { OPEN_FOOD_FACTS, CUSTOM }

/** A food the user can log. Nutrient values are per 100 g. */
data class Food(
    val id: Long,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val per100g: Nutrients,
    val servingGrams: Double?,
    val source: FoodSource,
    /** Stable id that stays the same on every phone (see [newId]). */
    val uuid: String,
) {
    /** Name for the diary: adds the brand in parentheses unless the name already mentions it. */
    val diaryName: String
        get() = if (brand == null || name.contains(brand, ignoreCase = true)) name else "$name ($brand)"
}

/**
 * One logged portion. [nutrients] is a snapshot for [grams] taken when the entry was
 * created, so editing or deleting the source food never changes past days.
 */
data class DiaryEntry(
    val id: Long,
    val date: LocalDate,
    val meal: MealType,
    val foodId: Long?,
    val name: String,
    val grams: Double,
    val nutrients: Nutrients,
    /** Epoch millis when it was logged; orders entries within a meal. */
    val loggedAt: Long,
    /** Stable id that stays the same on every phone (see [newId]). */
    val uuid: String,
    /** Who ate it; null is the phone's user. */
    val memberId: String? = null,
)

data class WeightEntry(
    val id: Long,
    val date: LocalDate,
    val kg: Double,
    /** Stable id that stays the same on every phone (see [newId]). */
    val uuid: String,
)

data class NutritionGoals(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
) {
    /** Calories implied by the macro targets, using 4/4/9 kcal per gram. */
    val kcalFromMacros: Int get() = proteinG * 4 + carbsG * 4 + fatG * 9

    companion object {
        val DEFAULT = NutritionGoals(kcal = 2000, proteinG = 150, carbsG = 175, fatG = 78)
    }
}
