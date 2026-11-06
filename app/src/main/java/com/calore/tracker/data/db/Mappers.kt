package com.calore.tracker.data.db

import com.calore.tracker.domain.DiaryEntry
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.WeightEntry
import com.calore.tracker.domain.parseIsoDate
import java.time.LocalDate

/** Parses a stored ISO date. Returns null for a corrupted value instead of crashing. */
internal fun parseStoredDate(text: String): LocalDate? = parseIsoDate(text)

internal fun LocalDate.toStored(): String = toString()

private fun mealFromStored(name: String): MealType =
    MealType.entries.firstOrNull { it.name == name } ?: MealType.SNACK

private fun sourceFromStored(name: String): FoodSource =
    FoodSource.entries.firstOrNull { it.name == name } ?: FoodSource.CUSTOM

internal fun FoodEntity.toDomain(): Food = Food(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    per100g = Nutrients(
        kcal = kcalPer100g,
        protein = proteinPer100g,
        carbs = carbsPer100g,
        fat = fatPer100g,
        fiber = fiberPer100g,
        sugar = sugarPer100g,
    ),
    servingGrams = servingGrams,
    source = sourceFromStored(source),
    uuid = uuid,
)

internal fun Food.toEntity(lastUsedAt: Long, updatedAt: Long): FoodEntity = FoodEntity(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    kcalPer100g = per100g.kcal,
    proteinPer100g = per100g.protein,
    carbsPer100g = per100g.carbs,
    fatPer100g = per100g.fat,
    fiberPer100g = per100g.fiber,
    sugarPer100g = per100g.sugar,
    servingGrams = servingGrams,
    source = source.name,
    lastUsedAt = lastUsedAt,
    uuid = uuid,
    updatedAt = updatedAt,
)

/** Returns null when the stored date is unreadable so one bad row can't break a whole day. */
internal fun DiaryEntryEntity.toDomain(): DiaryEntry? {
    val parsedDate = parseStoredDate(date) ?: return null
    return DiaryEntry(
        id = id,
        date = parsedDate,
        meal = mealFromStored(meal),
        foodId = foodId,
        name = name,
        grams = grams,
        nutrients = Nutrients(kcal = kcal, protein = protein, carbs = carbs, fat = fat, fiber = fiber, sugar = sugar),
        loggedAt = createdAt,
        uuid = uuid,
        memberId = memberId,
    )
}

internal fun DiaryEntry.toEntity(): DiaryEntryEntity = DiaryEntryEntity(
    id = id,
    date = date.toStored(),
    meal = meal.name,
    foodId = foodId,
    name = name,
    grams = grams,
    kcal = nutrients.kcal,
    protein = nutrients.protein,
    carbs = nutrients.carbs,
    fat = nutrients.fat,
    fiber = nutrients.fiber,
    sugar = nutrients.sugar,
    createdAt = loggedAt,
    uuid = uuid,
    memberId = memberId,
)

internal fun WeightEntryEntity.toDomain(): WeightEntry? {
    val parsedDate = parseStoredDate(date) ?: return null
    return WeightEntry(id = id, date = parsedDate, kg = kg, uuid = uuid)
}

internal fun WeightEntry.toEntity(): WeightEntryEntity =
    WeightEntryEntity(id = id, date = date.toStored(), kg = kg, uuid = uuid)
