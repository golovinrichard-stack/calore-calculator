package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FoodDao {

    @Query("SELECT * FROM foods WHERE id = :id")
    abstract suspend fun getById(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    abstract fun observeById(id: Long): Flow<FoodEntity?>

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    abstract suspend fun getByBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM foods ORDER BY lastUsedAt DESC LIMIT :limit")
    abstract fun recent(limit: Int): Flow<List<FoodEntity>>

    /** [pattern] is a LIKE pattern whose literal %, _ and \ are escaped with a backslash. */
    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE :pattern ESCAPE '\' OR brand LIKE :pattern ESCAPE '\'
        ORDER BY lastUsedAt DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun search(pattern: String, limit: Int): List<FoodEntity>

    @Insert
    abstract suspend fun insert(food: FoodEntity): Long

    @Update
    abstract suspend fun update(food: FoodEntity)

    @Query("UPDATE foods SET lastUsedAt = :time WHERE id = :id")
    abstract suspend fun touch(id: Long, time: Long)

    @Query("DELETE FROM foods WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Inserts the food, or refreshes the stored copy when one with the same barcode exists. */
    @Transaction
    open suspend fun upsertByBarcode(food: FoodEntity): Long {
        val barcode = food.barcode ?: return insert(food)
        val existing = getByBarcode(barcode) ?: return insert(food)
        // Keep the stored row's identity; only its contents are refreshed.
        update(food.copy(id = existing.id, uuid = existing.uuid))
        return existing.id
    }
}
