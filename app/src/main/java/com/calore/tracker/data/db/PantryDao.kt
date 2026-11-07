package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** The pantry (M5.5). */
@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items WHERE state = 'AVAILABLE' AND ${SoftDelete.NOT_DELETED} ORDER BY bestBeforeDate IS NULL, bestBeforeDate, name")
    fun observeAvailable(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE state = 'AVAILABLE' AND foodUuid = :foodUuid AND ${SoftDelete.NOT_DELETED}")
    suspend fun availableFor(foodUuid: String): List<PantryItemEntity>

    @Query("SELECT * FROM pantry_items WHERE state = 'AVAILABLE' AND ${SoftDelete.NOT_DELETED}")
    suspend fun available(): List<PantryItemEntity>

    @Query("SELECT * FROM pantry_items WHERE state = 'WASTED' AND ${SoftDelete.NOT_DELETED}")
    fun observeWasted(): Flow<List<PantryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PantryItemEntity)

    @Update
    suspend fun update(item: PantryItemEntity)
}
