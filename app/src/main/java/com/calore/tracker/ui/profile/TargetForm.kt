package com.calore.tracker.ui.profile

import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.BodyFatReading
import com.calore.tracker.domain.BodyProfile
import com.calore.tracker.domain.CalculatorResult
import com.calore.tracker.domain.CalorieCalculator
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MacroMode
import com.calore.tracker.domain.MacroSplit
import com.calore.tracker.domain.Macros
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.ProfileField
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.ageOn
import com.calore.tracker.domain.parseDecimal
import com.calore.tracker.domain.parseWholeIn
import com.calore.tracker.ui.formatEditable
import java.time.LocalDate

/**
 * What the calculator and the first-run setup edit: body, goal and macro settings, as typed.
 * Pure Kotlin so the rules can be unit-tested.
 */
data class TargetForm(
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val heightCm: String = "",
    val weightKg: String = "",
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val goalType: GoalType = GoalType.MAINTAIN,
    val goalRatePct: Double? = null,
    val macroMode: MacroMode = MacroMode.AUTO,
    /** Null until the user picks a value; then the goal's default applies. */
    val proteinPerKg: Double? = null,
    val fatPct: Int = Macros.DEFAULT_FAT_PCT,
    val proteinPercent: String = MacroSplit.DEFAULT.proteinPercent.toString(),
    val carbsPercent: String = MacroSplit.DEFAULT.carbsPercent.toString(),
    val fatPercent: String = MacroSplit.DEFAULT.fatPercent.toString(),
    /** The latest body-fat value, read-only here; it decides the formula (A2). */
    val bodyFat: BodyFatReading? = null,
) {
    private val heightValue get() = parseDecimal(heightCm)
    val weightValue: Double? get() = parseDecimal(weightKg)

    fun ageYears(today: LocalDate): Int? = birthDate?.let { ageOn(it, today) }

    val effectiveProteinPerKg: Double get() = proteinPerKg ?: goalType.defaultProteinPerKg

    /** The speed in use: the chosen one if the goal offers it, otherwise the goal's default. */
    val effectiveRatePct: Double?
        get() = if (!goalType.hasSpeed) null else goalRatePct?.takeIf { it in goalType.speedsPct } ?: goalType.defaultSpeedPct

    val split: MacroSplit?
        get() {
            return MacroSplit(
                proteinPercent = parseWholeIn(proteinPercent, 0..100) ?: return null,
                carbsPercent = parseWholeIn(carbsPercent, 0..100) ?: return null,
                fatPercent = parseWholeIn(fatPercent, 0..100) ?: return null,
            )
        }

    /** Sum of the percentages as typed, for the "must add up to 100" hint. */
    val splitTotal: Int
        get() = listOf(proteinPercent, carbsPercent, fatPercent).sumOf { it.trim().toIntOrNull()?.coerceIn(0, 100) ?: 0 }

    /** Fields holding an out-of-range value. Blank fields are not errors yet. */
    fun invalidFields(today: LocalDate): Set<ProfileField> = buildSet {
        val age = ageYears(today)
        if (age != null && age !in CalorieCalculator.AGE_RANGE) add(ProfileField.AGE)
        if (heightCm.isNotBlank() && heightValue?.let { it in CalorieCalculator.HEIGHT_RANGE_CM } != true) {
            add(ProfileField.HEIGHT)
        }
        if (weightKg.isNotBlank() && weightValue?.let { it in CalorieCalculator.WEIGHT_RANGE_KG } != true) {
            add(ProfileField.WEIGHT)
        }
    }

    fun profile(today: LocalDate): BodyProfile? {
        return BodyProfile(
            sex = sex ?: return null,
            ageYears = ageYears(today) ?: return null,
            heightCm = heightValue ?: return null,
            weightKg = weightValue ?: return null,
            activity = activity,
        )
    }

    fun result(today: LocalDate): CalculatorResult? {
        val body = profile(today) ?: return null
        return CalorieCalculator.calculate(
            com.calore.tracker.domain.TargetInput(
                profile = body,
                goalType = goalType,
                goalRatePct = effectiveRatePct,
                bodyFat = bodyFat,
                today = today,
                macroMode = macroMode,
                proteinPerKg = effectiveProteinPerKg,
                fatPct = fatPct,
                split = split ?: MacroSplit(0, 0, 0),
            ),
        )
    }

    /** The member with these settings and, if [result] is given, its targets. */
    fun applyTo(member: Member, result: CalculatorResult?): Member = member.copy(
        sex = sex,
        birthDate = birthDate,
        heightCm = heightValue ?: member.heightCm,
        activity = activity,
        goalType = goalType,
        goalRatePct = effectiveRatePct,
        macroMode = macroMode,
        proteinPerKg = proteinPerKg,
        fatPct = fatPct,
        split = split?.takeIf { it.isValid } ?: member.split,
        targets = result?.goals ?: member.targets,
    )

    companion object {
        /** Fills the form from the stored profile, the latest weigh-in and the latest body fat. */
        fun from(member: Member, latestWeightKg: Double?, bodyFat: BodyFatReading?): TargetForm {
            val split = member.effectiveSplit
            val goal = member.goalType ?: GoalType.MAINTAIN
            return TargetForm(
                sex = member.sex,
                birthDate = member.birthDate,
                heightCm = formatEditable(member.heightCm),
                weightKg = formatEditable(latestWeightKg),
                activity = member.activity ?: ActivityLevel.LIGHT,
                goalType = goal,
                goalRatePct = member.goalRatePct,
                macroMode = member.macroMode,
                proteinPerKg = member.proteinPerKg,
                fatPct = member.effectiveFatPct,
                proteinPercent = split.proteinPercent.toString(),
                carbsPercent = split.carbsPercent.toString(),
                fatPercent = split.fatPercent.toString(),
                bodyFat = bodyFat,
            )
        }
    }
}
