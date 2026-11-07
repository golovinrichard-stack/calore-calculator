package com.calore.tracker.data.db

import com.calore.tracker.domain.Batch
import com.calore.tracker.domain.Box
import com.calore.tracker.domain.BoxPart
import com.calore.tracker.domain.BoxState
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Pot
import com.calore.tracker.domain.Storage
import com.calore.tracker.domain.WasteReason
import com.calore.tracker.domain.parseIsoDate

internal fun PotEntity.toDomain() = Pot(id, name, emptyGrams)

internal fun BatchEntity.toDomain() = Batch(
    id = id,
    recipeId = recipeId,
    name = name,
    cookedAt = cookedAt,
    rawGrams = rawGrams,
    cookedGrams = cookedGrams,
    costHalers = costHalers,
    per100g = Nutrients(
        kcal = kcalPer100g,
        protein = proteinPer100g,
        carbs = carbsPer100g,
        fat = fatPer100g,
        fiber = fiberPer100g,
        sugar = sugarPer100g,
        ironMg = ironMgPer100g,
        magnesiumMg = magnesiumMgPer100g,
        vitaminCMg = vitaminCMgPer100g,
    ),
)

internal fun BoxPartEntity.toDomain() = BoxPart(id, boxId, batchId, grams)

/** Null when the stored eat-by date is unreadable, so one bad row can't break the kitchen. */
internal fun BoxEntity.toDomain(): Box? {
    val eatBy = parseIsoDate(eatByDate) ?: return null
    return Box(
        id = id,
        code = code,
        ownerMemberId = ownerMemberId,
        storage = JsonColumns.enumByName<Storage>(storage) ?: Storage.FRIDGE,
        state = JsonColumns.enumByName<BoxState>(state) ?: BoxState.READY,
        remainingFraction = remainingFraction,
        eatByDate = eatBy,
        frozenAt = frozenAt,
        thawedAt = thawedAt,
        plannedDate = parseIsoDate(plannedDate),
        plannedMeal = plannedMeal?.let { JsonColumns.enumByName<MealType>(it) },
        planSlotId = planSlotId,
        wastedReason = wastedReason?.let { JsonColumns.enumByName<WasteReason>(it) },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

internal fun Box.toEntity(deletedAt: Long? = null) = BoxEntity(
    id = id,
    code = code,
    ownerMemberId = ownerMemberId,
    storage = storage.name,
    state = state.name,
    remainingFraction = remainingFraction,
    eatByDate = eatByDate.toString(),
    frozenAt = frozenAt,
    thawedAt = thawedAt,
    plannedDate = plannedDate?.toString(),
    plannedMeal = plannedMeal?.name,
    planSlotId = planSlotId,
    wastedReason = wastedReason?.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
