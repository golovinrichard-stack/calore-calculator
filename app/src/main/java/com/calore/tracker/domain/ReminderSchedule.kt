package com.calore.tracker.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** When background reminders run (M1.4, M1.7). Pure so the timing can be tested with a fixed clock. */
object ReminderSchedule {
    /** The weekly check-in runs on Monday morning, in a window starting at this time. */
    val CHECKIN_TIME: LocalTime = LocalTime.of(7, 0)
    val CHECKIN_DAY: DayOfWeek = DayOfWeek.MONDAY

    /** Body-fat reminder intervals the user can pick, in weeks; 0 is off. */
    val MEASURE_WEEKS = 2..8
    const val DEFAULT_MEASURE_WEEKS = 4

    /** Time until the next Monday 07:00 after [now] (the same Monday if it is still before 07:00). */
    fun untilNextCheckin(now: ZonedDateTime): Duration {
        val todayAtTime = now.with(CHECKIN_TIME)
        val next = if (now.dayOfWeek == CHECKIN_DAY && now.isBefore(todayAtTime)) {
            todayAtTime
        } else {
            now.with(TemporalAdjusters.next(CHECKIN_DAY)).with(CHECKIN_TIME)
        }
        return Duration.between(now, next)
    }

    /** The Monday of the week [date] is in. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Time until the next measuring reminder: [weeks] after [lastMeasured], at 08:00, but never in the past. */
    fun untilMeasureReminder(lastMeasured: LocalDate, weeks: Int, now: ZonedDateTime): Duration {
        val due = lastMeasured.plusWeeks(weeks.toLong()).atTime(MEASURE_TIME).atZone(now.zone)
        return Duration.between(now, due).coerceAtLeast(Duration.ZERO)
    }

    private val MEASURE_TIME: LocalTime = LocalTime.of(8, 0)
}
