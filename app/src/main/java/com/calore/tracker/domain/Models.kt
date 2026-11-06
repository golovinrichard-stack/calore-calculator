package com.calore.tracker.domain

import java.time.LocalDate
import java.util.Locale

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
    /** Stable key of a bundled basic food, e.g. `generic:chicken_breast_raw`; null for others. */
    val key: String? = null,
    val nameCs: String? = null,
    val nameEn: String? = null,
    val section: ShopSection = ShopSection.OTHER,
    val isGeneric: Boolean = false,
    /** `usda:<fdcId>`, `off:<barcode>`, `label:<note>` or `user`. */
    val sourceRef: String? = null,
    val allergens: Set<Allergen> = emptySet(),
    val animalFlags: Set<AnimalFlag> = emptySet(),
    /** A typical pack size, used until an exact product is chosen (M5). */
    val defaultPackGrams: Double? = null,
    val storage: Storage? = null,
    /** Unopened, when the pack has no date. */
    val shelfLifeDays: Int? = null,
    /** For foods counted in pieces (eggs, rolls). */
    val gramsPerPiece: Double? = null,
) {
    /** The name in the app's language: Czech or English for basic foods, otherwise [name]. */
    fun displayName(locale: Locale = Locale.getDefault()): String = when (locale.language) {
        "cs" -> nameCs ?: nameEn ?: name
        else -> nameEn ?: nameCs ?: name
    }

    /** Name for the diary: adds the brand in parentheses unless the name already mentions it. */
    val diaryName: String
        get() {
            val shown = displayName()
            return if (brand == null || shown.contains(brand, ignoreCase = true)) shown else "$shown ($brand)"
        }
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
    /** A quick add has no food and 0 g (M2.5). */
    val entryType: EntryType = EntryType.FOOD,
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
