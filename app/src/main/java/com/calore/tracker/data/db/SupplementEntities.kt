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

/** A supplement (data-model M8). Private, never synced; deleted for real with its logs. */
@Entity(tableName = "supplements")
data class SupplementEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** [com.calore.tracker.domain.SupplementKind] name. */
    val kind: String,
    val doseAmount: Double,
    /** [com.calore.tracker.domain.DoseUnit] name. */
    val doseUnit: String,
    /** `HH:mm`. */
    val times: List<String>,
    /** [java.time.DayOfWeek] names; empty = every day. */
    val daysOfWeek: List<String>,
    val startDate: String,
    val endDate: String?,
    val remindersOn: Boolean,
    /** Nutrients per dose, all optional; with calories, taking it logs a diary entry. */
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Taken or skipped, per scheduled dose (data-model M8). */
@Entity(
    tableName = "supplement_logs",
    indices = [Index(value = ["supplementId", "scheduledAt"], unique = true)],
)
data class SupplementLogEntity(
    @PrimaryKey val id: String,
    val supplementId: String,
    val scheduledAt: Long?,
    val takenAt: Long?,
    /** `TAKEN` or `SKIPPED`. */
    val status: String,
    val diaryEntryUuid: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
abstract class SupplementDao {
    @Query("SELECT * FROM supplements ORDER BY name")
    abstract fun observeSupplements(): Flow<List<SupplementEntity>>

    @Query("SELECT * FROM supplements ORDER BY name")
    abstract suspend fun supplements(): List<SupplementEntity>

    @Query("SELECT * FROM supplements WHERE id = :id")
    abstract suspend fun supplement(id: String): SupplementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSupplement(row: SupplementEntity)

    @Query("SELECT * FROM supplement_logs WHERE scheduledAt >= :from ORDER BY scheduledAt")
    abstract fun observeLogsSince(from: Long): Flow<List<SupplementLogEntity>>

    @Query("SELECT * FROM supplement_logs WHERE supplementId = :supplementId AND scheduledAt = :scheduledAt")
    abstract suspend fun log(supplementId: String, scheduledAt: Long): SupplementLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertLog(row: SupplementLogEntity)

    @Query("DELETE FROM supplement_logs WHERE id = :id")
    abstract suspend fun deleteLog(id: String)

    @Query("DELETE FROM supplements WHERE id = :id")
    protected abstract suspend fun deleteSupplementRow(id: String)

    @Query("DELETE FROM supplement_logs WHERE supplementId = :id")
    protected abstract suspend fun deleteLogsOf(id: String)

    /** A supplement and its logs; diary entries it made stay (they are what was eaten). */
    @Transaction
    open suspend fun deleteSupplement(id: String) {
        deleteLogsOf(id)
        deleteSupplementRow(id)
    }
}
