package com.calore.tracker.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** One recipe to cook on one day for the plan (A15 step 4). */
data class PlannedCook(
    val date: LocalDate,
    val recipeId: String,
    /** The slots this cook fills, in eating order. */
    val slots: List<PlanSlot>,
    val targetCookedGrams: Double,
    /** Target cooked grams ÷ (raw grams × yield). */
    val scale: Double,
) {
    val portions: Int get() = slots.size
}

/** From the plan to the pot (M5.4, algorithms A15 step 4). */
object PlanCooks {

    /** Cooked kcal per 100 g, with the learned yield or the 1.0 default before the first weighing (A9). */
    fun kcalPer100gCooked(nutrition: RecipeNutrition, learnedYield: Double?): Double? {
        val cooked = Batches.estimatedCookedGrams(nutrition.rawGrams, learnedYield)
        return if (cooked > 0) nutrition.total.kcal / cooked * 100 else null
    }

    /** One slot's grams: its kcal target ÷ density; without a target, one serving of the cooked pot. */
    fun slotGrams(slot: PlanSlot, nutrition: RecipeNutrition, servings: Int, learnedYield: Double?): Double {
        val cooked = Batches.estimatedCookedGrams(nutrition.rawGrams, learnedYield)
        val density = kcalPer100gCooked(nutrition, learnedYield)
        val target = slot.target?.kcal
        return if (target != null && density != null && density > 0) target / (density / 100) else cooked / servings.coerceAtLeast(1)
    }

    /**
     * Groups the plan's box slots by the cooking day that covers them (A14) and recipe, plus cook-fresh
     * slots on their own day, with the grams each needs.
     */
    fun compute(
        slots: List<PlanSlot>,
        recipes: Map<String, Pair<Recipe, RecipeNutrition>>,
        cookingDays: List<DayOfWeek>,
        prepped: Set<MealType>,
    ): List<PlannedCook> {
        val byCook = slots.mapNotNull { slot ->
            val recipeId = slot.recipeId ?: return@mapNotNull null
            if (recipeId !in recipes) return@mapNotNull null
            val date = when (slot.kind) {
                SlotKind.BOX -> CookingSessions.coveringDay(slot.date, slot.meal, cookingDays, prepped) ?: return@mapNotNull null
                SlotKind.COOK_FRESH -> slot.date
                else -> return@mapNotNull null
            }
            (date to recipeId) to slot
        }.groupBy({ it.first }, { it.second })
        return byCook.map { (key, list) ->
            val (recipe, nutrition) = recipes.getValue(key.second)
            val ordered = list.sortedWith(compareBy({ it.date }, { CookingSessions.MEAL_ORDER.indexOf(it.meal) }, { it.memberId }))
            val grams = ordered.sumOf { slotGrams(it, nutrition, recipe.servings, recipe.learnedYield) }
            val potGrams = Batches.estimatedCookedGrams(nutrition.rawGrams, recipe.learnedYield)
            PlannedCook(key.first, key.second, ordered, grams, if (potGrams > 0) grams / potGrams else 1.0)
        }.sortedWith(compareBy({ it.date }, { it.recipeId }))
    }
}
