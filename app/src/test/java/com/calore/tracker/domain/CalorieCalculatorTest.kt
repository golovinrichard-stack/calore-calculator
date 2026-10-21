package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun targetsAreRoundedToTenKcal() {
        // TDEE 2759
        assertEquals(2760, CalorieCalculator.targetKcal(man, WeightGoal.MAINTAIN))
        assertEquals(2260, CalorieCalculator.targetKcal(man, WeightGoal.LOSE))
        assertEquals(3060, CalorieCalculator.targetKcal(man, WeightGoal.GAIN))
    }

    @Test
    fun weightLossNeverGoesBelowSafeMinimum() {
        // TDEE 1614.3, minus 500 would be 1114, below the 1200 minimum for women.
        assertEquals(1200, CalorieCalculator.targetKcal(woman, WeightGoal.LOSE))
    }

    @Test
    fun safeMinimumNeverExceedsMaintenance() {
        val smallOlderWoman = BodyProfile(Sex.FEMALE, 70, 150.0, 45.0, ActivityLevel.SEDENTARY)
        val maintenance = CalorieCalculator.tdee(smallOlderWoman) // 1051.8

        val target = CalorieCalculator.targetKcal(smallOlderWoman, WeightGoal.LOSE)

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

    @Test
    fun calculateRejectsInvalidInput() {
        assertNull(CalorieCalculator.calculate(man.copy(ageYears = 5), WeightGoal.MAINTAIN, MacroSplit.DEFAULT))
        assertNull(CalorieCalculator.calculate(man, WeightGoal.MAINTAIN, MacroSplit(50, 50, 50)))
    }

    @Test
    fun calculateReturnsRoundedFigures() {
        val result = CalorieCalculator.calculate(man, WeightGoal.MAINTAIN, MacroSplit.DEFAULT)

        assertNotNull(result)
        assertEquals(1780, result!!.bmr)
        assertEquals(2759, result.tdee)
        assertEquals(2760, result.goals.kcal)
    }
}
