package com.calore.tracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** One weigh-in with the smoothed trend at that day. */
data class TrendPoint(val date: LocalDate, val kg: Double, val trendKg: Double)

/**
 * The weight trend (algorithms A6): an exponential moving average that handles gaps, so daily
 * water-weight jumps don't hide the real direction.
 */
object TrendWeight {
    /** How much of the old trend survives one day. */
    const val DAILY_KEEP = 0.9

    /** Next trend value after a weigh-in [gapDays] after the previous one (at least 1 day). */
    fun next(trend: Double, weightKg: Double, gapDays: Long): Double {
        val alpha = 1.0 - DAILY_KEEP.pow(gapDays.coerceAtLeast(1).toDouble())
        return trend + alpha * (weightKg - trend)
    }

    /** Trend points for [weighIns] in any order; the result is oldest first. */
    fun of(weighIns: List<WeightEntry>): List<TrendPoint> {
        val sorted = weighIns.sortedBy { it.date }
        val points = ArrayList<TrendPoint>(sorted.size)
        for (entry in sorted) {
            val previous = points.lastOrNull()
            val trend = if (previous == null) {
                entry.kg
            } else {
                next(previous.trendKg, entry.kg, ChronoUnit.DAYS.between(previous.date, entry.date))
            }
            points += TrendPoint(entry.date, entry.kg, trend)
        }
        return points
    }
}
