package com.calore.tracker.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** What a planned meal is (data-model enums). */
enum class SlotKind { BOX, COOK_FRESH, EAT_OUT, FREE }

/** One meal of one person on one day (M5.3). [target] is the meal target when the slot was planned (A8). */
data class PlanSlot(
    val id: String,
    val planId: String,
    val date: LocalDate,
    val meal: MealType,
    val memberId: String,
    val kind: SlotKind,
    val recipeId: String? = null,
    val boxId: String? = null,
    val target: MealTarget? = null,
    val locked: Boolean = false,
    val done: Boolean = false,
)

/** One prepped meal a cooking session covers. */
data class CoveredMeal(val day: DayOfWeek, val meal: MealType)

/** A cooking day and the prepped meals its boxes are for (A14). */
data class SessionCoverage(val day: DayOfWeek, val meals: List<CoveredMeal>)

/** Cooking sessions (algorithms A14). */
object CookingSessions {

    /** Default days for 1–7 sessions a week (A14 table). */
    val DEFAULT_DAYS: Map<Int, List<DayOfWeek>> = mapOf(
        1 to listOf(DayOfWeek.SUNDAY),
        2 to listOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
        3 to listOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
        4 to listOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
        5 to listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        6 to DayOfWeek.entries.filter { it != DayOfWeek.SATURDAY },
        7 to DayOfWeek.entries.toList(),
    )

    /** A session happens after this meal (*tunable*): cooking in the afternoon, so dinner is the first meal it covers. */
    val SESSION_AFTER: MealType = MealType.LUNCH

    /** Meals in the order they are eaten in a day. */
    val MEAL_ORDER: List<MealType> = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER)

    /** The member's days if they picked exactly as many as sessions; otherwise the A14 defaults. */
    fun days(sessionsPerWeek: Int, picked: List<DayOfWeek>): List<DayOfWeek> {
        val n = sessionsPerWeek.coerceIn(1, 7)
        val distinct = picked.distinct()
        return if (distinct.size == n) distinct.sortedBy { (it.value % 7) } else DEFAULT_DAYS.getValue(n)
    }

    /**
     * What each session covers: the prepped meals from the first meal after the session until
     * the meal before the next session's first covered meal (A14), around the week.
     */
    fun coverage(days: List<DayOfWeek>, prepped: Set<MealType>): List<SessionCoverage> {
        if (days.isEmpty()) return emptyList()
        val slotsPerDay = MEAL_ORDER.size
        fun position(day: DayOfWeek) = (day.value % 7) * slotsPerDay + MEAL_ORDER.indexOf(SESSION_AFTER) + 1
        val sessions = days.distinct().sortedBy { position(it) }
        val total = 7 * slotsPerDay
        return sessions.mapIndexed { i, day ->
            val start = position(day)
            val next = position(sessions[(i + 1) % sessions.size])
            val length = if (sessions.size == 1) total else (next - start + total) % total
            val meals = (0 until length).map { (start + it) % total }.map { slot ->
                CoveredMeal(DayOfWeek.of(((slot / slotsPerDay) + 6) % 7 + 1), MEAL_ORDER[slot % slotsPerDay])
            }.filter { it.meal in prepped }
            SessionCoverage(day, meals)
        }
    }

    /** With one cooking day a week, about half the boxes end up frozen (A14 warning). */
    fun freezerWarning(sessionsPerWeek: Int): Boolean = sessionsPerWeek == 1

    /** The cooking day whose boxes a prepped [date] and [meal] eats from, within the week of cooking [days]. */
    fun coveringDay(date: LocalDate, meal: MealType, days: List<DayOfWeek>, prepped: Set<MealType>): LocalDate? {
        val coverage = coverage(days, prepped + meal)
        val session = coverage.firstOrNull { c -> c.meals.any { it.day == date.dayOfWeek && it.meal == meal } } ?: return null
        // The session is the latest such weekday on or before the date (the same day only if the meal comes after the session).
        var sessionDate = date
        while (sessionDate.dayOfWeek != session.day) sessionDate = sessionDate.minusDays(1)
        if (sessionDate == date && MEAL_ORDER.indexOf(meal) <= MEAL_ORDER.indexOf(SESSION_AFTER)) sessionDate = sessionDate.minusDays(7)
        return sessionDate
    }
}

/** Daily totals of a plan (A8): planned meals count with their slot targets. */
object PlanTotals {

    /** The planned calories and macros of one person's day: the targets of their box and cook-fresh slots. */
    fun planned(slots: List<PlanSlot>): MealTarget = slots.filter { it.kind == SlotKind.BOX || it.kind == SlotKind.COOK_FRESH }
        .mapNotNull { it.target }
        .fold(MealTarget(0.0, 0.0, 0.0, 0.0)) { sum, t ->
            MealTarget(sum.kcal + t.kcal, sum.proteinG + t.proteinG, sum.carbsG + t.carbsG, sum.fatG + t.fatG)
        }

    /** A box eaten after its eat-by date goes in the freezer at portioning (A13): its age in days, or null if fine. */
    fun boxAgeIfFrozen(cookDate: LocalDate, eatDate: LocalDate, fridgeDays: Int): Long? {
        val age = java.time.temporal.ChronoUnit.DAYS.between(cookDate, eatDate)
        return age.takeIf { eatDate.isAfter(Freshness.eatBy(cookDate, fridgeDays)) }
    }
}
