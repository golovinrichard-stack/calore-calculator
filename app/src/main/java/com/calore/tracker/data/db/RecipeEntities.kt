package com.calore.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.MealType

/** A recipe (data-model M3). Shared with the household in M11. */
@Entity(
    tableName = "recipes",
    indices = [Index(value = ["key"], unique = true)],
)
data class RecipeEntity(
    @PrimaryKey val id: String,
    val key: String?,
    val name: String,
    val nameCs: String?,
    val nameEn: String?,
    @ColumnInfo(defaultValue = "0") val userEdited: Boolean,
    val shortCode: String,
    val servings: Int,
    val mealTypes: List<MealType>,
    val cuisine: String?,
    val tags: List<String>,
    val equipment: List<Equipment>,
    @ColumnInfo(defaultValue = "3") val fridgeDays: Int,
    @ColumnInfo(defaultValue = "0") val freezable: Boolean,
    @ColumnInfo(defaultValue = "3") val freezerMonths: Int,
    val reheatNote: String?,
    @ColumnInfo(defaultValue = "0") val mealPrepFriendly: Boolean,
    val learnedYield: Double?,
    @ColumnInfo(defaultValue = "0") val learnedYieldCount: Int,
    /** Null means the whole household (M11). */
    val ownerMemberId: String? = null,
    /** [com.calore.tracker.domain.Visibility] name. */
    @ColumnInfo(defaultValue = "SHARED") val visibility: String = "SHARED",
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<RecipeEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) =
        copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

@Entity(
    tableName = "recipe_ingredients",
    indices = [Index(value = ["recipeId"])],
)
data class RecipeIngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    /** `foods.uuid`. */
    val foodUuid: String,
    val grams: Double,
    val note: String?,
    @ColumnInfo(defaultValue = "0") val optional: Boolean,
)

@Entity(
    tableName = "recipe_steps",
    indices = [Index(value = ["recipeId"])],
)
data class RecipeStepEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val text: String,
    val textCs: String?,
    val textEn: String?,
    val minutes: Int?,
    @ColumnInfo(defaultValue = "0") val passive: Boolean,
    val equipment: String?,
)
