package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Algorithms A7. */
class SmartMaintenanceTest {

    private val today = LocalDate.of(2026, 11, 9)

    @Test
    fun dataMaintenanceFromIntakeAndTrend() {
        // Average intake 2,500; trend 80.0 → 79.1 kg over 21 days.
        assertEquals(2830.0, SmartMaintenance.dataMaintenance(2500.0, 80.0, 79.1, 21), 1e-6)
    }

    @Test
    fun blendAndProposalForA21DayCut() {
        val estimate = SmartMaintenance.blend(dataMaintenanceKcal = 2830.0, countedDays = 21, formulaTdee = 2600.0)
        assertEquals(2772.5, estimate, 1e-6)

        val delta = CalorieCalculator.dailyDeltaKcal(79.1, GoalType.CUT, 0.5)
        assertEquals(-435.0, delta, 0.1)

        val proposed = SmartMaintenance.proposedTarget(estimate, delta, Sex.MALE, currentTargetKcal = 2200)
        assertEquals(2340, proposed)
        assertEquals("change +140, within the limit", 140, proposed - 2200)
    }

    @Test
    fun only9CountedDaysGiveNoProposal() {
        val input = input(intakeDays = 9, weighInDays = (0L..20L step 3).toList())
        val outcome = SmartMaintenance.run(input)
        assertTrue(outcome is CheckinOutcome.NotEnoughData)
        outcome as CheckinOutcome.NotEnoughData
        assertEquals(9, outcome.countedDays)
        assertEquals(5, outcome.daysMissing)
    }

    @Test
    fun theTargetMovesAtMost150KcalPerCheckin() {
        assertEquals(2150, SmartMaintenance.proposedTarget(3000.0, 0.0, Sex.MALE, currentTargetKcal = 2000))
        assertEquals(1850, SmartMaintenance.proposedTarget(1500.0, 0.0, Sex.MALE, currentTargetKcal = 2000))
        assertEquals(2010, SmartMaintenance.proposedTarget(2005.0, 0.0, Sex.MALE, currentTargetKcal = 2000))
    }

    @Test
    fun withoutAFormulaOnlyTheDataCounts() {
        assertEquals(2830.0, SmartMaintenance.blend(2830.0, countedDays = 7, formulaTdee = null), 0.0)
        assertEquals(2600.0, SmartMaintenance.blend(2830.0, countedDays = 0, formulaTdee = 2600.0), 0.0)
        assertEquals(2830.0, SmartMaintenance.blend(2830.0, countedDays = 35, formulaTdee = 2600.0), 0.0)
    }

    @Test
    fun aStableWeightMeansMaintenanceIsTheIntake() {
        val outcome = SmartMaintenance.run(input(intakeDays = 28, weighInDays = (0L..27L step 2).toList())) as CheckinOutcome.Proposal
        assertEquals(28, outcome.countedDays)
        assertEquals(2500.0, outcome.avgIntakeKcal, 1e-9)
        assertEquals(2500.0, outcome.dataMaintenanceKcal, 1e-9)
        // w = 1, so the formula no longer matters; maintaining keeps the estimate and the step limit applies.
        assertEquals(2500.0, outcome.estimatedMaintenanceKcal, 1e-9)
        assertEquals(2350, outcome.proposedTargetKcal)
    }

    @Test
    fun lightlyLoggedDaysAreSkipped() {
        val base = input(intakeDays = 28, weighInDays = (0L..27L step 2).toList())
        val withLightDays = base.copy(
            intakes = base.intakes.mapIndexed { i, day -> if (i < 10) day.copy(kcal = 900.0) else day },
        )
        val outcome = SmartMaintenance.run(withLightDays) as CheckinOutcome.Proposal
        assertEquals("900 kcal is below half of 2,200", 18, outcome.countedDays)
    }

    @Test
    fun tooFewWeighInsOrTooShortASpanGiveNoProposal() {
        val fewWeighIns = SmartMaintenance.run(input(intakeDays = 28, weighInDays = listOf(0L, 10L, 20L)))
        assertEquals(3, (fewWeighIns as CheckinOutcome.NotEnoughData).weighIns)
        val shortSpan = SmartMaintenance.run(input(intakeDays = 28, weighInDays = listOf(0L, 2L, 4L, 6L, 8L)))
        assertTrue(shortSpan is CheckinOutcome.NotEnoughData)
    }

    @Test
    fun waterRetentionDaysAreLeftOutOfTheTrend() {
        val base = input(intakeDays = 28, weighInDays = (0L..27L step 2).toList())
        val spike = today.minusDays(2)
        val withSpike = base.copy(weighIns = base.weighIns.map { if (it.date == spike) it.copy(kg = 82.0) else it })
        val ignored = SmartMaintenance.run(withSpike.copy(excludedWeighInDates = setOf(spike))) as CheckinOutcome.Proposal
        assertEquals(80.0, ignored.trendEndKg, 1e-9)
    }

    /** 28 days back from [today]: [intakeDays] days of 2,500 kcal, weigh-ins of 80 kg [weighInDays] days ago. */
    private fun input(intakeDays: Int, weighInDays: List<Long>) = CheckinInput(
        today = today,
        intakes = (1L..intakeDays.toLong()).map { DayIntake(today.minusDays(it), 2500.0) },
        dailyTargetKcal = 2200,
        weighIns = weighInDays.map { WeightEntry(0, today.minusDays(it), 80.0, newId()) },
        formulaTdee = 2600.0,
        sex = Sex.MALE,
        goalType = GoalType.MAINTAIN,
        goalRatePct = null,
        currentTargetKcal = 2200,
    )
}
