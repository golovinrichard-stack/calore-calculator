package com.calore.tracker.domain

import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

enum class ActivityLevel(val multiplier: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9),
}

/** Daily calorie change applied to maintenance. 500 kcal is roughly 0.5 kg per week. */
enum class WeightGoal(val dailyKcalDelta: Int) {
    LOSE(-500),
    MAINTAIN(0),
    GAIN(300),
}

/** Algorithms A3, "old settings": lose becomes a 0.5 % cut, gain a 0.25 % build. */
val WeightGoal.goalType: GoalType
    get() = when (this) {
        WeightGoal.LOSE -> GoalType.CUT
        WeightGoal.MAINTAIN -> GoalType.MAINTAIN
        WeightGoal.GAIN -> GoalType.BUILD
    }

val WeightGoal.goalRatePct: Double?
    get() = when (this) {
        WeightGoal.LOSE -> 0.5
        WeightGoal.MAINTAIN -> null
        WeightGoal.GAIN -> 0.25
    }

data class BodyProfile(
    val sex: Sex,
    val ageYears: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: ActivityLevel,
)

enum class ProfileField { AGE, HEIGHT, WEIGHT }

/** Share of calories from each macro, in whole percent. Valid only when they add up to 100. */
data class MacroSplit(val proteinPercent: Int, val carbsPercent: Int, val fatPercent: Int) {
    val isValid: Boolean
        get() = listOf(proteinPercent, carbsPercent, fatPercent).all { it in 0..100 } &&
            proteinPercent + carbsPercent + fatPercent == 100

    companion object {
        val DEFAULT = MacroSplit(proteinPercent = 30, carbsPercent = 35, fatPercent = 35)
    }
}

data class CalculatorResult(
    val bmr: Int,
    val tdee: Int,
    val goals: NutritionGoals,
)

/**
 * Estimates daily energy needs with the Mifflin-St Jeor equation, the formula most
 * dietitians default to for healthy adults.
 */
object CalorieCalculator {
    val AGE_RANGE = 15..100
    val HEIGHT_RANGE_CM = 100.0..250.0
    val WEIGHT_RANGE_KG = 30.0..300.0

    /** Common minimums below which a diet should be medically supervised. */
    private const val MIN_TARGET_KCAL_FEMALE = 1200
    private const val MIN_TARGET_KCAL_MALE = 1500

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0

    fun invalidFields(profile: BodyProfile): Set<ProfileField> = buildSet {
        if (profile.ageYears !in AGE_RANGE) add(ProfileField.AGE)
        if (profile.heightCm !in HEIGHT_RANGE_CM) add(ProfileField.HEIGHT)
        if (profile.weightKg !in WEIGHT_RANGE_KG) add(ProfileField.WEIGHT)
    }

    fun bmr(profile: BodyProfile): Double {
        val base = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.ageYears
        return when (profile.sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    fun tdee(profile: BodyProfile): Double = bmr(profile) * profile.activity.multiplier

    /**
     * Maintenance calories adjusted for [goal]. A weight-loss target never drops below the
     * usual safe minimum, unless maintenance itself is lower than that minimum, in which
     * case it stays at maintenance rather than recommending a surplus.
     */
    fun targetKcal(profile: BodyProfile, goal: WeightGoal): Int {
        val maintenance = tdee(profile)
        val raw = maintenance + goal.dailyKcalDelta
        val floor = when (profile.sex) {
            Sex.MALE -> MIN_TARGET_KCAL_MALE
            Sex.FEMALE -> MIN_TARGET_KCAL_FEMALE
        }.toDouble()
        val target = if (goal.dailyKcalDelta < 0) maxOf(raw, minOf(floor, maintenance)) else raw
        return roundToTen(target)
    }

    fun goalsFor(kcal: Int, split: MacroSplit): NutritionGoals = NutritionGoals(
        kcal = kcal,
        proteinG = (kcal * split.proteinPercent / 100.0 / KCAL_PER_G_PROTEIN).roundToInt(),
        carbsG = (kcal * split.carbsPercent / 100.0 / KCAL_PER_G_CARBS).roundToInt(),
        fatG = (kcal * split.fatPercent / 100.0 / KCAL_PER_G_FAT).roundToInt(),
    )

    /** Returns null when the profile is out of range or the split does not add up to 100%. */
    fun calculate(profile: BodyProfile, goal: WeightGoal, split: MacroSplit): CalculatorResult? {
        if (invalidFields(profile).isNotEmpty() || !split.isValid) return null
        return CalculatorResult(
            bmr = bmr(profile).roundToInt(),
            tdee = tdee(profile).roundToInt(),
            goals = goalsFor(targetKcal(profile, goal), split),
        )
    }

    private fun roundToTen(value: Double): Int = (value / 10.0).roundToInt() * 10
}
