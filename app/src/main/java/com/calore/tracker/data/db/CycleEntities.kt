package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A period (data-model M7). Private: never synced, never in research reports without its own
 * consent. Cycle rows are deleted for real, not soft-deleted.
 */
@Entity(tableName = "cycle_periods", indices = [Index(value = ["startDate"], unique = true)])
data class CyclePeriodEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDate: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** What was logged on one day (data-model M7). The note never leaves the phone. */
@Entity(tableName = "cycle_days", indices = [Index(value = ["date"], unique = true)])
data class CycleDayEntity(
    @PrimaryKey val id: String,
    val date: String,
    /** [com.calore.tracker.domain.cycle.Flow] name. */
    val flow: String?,
    /** [com.calore.tracker.domain.cycle.Symptom] names. */
    val symptoms: List<String>,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
abstract class CycleDao {
    @Query("SELECT * FROM cycle_periods ORDER BY startDate")
    abstract fun observePeriods(): Flow<List<CyclePeriodEntity>>

    @Query("SELECT * FROM cycle_periods ORDER BY startDate")
    abstract suspend fun periods(): List<CyclePeriodEntity>

    @Query("SELECT * FROM cycle_days ORDER BY date")
    abstract fun observeDays(): Flow<List<CycleDayEntity>>

    @Query("SELECT * FROM cycle_days WHERE date = :date")
    abstract suspend fun day(date: String): CycleDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPeriod(period: CyclePeriodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDay(day: CycleDayEntity)

    @Query("DELETE FROM cycle_periods WHERE id = :id")
    abstract suspend fun deletePeriod(id: String)

    @Query("DELETE FROM cycle_days WHERE date = :date")
    abstract suspend fun deleteDay(date: String)

    @Query("DELETE FROM cycle_periods")
    protected abstract suspend fun deleteAllPeriods()

    @Query("DELETE FROM cycle_days")
    protected abstract suspend fun deleteAllDays()

    /** M7.6 "Delete all cycle data". */
    @Transaction
    open suspend fun deleteAll() {
        deleteAllPeriods()
        deleteAllDays()
    }
}
