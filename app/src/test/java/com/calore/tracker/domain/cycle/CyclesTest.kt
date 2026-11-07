package com.calore.tracker.domain.cycle

import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeNutrition
import com.calore.tracker.domain.plan.PlannerRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CyclesTest {

    private fun d(s: String) = LocalDate.parse(s)

    /** Periods whose starts give these cycle lengths, oldest first. */
    private fun periodsWith(vararg lengths: Int, first: LocalDate = d("2026-01-01")): List<CyclePeriod> {
        var start = first
        val starts = mutableListOf(start)
        for (l in lengths) {
            start = start.plusDays(l.toLong())
            starts += start
        }
        return starts.mapIndexed { i, s -> CyclePeriod("p$i", s) }
    }

    private val on = CycleSettings(enabled = true)

    // --- A19 test cases ---

    @Test
    fun cycles28_30_29GiveAverage29RangeOneDayRegular() {
        val stats = Cycles.stats(periodsWith(28, 30, 29))
        assertEquals(29, stats.averageDays)
        assertEquals(1.0, stats.rangeDays, 0.0)
        assertFalse(stats.irregular)
    }

    @Test
    fun lastStartOctober1Average28Period5Days() {
        val cycle = Cycles.days(d("2026-10-01"), Cycles.nextStart(d("2026-10-01"), CycleStats(28.0, 1.0, false, listOf(28, 28))), periodLength = 5)
        assertEquals(d("2026-10-29"), cycle.nextStart)
        assertEquals(d("2026-10-01")..d("2026-10-05"), cycle.period)
        assertEquals(d("2026-10-06")..d("2026-10-12"), cycle.follicular)
        assertEquals(d("2026-10-13")..d("2026-10-16"), cycle.ovulation)
        assertEquals(d("2026-10-17")..d("2026-10-28"), cycle.luteal)
        assertEquals(d("2026-10-24")..d("2026-10-31"), cycle.water)
    }

    @Test
    fun cycles24_35_28AreIrregularWithRange5AndAHalf() {
        val stats = Cycles.stats(periodsWith(24, 35, 28))
        assertTrue(stats.irregular)
        assertEquals(5.5, stats.rangeDays, 0.0)
    }

    @Test
    fun contraceptionPillMeansNoPhasesAndNoTips() {
        val pill = on.copy(contraception = Contraception.PILL)
        val periods = listOf(CyclePeriod("p", d("2026-10-01")))
        assertNull(Cycles.phaseOn(d("2026-10-02"), periods, pill))
        assertEquals(emptyList<CycleTip>(), CycleTips.tips(Cycles.phaseOn(d("2026-10-20"), periods, pill)))
        assertEquals(emptySet<LocalDate>(), Cycles.waterDays(d("2026-10-01"), d("2026-11-30"), periods, pill))
        // Without the pill the same days do have phases.
        assertEquals(CyclePhase.PERIOD, Cycles.phaseOn(d("2026-10-02"), periods, on))
    }

    // --- More of A19 ---

    @Test
    fun theAverageUsesTheLastSixCyclesWeightedNewestHighest() {
        // 40 is the 7th newest and is left out: (25×1 + 26×2 + 27×3 + 28×4 + 29×5 + 30×6) / 21 = 28.33.
        val stats = Cycles.stats(periodsWith(40, 25, 26, 27, 28, 29, 30))
        assertEquals(listOf(25, 26, 27, 28, 29, 30), stats.lengths)
        assertEquals(28.333, stats.average, 0.001)
        assertEquals(28, stats.averageDays)
    }

    @Test
    fun beforeTwoCompleteCyclesTheDefaultIsUsed() {
        assertEquals(28, Cycles.stats(emptyList()).averageDays)
        assertEquals(28, Cycles.stats(periodsWith(35)).averageDays)
        assertEquals(31, Cycles.stats(periodsWith(35), defaultLength = 31).averageDays)
        assertEquals(Cycles.UNKNOWN_RANGE_DAYS, Cycles.stats(periodsWith(35)).rangeDays, 0.0)
    }

    @Test
    fun periodLengthIsTheLoggedAverageOrTheDefault() {
        assertEquals(5, Cycles.periodLength(listOf(CyclePeriod("a", d("2026-10-01")))))
        assertEquals(4, Cycles.periodLength(listOf(CyclePeriod("a", d("2026-09-01"), d("2026-09-04")), CyclePeriod("b", d("2026-10-01"), d("2026-10-04")))))
        assertEquals(6, Cycles.periodLength(emptyList(), defaultLength = 6))
    }

    @Test
    fun aShortCycleHasNoFollicularDays() {
        // Next start 20 days later: ovulation day = day 6, phase days 4–7, but the period lasts until day 5.
        val cycle = Cycles.days(d("2026-10-01"), d("2026-10-21"), periodLength = 5)
        assertNull(cycle.follicular)
    }

    @Test
    fun loggedCyclesUseTheirOwnNextStartAndLaterCyclesArePredicted() {
        val periods = listOf(CyclePeriod("a", d("2026-09-01"), d("2026-09-04")), CyclePeriod("b", d("2026-09-29")))
        // The September cycle is logged: 28 days, its own 4-day period.
        val september = Cycles.cycleOn(d("2026-09-10"), periods, on)!!
        assertEquals(d("2026-09-29"), september.nextStart)
        assertEquals(d("2026-09-01")..d("2026-09-04"), september.period)
        // After the last start, cycles of 28 days (default, only one complete cycle) are predicted.
        val november = Cycles.cycleOn(d("2026-11-05"), periods, on)!!
        assertEquals(d("2026-10-27"), november.start)
        assertEquals(d("2026-11-24"), november.nextStart)
        assertNull(Cycles.cycleOn(d("2026-08-31"), periods, on))
    }

    @Test
    fun theTodayCardSaysTheDayThePhaseAndTheNextPeriodRange() {
        val periods = periodsWith(28, 28, first = d("2026-09-03"))
        // Last start 2026-10-29; next 2026-11-26 ± 1.
        val today = Cycles.today(d("2026-11-15"), periods, on)!!
        assertEquals(18, today.day)
        assertEquals(CyclePhase.LUTEAL, today.phase)
        assertEquals(10L, today.nextInFrom)
        assertEquals(12L, today.nextInTo)
        assertEquals(d("2026-11-25")..d("2026-11-27"), Cycles.nextRange(periods, on))
        assertNull(Cycles.today(d("2026-11-15"), emptyList(), on))
    }

    @Test
    fun waterDaysSurroundEveryLoggedAndPredictedStart() {
        val periods = listOf(CyclePeriod("a", d("2026-10-01")))
        val days = Cycles.waterDays(d("2026-09-20"), d("2026-11-30"), periods, on)
        val expected = (generateSequence(d("2026-09-26")) { it.plusDays(1) }.take(8) + // 10-01 − 5 … + 2
            generateSequence(d("2026-10-24")) { it.plusDays(1) }.take(8) + // 10-29
            generateSequence(d("2026-11-21")) { it.plusDays(1) }.take(8)).toSet() // 11-26
        assertEquals(expected, days)
        assertEquals(emptySet<LocalDate>(), Cycles.waterDays(d("2026-10-01"), d("2026-10-31"), periods, CycleSettings(enabled = false)))
    }

    @Test
    fun pregnancyBreastfeedingAndMenopausePausePredictions() {
        assertTrue(on.copy(pregnant = true).paused)
        assertTrue(on.copy(breastfeeding = true).paused)
        assertTrue(on.copy(menopause = Menopause.PERIMENOPAUSE).paused)
        assertTrue(on.copy(contraception = Contraception.HORMONAL_IUD).paused)
        assertFalse(on.copy(contraception = Contraception.COPPER_IUD).paused)
    }

    // --- A20 ---

    @Test
    fun tipsByPhase() {
        assertEquals(listOf(CycleTip.IRON_WITH_VITAMIN_C), CycleTips.tips(CyclePhase.PERIOD))
        assertEquals(listOf(CycleTip.IRON_WITH_VITAMIN_C, CycleTip.WARM_EASY_MEALS), CycleTips.tips(CyclePhase.PERIOD, setOf(Symptom.CRAMPS)))
        assertEquals(listOf(CycleTip.PROTEIN_FIBRE_SNACKS, CycleTip.MAGNESIUM_FOOD), CycleTips.tips(CyclePhase.LUTEAL))
        assertEquals(emptyList<CycleTip>(), CycleTips.tips(CyclePhase.FOLLICULAR))
        assertEquals(emptyList<CycleTip>(), CycleTips.tips(CyclePhase.OVULATION))
    }

    @Test
    fun plannerBonusPerServingIsCappedAt10() {
        val ironAndC = Nutrients(kcal = 500.0, ironMg = 3.0, vitaminCMg = 20.0)
        assertEquals(10.0, CycleTips.bonus(CyclePhase.PERIOD, ironAndC), 0.0)
        assertEquals(2.0, CycleTips.bonus(CyclePhase.PERIOD, Nutrients(kcal = 500.0, ironMg = 2.9, vitaminCMg = 25.0)), 0.0)
        assertEquals(0.0, CycleTips.bonus(CyclePhase.FOLLICULAR, ironAndC), 0.0)
        val magnesiumAndFibre = Nutrients(kcal = 500.0, magnesiumMg = 80.0, fiber = 8.0)
        assertEquals(10.0, CycleTips.bonus(CyclePhase.LUTEAL, magnesiumAndFibre), 0.0)
        assertEquals(4.0, CycleTips.bonus(CyclePhase.LUTEAL, Nutrients(kcal = 500.0, fiber = 9.0)), 0.0)
        assertEquals(0.0, CycleTips.bonus(null, magnesiumAndFibre), 0.0)
        assertEquals(setOf(RecipeBadge.IRON_RICH, RecipeBadge.MAGNESIUM_RICH), CycleTips.badges(Nutrients(kcal = 1.0, ironMg = 4.0, magnesiumMg = 90.0)))
    }

    @Test
    fun onlyTheTrackingPersonGetsTheBonus() {
        val perServing = Nutrients(kcal = 500.0, ironMg = 5.0)
        val recipe = PlannerRecipe(Recipe(id = "r", name = "r", shortCode = "R", servings = 1), RecipeNutrition(perServing, perServing, null, 100.0, 0), emptyList(), emptySet(), emptySet())
        val provider = CyclePhaseBonus("me") { CyclePhase.PERIOD }
        assertEquals(10.0, provider.bonus("me", d("2026-10-01"), recipe), 0.0)
        assertEquals(0.0, provider.bonus("kid", d("2026-10-01"), recipe), 0.0)
        assertEquals(0.0, CyclePhaseBonus("me") { null }.bonus("me", d("2026-10-01"), recipe), 0.0)
    }
}
