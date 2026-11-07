package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Body measurements and weekly check-ins. Both are private and never sync. */
@Dao
interface BodyDao {

    @Query("SELECT * FROM body_measurements WHERE memberId = :memberId AND ${SoftDelete.NOT_DELETED} ORDER BY date, createdAt")
    fun observeMeasurements(memberId: String): Flow<List<BodyMeasurementEntity>>

    @Query(
        """
        SELECT * FROM body_measurements
        WHERE memberId = :memberId AND bodyFatPct IS NOT NULL AND ${SoftDelete.NOT_DELETED}
        ORDER BY date DESC, createdAt DESC LIMIT 1
        """,
    )
    fun observeLatestBodyFat(memberId: String): Flow<BodyMeasurementEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurement(measurement: BodyMeasurementEntity)

    @Query("SELECT * FROM checkins WHERE memberId = :memberId ORDER BY weekStart DESC")
    fun observeCheckins(memberId: String): Flow<List<CheckinEntity>>

    @Query("SELECT * FROM checkins WHERE memberId = :memberId AND weekStart = :weekStart")
    suspend fun checkinFor(memberId: String, weekStart: String): CheckinEntity?

    /** One check-in per member and week: a new one for the same week replaces the old. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckin(checkin: CheckinEntity)
}
