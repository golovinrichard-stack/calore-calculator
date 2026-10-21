package com.calore.tracker.domain

data class MealSummary(
    val meal: MealType,
    val entries: List<DiaryEntry>,
    val totals: Nutrients,
)

data class DaySummary(
    val totals: Nutrients,
    val meals: List<MealSummary>,
)

object DiaryTotals {
    /** Groups a day's entries by meal. Every meal is present, in order, even when empty. */
    fun summarize(entries: List<DiaryEntry>): DaySummary {
        val byMeal = entries.groupBy { it.meal }
        val meals = MealType.entries.map { meal ->
            val mealEntries = byMeal[meal].orEmpty()
            MealSummary(meal = meal, entries = mealEntries, totals = sum(mealEntries))
        }
        return DaySummary(totals = sum(entries), meals = meals)
    }

    private fun sum(entries: List<DiaryEntry>): Nutrients =
        entries.fold(Nutrients.ZERO) { acc, entry -> acc + entry.nutrients }
}
