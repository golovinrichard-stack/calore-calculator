package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class SupplementScheduleTest {

    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 11, 9)
    private val vitaminD = Supplement(id = "d", name = "Vitamin D", kind = SupplementKind.VITAMIN_D, startDate = monday)

    @Test
    fun dueOnItsDaysBetweenStartAndEnd() {
        val weekdays = vitaminD.copy(daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), endDate = monday.plusDays(9))
        assertTrue(SupplementSchedule.isDue(weekdays, monday))
        assertFalse(SupplementSchedule.isDue(weekdays, monday.plusDays(1)))
        assertTrue(SupplementSchedule.isDue(weekdays, monday.plusDays(2)))
        assertFalse(SupplementSchedule.isDue(weekdays, monday.minusDays(7)))
        assertFalse("after the end", SupplementSchedule.isDue(weekdays, monday.plusDays(14)))
        assertFalse(SupplementSchedule.isDue(vitaminD.copy(active = false), monday))
        // Empty days of the week: every day.
        assertTrue(SupplementSchedule.isDue(vitaminD, monday.plusDays(5)))
    }

    @Test
    fun dosesAreInTimeOrderAndTheNextDoseSkipsPastOnes() {
        val twice = vitaminD.copy(times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0)))
        val doses = SupplementSchedule.doses(twice, monday, zone)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), doses.map { it.time })
        val at9 = monday.atTime(9, 0).toInstant(zone).toEpochMilli()
        assertEquals(monday.atTime(20, 0).toInstant(zone).toEpochMilli(), SupplementSchedule.nextDose(twice, at9, zone)!!.scheduledAt)
        val at21 = monday.atTime(21, 0).toInstant(zone).toEpochMilli()
        assertEquals(monday.plusDays(1).atTime(8, 0).toInstant(zone).toEpochMilli(), SupplementSchedule.nextDose(twice, at21, zone)!!.scheduledAt)
        assertNull(SupplementSchedule.nextDose(twice.copy(endDate = monday), at21, zone))
    }

    @Test
    fun regularityOver7Days() {
        val today = monday.plusDays(6)
        val doses = (0L..6L).map { SupplementSchedule.doses(vitaminD, monday.plusDays(it), zone).single() }
        val logs = doses.take(5).map { SupplementLog("t${it.date}", "d", it.scheduledAt, it.scheduledAt, SupplementStatus.TAKEN) } +
            SupplementLog("s", "d", doses[5].scheduledAt, null, SupplementStatus.SKIPPED) +
            // Another supplement's log doesn't count.
            SupplementLog("x", "other", doses[6].scheduledAt, doses[6].scheduledAt, SupplementStatus.TAKEN)
        val r = SupplementSchedule.regularity(vitaminD, logs, today, days = 7, zone = zone)
        assertEquals(Regularity(due = 7, taken = 5, skipped = 1), r)
        assertEquals(71, r.percent)
        assertNull(Regularity(0, 0, 0).percent)
    }

    @Test
    fun onlySupplementsWithCaloriesLogToTheDiary() {
        assertFalse(vitaminD.logsToDiary)
        assertTrue(vitaminD.copy(nutrients = Nutrients(kcal = 120.0, protein = 24.0)).logsToDiary)
        assertFalse(vitaminD.copy(nutrients = Nutrients(kcal = 0.0)).logsToDiary)
    }
}
