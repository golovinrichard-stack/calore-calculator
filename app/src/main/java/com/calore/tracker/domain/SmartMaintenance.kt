package com.calore.tracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Calories logged on one day. */
data class DayIntake(val date: LocalDate, val kcal: Double)

/** Everything the weekly check-in needs (algorithms A7). */
data class CheckinInput(
    val today: LocalDate,
    /** Daily totals; days without entries may be missing. */
    val intakes: List<DayIntake>,
    /** The target a day's total is compared with to decide whether the day was fully logged. */
    val dailyTargetKcal: Int,
    /** All weigh-ins, any order. */
    val weighIns: List<WeightEntry>,
    /** Likely water-retention days from cycle tracking (A19), left out of the trend (M7). */
    val excludedWeighInDates: Set<LocalDate> = emptySet(),
    /** Maintenance from the formula (A2), or null when the profile is incomplete. */
    val formulaTdee: Double?,
    val sex: Sex,
    val goalType: GoalType,
    val goalRatePct: Double?,
    val currentTargetKcal: Int,
)

sealed interface CheckinOutcome {
    /** "Keep logging; your first check-in comes when there is enough data." */
    data class NotEnoughData(val countedDays: Int, val weighIns: Int, val spanDays: Long) : CheckinOutcome {
        val daysMissing: Int get() = (SmartMaintenance.MIN_COUNTED_DAYS - countedDays).coerceAtLeast(0)
        val weighInsMissing: Int get() = (SmartMaintenance.MIN_WEIGH_INS - weighIns).coerceAtLeast(0)
    }

    data class Proposal(
        val trendStartKg: Double,
        val trendEndKg: Double,
        val spanDays: Long,
        val avgIntakeKcal: Double,
        val countedDays: Int,
        val dataMaintenanceKcal: Double,
        val estimatedMaintenanceKcal: Double,
        val dailyDeltaKcal: Double,
        val oldTargetKcal: Int,
        val proposedTargetKcal: Int,
    ) : CheckinOutcome {
        val trendChangeKg: Double get() = trendEndKg - trendStartKg
    }
}

/**
 * Smart maintenance (A7): the real maintenance from the weight trend and the food log, blended
 * with the formula while data is thin, and a new target that moves at most 150 kcal a week.
 */
object SmartMaintenance {
    /** Tunable values. */
    const val WINDOW_DAYS = 28L
    const val COUNTED_SHARE = 0.5
    const val MIN_COUNTED_DAYS = 14
    const val MIN_WEIGH_INS = 4
    const val MIN_SPAN_DAYS = 10L
    const val MAX_STEP_KCAL = 150

    /** Step 5: `avgIntake − (trendEnd − trendStart) × 7,700 / spanDays`. */
    fun dataMaintenance(avgIntakeKcal: Double, trendStartKg: Double, trendEndKg: Double, spanDays: Long): Double =
        avgIntakeKcal - (trendEndKg - trendStartKg) * CalorieCalculator.KCAL_PER_KG / spanDays

    /** Step 6: `w = min(1, countedDays / 28)`; without a formula value only the data counts. */
    fun blend(dataMaintenanceKcal: Double, countedDays: Int, formulaTdee: Double?): Double {
        if (formulaTdee == null) return dataMaintenanceKcal
        val w = min(1.0, countedDays / WINDOW_DAYS.toDouble())
        return w * dataMaintenanceKcal + (1 - w) * formulaTdee
    }

    /** Steps 7–8: the A3 target with its safety floors, moved at most [MAX_STEP_KCAL] from [currentTargetKcal]. */
    fun proposedTarget(estimateKcal: Double, dailyDeltaKcal: Double, sex: Sex, currentTargetKcal: Int): Int {
        val target = CalorieCalculator.targetKcal(estimateKcal, dailyDeltaKcal, sex)
        val step = (target - currentTargetKcal).coerceIn(-MAX_STEP_KCAL, MAX_STEP_KCAL)
        return currentTargetKcal + step
    }

    fun run(input: CheckinInput): CheckinOutcome {
        // Step 1: the last 28 complete days for food; weigh-ins up to today.
        val firstDay = input.today.minusDays(WINDOW_DAYS)
        val lastFoodDay = input.today.minusDays(1)

        // Step 2: a day counts when at least half its target was logged.
        val threshold = input.dailyTargetKcal * COUNTED_SHARE
        val counted = input.intakes
            .filter { !it.date.isBefore(firstDay) && !it.date.isAfter(lastFoodDay) && it.kcal >= threshold }
            .distinctBy { it.date }

        // Step 4: leave out likely water-retention days.
        val usable = input.weighIns.filter { it.date !in input.excludedWeighInDates && !it.date.isAfter(input.today) }
        val trend = TrendWeight.of(usable)
        val inWindow = trend.filter { !it.date.isBefore(firstDay) }
        val span = if (inWindow.size >= 2) ChronoUnit.DAYS.between(inWindow.first().date, inWindow.last().date) else 0L

        // Step 3: enough data?
        if (counted.size < MIN_COUNTED_DAYS || inWindow.size < MIN_WEIGH_INS || span < MIN_SPAN_DAYS) {
            return CheckinOutcome.NotEnoughData(counted.size, inWindow.size, span)
        }

        val avgIntake = counted.sumOf { it.kcal } / counted.size
        val start = inWindow.first().trendKg
        val end = inWindow.last().trendKg
        val data = dataMaintenance(avgIntake, start, end, span)
        val estimate = blend(data, counted.size, input.formulaTdee)
        val delta = CalorieCalculator.dailyDeltaKcal(end, input.goalType, input.goalRatePct)
        return CheckinOutcome.Proposal(
            trendStartKg = start,
            trendEndKg = end,
            spanDays = span,
            avgIntakeKcal = avgIntake,
            countedDays = counted.size,
            dataMaintenanceKcal = data,
            estimatedMaintenanceKcal = estimate,
            dailyDeltaKcal = delta,
            oldTargetKcal = input.currentTargetKcal,
            proposedTargetKcal = proposedTarget(estimate, delta, input.sex, input.currentTargetKcal),
        )
    }

    /** True when the proposal changes nothing worth showing as a new target. */
    fun isUnchanged(proposal: CheckinOutcome.Proposal): Boolean =
        abs(proposal.proposedTargetKcal - proposal.oldTargetKcal) < 10

    fun roundKcal(value: Double): Int = value.roundToInt()
}
