package com.calore.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A food the user has created or picked from Open Food Facts. Values are per 100 g. */
@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["lastUsedAt"]),
        Index(value = ["uuid"], unique = true),
    ],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
    val fiberPer100g: Double?,
    val sugarPer100g: Double?,
    val servingGrams: Double?,
    /** [com.calore.tracker.domain.FoodSource] name. */
    val source: String,
    /** Epoch millis; drives the "recent foods" list. */
    val lastUsedAt: Long,
    /** Stable id shared across phones. New tables reference foods by this, as `foodUuid`. */
    @ColumnInfo(defaultValue = "") val uuid: String,
    /** Epoch millis of the last change to the food itself. Using a food changes only [lastUsedAt]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long,
)

/**
 * One logged portion. Nutrient columns hold the amounts for [grams], copied when the
 * entry is created. [foodId] is deliberately not a foreign key: deleting a food must
 * not delete or change history.
 */
@Entity(
    tableName = "diary_entries",
    indices = [
        Index(value = ["date"]),
        Index(value = ["uuid"], unique = true),
    ],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO-8601 local date, e.g. 2026-10-21. Sorts correctly as text. */
    val date: String,
    /** [com.calore.tracker.domain.MealType] name. */
    val meal: String,
    val foodId: Long?,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "") val uuid: String,
    /** The member who ate it; null is the phone's user (ME). */
    val memberId: String? = null,
)

/** At most one weight per day; saving again for the same date replaces it. */
@Entity(
    tableName = "weight_entries",
    indices = [
        Index(value = ["date"], unique = true),
        Index(value = ["uuid"], unique = true),
    ],
)
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val kg: Double,
    @ColumnInfo(defaultValue = "") val uuid: String,
)
