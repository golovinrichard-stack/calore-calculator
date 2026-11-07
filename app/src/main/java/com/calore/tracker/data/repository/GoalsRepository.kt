package com.calore.tracker.data.repository

import com.calore.tracker.domain.NutritionGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The phone's user's daily targets. Since M1 they live in the ME row of `members`. */
class GoalsRepository(private val members: MemberRepository) {

    val goals: Flow<NutritionGoals> = members.me().map { it.effectiveTargets }.distinctUntilChanged()

    suspend fun setGoals(goals: NutritionGoals) {
        members.updateMe { it.copy(targets = goals) }
    }
}
