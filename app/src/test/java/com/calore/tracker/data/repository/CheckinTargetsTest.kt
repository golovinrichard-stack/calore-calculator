package com.calore.tracker.data.repository

import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MacroMode
import com.calore.tracker.domain.MacroSplit
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** M1.7: accepting a check-in sets the calories and recalculates the macros (A4). */
class CheckinTargetsTest {

    private val me = Member.newMe("id", "", 0).copy(
        sex = Sex.MALE,
        birthDate = LocalDate.of(1996, 1, 1),
        heightCm = 180.0,
        activity = ActivityLevel.LIGHT,
        goalType = GoalType.CUT,
        goalRatePct = 0.5,
    )

    @Test
    fun autoModeRecalculatesProteinFatAndCarbs() {
        val targets = CheckinService.targetsFor(me.copy(macroMode = MacroMode.AUTO), kcal = 2200, weightKg = 80.0)
        assertEquals(NutritionGoals(kcal = 2200, proteinG = 160, carbsG = 253, fatG = 61), targets)
    }

    @Test
    fun splitModeKeepsThePercentages() {
        val targets = CheckinService.targetsFor(
            me.copy(macroMode = MacroMode.SPLIT, split = MacroSplit(30, 35, 35)),
            kcal = 2000,
            weightKg = 80.0,
        )
        assertEquals(NutritionGoals(kcal = 2000, proteinG = 150, carbsG = 175, fatG = 78), targets)
    }

    @Test
    fun formulaNeedsACompleteProfile() {
        val today = LocalDate.of(2026, 11, 9)
        // Mifflin-St Jeor 1,780 × 1.375.
        assertEquals(2447.5, CheckinService.formulaTdee(me, 80.0, today)!!, 1e-9)
        assertNull(CheckinService.formulaTdee(me.copy(heightCm = null), 80.0, today))
        assertNull(CheckinService.formulaTdee(me, null, today))
    }
}
