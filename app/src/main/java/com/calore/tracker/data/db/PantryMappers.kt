package com.calore.tracker.data.db

import com.calore.tracker.domain.Visibility

import com.calore.tracker.domain.AmountUnit
import com.calore.tracker.domain.PantryItem
import com.calore.tracker.domain.PantryState
import com.calore.tracker.domain.Storage
import com.calore.tracker.domain.parseIsoDate

/** Null when the bought date is unreadable, so one bad row can't break the pantry. */
internal fun PantryItemEntity.toDomain(): PantryItem? = PantryItem(
    id = id,
    foodUuid = foodUuid,
    storeProductId = storeProductId,
    name = name,
    amount = amount,
    unit = JsonColumns.enumByName<AmountUnit>(unit) ?: AmountUnit.G,
    storage = JsonColumns.enumByName<Storage>(storage) ?: Storage.CUPBOARD,
    boughtDate = parseIsoDate(boughtDate) ?: return null,
    bestBeforeDate = parseIsoDate(bestBeforeDate),
    openedDate = parseIsoDate(openedDate),
    state = JsonColumns.enumByName<PantryState>(state) ?: PantryState.AVAILABLE,
    costHalers = costHalers,
    reservedForCookId = reservedForCookId,
    ownerMemberId = ownerMemberId,
    visibility = JsonColumns.enumByName<Visibility>(visibility) ?: Visibility.SHARED,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun PantryItem.toEntity() = PantryItemEntity(
    id = id,
    foodUuid = foodUuid,
    storeProductId = storeProductId,
    name = name,
    amount = amount,
    unit = unit.name,
    storage = storage.name,
    boughtDate = boughtDate.toString(),
    bestBeforeDate = bestBeforeDate?.toString(),
    openedDate = openedDate?.toString(),
    state = state.name,
    costHalers = costHalers,
    reservedForCookId = reservedForCookId,
    ownerMemberId = ownerMemberId,
    visibility = visibility.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
