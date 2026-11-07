package com.calore.tracker.data.repository

import com.calore.tracker.data.db.DiaryDao
import com.calore.tracker.data.db.parseStoredDate
import com.calore.tracker.domain.BodyProfile
import com.calore.tracker.domain.CalorieCalculator
import com.calore.tracker.domain.Checkin
import com.calore.tracker.domain.CheckinInput
import com.calore.tracker.domain.CheckinOutcome
import com.calore.tracker.domain.DayIntake
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MacroMode
import com.calore.tracker.domain.Macros
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.SmartMaintenance
import com.calore.tracker.domain.TargetInput
import com.calore.tracker.domain.ageOn
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import kotlin.math.roundToInt
import com.calore.tracker.domain.cycle.Cycles

/** The weekly check-in (M1.7): gathers the data for A7, saves the check-in, applies an accepted target. */
class CheckinService(
    private val diaryDao: DiaryDao,
    private val weights: WeightRepository,
    private val members: MemberRepository,
    private val body: BodyRepository,
    private val clock: Clock,
    private val cycle: CycleRepository? = null,
) {
    /** What the check-in says today. Nothing is saved. */
    suspend fun evaluate(): CheckinOutcome {
        val today = LocalDate.now(clock)
        val me = members.ensureMe()
        val from = today.minusDays(SmartMaintenance.WINDOW_DAYS)
        val intakes = diaryDao.dailyKcal(from.toString(), today.minusDays(1).toString())
            .mapNotNull { row -> parseStoredDate(row.date)?.let { DayIntake(it, row.kcal) } }
        val weighIns = weights.entries().first()
        val target = me.effectiveTargets.kcal
        // A7 step 4 (M7.5): likely water-retention days from cycle tracking are left out of the trend.
        val waterDays = cycle?.let { repository ->
            val first = weighIns.minOfOrNull { it.date } ?: return@let emptySet()
            Cycles.waterDays(first, today, repository.periodsNow(), repository.settings.first())
        }.orEmpty()
        return SmartMaintenance.run(
            CheckinInput(
                today = today,
                intakes = intakes,
                // That day's target isn't stored per day, so the current target is used for every day.
                dailyTargetKcal = target,
                weighIns = weighIns,
                excludedWeighInDates = waterDays,
                formulaTdee = formulaTdee(me, weighIns.lastOrNull()?.kg, today),
                sex = me.sex ?: Sex.FEMALE,
                goalType = me.goalType ?: GoalType.MAINTAIN,
                goalRatePct = me.goalRatePct,
                currentTargetKcal = target,
            ),
        )
    }

    /**
     * Runs the check-in for this week and saves it, unless this week's check-in exists already.
     * Returns the saved check-in, or null when there isn't enough data yet.
     */
    suspend fun checkInThisWeek(): Checkin? {
        val today = LocalDate.now(clock)
        val week = ReminderSchedule.weekStart(today)
        body.checkinFor(week)?.let { return it }
        val proposal = evaluate() as? CheckinOutcome.Proposal ?: return null
        val me = members.ensureMe()
        val checkin = Checkin(
            id = newId(),
            memberId = me.id,
            weekStart = week,
            trendStartKg = proposal.trendStartKg,
            trendEndKg = proposal.trendEndKg,
            avgIntakeKcal = proposal.avgIntakeKcal,
            countedDays = proposal.countedDays,
            estimatedMaintenanceKcal = proposal.estimatedMaintenanceKcal.roundToInt(),
            oldTargetKcal = proposal.oldTargetKcal,
            proposedTargetKcal = proposal.proposedTargetKcal,
            accepted = false,
            createdAt = clock.millis(),
        )
        body.saveCheckin(checkin)
        return checkin
    }

    /** "Use it": the proposed calories become the target, and the macros are recalculated (A4). */
    suspend fun accept(checkin: Checkin) {
        val latestKg = weights.latest()?.kg ?: checkin.trendEndKg
        members.updateMe { me -> me.copy(targets = targetsFor(me, checkin.proposedTargetKcal, latestKg)) }
        body.saveCheckin(checkin.copy(accepted = true))
    }

    /** "Keep my target": nothing changes, and the check-in stays as not accepted. */
    suspend fun keep(checkin: Checkin) {
        body.saveCheckin(checkin.copy(accepted = false))
    }

    companion object {
        /** New targets for [kcal], with macros in the member's mode (A4). */
        fun targetsFor(me: Member, kcal: Int, weightKg: Double?): NutritionGoals {
            val height = me.heightCm
            return if (me.macroMode == MacroMode.AUTO && weightKg != null) {
                val protein = if (height != null) {
                    Macros.proteinGrams(weightKg, height, me.effectiveProteinPerKg)
                } else {
                    me.effectiveProteinPerKg * weightKg
                }
                Macros.auto(kcal, protein, me.effectiveFatPct).goals
            } else {
                CalorieCalculator.goalsFor(kcal, me.effectiveSplit)
            }
        }

        /** Maintenance from the formula, or null when the profile can't give one. */
        fun formulaTdee(me: Member, weightKg: Double?, today: LocalDate): Double? {
            val profile = BodyProfile(
                sex = me.sex ?: return null,
                ageYears = me.birthDate?.let { ageOn(it, today) } ?: return null,
                heightCm = me.heightCm ?: return null,
                weightKg = weightKg ?: return null,
                activity = me.activity ?: return null,
            )
            if (CalorieCalculator.invalidFields(profile).isNotEmpty()) return null
            return CalorieCalculator.tdee(profile)
        }
    }
}
