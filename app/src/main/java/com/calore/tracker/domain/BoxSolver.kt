package com.calore.tracker.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** One part of a box made of several batches (A11): its nutrients per 100 g and its limits. */
data class BoxPartInput(
    val per100g: Nutrients,
    /** Fixed grams (for example 100 g broccoli), or null for a part the solver chooses. */
    val fixedGrams: Double? = null,
    val minGrams: Double = 0.0,
    val maxGrams: Double = BoxSolver.DEFAULT_MAX_GRAMS,
)

/** Targets for one box; a null target is skipped. */
data class BoxTargets(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
)

data class BoxSolution(
    /** Grams of every part, fixed ones included, in input order. */
    val grams: List<Double>,
    val predicted: Nutrients,
    /** True when kcal misses by more than 5 % or protein by more than 10 %. */
    val cannotReachTarget: Boolean,
)

/** The box solver (algorithms A11): projected gradient descent, then 5 g rounding and a ±5 g search. */
object BoxSolver {

    const val DEFAULT_MAX_GRAMS = 600.0
    const val MAX_FLEXIBLE_PARTS = 4
    const val ITERATIONS = 500

    /** Default weights (*tunable*). */
    const val WEIGHT_KCAL = 4.0
    const val WEIGHT_PROTEIN = 2.0
    const val WEIGHT_CARBS = 0.5
    const val WEIGHT_FAT = 0.5

    private const val KCAL_TOLERANCE = 0.05
    private const val PROTEIN_TOLERANCE = 0.10
    private const val STEP_GRAMS = 5.0

    fun solve(parts: List<BoxPartInput>, targets: BoxTargets): BoxSolution {
        val flexible = parts.indices.filter { parts[it].fixedGrams == null }
        require(flexible.size <= MAX_FLEXIBLE_PARTS) { "At most $MAX_FLEXIBLE_PARTS flexible parts" }
        val terms = terms(targets)
        val fixed = parts.filter { it.fixedGrams != null }.map { it.per100g.forGramsOfPer100g(it.fixedGrams!!) }
            .fold(Nutrients(kcal = 0.0)) { a, b -> a + b }

        // Per gram of each flexible part, for each nutrient term.
        val perGram = flexible.map { i -> terms.map { it.value(parts[i].per100g) / 100.0 } }
        val fixedValues = terms.map { it.value(fixed) }
        val lower = flexible.map { parts[it].minGrams }
        val upper = flexible.map { parts[it].maxGrams }

        fun predictedTerms(x: List<Double>) = terms.indices.map { k -> fixedValues[k] + x.indices.sumOf { i -> perGram[i][k] * x[i] } }
        fun objective(x: List<Double>): Double {
            val p = predictedTerms(x)
            return terms.indices.sumOf { k -> val r = (p[k] - terms[k].target) / terms[k].target; terms[k].weight * r * r }
        }

        // Start: equal grams that hit the kcal target (or the middle of the range without one).
        var x = startingPoint(parts, flexible, targets.kcal, fixed.kcal, lower, upper)
        if (flexible.isNotEmpty() && terms.isNotEmpty()) {
            // Step 1 / trace(H) is safe: the trace bounds the largest curvature of this quadratic.
            val trace = flexible.indices.sumOf { i -> terms.indices.sumOf { k -> 2 * terms[k].weight * perGram[i][k] * perGram[i][k] / (terms[k].target * terms[k].target) } }
            if (trace > 0) {
                val step = 1.0 / trace
                repeat(ITERATIONS) {
                    val p = predictedTerms(x)
                    x = x.indices.map { i ->
                        val gradient = terms.indices.sumOf { k -> 2 * terms[k].weight * (p[k] - terms[k].target) / (terms[k].target * terms[k].target) * perGram[i][k] }
                        (x[i] - step * gradient).coerceIn(lower[i], upper[i])
                    }
                }
            }
            // Round to 5 g, then try ±5 g on each part while that helps.
            x = x.indices.map { i -> roundTo5(x[i]).coerceIn(lower[i], upper[i]) }
            var best = objective(x)
            var improved = true
            while (improved) {
                improved = false
                for (i in x.indices) {
                    for (delta in listOf(-STEP_GRAMS, STEP_GRAMS)) {
                        val candidate = x.toMutableList().also { it[i] = (it[i] + delta).coerceIn(lower[i], upper[i]) }
                        val value = objective(candidate)
                        if (value < best - 1e-12) {
                            x = candidate
                            best = value
                            improved = true
                        }
                    }
                }
            }
        }

        val grams = parts.indices.map { i -> parts[i].fixedGrams ?: x[flexible.indexOf(i)] }
        val predicted = parts.indices.map { parts[it].per100g.forGramsOfPer100g(grams[it]) }.fold(Nutrients(kcal = 0.0)) { a, b -> a + b }
        val missesKcal = targets.kcal?.let { abs(predicted.kcal - it) / it > KCAL_TOLERANCE } ?: false
        val missesProtein = targets.protein?.let { abs((predicted.protein ?: 0.0) - it) / it > PROTEIN_TOLERANCE } ?: false
        return BoxSolution(grams, predicted, cannotReachTarget = missesKcal || missesProtein)
    }

    private class Term(val target: Double, val weight: Double, val value: (Nutrients) -> Double)

    private fun terms(targets: BoxTargets): List<Term> = listOfNotNull(
        targets.kcal?.takeIf { it > 0 }?.let { Term(it, WEIGHT_KCAL) { n -> n.kcal } },
        targets.protein?.takeIf { it > 0 }?.let { Term(it, WEIGHT_PROTEIN) { n -> n.protein ?: 0.0 } },
        targets.carbs?.takeIf { it > 0 }?.let { Term(it, WEIGHT_CARBS) { n -> n.carbs ?: 0.0 } },
        targets.fat?.takeIf { it > 0 }?.let { Term(it, WEIGHT_FAT) { n -> n.fat ?: 0.0 } },
    )

    private fun startingPoint(
        parts: List<BoxPartInput>,
        flexible: List<Int>,
        kcalTarget: Double?,
        fixedKcal: Double,
        lower: List<Double>,
        upper: List<Double>,
    ): List<Double> {
        val kcalPerGram = flexible.sumOf { parts[it].per100g.kcal / 100.0 }
        val equal = if (kcalTarget != null && kcalPerGram > 0) (kcalTarget - fixedKcal) / kcalPerGram else null
        return flexible.indices.map { i -> (equal ?: (lower[i] + upper[i]) / 2).coerceIn(lower[i], upper[i]) }
    }

    private fun roundTo5(grams: Double): Double = (grams / STEP_GRAMS).roundToInt() * STEP_GRAMS
}
