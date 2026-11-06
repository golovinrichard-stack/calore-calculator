package com.calore.tracker.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class FreshnessColour { GREEN, YELLOW, RED }

/** Eat-by dates, colours, freezing and thawing (algorithms A12 and A13). Dates are local calendar dates. */
object Freshness {

    /** Thaw reminder the evening before (*tunable*). */
    val THAW_REMINDER_TIME: LocalTime = LocalTime.of(19, 0)

    /** "Is the food in the fridge?" this long after cook mode ends. */
    const val COOLING_REMINDER_HOURS = 2L

    fun eatBy(cookedDate: LocalDate, fridgeDays: Int = Recipe.DEFAULT_FRIDGE_DAYS): LocalDate = cookedDate.plusDays(fridgeDays.toLong())

    /** A box of several parts: the earliest eat-by date of its parts. */
    fun eatByOfParts(partEatBy: List<LocalDate>): LocalDate = partEatBy.min()

    /** Thawed in the fridge: eat within about a day. */
    fun eatByAfterThaw(thawDate: LocalDate): LocalDate = thawDate.plusDays(1)

    /** Red after the eat-by date, yellow on it and the day before, green otherwise. */
    fun colour(eatBy: LocalDate, today: LocalDate): FreshnessColour = when {
        eatBy.isBefore(today) -> FreshnessColour.RED
        !eatBy.isAfter(today.plusDays(1)) -> FreshnessColour.YELLOW
        else -> FreshnessColour.GREEN
    }

    /** Freezing stops the clock, but a box can't be frozen after its eat-by date. */
    fun canFreeze(eatBy: LocalDate, today: LocalDate): Boolean = !today.isAfter(eatBy)

    /** After this many months frozen, best quality is over (still safe if kept frozen). */
    fun freezerQualityOver(frozenDate: LocalDate, freezerMonths: Int, today: LocalDate): Boolean =
        !today.isBefore(frozenDate.plusMonths(freezerMonths.toLong()))

    /** A13: a box planned for after its fridge eat-by date goes to the freezer straight away. */
    fun goesToFreezer(plannedDate: LocalDate?, fridgeEatBy: LocalDate): Boolean = plannedDate != null && plannedDate.isAfter(fridgeEatBy)

    /** A13: the thaw reminder, at 19:00 the evening before the planned day. */
    fun thawReminderAt(plannedDate: LocalDate): LocalDateTime = plannedDate.minusDays(1).atTime(THAW_REMINDER_TIME)
}
