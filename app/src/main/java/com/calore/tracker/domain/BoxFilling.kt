package com.calore.tracker.domain

import java.time.LocalDate

enum class FillMode { EQUAL, BY_CALORIES, BY_PERSON }

/** One box to fill for a person's meal; [memberId] null is the whole household. */
data class PersonMeal(val memberId: String?, val meal: MealType, val target: MealTarget)

/** A box as planned before saving (M4.5): its parts, owner, plan and predicted nutrients. */
data class PlannedBox(
    val parts: List<Pair<String, Double>>,
    val ownerMemberId: String?,
    val nutrients: Nutrients,
    val plannedMeal: MealType? = null,
    val plannedDate: LocalDate? = null,
    /** The solver could not reach this box's target (A11). */
    val missesTarget: Boolean = false,
) {
    val grams: Double get() = parts.sumOf { it.second }
}

data class FillResult(
    val boxes: List<PlannedBox>,
    val leftoverGrams: Double = 0.0,
    val offerLeftoverBox: Boolean = false,
    val scaledDown: Boolean = false,
    /** Mixed boxes need more of a batch than was cooked. */
    val notEnough: Set<String> = emptySet(),
)

/** A person's target for one meal (A8): their daily targets × the meal's share. */
fun Member.mealTarget(meal: MealType): MealTarget? {
    val share = MealTargets.normalizedSplit(mealSplit)[meal] ?: return null
    return MealTargets.forMeal(effectiveTargets, share)
}

/** Planning the boxes of a cook (M4.5): one batch per box (A10), or several mixed (A11). */
object BoxFilling {

    /** Boxes from one batch. With [addLeftoverBox] a leftover becomes an extra box, otherwise it is spread over the boxes. */
    fun singleBatch(
        batch: Batch,
        mode: FillMode,
        count: Int = 0,
        targetKcal: Double = 0.0,
        people: List<PersonMeal> = emptyList(),
        owner: String? = null,
        addLeftoverBox: Boolean = true,
    ): FillResult {
        val split = when (mode) {
            FillMode.EQUAL -> BoxSplitter.equal(batch.cookedGrams, count.coerceAtLeast(1))
            FillMode.BY_CALORIES -> BoxSplitter.byCalorieTarget(batch.cookedGrams, batch.per100g.kcal, targetKcal)
            FillMode.BY_PERSON -> BoxSplitter.byPerson(batch.cookedGrams, batch.per100g.kcal, people.map { it.target.kcal })
        }
        var grams = split.boxGrams
        var leftover = split.leftoverGrams
        val owners = if (mode == FillMode.BY_PERSON) people.map { it.memberId } else List(grams.size) { owner }
        val meals: List<MealType?> = if (mode == FillMode.BY_PERSON) people.map { it.meal } else List(grams.size) { null }
        val extra = leftover > 0 && (if (addLeftoverBox) split.offerLeftoverBox || mode == FillMode.BY_PERSON else false)
        if (leftover > 0 && !extra && grams.isNotEmpty()) {
            grams = grams.map { it + leftover / grams.size }
            leftover = 0.0
        }
        val boxes = grams.mapIndexed { i, g -> box(batch, g, owners.getOrNull(i) ?: owner, meals.getOrNull(i)) } +
            if (extra) listOf(box(batch, leftover, owner, null)) else emptyList()
        return FillResult(boxes, leftoverGrams = if (extra) 0.0 else leftover, offerLeftoverBox = split.offerLeftoverBox, scaledDown = split.scaledDown)
    }

    /**
     * Boxes mixed from several batches (A11): for each target, the solver picks the grams of each
     * flexible batch; [fixedGrams] are added as they are. Flags batches that run out.
     */
    fun mixed(batches: List<Batch>, fixedGrams: Map<String, Double>, targets: List<PersonMeal>): FillResult {
        val boxes = targets.map { person ->
            val solution = BoxSolver.solve(
                batches.map { BoxPartInput(it.per100g, fixedGrams = fixedGrams[it.id]) },
                BoxTargets(kcal = person.target.kcal, protein = person.target.proteinG),
            )
            PlannedBox(
                parts = batches.mapIndexed { i, batch -> batch.id to solution.grams[i] }.filter { it.second > 0 },
                ownerMemberId = person.memberId,
                nutrients = solution.predicted,
                plannedMeal = person.meal,
                missesTarget = solution.cannotReachTarget,
            )
        }
        val used = boxes.flatMap { it.parts }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val short = batches.filter { (used[it.id] ?: 0.0) > it.cookedGrams + 0.5 }.map { it.id }.toSet()
        return FillResult(boxes, notEnough = short)
    }

    /** Optional plan: one box a day from [start], for [meal]; A13 then decides fridge or freezer. */
    fun planDays(boxes: List<PlannedBox>, start: LocalDate, meal: MealType?): List<PlannedBox> =
        boxes.mapIndexed { i, box -> box.copy(plannedDate = start.plusDays(i.toLong()), plannedMeal = meal ?: box.plannedMeal) }

    private fun box(batch: Batch, grams: Double, owner: String?, meal: MealType?) =
        PlannedBox(listOf(batch.id to grams), owner, batch.per100g.forGramsOfPer100g(grams), plannedMeal = meal)
}
