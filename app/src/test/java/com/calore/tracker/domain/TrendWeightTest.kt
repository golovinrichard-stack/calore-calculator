package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Algorithms A6. */
class TrendWeightTest {

    private val day = LocalDate.of(2026, 11, 2)

    private fun weight(date: LocalDate, kg: Double) = WeightEntry(id = 0, date = date, kg = kg, uuid = newId())

    @Test
    fun nextDay() {
        assertEquals(79.90, TrendWeight.next(trend = 80.0, weightKg = 79.0, gapDays = 1), 0.005)
    }

    @Test
    fun threeDaysLater() {
        assertEquals(79.73, TrendWeight.next(trend = 80.0, weightKg = 79.0, gapDays = 3), 0.005)
    }

    @Test
    fun firstWeighInSetsTheTrend() {
        val points = TrendWeight.of(listOf(weight(day, 80.0)))
        assertEquals(80.0, points.single().trendKg, 0.0)
    }

    @Test
    fun aSeriesWithGapsAndAnyOrder() {
        val points = TrendWeight.of(
            listOf(weight(day.plusDays(4), 79.0), weight(day, 80.0), weight(day.plusDays(1), 79.0)),
        )
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(4)), points.map { it.date })
        assertEquals(80.0, points[0].trendKg, 1e-9)
        assertEquals(79.9, points[1].trendKg, 1e-9)
        // 79.9 + 0.271 × (79.0 − 79.9)
        assertEquals(79.6561, points[2].trendKg, 1e-9)
    }

    @Test
    fun sameDayCountsAsOneDay() {
        assertEquals(79.9, TrendWeight.next(80.0, 79.0, gapDays = 0), 1e-9)
    }

    @Test
    fun trendMovesLessThanTheScale() {
        val points = TrendWeight.of((0L..13L).map { weight(day.plusDays(it), if (it % 2 == 0L) 80.0 else 81.0) })
        assertTrue(points.last().trendKg in 80.3..80.7)
    }
}
