package com.calore.tracker.data.db

import com.calore.tracker.domain.MealTarget
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.parseIsoDate

/** Null when the stored date is unreadable, so one bad row can't break the plan. */
internal fun PlanSlotEntity.toDomain(): PlanSlot? {
    val day = parseIsoDate(date) ?: return null
    return PlanSlot(
        id = id,
        planId = planId,
        date = day,
        meal = JsonColumns.enumByName<MealType>(meal) ?: return null,
        memberId = memberId,
        kind = JsonColumns.enumByName<SlotKind>(kind) ?: SlotKind.FREE,
        recipeId = recipeId,
        boxId = boxId,
        target = targetKcal?.let { MealTarget(it.toDouble(), (targetProteinG ?: 0).toDouble(), (targetCarbsG ?: 0).toDouble(), (targetFatG ?: 0).toDouble()) },
        locked = locked,
        done = done,
    )
}

internal fun PlanSlot.toEntity(createdAt: Long, updatedAt: Long) = PlanSlotEntity(
    id = id,
    planId = planId,
    date = date.toString(),
    meal = meal.name,
    memberId = memberId,
    kind = kind.name,
    recipeId = recipeId,
    boxId = boxId,
    targetKcal = target?.kcal?.let { Math.round(it).toInt() },
    targetProteinG = target?.proteinG?.let { Math.round(it).toInt() },
    targetCarbsG = target?.carbsG?.let { Math.round(it).toInt() },
    targetFatG = target?.fatG?.let { Math.round(it).toInt() },
    locked = locked,
    done = done,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
