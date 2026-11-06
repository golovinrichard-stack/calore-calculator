package com.calore.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved pot and its empty weight (M4.2). */
@Entity(tableName = "pots")
data class PotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emptyGrams: Double,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<PotEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) =
        copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** One cooked pot or tray (M4), with its nutrients per 100 g cooked as a snapshot. */
@Entity(tableName = "batches", indices = [Index(value = ["recipeId"])])
data class BatchEntity(
    @PrimaryKey val id: String,
    /** Null for a single food cooked on its own, for example plain rice. */
    val recipeId: String?,
    val name: String,
    val cookedAt: Long,
    val cookedByMemberId: String?,
    val rawGrams: Double,
    val cookedGrams: Double,
    val kcalPer100g: Double,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
    val fiberPer100g: Double?,
    val sugarPer100g: Double?,
    val ironMgPer100g: Double?,
    val magnesiumMgPer100g: Double?,
    val vitaminCMgPer100g: Double?,
    /** Filled in M5 when prices are known. */
    val costHalers: Long? = null,
    val ownerMemberId: String? = null,
    @ColumnInfo(defaultValue = "SHARED") val visibility: String = "SHARED",
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<BatchEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) =
        copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** What actually went into a batch. */
@Entity(tableName = "batch_ingredients", indices = [Index(value = ["batchId"])])
data class BatchIngredientEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val foodUuid: String,
    val grams: Double,
)

/** A prep box (M4.5). */
@Entity(tableName = "boxes", indices = [Index(value = ["state"]), Index(value = ["ownerMemberId"])])
data class BoxEntity(
    @PrimaryKey val id: String,
    /** For example "C3"; unique among boxes that are not eaten or wasted. */
    val code: String,
    val ownerMemberId: String?,
    @ColumnInfo(defaultValue = "SHARED") val visibility: String = "SHARED",
    /** `FRIDGE` or `FREEZER`. */
    val storage: String,
    /** [com.calore.tracker.domain.BoxState] name. */
    val state: String,
    @ColumnInfo(defaultValue = "1.0") val remainingFraction: Double = 1.0,
    /** ISO date (A12). */
    val eatByDate: String,
    val frozenAt: Long? = null,
    val thawedAt: Long? = null,
    val plannedDate: String? = null,
    val plannedMeal: String? = null,
    /** M5. */
    val planSlotId: String? = null,
    val wastedReason: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<BoxEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) =
        copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** A box holds one or more batches. */
@Entity(tableName = "box_parts", indices = [Index(value = ["boxId"]), Index(value = ["batchId"])])
data class BoxPartEntity(
    @PrimaryKey val id: String,
    val boxId: String,
    val batchId: String,
    val grams: Double,
)
