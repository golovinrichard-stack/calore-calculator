package com.calore.tracker.domain

import kotlin.math.log10

/** Why tape measurements can't give a body-fat value. */
enum class BodyFatProblem {
    /** Women need a hip measurement. */
    MISSING_HIP,

    /** A measurement outside 20–200 cm, or a height outside the calculator's range. */
    OUT_OF_RANGE,

    /** Waist not larger than neck (men), or waist + hip not larger than neck (women). */
    WAIST_NOT_ABOVE_NECK,

    /** The formula gave less than 3 % or more than 60 %: "Check your measurements". */
    IMPLAUSIBLE_RESULT,
}

sealed interface BodyFatEstimate {
    data class Ok(val pct: Double) : BodyFatEstimate
    data class Invalid(val problem: BodyFatProblem) : BodyFatEstimate
}

/** Body fat from tape measurements, US Navy method (Hodgdon and Beckett, 1984); algorithms A5. */
object NavyBodyFat {
    val MEASUREMENT_RANGE_CM = 20.0..200.0
    val RESULT_RANGE_PCT = 3.0..60.0

    /** A typed-in value from a smart scale or a gym must be within this range too. */
    val MANUAL_RANGE_PCT = RESULT_RANGE_PCT

    fun estimate(sex: Sex, heightCm: Double, neckCm: Double, waistCm: Double, hipCm: Double?): BodyFatEstimate {
        if (heightCm !in CalorieCalculator.HEIGHT_RANGE_CM) return BodyFatEstimate.Invalid(BodyFatProblem.OUT_OF_RANGE)
        if (sex == Sex.FEMALE && hipCm == null) return BodyFatEstimate.Invalid(BodyFatProblem.MISSING_HIP)
        val measurements = listOfNotNull(neckCm, waistCm, hipCm.takeIf { sex == Sex.FEMALE })
        if (measurements.any { it !in MEASUREMENT_RANGE_CM }) return BodyFatEstimate.Invalid(BodyFatProblem.OUT_OF_RANGE)

        val pct = when (sex) {
            Sex.MALE -> {
                if (waistCm <= neckCm) return BodyFatEstimate.Invalid(BodyFatProblem.WAIST_NOT_ABOVE_NECK)
                495.0 / (1.0324 - 0.19077 * log10(waistCm - neckCm) + 0.15456 * log10(heightCm)) - 450.0
            }
            Sex.FEMALE -> {
                val girth = waistCm + hipCm!! - neckCm
                if (girth <= 0.0) return BodyFatEstimate.Invalid(BodyFatProblem.WAIST_NOT_ABOVE_NECK)
                495.0 / (1.29579 - 0.35004 * log10(girth) + 0.22100 * log10(heightCm)) - 450.0
            }
        }
        if (!pct.isFinite() || pct !in RESULT_RANGE_PCT) return BodyFatEstimate.Invalid(BodyFatProblem.IMPLAUSIBLE_RESULT)
        return BodyFatEstimate.Ok(pct)
    }
}
