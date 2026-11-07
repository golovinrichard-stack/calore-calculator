package com.calore.tracker.domain.cycle

import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.plan.PhaseBonusProvider
import com.calore.tracker.domain.plan.PlannerRecipe
import java.time.LocalDate

/** Phase tips (A20). No tip ever changes the calorie target. */
enum class CycleTip { IRON_WITH_VITAMIN_C, WARM_EASY_MEALS, PROTEIN_FIBRE_SNACKS, MAGNESIUM_FOOD }

/** Recipe badges (M7.4), from nutrients per serving. */
enum class RecipeBadge { IRON_RICH, MAGNESIUM_RICH }

/** Cycle tips and the planner bonus (algorithms A20). */
object CycleTips {

    /** Per serving (*tunable*). */
    const val IRON_MG = 3.0
    const val VITAMIN_C_MG = 20.0
    const val MAGNESIUM_MG = 80.0
    const val FIBRE_G = 8.0

    const val IRON_BONUS = 10.0
    const val VITAMIN_C_BONUS = 2.0
    const val MAGNESIUM_BONUS = 8.0
    const val FIBRE_BONUS = 4.0

    /** The bonus is capped at this per recipe. */
    const val MAX_BONUS = 10.0

    /** The tips for a phase; [symptoms] are those logged in the current period (cramps add warm, easy meals). */
    fun tips(phase: CyclePhase?, symptoms: Set<Symptom> = emptySet()): List<CycleTip> = when (phase) {
        CyclePhase.PERIOD -> listOfNotNull(CycleTip.IRON_WITH_VITAMIN_C, CycleTip.WARM_EASY_MEALS.takeIf { Symptom.CRAMPS in symptoms })
        CyclePhase.LUTEAL -> listOf(CycleTip.PROTEIN_FIBRE_SNACKS, CycleTip.MAGNESIUM_FOOD)
        CyclePhase.FOLLICULAR, CyclePhase.OVULATION, null -> emptyList()
    }

    /** A20 planner bonus for one serving, capped at [MAX_BONUS]. */
    fun bonus(phase: CyclePhase?, perServing: Nutrients): Double {
        val points = when (phase) {
            CyclePhase.PERIOD -> (if ((perServing.ironMg ?: 0.0) >= IRON_MG) IRON_BONUS else 0.0) +
                (if ((perServing.vitaminCMg ?: 0.0) >= VITAMIN_C_MG) VITAMIN_C_BONUS else 0.0)
            CyclePhase.LUTEAL -> (if ((perServing.magnesiumMg ?: 0.0) >= MAGNESIUM_MG) MAGNESIUM_BONUS else 0.0) +
                (if ((perServing.fiber ?: 0.0) >= FIBRE_G) FIBRE_BONUS else 0.0)
            CyclePhase.FOLLICULAR, CyclePhase.OVULATION, null -> 0.0
        }
        return minOf(points, MAX_BONUS)
    }

    fun badges(perServing: Nutrients): Set<RecipeBadge> = buildSet {
        if ((perServing.ironMg ?: 0.0) >= IRON_MG) add(RecipeBadge.IRON_RICH)
        if ((perServing.magnesiumMg ?: 0.0) >= MAGNESIUM_MG) add(RecipeBadge.MAGNESIUM_RICH)
    }
}

/**
 * The planner's cycle bonus (A20, M6.2 [PhaseBonusProvider]) for the person who tracks their cycle.
 * [phaseOn] returns null when tracking is off or paused, so nobody gets a bonus then.
 */
class CyclePhaseBonus(private val memberId: String, private val phaseOn: (LocalDate) -> CyclePhase?) : PhaseBonusProvider {
    override fun bonus(memberId: String, date: LocalDate, recipe: PlannerRecipe): Double =
        if (memberId != this.memberId) 0.0 else CycleTips.bonus(phaseOn(date), recipe.nutrition.perServing)
}
