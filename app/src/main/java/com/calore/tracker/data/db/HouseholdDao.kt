package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The household (M11): this phone's household, its phones, requests and the activity feed. */
@Dao
interface HouseholdDao {

    @Query("SELECT * FROM household LIMIT 1")
    fun observeHousehold(): Flow<HouseholdEntity?>

    @Query("SELECT * FROM household LIMIT 1")
    suspend fun household(): HouseholdEntity?

    @Upsert
    suspend fun upsertHousehold(household: HouseholdEntity)

    @Query("DELETE FROM household")
    suspend fun deleteHousehold()

    @Query("SELECT * FROM household_devices WHERE deletedAt IS NULL ORDER BY isHub DESC, memberName")
    fun observeDevices(): Flow<List<HouseholdDeviceEntity>>

    @Query("SELECT * FROM household_devices")
    suspend fun allDevices(): List<HouseholdDeviceEntity>

    @Query("SELECT * FROM household_devices WHERE id = :id")
    suspend fun device(id: String): HouseholdDeviceEntity?

    @Upsert
    suspend fun upsertDevice(device: HouseholdDeviceEntity)

    /** Phone-only columns: when a phone last synced, and what it had seen (for compaction). */
    @Query("UPDATE household_devices SET lastSeenAt = :at, seenVector = :vector WHERE id = :id")
    suspend fun markSeen(id: String, at: Long, vector: String?)

    @Query("DELETE FROM household_devices")
    suspend fun deleteDevices()

    /** Local changes not yet turned into ops (M11.10). */
    @Query("SELECT COUNT(*) FROM sync_changes")
    fun observeRecordedChanges(): Flow<Int>

    /** Ops this phone made that the hub hasn't seen yet (M11.10). */
    @Query("SELECT COUNT(*) FROM sync_ops WHERE deviceId = :deviceId AND seq > :hubSeen")
    fun observeUnsent(deviceId: String, hubSeen: Long): Flow<Int>

    @Query("SELECT * FROM requests WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeRequests(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE id = :id")
    suspend fun request(id: String): RequestEntity?

    @Upsert
    suspend fun upsertRequest(request: RequestEntity)

    @Query("SELECT * FROM activity WHERE at >= :since ORDER BY at DESC")
    fun observeActivity(since: Long): Flow<List<ActivityEntity>>

    @Upsert
    suspend fun upsertActivity(activity: ActivityEntity)
}
