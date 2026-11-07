package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A person's 👍 (1) or 👎 (−1) for a recipe (data-model M6). Shared with the household in M11. */
@Entity(
    tableName = "recipe_ratings",
    indices = [Index(value = ["memberId", "recipeId"], unique = true)],
)
data class RecipeRatingEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val recipeId: String,
    /** −1 or 1. */
    val rating: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    /** Set when the rating is taken back. */
    override val deletedAt: Long? = null,
) : Stamped<RecipeRatingEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) =
        copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

@Dao
interface RatingDao {
    @Query("SELECT * FROM recipe_ratings WHERE ${SoftDelete.NOT_DELETED}")
    fun observeAll(): Flow<List<RecipeRatingEntity>>

    @Query("SELECT * FROM recipe_ratings WHERE ${SoftDelete.NOT_DELETED}")
    suspend fun all(): List<RecipeRatingEntity>

    @Query("SELECT * FROM recipe_ratings WHERE memberId = :memberId AND recipeId = :recipeId")
    suspend fun find(memberId: String, recipeId: String): RecipeRatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: RecipeRatingEntity)
}
