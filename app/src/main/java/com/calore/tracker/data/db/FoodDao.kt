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

    /** Foods the user has used, newest first. Basic foods appear only once they were used. */
    @Query("SELECT * FROM foods WHERE lastUsedAt > 0 ORDER BY lastUsedAt DESC LIMIT :limit")
    abstract fun recent(limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE `key` = :key")
    abstract suspend fun getByKey(key: String): FoodEntity?

    /** [pattern] is a LIKE pattern whose literal %, _ and \ are escaped with a backslash. */
    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE :pattern ESCAPE '\' OR brand LIKE :pattern ESCAPE '\'
            OR nameCs LIKE :pattern ESCAPE '\' OR nameEn LIKE :pattern ESCAPE '\'
        ORDER BY isGeneric DESC, lastUsedAt DESC, name
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

    /**
     * Stores the bundled basic foods (M2.2): updates rows with the same key, keeping their id,
     * uuid and last use, and inserts the new ones. Foods without a key are not touched.
     */
    @Transaction
    open suspend fun upsertGeneric(foods: List<FoodEntity>) {
        for (food in foods) {
            val key = food.key ?: continue
            val existing = getByKey(key)
            if (existing == null) {
                insert(food)
            } else {
                update(food.copy(id = existing.id, uuid = existing.uuid, lastUsedAt = existing.lastUsedAt))
            }
        }
    }

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
