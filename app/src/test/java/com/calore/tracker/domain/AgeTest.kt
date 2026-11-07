package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Algorithms A1. */
class AgeTest {

    private val today = LocalDate.of(2026, 11, 6)

    @Test
    fun birthdayAlreadyPassedThisYear() {
        assertEquals(26, ageOn(LocalDate.of(2000, 7, 15), today))
    }

    @Test
    fun birthdayStillToCome() {
        assertEquals(25, ageOn(LocalDate.of(2000, 12, 1), today))
    }

    @Test
    fun birthdayToday() {
        assertEquals(26, ageOn(LocalDate.of(2000, 11, 6), today))
    }

    @Test
    fun oldAgeBecomesFirstOfJuly() {
        assertEquals(LocalDate.of(1996, 7, 1), birthDateFromAge(30, today))
        assertEquals(30, ageOn(birthDateFromAge(30, today), today))
    }

    @Test
    fun oldWeightGoalsMapToGoalTypes() {
        assertEquals(GoalType.CUT, WeightGoal.LOSE.goalType)
        assertEquals(0.5, WeightGoal.LOSE.goalRatePct!!, 0.0)
        assertEquals(GoalType.BUILD, WeightGoal.GAIN.goalType)
        assertEquals(0.25, WeightGoal.GAIN.goalRatePct!!, 0.0)
        assertEquals(GoalType.MAINTAIN, WeightGoal.MAINTAIN.goalType)
        assertEquals(null, WeightGoal.MAINTAIN.goalRatePct)
    }
}
