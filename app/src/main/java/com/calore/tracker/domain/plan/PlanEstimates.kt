package com.calore.tracker.domain.plan

import com.calore.tracker.domain.Need
import com.calore.tracker.domain.PlanCooks
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.PlannedCook
import com.calore.tracker.domain.ShoppingLine
import java.time.LocalDate

/** The shopping list's estimated cost: haléře for the lines with a known price, and how many lines have none. */
data class ShoppingEstimate(val halers: Double, val unpriced: Int)

/** A15 steps 4 and 5 for a proposal: what to cook and how much, what to buy, and what it costs. */
object PlanEstimates {

    /** Step 4: the cooks of [slots] from [from] on that are not cooked yet ([cooked]: cook day and recipe). */
    fun cooks(input: PlannerInput, slots: List<PlanSlot>, from: LocalDate, cooked: Set<Pair<LocalDate, String>> = emptySet()): List<PlannedCook> =
        PlanCooks.compute(slots, input.recipes.associate { it.id to (it.recipe to it.nutrition) }, input.cookingDays, input.prepped)
            .filter { !it.date.isBefore(from) && (it.date to it.recipeId) !in cooked }

    /** What the cooks need, per food: ingredient grams × scale; optional ingredients are left out (A16 step 1). */
    fun needs(cooks: List<PlannedCook>, recipes: Map<String, PlannerRecipe>): List<Need> = cooks.flatMap { cook ->
        recipes[cook.recipeId]?.ingredients.orEmpty().filter { !it.optional }.map { Need(it.foodUuid, it.grams * cook.scale, it.section, cook.date) }
    }

    /** Lines with a product cost their packs; lines without one cost their grams at the food's price (A18). */
    fun cost(lines: List<ShoppingLine>, pricePerGramByFood: Map<String, Double>): ShoppingEstimate {
        var halers = 0.0
        var unpriced = 0
        for (line in lines) {
            val product = line.product
            val packs = line.packs
            val cost = when {
                product?.pricePerGram != null && packs != null -> packs * product.packGrams * product.pricePerGram
                else -> pricePerGramByFood[line.foodUuid]?.let { it * line.toBuyGrams }
            }
            if (cost == null) unpriced++ else halers += cost
        }
        return ShoppingEstimate(halers, unpriced)
    }

    /** Minutes of active cooking per cooking day. */
    fun sessionMinutes(cooks: List<PlannedCook>, recipes: Map<String, PlannerRecipe>): Map<LocalDate, Int> =
        cooks.groupBy { it.date }.mapValues { (_, list) -> list.map { it.recipeId }.distinct().sumOf { recipes[it]?.activeMinutes ?: 0 } }
}
