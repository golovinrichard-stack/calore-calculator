package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalorieCalculatorTest {

    private val man = BodyProfile(
        sex = Sex.MALE,
        ageYears = 30,
        heightCm = 180.0,
        weightKg = 80.0,
        activity = ActivityLevel.MODERATE,
    )

    private val woman = BodyProfile(
        sex = Sex.FEMALE,
        ageYears = 25,
        heightCm = 165.0,
        weightKg = 60.0,
        activity = ActivityLevel.SEDENTARY,
    )

    @Test
    fun bmrMatchesMifflinStJeorForMen() {
        // 10*80 + 6.25*180 - 5*30 + 5
        assertEquals(1780.0, CalorieCalculator.bmr(man), 1e-9)
    }

    @Test
    fun bmrMatchesMifflinStJeorForWomen() {
        // 10*60 + 6.25*165 - 5*25 - 161
        assertEquals(1345.25, CalorieCalculator.bmr(woman), 1e-9)
    }

    @Test
    fun tdeeAppliesActivityMultiplier() {
        assertEquals(1780.0 * 1.55, CalorieCalculator.tdee(man), 1e-9)
        assertEquals(1345.25 * 1.2, CalorieCalculator.tdee(woman), 1e-9)
    }

    private fun target(profile: BodyProfile, goal: GoalType, ratePct: Double?): Int =
        CalorieCalculator.targetKcal(
            CalorieCalculator.tdee(profile),
            CalorieCalculator.dailyDeltaKcal(profile.weightKg, goal, ratePct),
            profile.sex,
        )

    @Test
    fun targetsAreRoundedToTenKcal() {
        // TDEE 2759; a 0.5 % cut at 80 kg is -440 kcal, a 0.25 % build +220 kcal.
        assertEquals(2760, target(man, GoalType.MAINTAIN, null))
        assertEquals(2320, target(man, GoalType.CUT, 0.5))
        assertEquals(2980, target(man, GoalType.BUILD, 0.25))
        assertEquals(2760, target(man, GoalType.RECOMP, null))
    }

    @Test
    fun weightLossNeverGoesBelowSafeMinimum() {
        // TDEE 1614.3, minus 660 (1 % of 60 kg) would be 954, below the 1200 minimum for women.
        assertEquals(1200, target(woman, GoalType.CUT, 1.0))
    }

    @Test
    fun safeMinimumNeverExceedsMaintenance() {
        val smallOlderWoman = BodyProfile(Sex.FEMALE, 70, 150.0, 45.0, ActivityLevel.SEDENTARY)
        val maintenance = CalorieCalculator.tdee(smallOlderWoman) // 1051.8

        val target = target(smallOlderWoman, GoalType.CUT, 0.5)

        assertEquals(1050, target)
        assertTrue(target <= maintenance + 5)
    }

    @Test
    fun macroGoalsFollowSplit() {
        val goals = CalorieCalculator.goalsFor(2000, MacroSplit.DEFAULT)

        assertEquals(NutritionGoals(kcal = 2000, proteinG = 150, carbsG = 175, fatG = 78), goals)
        assertEquals(NutritionGoals.DEFAULT, goals)
    }

    @Test
    fun splitMustAddUpTo100() {
        assertTrue(MacroSplit(30, 40, 30).isValid)
        assertTrue(MacroSplit(0, 100, 0).isValid)
        assertTrue(!MacroSplit(30, 40, 40).isValid)
        assertTrue(!MacroSplit(-10, 60, 50).isValid)
    }

    @Test
    fun profileBoundsAreInclusive() {
        assertTrue(CalorieCalculator.invalidFields(man.copy(ageYears = 15)).isEmpty())
        assertTrue(CalorieCalculator.invalidFields(man.copy(ageYears = 100)).isEmpty())
        assertEquals(setOf(ProfileField.AGE), CalorieCalculator.invalidFields(man.copy(ageYears = 14)))
        assertEquals(setOf(ProfileField.AGE), CalorieCalculator.invalidFields(man.copy(ageYears = 101)))
        assertEquals(
            setOf(ProfileField.HEIGHT, ProfileField.WEIGHT),
            CalorieCalculator.invalidFields(man.copy(heightCm = 99.9, weightKg = 300.1)),
        )
    }

    private val today = LocalDate.of(2026, 11, 6)

    private fun input(
        profile: BodyProfile = man,
        goal: GoalType = GoalType.MAINTAIN,
        rate: Double? = null,
        mode: MacroMode = MacroMode.SPLIT,
        split: MacroSplit = MacroSplit.DEFAULT,
        bodyFat: BodyFatReading? = null,
    ) = TargetInput(
        profile = profile,
        goalType = goal,
        goalRatePct = rate,
        bodyFat = bodyFat,
        today = today,
        macroMode = mode,
        proteinPerKg = goal.defaultProteinPerKg,
        fatPct = 25,
        split = split,
    )

    @Test
    fun calculateRejectsInvalidInput() {
        assertNull(CalorieCalculator.calculate(input(profile = man.copy(ageYears = 5))))
        assertNull(CalorieCalculator.calculate(input(split = MacroSplit(50, 50, 50))))
    }

    @Test
    fun calculateReturnsRoundedFigures() {
        val result = CalorieCalculator.calculate(input())

        assertNotNull(result)
        assertEquals(1780, result!!.bmr)
        assertEquals(2759, result.tdee)
        assertEquals(2760, result.goals.kcal)
        assertEquals(BmrFormula.MIFFLIN_ST_JEOR, result.formula)
        assertEquals(NutritionGoals(2760, 207, 242, 107), result.goals)
    }

    @Test
    fun splitModeWorksAsBefore() {
        val result = CalorieCalculator.calculate(input(mode = MacroMode.SPLIT, split = MacroSplit.DEFAULT))!!
        assertEquals(CalorieCalculator.goalsFor(2760, MacroSplit.DEFAULT), result.goals)
    }

    @Test
    fun autoModeUsesProteinPerKg() {
        val result = CalorieCalculator.calculate(input(goal = GoalType.CUT, rate = 0.5, mode = MacroMode.AUTO))!!
        assertEquals(2320, result.goals.kcal)
        assertEquals(160, result.goals.proteinG)
        assertEquals(-0.4, result.weeklyChangeKg, 1e-9)
        assertEquals(-440.0, result.dailyDeltaKcal, 1e-9)
    }

    // A2

    @Test
    fun katchMcArdleFromLeanMass() {
        // 80 kg at 15 % body fat: lean mass 68 kg, BMR 1,839 kcal.
        assertEquals(1839, kotlin.math.round(CalorieCalculator.katchMcArdleBmr(80.0, 15.0)).toInt())
        assertEquals(1838.8, CalorieCalculator.katchMcArdleBmr(80.0, 15.0), 1e-9)
    }

    @Test
    fun recentBodyFatSelectsKatchMcArdle() {
        val recent = BodyFatReading(15.0, today.minusDays(30))
        assertEquals(BmrFormula.KATCH_MCARDLE, CalorieCalculator.formulaFor(recent, today))
        val result = CalorieCalculator.calculate(input(bodyFat = recent))!!
        assertEquals(BmrFormula.KATCH_MCARDLE, result.formula)
        assertEquals(1839, result.bmr)
    }

    @Test
    fun bodyFatMeasured120DaysAgoUsesMifflinStJeor() {
        val old = BodyFatReading(15.0, today.minusDays(120))
        assertEquals(BmrFormula.MIFFLIN_ST_JEOR, CalorieCalculator.formulaFor(old, today))
        assertEquals(BmrFormula.MIFFLIN_ST_JEOR, CalorieCalculator.calculate(input(bodyFat = old))!!.formula)
        assertEquals(BmrFormula.MIFFLIN_ST_JEOR, CalorieCalculator.formulaFor(null, today))
    }

    // A3

    @Test
    fun cutHalfPercentAt80Kg() {
        assertEquals(-0.4, CalorieCalculator.weeklyChangeKg(80.0, GoalType.CUT, 0.5), 1e-9)
        assertEquals(-440.0, CalorieCalculator.dailyDeltaKcal(80.0, GoalType.CUT, 0.5), 1e-9)
    }

    @Test
    fun buildQuarterPercentAt70Kg() {
        assertEquals(192.5, CalorieCalculator.dailyDeltaKcal(70.0, GoalType.BUILD, 0.25), 1e-9)
        val target = CalorieCalculator.targetKcal(2500.0, 192.5, Sex.MALE)
        assertEquals(2690, target)
        assertEquals(0, target % 10)
    }

    @Test
    fun maintainAndRecompHaveNoDelta() {
        assertEquals(0.0, CalorieCalculator.dailyDeltaKcal(80.0, GoalType.MAINTAIN, 0.5), 0.0)
        assertEquals(0.0, CalorieCalculator.dailyDeltaKcal(80.0, GoalType.RECOMP, null), 0.0)
    }

    @Test
    fun offeredSpeeds() {
        assertEquals(listOf(0.5, 0.75, 1.0), GoalType.CUT.speedsPct)
        assertEquals(listOf(0.25, 0.5), GoalType.BUILD.speedsPct)
    }

    @Test
    fun warningsForFastCutsAndLowBodyFat() {
        assertEquals(setOf(TargetWarning.FAST_CUT), CalorieCalculator.warnings(GoalType.CUT, 1.25, Sex.MALE, null))
        assertTrue(CalorieCalculator.warnings(GoalType.CUT, 1.0, Sex.MALE, null).isEmpty())
        assertEquals(setOf(TargetWarning.LOW_BODY_FAT_CUT), CalorieCalculator.warnings(GoalType.CUT, 0.5, Sex.MALE, 9.5))
        assertEquals(setOf(TargetWarning.LOW_BODY_FAT_CUT), CalorieCalculator.warnings(GoalType.CUT, 0.5, Sex.FEMALE, 17.0))
        assertTrue(CalorieCalculator.warnings(GoalType.CUT, 0.5, Sex.FEMALE, 19.0).isEmpty())
        assertTrue(CalorieCalculator.warnings(GoalType.BUILD, 0.5, Sex.MALE, 8.0).isEmpty())
    }

    // A4

    @Test
    fun autoMacrosFor2200KcalAt80Kg() {
        val protein = Macros.proteinGrams(weightKg = 80.0, heightCm = 180.0, proteinPerKg = 2.0)
        val result = Macros.auto(targetKcal = 2200, proteinG = protein, fatPct = 25)
        assertEquals(NutritionGoals(kcal = 2200, proteinG = 160, carbsG = 253, fatG = 61), result.goals)
        assertTrue(!result.proteinTooHigh)
    }

    @Test
    fun highBmiUsesWeightAtBmi25() {
        assertEquals(81.0, Macros.proteinBaseWeight(weightKg = 120.0, heightCm = 180.0), 1e-9)
        assertEquals(162.0, Macros.proteinGrams(weightKg = 120.0, heightCm = 180.0, proteinPerKg = 2.0), 1e-9)
        assertEquals(80.0, Macros.proteinBaseWeight(weightKg = 80.0, heightCm = 180.0), 1e-9)
    }

    @Test
    fun proteinTooHighForTheTarget() {
        val result = Macros.auto(targetKcal = 1200, proteinG = 250.0, fatPct = 25)
        assertEquals(0, result.goals.carbsG)
        assertEquals(22, result.goals.fatG)
        assertEquals(20, result.fatPctUsed)
        assertTrue(result.proteinTooHigh)
    }

    @Test
    fun fatIsLoweredTo20PercentBeforeGivingUp() {
        // 1,800 kcal, 350 g protein = 1,400 kcal: fat at 25 % leaves -50 kcal for carbs, at 20 % +40 kcal.
        val result = Macros.auto(targetKcal = 1800, proteinG = 350.0, fatPct = 25)
        assertEquals(20, result.fatPctUsed)
        assertEquals(40, result.goals.fatG)
        assertEquals(10, result.goals.carbsG)
        assertTrue(!result.proteinTooHigh)
    }

    @Test
    fun defaultProteinPerGoal() {
        assertEquals(2.0, GoalType.CUT.defaultProteinPerKg, 0.0)
        assertEquals(1.6, GoalType.MAINTAIN.defaultProteinPerKg, 0.0)
        assertEquals(1.8, GoalType.BUILD.defaultProteinPerKg, 0.0)
        assertEquals(2.0, GoalType.RECOMP.defaultProteinPerKg, 0.0)
    }
}
