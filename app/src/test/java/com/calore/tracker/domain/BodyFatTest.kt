package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Algorithms A5. */
class BodyFatTest {

    private fun pct(estimate: BodyFatEstimate) = (estimate as BodyFatEstimate.Ok).pct

    @Test
    fun manAt180Cm() {
        val estimate = NavyBodyFat.estimate(Sex.MALE, heightCm = 180.0, neckCm = 38.0, waistCm = 85.0, hipCm = null)
        assertEquals(16.1, pct(estimate), 0.05)
    }

    @Test
    fun womanAt165Cm() {
        val estimate = NavyBodyFat.estimate(Sex.FEMALE, heightCm = 165.0, neckCm = 32.0, waistCm = 70.0, hipCm = 95.0)
        assertEquals(24.9, pct(estimate), 0.05)
    }

    @Test
    fun waistSmallerThanNeckIsRejected() {
        val estimate = NavyBodyFat.estimate(Sex.MALE, heightCm = 180.0, neckCm = 40.0, waistCm = 38.0, hipCm = null)
        assertEquals(BodyFatEstimate.Invalid(BodyFatProblem.WAIST_NOT_ABOVE_NECK), estimate)
    }

    @Test
    fun womenNeedTheHips() {
        val estimate = NavyBodyFat.estimate(Sex.FEMALE, heightCm = 165.0, neckCm = 32.0, waistCm = 70.0, hipCm = null)
        assertEquals(BodyFatEstimate.Invalid(BodyFatProblem.MISSING_HIP), estimate)
    }

    @Test
    fun measurementsMustBe20To200Cm() {
        assertEquals(
            BodyFatEstimate.Invalid(BodyFatProblem.OUT_OF_RANGE),
            NavyBodyFat.estimate(Sex.MALE, 180.0, neckCm = 19.0, waistCm = 85.0, hipCm = null),
        )
        assertEquals(
            BodyFatEstimate.Invalid(BodyFatProblem.OUT_OF_RANGE),
            NavyBodyFat.estimate(Sex.FEMALE, 165.0, neckCm = 32.0, waistCm = 70.0, hipCm = 201.0),
        )
        assertEquals(
            BodyFatEstimate.Invalid(BodyFatProblem.OUT_OF_RANGE),
            NavyBodyFat.estimate(Sex.MALE, heightCm = 90.0, neckCm = 38.0, waistCm = 85.0, hipCm = null),
        )
    }

    @Test
    fun implausibleResultsAreRejected() {
        // Waist barely above neck gives a negative percentage.
        assertEquals(
            BodyFatEstimate.Invalid(BodyFatProblem.IMPLAUSIBLE_RESULT),
            NavyBodyFat.estimate(Sex.MALE, 180.0, neckCm = 40.0, waistCm = 42.0, hipCm = null),
        )
        val ok = NavyBodyFat.estimate(Sex.MALE, 180.0, neckCm = 40.0, waistCm = 120.0, hipCm = null)
        assertTrue(pct(ok) in NavyBodyFat.RESULT_RANGE_PCT)
    }

    @Test
    fun menIgnoreTheHips() {
        val withHip = NavyBodyFat.estimate(Sex.MALE, 180.0, 38.0, 85.0, hipCm = 500.0)
        assertEquals(16.1, pct(withHip), 0.05)
    }
}
