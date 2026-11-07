package com.calore.tracker.domain.plan

import com.calore.tracker.domain.PantryItem
import com.calore.tracker.domain.PantryState
import java.time.LocalDate

/** "Use it up" (M6.4): pantry food that goes off soon, and the recipes that use it. */
object UseItUp {

    /** Items with a food whose best-before date is from [today] to [today] + [days] (A15: 4), soonest first. */
    fun expiring(items: List<PantryItem>, today: LocalDate, days: Long = PlannerConfig().expiringWithinDays): List<PantryItem> =
        items.filter { item ->
            val date = item.effectiveBestBefore
            item.state == PantryState.AVAILABLE && item.foodUuid != null && date != null && !date.isBefore(today) && !date.isAfter(today.plusDays(days))
        }.sortedWith(compareBy({ it.effectiveBestBefore }, { it.name }))

    /** Recipes with the food as a (not optional) ingredient, the ones where it weighs most first. */
    fun recipesUsing(foodUuid: String, recipes: List<PlannerRecipe>): List<PlannerRecipe> = recipes
        .mapNotNull { recipe ->
            val counted = recipe.ingredients.filter { !it.optional }
            val grams = counted.filter { it.foodUuid == foodUuid }.sumOf { it.grams }
            val total = counted.sumOf { it.grams }
            if (grams > 0 && total > 0) recipe to grams / total else null
        }
        .sortedWith(compareByDescending<Pair<PlannerRecipe, Double>> { it.second }.thenBy { it.first.recipe.displayName() })
        .map { it.first }
}
