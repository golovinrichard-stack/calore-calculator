package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** The shopping list (M5.7). */
@Dao
abstract class ShoppingDao {

    @Query("SELECT * FROM shopping_items WHERE ${SoftDelete.NOT_DELETED} ORDER BY createdAt")
    abstract fun observeItems(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE ${SoftDelete.NOT_DELETED}")
    abstract suspend fun items(): List<ShoppingItemEntity>

    /** Everything bought with a price, cleared from the list or not (M5.9). */
    @Query("SELECT * FROM shopping_items WHERE checkedAt IS NOT NULL AND priceHalers IS NOT NULL")
    abstract fun observeBought(): Flow<List<ShoppingItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Update
    abstract suspend fun update(item: ShoppingItemEntity)

    @Query("UPDATE shopping_items SET deletedAt = :now, updatedAt = :now WHERE manual = 0 AND checkedAt IS NULL AND deletedAt IS NULL")
    protected abstract suspend fun deleteGeneratedOpen(now: Long)

    @Query("UPDATE shopping_items SET deletedAt = :now, updatedAt = :now WHERE checkedAt IS NOT NULL AND deletedAt IS NULL")
    abstract suspend fun deleteTicked(now: Long)

    @Query("UPDATE shopping_items SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    abstract suspend fun delete(id: String, now: Long)

    /** Regenerating keeps manual items and ticks (A16 step 6). */
    @Transaction
    open suspend fun replaceGenerated(items: List<ShoppingItemEntity>, now: Long) {
        deleteGeneratedOpen(now)
        insertAll(items)
    }
}
