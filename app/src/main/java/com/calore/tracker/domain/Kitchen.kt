package com.calore.tracker.domain

import java.time.LocalDate

/** A saved pot (M4.2): the app subtracts its empty weight when the full pot is weighed. */
data class Pot(val id: String, val name: String, val emptyGrams: Double)

/** One cooked pot or tray, with its nutrients per 100 g cooked as a snapshot (M4). */
data class Batch(
    val id: String,
    val recipeId: String?,
    val name: String,
    val cookedAt: Long,
    val rawGrams: Double,
    val cookedGrams: Double,
    val per100g: Nutrients,
)

data class BoxPart(val id: String, val boxId: String, val batchId: String, val grams: Double)

/** A prep box (M4.5). */
data class Box(
    val id: String,
    val code: String,
    val ownerMemberId: String?,
    val storage: Storage,
    val state: BoxState,
    val remainingFraction: Double,
    val eatByDate: LocalDate,
    val frozenAt: Long? = null,
    val thawedAt: Long? = null,
    val plannedDate: LocalDate? = null,
    val plannedMeal: MealType? = null,
    /** The plan slot this box is for (M5.4). */
    val planSlotId: String? = null,
    val wastedReason: WasteReason? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** A box with what is in it, ready to show. A part whose batch is missing counts as nothing. */
data class BoxView(val box: Box, val parts: List<Pair<BoxPart, Batch?>>) {

    /** The whole box when it was filled. */
    val fullNutrients: Nutrients
        get() = parts.mapNotNull { (part, batch) -> batch?.per100g?.forGramsOfPer100g(part.grams) }
            .fold(Nutrients(kcal = 0.0)) { sum, n -> sum + n }

    /** What is still in the box. */
    val remainingNutrients: Nutrients get() = fullNutrients.scaledBy(box.remainingFraction)

    val fullGrams: Double get() = parts.sumOf { it.first.grams }

    /** "Chili", or "Chicken + Rice + Broccoli". */
    val name: String get() = parts.mapNotNull { it.second?.name }.distinct().joinToString(" + ")

    fun colour(today: LocalDate): FreshnessColour = Freshness.colour(box.eatByDate, today)
}

/** Eating a box (M4.7): which share of the whole box is eaten, and what is left. */
object BoxEating {

    /** Below this share left, the box counts as eaten. */
    private const val EMPTY = 0.005

    /** Eating [gramsEaten] of a box filled with [fullGrams]: the share of the whole box. */
    fun shareForGrams(gramsEaten: Double, fullGrams: Double): Double = if (fullGrams <= 0) 0.0 else gramsEaten / fullGrams

    /**
     * The state after eating [share] of the whole box (all that is left when null): the eaten
     * share (never more than what is left), the remaining fraction and the new state.
     */
    fun eat(box: Box, share: Double? = null): Triple<Double, Double, BoxState> {
        val eaten = (share ?: box.remainingFraction).coerceIn(0.0, box.remainingFraction)
        val left = box.remainingFraction - eaten
        return if (left <= EMPTY) Triple(eaten, 0.0, BoxState.EATEN) else Triple(eaten, left, BoxState.PART_EATEN)
    }
}
