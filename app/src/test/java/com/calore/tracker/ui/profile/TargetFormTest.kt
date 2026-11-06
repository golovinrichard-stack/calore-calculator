package com.calore.tracker.ui.profile

import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.BmrFormula
import com.calore.tracker.domain.BodyFatReading
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MacroMode
import com.calore.tracker.domain.MacroSplit
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.ProfileField
import com.calore.tracker.domain.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TargetFormTest {

    private val today = LocalDate.of(2026, 11, 6)

    private val form = TargetForm(
        sex = Sex.MALE,
        birthDate = LocalDate.of(1996, 3, 1),
        heightCm = "180",
        weightKg = "80",
        activity = ActivityLevel.MODERATE,
        goalType = GoalType.CUT,
        goalRatePct = 0.5,
        macroMode = MacroMode.AUTO,
    )

    @Test
    fun cutTargetWithAutoMacros() {
        val result = form.result(today)!!
        assertEquals(BmrFormula.MIFFLIN_ST_JEOR, result.formula)
        assertEquals(2320, result.goals.kcal)
        assertEquals(160, result.goals.proteinG)
    }

    @Test
    fun speedFallsBackToTheGoalsDefault() {
        assertEquals(0.5, form.copy(goalRatePct = null).effectiveRatePct!!, 0.0)
        assertEquals("0.75 is not a build speed", 0.25, form.copy(goalType = GoalType.BUILD, goalRatePct = 0.75).effectiveRatePct!!, 0.0)
        assertNull(form.copy(goalType = GoalType.MAINTAIN).effectiveRatePct)
    }

    @Test
    fun proteinDefaultsFollowTheGoalUntilChosen() {
        assertEquals(2.0, form.effectiveProteinPerKg, 0.0)
        assertEquals(1.8, form.copy(goalType = GoalType.BUILD).effectiveProteinPerKg, 0.0)
        assertEquals(1.4, form.copy(proteinPerKg = 1.4).effectiveProteinPerKg, 0.0)
    }

    @Test
    fun recentBodyFatSwitchesTheFormula() {
        val withFat = form.copy(bodyFat = BodyFatReading(15.0, today.minusDays(10)))
        assertEquals(BmrFormula.KATCH_MCARDLE, withFat.result(today)!!.formula)
    }

    @Test
    fun outOfRangeValuesAreFlaggedAndGiveNoResult() {
        val young = form.copy(birthDate = today.minusYears(10), heightCm = "90")
        assertEquals(setOf(ProfileField.AGE, ProfileField.HEIGHT), young.invalidFields(today))
        assertNull(young.result(today))
        assertTrue(TargetForm().invalidFields(today).isEmpty())
        assertNull(TargetForm().result(today))
    }

    @Test
    fun applyingSavesSettingsAndTargets() {
        val me = Member.newMe("id", "", 0)
        val result = form.result(today)!!
        val saved = form.applyTo(me, result)
        assertEquals(Sex.MALE, saved.sex)
        assertEquals(LocalDate.of(1996, 3, 1), saved.birthDate)
        assertEquals(180.0, saved.heightCm!!, 0.0)
        assertEquals(GoalType.CUT, saved.goalType)
        assertEquals(0.5, saved.goalRatePct!!, 0.0)
        assertEquals(result.goals, saved.targets)
        assertNull("protein stays on the goal default", saved.proteinPerKg)
    }

    @Test
    fun fillsFromTheProfile() {
        val me = Member.newMe("id", "", 0).copy(
            sex = Sex.FEMALE,
            heightCm = 165.0,
            goalType = GoalType.BUILD,
            goalRatePct = 0.25,
            macroMode = MacroMode.SPLIT,
            split = MacroSplit(25, 50, 25),
            targets = NutritionGoals(2100, 131, 263, 58),
        )
        val loaded = TargetForm.from(me, latestWeightKg = 61.5, bodyFat = null)
        assertEquals("165", loaded.heightCm)
        assertEquals("61.5", loaded.weightKg)
        assertEquals(MacroMode.SPLIT, loaded.macroMode)
        assertEquals("50", loaded.carbsPercent)
        assertEquals(GoalType.BUILD, loaded.goalType)
    }
}
