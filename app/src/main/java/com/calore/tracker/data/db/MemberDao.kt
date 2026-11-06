package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Query("SELECT * FROM members WHERE kind = 'ME' AND ${SoftDelete.NOT_DELETED} ORDER BY createdAt LIMIT 1")
    fun observeMe(): Flow<MemberEntity?>

    @Query("SELECT * FROM members WHERE kind = 'ME' AND ${SoftDelete.NOT_DELETED} ORDER BY createdAt LIMIT 1")
    suspend fun me(): MemberEntity?

    @Query("SELECT * FROM members WHERE kind = 'MANAGED' AND ${SoftDelete.NOT_DELETED} ORDER BY name")
    fun observeManaged(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun byId(id: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: MemberEntity)

    /** True when the app already holds anything the user logged, i.e. this is not a fresh install. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM diary_entries) OR EXISTS(SELECT 1 FROM foods) OR EXISTS(SELECT 1 FROM weight_entries)",
    )
    suspend fun hasLoggedData(): Boolean
}
