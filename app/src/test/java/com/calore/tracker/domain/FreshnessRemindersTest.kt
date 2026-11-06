package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** M4.9: which boxes the morning and evening reminders and the Today card name. */
class FreshnessRemindersTest {

    private val today = LocalDate.of(2026, 11, 10)

    private fun box(code: String, eatBy: LocalDate, storage: Storage = Storage.FRIDGE, planned: LocalDate? = null) = Box(
        id = code, code = code, ownerMemberId = null, storage = storage, state = BoxState.READY,
        remainingFraction = 1.0, eatByDate = eatBy, plannedDate = planned,
    )

    private val boxes = listOf(
        box("C1", today),
        box("C2", today.minusDays(1)),
        box("C3", today.plusDays(1)),
        box("C4", today.minusDays(5), Storage.FREEZER, planned = today.plusDays(1)),
        box("C5", today.plusDays(3)),
        box("C6", today.minusDays(5), Storage.FREEZER, planned = today.plusDays(2)),
    )

    @Test
    fun theMorningReminderNamesBoxesDueTodayAndRedOnes() {
        assertEquals(listOf("C1", "C2"), Freshness.dueToday(boxes, today).map { it.code })
    }

    @Test
    fun theEveningReminderNamesFrozenBoxesPlannedForTomorrow() {
        assertEquals(listOf("C4"), Freshness.toThawTonight(boxes, today).map { it.code })
    }

    @Test
    fun eatFirstShowsUpToThreeFridgeBoxes() {
        assertEquals(listOf("C2", "C1", "C3"), Freshness.eatFirst(boxes).map { it.code })
    }

    @Test
    fun theNextReminderTime() {
        val morning = ZonedDateTime.of(today.atTime(6, 0), ZoneOffset.UTC)
        assertEquals(Duration.ofMinutes(90), Freshness.until(LocalTime.of(7, 30), morning))
        val evening = ZonedDateTime.of(today.atTime(20, 0), ZoneOffset.UTC)
        assertEquals(Duration.ofHours(23), Freshness.until(LocalTime.of(19, 0), evening))
    }
}
