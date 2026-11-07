package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** M1.4 and M1.7: when reminders and the weekly check-in run, with a fixed clock. */
class ReminderScheduleTest {

    private val prague = ZoneId.of("Europe/Prague")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(prague)

    @Test
    fun checkinRunsNextMondayMorning() {
        // Friday 6 November 2026, 18:00 → Monday 9 November, 07:00.
        assertEquals(Duration.ofDays(2).plusHours(13), ReminderSchedule.untilNextCheckin(at("2026-11-06T18:00")))
    }

    @Test
    fun earlyOnMondayItRunsTheSameMorning() {
        assertEquals(Duration.ofHours(1), ReminderSchedule.untilNextCheckin(at("2026-11-09T06:00")))
    }

    @Test
    fun laterOnMondayItRunsTheMondayAfter() {
        assertEquals(Duration.ofDays(7), ReminderSchedule.untilNextCheckin(at("2026-11-09T07:00")))
        assertEquals(Duration.ofDays(6).plusHours(23), ReminderSchedule.untilNextCheckin(at("2026-11-09T08:00")))
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 2), ReminderSchedule.weekStart(LocalDate.of(2026, 11, 6)))
        assertEquals(LocalDate.of(2026, 11, 9), ReminderSchedule.weekStart(LocalDate.of(2026, 11, 9)))
        assertEquals(LocalDate.of(2026, 11, 9), ReminderSchedule.weekStart(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun measureReminderComesWeeksAfterTheLastMeasurement() {
        val delay = ReminderSchedule.untilMeasureReminder(LocalDate.of(2026, 11, 6), weeks = 4, now = at("2026-11-06T09:00"))
        // Friday 4 December 2026, 08:00.
        assertEquals(Duration.ofDays(27).plusHours(23), delay)
    }

    @Test
    fun anOverdueReminderComesNow() {
        val delay = ReminderSchedule.untilMeasureReminder(LocalDate.of(2026, 9, 1), weeks = 2, now = at("2026-11-06T09:00"))
        assertEquals(Duration.ZERO, delay)
    }
}
