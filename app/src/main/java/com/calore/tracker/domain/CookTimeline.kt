package com.calore.tracker.domain

/** One step on the cook-mode timeline (M4.4); minutes from the start. */
data class TimelineStep(
    val recipeIndex: Int,
    val stepIndex: Int,
    val step: RecipeStep,
    val start: Int,
    val end: Int,
) {
    val passive: Boolean get() = step.passive
}

/**
 * Cook mode's timeline for several recipes (M4.4). Steps keep their order within a recipe.
 * Whenever the cook is free, the next step of the recipe with the most time left starts; passive
 * steps (oven, simmering) only need starting, so the cook can do other steps meanwhile.
 */
object CookTimeline {

    /** A step without minutes still takes the cook a moment (*tunable*). */
    const val DEFAULT_STEP_MINUTES = 5

    fun schedule(recipes: List<List<RecipeStep>>): List<TimelineStep> {
        val next = IntArray(recipes.size)
        val readyAt = IntArray(recipes.size)
        val result = mutableListOf<TimelineStep>()
        var now = 0
        fun minutes(step: RecipeStep) = step.minutes?.takeIf { it > 0 } ?: DEFAULT_STEP_MINUTES
        fun timeLeft(r: Int) = recipes[r].drop(next[r]).sumOf { minutes(it) }

        while (recipes.indices.any { next[it] < recipes[it].size }) {
            val available = recipes.indices.filter { next[it] < recipes[it].size && readyAt[it] <= now }
            if (available.isEmpty()) {
                // Everything waits for a passive step: jump to the first one that ends.
                now = recipes.indices.filter { next[it] < recipes[it].size }.minOf { readyAt[it] }
                continue
            }
            val r = available.maxWith(compareBy<Int> { timeLeft(it) }.thenByDescending { it })
            val step = recipes[r][next[r]]
            val end = now + minutes(step)
            result += TimelineStep(r, next[r], step, now, end)
            next[r]++
            readyAt[r] = end
            if (!step.passive) now = end
        }
        return result.sortedWith(compareBy({ it.start }, { it.recipeIndex }))
    }

    /** Estimated total time: when the last step ends. */
    fun totalMinutes(timeline: List<TimelineStep>): Int = timeline.maxOfOrNull { it.end } ?: 0

    /** Steps running at [minute] (an active one and any passive ones). */
    fun now(timeline: List<TimelineStep>, minute: Int): List<TimelineStep> = timeline.filter { minute >= it.start && minute < it.end }

    /** The next step to start after [minute]. */
    fun next(timeline: List<TimelineStep>, minute: Int): TimelineStep? = timeline.firstOrNull { it.start > minute }
}
