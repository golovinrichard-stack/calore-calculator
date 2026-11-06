package com.calore.tracker.data.backup

import com.calore.tracker.data.db.DiaryEntryEntity
import com.calore.tracker.data.db.FoodEntity
import com.calore.tracker.data.db.WeightEntryEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The backup file (M0.5). One JSON object with a header and one array per database table.
 * The rows are separate DTOs, not the Room entities, so a database change never silently
 * changes the file format.
 *
 * Every new table is added to [BackupTables] (definition of done). When a table or column is
 * added, raise [BackupCodec.VERSION]: this app reads its own version and older ones (a table
 * missing from an older file is simply empty), and refuses newer files instead of dropping data.
 */
@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    /** ISO-8601 instant, e.g. 2026-11-06T18:00:00Z. */
    val exportedAt: String,
    val appVersion: String,
    val tables: BackupTables,
)

@Serializable
data class BackupTables(
    val foods: List<FoodRow> = emptyList(),
    @SerialName("diary_entries") val diaryEntries: List<DiaryEntryRow> = emptyList(),
    @SerialName("weight_entries") val weightEntries: List<WeightEntryRow> = emptyList(),
) {
    val counts: BackupCounts get() = BackupCounts(foods.size, diaryEntries.size, weightEntries.size)
}

/** How many rows of each kind a backup holds; shown before an import. */
data class BackupCounts(val foods: Int, val diaryEntries: Int, val weightEntries: Int)

@Serializable
data class FoodRow(
    val id: Long,
    val uuid: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double? = null,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val fiberPer100g: Double? = null,
    val sugarPer100g: Double? = null,
    val servingGrams: Double? = null,
    val source: String,
    val lastUsedAt: Long,
    val updatedAt: Long,
)

@Serializable
data class DiaryEntryRow(
    val id: Long,
    val uuid: String,
    val date: String,
    val meal: String,
    val foodId: Long? = null,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val createdAt: Long,
)

@Serializable
data class WeightEntryRow(
    val id: Long,
    val uuid: String,
    val date: String,
    val kg: Double,
)

internal fun FoodEntity.toRow() = FoodRow(
    id = id,
    uuid = uuid,
    name = name,
    brand = brand,
    barcode = barcode,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g,
    fiberPer100g = fiberPer100g,
    sugarPer100g = sugarPer100g,
    servingGrams = servingGrams,
    source = source,
    lastUsedAt = lastUsedAt,
    updatedAt = updatedAt,
)

internal fun FoodRow.toEntity() = FoodEntity(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g,
    fiberPer100g = fiberPer100g,
    sugarPer100g = sugarPer100g,
    servingGrams = servingGrams,
    source = source,
    lastUsedAt = lastUsedAt,
    uuid = uuid,
    updatedAt = updatedAt,
)

internal fun DiaryEntryEntity.toRow() = DiaryEntryRow(
    id = id,
    uuid = uuid,
    date = date,
    meal = meal,
    foodId = foodId,
    name = name,
    grams = grams,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    sugar = sugar,
    createdAt = createdAt,
)

internal fun DiaryEntryRow.toEntity() = DiaryEntryEntity(
    id = id,
    date = date,
    meal = meal,
    foodId = foodId,
    name = name,
    grams = grams,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    sugar = sugar,
    createdAt = createdAt,
    uuid = uuid,
)

internal fun WeightEntryEntity.toRow() = WeightEntryRow(id = id, uuid = uuid, date = date, kg = kg)

internal fun WeightEntryRow.toEntity() = WeightEntryEntity(id = id, date = date, kg = kg, uuid = uuid)
