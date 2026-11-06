package com.calore.tracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

enum class ActivityLevel(val multiplier: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9),
}

/** The 0.x goal setting. Only read once, when it moves to [GoalType] (M1.1). */
enum class WeightGoal { LOSE, MAINTAIN, GAIN }

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

/** Which resting-energy equation was used (A2). */
enum class BmrFormula { MIFFLIN_ST_JEOR, KATCH_MCARDLE }

/** Things to point out about a target. Warnings never block (A3, A4). */
enum class TargetWarning { FAST_CUT, LOW_BODY_FAT_CUT, PROTEIN_TOO_HIGH }

/** The latest body-fat value and when it was measured. */
data class BodyFatReading(val pct: Double, val date: LocalDate)

/** Everything the target calculation needs. */
data class TargetInput(
    val profile: BodyProfile,
    val goalType: GoalType,
    /** % of body weight per week; ignored for goals without a speed. */
    val goalRatePct: Double?,
    val bodyFat: BodyFatReading?,
    val today: LocalDate,
    val macroMode: MacroMode,
    val proteinPerKg: Double,
    val fatPct: Int,
    val split: MacroSplit,
)

data class CalculatorResult(
    val formula: BmrFormula,
    val bmr: Int,
    val tdee: Int,
    /** Signed: negative for a cut. */
    val weeklyChangeKg: Double,
    /** Signed calories per day added to maintenance. */
    val dailyDeltaKcal: Double,
    val goals: NutritionGoals,
    val warnings: Set<TargetWarning>,
)

/**
 * Daily energy needs and targets (algorithms A2–A4). Resting energy comes from Mifflin-St Jeor,
 * or from Katch-McArdle when a recent body-fat value is known.
 */
object CalorieCalculator {
    val AGE_RANGE = 15..100
    val HEIGHT_RANGE_CM = 100.0..250.0
    val WEIGHT_RANGE_KG = 30.0..300.0

    /** Approximate energy in one kg of body weight change. */
    const val KCAL_PER_KG = 7_700.0

    /** A body-fat value older than this is not used for Katch-McArdle (tunable). */
    const val BODY_FAT_VALID_DAYS = 90L

    /** A cut faster than this (% of body weight per week) gets a warning (A3). */
    const val FAST_CUT_PCT = 1.0

    /** Cutting below these body-fat values gets a warning (tunable). */
    const val LOW_BODY_FAT_MALE = 10.0
    const val LOW_BODY_FAT_FEMALE = 18.0

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

    /** Mifflin-St Jeor. */
    fun bmr(profile: BodyProfile): Double {
        val base = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.ageYears
        return when (profile.sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    /** Katch-McArdle: `370 + 21.6 × lean mass` (A2). */
    fun katchMcArdleBmr(weightKg: Double, bodyFatPct: Double): Double =
        370.0 + 21.6 * weightKg * (1.0 - bodyFatPct / 100.0)

    /** Katch-McArdle when body fat was measured in the last [BODY_FAT_VALID_DAYS] days (A2). */
    fun formulaFor(bodyFat: BodyFatReading?, today: LocalDate): BmrFormula {
        if (bodyFat == null || bodyFat.date.isAfter(today)) return BmrFormula.MIFFLIN_ST_JEOR
        val age = ChronoUnit.DAYS.between(bodyFat.date, today)
        return if (age <= BODY_FAT_VALID_DAYS) BmrFormula.KATCH_MCARDLE else BmrFormula.MIFFLIN_ST_JEOR
    }

    fun tdee(profile: BodyProfile): Double = bmr(profile) * profile.activity.multiplier

    /** Signed kg per week: `weightKg × goalRatePct / 100` (A3). */
    fun weeklyChangeKg(weightKg: Double, goalType: GoalType, goalRatePct: Double?): Double {
        if (!goalType.hasSpeed || goalRatePct == null) return 0.0
        return goalType.direction * weightKg * goalRatePct / 100.0
    }

    /** Signed calories per day: `weeklyChangeKg × 7,700 / 7` (A3). */
    fun dailyDeltaKcal(weightKg: Double, goalType: GoalType, goalRatePct: Double?): Double =
        weeklyChangeKg(weightKg, goalType, goalRatePct) * KCAL_PER_KG / 7.0

    /**
     * Maintenance plus [dailyDelta], rounded to 10. A cut never drops below the usual safe
     * minimum, unless maintenance itself is lower, and never goes above maintenance.
     */
    fun targetKcal(maintenance: Double, dailyDelta: Double, sex: Sex): Int {
        val raw = maintenance + dailyDelta
        val floor = when (sex) {
            Sex.MALE -> MIN_TARGET_KCAL_MALE
            Sex.FEMALE -> MIN_TARGET_KCAL_FEMALE
        }.toDouble()
        val target = if (dailyDelta < 0) maxOf(raw, minOf(floor, maintenance)) else raw
        return roundToTen(target)
    }

    fun warnings(goalType: GoalType, goalRatePct: Double?, sex: Sex, latestBodyFatPct: Double?): Set<TargetWarning> =
        buildSet {
            if (goalType != GoalType.CUT) return@buildSet
            if ((goalRatePct ?: 0.0) > FAST_CUT_PCT) add(TargetWarning.FAST_CUT)
            val low = when (sex) {
                Sex.MALE -> LOW_BODY_FAT_MALE
                Sex.FEMALE -> LOW_BODY_FAT_FEMALE
            }
            if (latestBodyFatPct != null && latestBodyFatPct < low) add(TargetWarning.LOW_BODY_FAT_CUT)
        }

    /** The manual split (`SPLIT` mode). */
    fun goalsFor(kcal: Int, split: MacroSplit): NutritionGoals = NutritionGoals(
        kcal = kcal,
        proteinG = (kcal * split.proteinPercent / 100.0 / KCAL_PER_G_PROTEIN).roundToInt(),
        carbsG = (kcal * split.carbsPercent / 100.0 / KCAL_PER_G_CARBS).roundToInt(),
        fatG = (kcal * split.fatPercent / 100.0 / KCAL_PER_G_FAT).roundToInt(),
    )

    /** Returns null when the profile is out of range, or the split doesn't add up in `SPLIT` mode. */
    fun calculate(input: TargetInput): CalculatorResult? {
        val profile = input.profile
        if (invalidFields(profile).isNotEmpty()) return null
        if (input.macroMode == MacroMode.SPLIT && !input.split.isValid) return null

        val formula = formulaFor(input.bodyFat, input.today)
        val bmr = when (formula) {
            BmrFormula.KATCH_MCARDLE -> katchMcArdleBmr(profile.weightKg, input.bodyFat!!.pct)
            BmrFormula.MIFFLIN_ST_JEOR -> bmr(profile)
        }
        val tdee = bmr * profile.activity.multiplier
        val delta = dailyDeltaKcal(profile.weightKg, input.goalType, input.goalRatePct)
        val target = targetKcal(tdee, delta, profile.sex)

        val warnings = warnings(input.goalType, input.goalRatePct, profile.sex, input.bodyFat?.pct).toMutableSet()
        val goals = when (input.macroMode) {
            MacroMode.SPLIT -> goalsFor(target, input.split)
            MacroMode.AUTO -> {
                val protein = Macros.proteinGrams(profile.weightKg, profile.heightCm, input.proteinPerKg)
                val auto = Macros.auto(target, protein, input.fatPct)
                if (auto.proteinTooHigh) warnings += TargetWarning.PROTEIN_TOO_HIGH
                auto.goals
            }
        }
        return CalculatorResult(
            formula = formula,
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            weeklyChangeKg = weeklyChangeKg(profile.weightKg, input.goalType, input.goalRatePct),
            dailyDeltaKcal = delta,
            goals = goals,
            warnings = warnings,
        )
    }

    fun roundToTen(value: Double): Int = (value / 10.0).roundToInt() * 10
}

/** The result of [Macros.auto]. */
data class AutoMacros(val goals: NutritionGoals, val fatPctUsed: Int, val proteinTooHigh: Boolean)

/** `AUTO` macros (A4): protein per kg of base weight, fat as % of calories, carbs fill the rest. */
object Macros {
    const val DEFAULT_FAT_PCT = Member.DEFAULT_FAT_PCT
    const val MIN_FAT_PCT = 20
    val PROTEIN_PER_KG_RANGE = 1.2..2.4

    /** From this BMI on, protein is based on the weight at [BASE_BMI] (tunable). */
    const val OBESE_BMI = 30.0
    const val BASE_BMI = 25.0

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0

    /** Body weight, or the weight at BMI 25 when BMI is 30 or more. */
    fun proteinBaseWeight(weightKg: Double, heightCm: Double): Double {
        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        return if (bmi >= OBESE_BMI) heightM * heightM * BASE_BMI else weightKg
    }

    fun proteinGrams(weightKg: Double, heightCm: Double, proteinPerKg: Double): Double =
        proteinPerKg * proteinBaseWeight(weightKg, heightCm)

    /**
     * Fat at [fatPct] of [targetKcal], carbs fill the rest. If carbs come out negative, fat goes
     * down to 20 %; if they are still negative, carbs are 0 and fat takes what is left.
     */
    fun auto(targetKcal: Int, proteinG: Double, fatPct: Int): AutoMacros {
        val proteinKcal = proteinG * KCAL_PER_G_PROTEIN
        fun carbsFor(pct: Int) = (targetKcal - proteinKcal - targetKcal * pct / 100.0) / KCAL_PER_G_CARBS
        fun result(fatKcal: Double, carbsG: Double, pct: Int, tooHigh: Boolean) = AutoMacros(
            goals = NutritionGoals(
                kcal = targetKcal,
                proteinG = proteinG.roundToInt(),
                carbsG = carbsG.roundToInt(),
                fatG = (fatKcal / KCAL_PER_G_FAT).roundToInt(),
            ),
            fatPctUsed = pct,
            proteinTooHigh = tooHigh,
        )

        val pct = fatPct.coerceAtLeast(MIN_FAT_PCT)
        val carbs = carbsFor(pct)
        if (carbs >= 0) return result(targetKcal * pct / 100.0, carbs, pct, tooHigh = false)

        val lowered = carbsFor(MIN_FAT_PCT)
        if (lowered >= 0) return result(targetKcal * MIN_FAT_PCT / 100.0, lowered, MIN_FAT_PCT, tooHigh = false)

        val leftover = (targetKcal - proteinKcal).coerceAtLeast(0.0)
        return result(leftover, 0.0, MIN_FAT_PCT, tooHigh = true)
    }
}
