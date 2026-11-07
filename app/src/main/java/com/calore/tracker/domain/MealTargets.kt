package com.calore.tracker.domain

/** Meal targets (algorithms A8). */
object MealTargets {

    /**
     * The split without the meals the member skips (share 0 or not eaten), spread over the other
     * meals in proportion. Shares are percentages and add up to 100 again.
     */
    fun normalizedSplit(split: Map<MealType, Int>, eaten: Set<MealType> = MealType.entries.toSet()): Map<MealType, Double> {
        val kept = split.filter { (meal, pct) -> meal in eaten && pct > 0 }
        val total = kept.values.sum()
        if (total <= 0) return emptyMap()
        return kept.mapValues { (_, pct) -> pct * 100.0 / total }
    }

    /** `mealTarget = dailyTarget × share / 100`, for calories and each macro. */
    fun forMeal(daily: NutritionGoals, sharePct: Double): MealTarget = MealTarget(
        kcal = daily.kcal * sharePct / 100.0,
        proteinG = daily.proteinG * sharePct / 100.0,
        carbsG = daily.carbsG * sharePct / 100.0,
        fatG = daily.fatG * sharePct / 100.0,
    )
}

data class MealTarget(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double)
