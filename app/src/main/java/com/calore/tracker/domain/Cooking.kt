package com.calore.tracker.domain

import kotlin.math.floor
import kotlin.math.roundToInt

enum class BoxState { READY, EATEN, PART_EATEN, WASTED }

enum class WasteReason { EXPIRED, SPOILED, DIDNT_LIKE, OTHER }

/** Batch nutrition, weighing and learned yield (algorithms A9). */
object Batches {

    /** Before the first weighing a recipe is assumed to weigh the same cooked as raw (*tunable*). */
    const val DEFAULT_YIELD = 1.0

    /** A weighed result above this many times the raw weight is a typo, not food. */
    const val MAX_YIELD = 5.0

    /** Nutrients of the whole pot: each food's per-100 g values × grams ÷ 100, summed. */
    fun potNutrients(ingredients: List<Pair<Food, Double>>): Nutrients =
        ingredients.fold(null as Nutrients?) { sum, (food, grams) ->
            val part = food.per100g.forGramsOfPer100g(grams)
            sum?.plus(part) ?: part
        } ?: Nutrients(kcal = 0.0)

    /** Per 100 g cooked = pot nutrients ÷ cooked grams × 100. */
    fun per100gCooked(pot: Nutrients, cookedGrams: Double): Nutrients = pot.scaledBy(100.0 / cookedGrams)

    /** Net weight = full pot − empty pot; null when it is ≤ 0 or more than [MAX_YIELD] × the raw weight. */
    fun netWeight(fullGrams: Double, emptyPotGrams: Double, rawGrams: Double): Double? =
        checkCookedGrams(fullGrams - emptyPotGrams, rawGrams)

    /** A typed net weight, checked the same way. */
    fun checkCookedGrams(cookedGrams: Double, rawGrams: Double): Double? =
        cookedGrams.takeIf { it > 0.0 && it.isFinite() && (rawGrams <= 0.0 || it <= MAX_YIELD * rawGrams) }

    /** First time the latest yield; afterwards half the old and half the new. */
    fun learnedYield(previous: Double?, latest: Double): Double =
        if (previous == null) latest else 0.5 * previous + 0.5 * latest

    /** Estimated cooked weight before cooking, for planning. */
    fun estimatedCookedGrams(rawGrams: Double, learnedYield: Double?): Double = rawGrams * (learnedYield ?: DEFAULT_YIELD)
}

/** What filling boxes gives (A10): grams per box, the leftover, and whether to offer it as a box. */
data class BoxSplit(
    val boxGrams: List<Double>,
    val leftoverGrams: Double,
    /** The leftover is big enough to be its own smaller box. */
    val offerLeftoverBox: Boolean,
    /** By person: the pot was too small, so every box was scaled down by the same factor. */
    val scaledDown: Boolean = false,
)

/** Splitting a batch into boxes (algorithms A10). */
object BoxSplitter {

    /** A leftover of at least this share of a box becomes an extra smaller box. */
    const val LEFTOVER_BOX_SHARE = 0.4
    const val ROUND_TO_GRAMS = 5

    /** [count] boxes of equal weight, rounded to 5 g; the last box takes the rounding remainder. */
    fun equal(cookedGrams: Double, count: Int): BoxSplit {
        require(count > 0)
        val each = roundTo5(cookedGrams / count)
        val boxes = List(count - 1) { each } + (cookedGrams - each * (count - 1))
        return BoxSplit(boxes, leftoverGrams = 0.0, offerLeftoverBox = false)
    }

    /** As many boxes of [targetKcal] as the pot holds; the rest is a leftover. */
    fun byCalorieTarget(cookedGrams: Double, kcalPer100g: Double, targetKcal: Double): BoxSplit {
        val grams = targetKcal / (kcalPer100g / 100.0)
        val count = floor(cookedGrams / grams + EPSILON).toInt()
        val leftover = (cookedGrams - count * grams).coerceAtLeast(0.0).cleaned()
        return BoxSplit(List(count) { grams }, leftover, offerLeftoverBox = leftover >= LEFTOVER_BOX_SHARE * grams)
    }

    /**
     * One box per person and meal: grams = that meal's target ÷ density. A pot that is too small
     * scales every box down by the same factor; anything left becomes an extra box.
     */
    fun byPerson(cookedGrams: Double, kcalPer100g: Double, targetsKcal: List<Double>): BoxSplit {
        val density = kcalPer100g / 100.0
        val wanted = targetsKcal.map { it / density }
        val total = wanted.sum()
        if (total > cookedGrams) {
            val factor = cookedGrams / total
            return BoxSplit(wanted.map { it * factor }, leftoverGrams = 0.0, offerLeftoverBox = false, scaledDown = true)
        }
        val leftover = (cookedGrams - total).cleaned()
        return BoxSplit(wanted, leftover, offerLeftoverBox = leftover > 0.0)
    }

    /**
     * Box codes for [count] new boxes: [prefix] plus the next free numbers ("C1", "C2" …).
     * [activeCodes] are the codes of boxes not eaten or wasted.
     */
    fun nextCodes(prefix: String, count: Int, activeCodes: Set<String>): List<String> {
        val used = activeCodes.mapNotNull { code -> code.removePrefix(prefix).takeIf { code.startsWith(prefix) }?.toIntOrNull() }
        val start = (used.maxOrNull() ?: 0) + 1
        return List(count) { "$prefix${start + it}" }
    }

    /**
     * The code prefix for a recipe's boxes: its short code, or two letters when another recipe's
     * active boxes already use that prefix (A10). [prefixOwners] maps prefixes in use to recipe ids.
     */
    fun prefixFor(recipeId: String?, shortCode: String, name: String, prefixOwners: Map<String, String?>): String {
        val owner = prefixOwners[shortCode]
        if (!prefixOwners.containsKey(shortCode) || owner == recipeId) return shortCode
        val taken = prefixOwners.filterValues { it != recipeId }.keys
        return Recipes.suggestShortCode(name, taken + shortCode).let { if (it in taken || it == shortCode) shortCode + shortCode.last() else it }
    }

    private fun roundTo5(grams: Double): Double = (grams / ROUND_TO_GRAMS).roundToInt() * ROUND_TO_GRAMS.toDouble()

    /** Removes floating-point dust, so 3,000 − 6 × 500 is exactly 0. */
    private fun Double.cleaned(): Double = if (kotlin.math.abs(this) < EPSILON) 0.0 else this

    private const val EPSILON = 1e-9
}
