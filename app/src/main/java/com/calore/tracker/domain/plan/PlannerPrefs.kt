package com.calore.tracker.domain.plan

import com.calore.tracker.domain.Member

/** Preferences "Plan my week" works better with (M6.1). Diet and variety always have a value. */
enum class PlannerPref { ALLERGENS, LIKED_FOODS, DISLIKED_FOODS, CUISINES, EQUIPMENT, BUDGET, COOK_DAYS }

object PlannerPrefs {
    /** The ones [me] has not filled in, for the checklist shown the first time. */
    fun missing(me: Member): List<PlannerPref> = buildList {
        if (me.allergens.isEmpty()) add(PlannerPref.ALLERGENS)
        if (me.likedFoods.isEmpty()) add(PlannerPref.LIKED_FOODS)
        if (me.dislikedFoods.isEmpty()) add(PlannerPref.DISLIKED_FOODS)
        if (me.cuisines.isEmpty()) add(PlannerPref.CUISINES)
        if (me.equipment.isEmpty()) add(PlannerPref.EQUIPMENT)
        if (me.weeklyBudget == null) add(PlannerPref.BUDGET)
        if (me.cookDays.isEmpty()) add(PlannerPref.COOK_DAYS)
    }
}
