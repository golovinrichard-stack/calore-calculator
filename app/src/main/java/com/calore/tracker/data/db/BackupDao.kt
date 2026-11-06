package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Every row of every table, read in one transaction so the copy is consistent. */
data class DatabaseSnapshot(
    val foods: List<FoodEntity>,
    val diaryEntries: List<DiaryEntryEntity>,
    val weightEntries: List<WeightEntryEntity>,
)

/** Whole-database reads and writes for backup export and import (M0.5). */
@Dao
abstract class BackupDao {

    @Query("SELECT * FROM foods ORDER BY id")
    protected abstract suspend fun allFoods(): List<FoodEntity>

    @Query("SELECT * FROM diary_entries ORDER BY id")
    protected abstract suspend fun allDiaryEntries(): List<DiaryEntryEntity>

    @Query("SELECT * FROM weight_entries ORDER BY id")
    protected abstract suspend fun allWeightEntries(): List<WeightEntryEntity>

    @Query("DELETE FROM foods")
    protected abstract suspend fun deleteAllFoods()

    @Query("DELETE FROM diary_entries")
    protected abstract suspend fun deleteAllDiaryEntries()

    @Query("DELETE FROM weight_entries")
    protected abstract suspend fun deleteAllWeightEntries()

    @Insert
    protected abstract suspend fun insertFoods(rows: List<FoodEntity>)

    @Insert
    protected abstract suspend fun insertDiaryEntries(rows: List<DiaryEntryEntity>)

    @Insert
    protected abstract suspend fun insertWeightEntries(rows: List<WeightEntryEntity>)

    @Transaction
    open suspend fun snapshot(): DatabaseSnapshot =
        DatabaseSnapshot(allFoods(), allDiaryEntries(), allWeightEntries())

    /**
     * Deletes every row and inserts [snapshot] instead, keeping ids and uuids. All in one
     * transaction: if any insert fails (for example a duplicate id), nothing changes.
     */
    @Transaction
    open suspend fun replaceAll(snapshot: DatabaseSnapshot) {
        deleteAllDiaryEntries()
        deleteAllFoods()
        deleteAllWeightEntries()
        insertFoods(snapshot.foods)
        insertDiaryEntries(snapshot.diaryEntries)
        insertWeightEntries(snapshot.weightEntries)
    }
}
