package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One day's calorie total. */
data class DayKcal(val date: String, val kcal: Double)

@Dao
interface DiaryDao {

    /** The phone's user's entries (memberId NULL) for one day. */
    @Query("SELECT * FROM diary_entries WHERE date = :date AND memberId IS NULL ORDER BY createdAt, id")
    fun forDate(date: String): Flow<List<DiaryEntryEntity>>

    /** Calories per day for the phone's user, for the weekly check-in (A7). */
    @Query(
        """
        SELECT date, SUM(kcal) AS kcal FROM diary_entries
        WHERE memberId IS NULL AND date BETWEEN :from AND :to
        GROUP BY date ORDER BY date
        """,
    )
    suspend fun dailyKcal(from: String, to: String): List<DayKcal>

    /** Replaces on id conflict so an undone delete can restore the exact same row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Update
    suspend fun update(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
