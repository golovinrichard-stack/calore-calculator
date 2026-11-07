package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A weekly research report (data-model M10): the JSON exactly as it is (or was) sent, so
 * "See what we send" shows what leaves the phone. Private, never synced.
 */
@Entity(tableName = "research_reports", indices = [Index(value = ["isoWeek"], unique = true)])
data class ResearchReportEntity(
    /** Also the `reportId` sent to the server. */
    @PrimaryKey val id: String,
    val isoWeek: String,
    /** [com.calore.tracker.domain.research.ResearchCategory] names. */
    val categories: List<String>,
    val payload: String,
    /** `PENDING`, `SENT` or `FAILED`. */
    val status: String,
    val attempts: Int,
    val createdAt: Long,
    val sentAt: Long?,
)

@Dao
interface ResearchDao {
    @Query("SELECT * FROM research_reports ORDER BY isoWeek DESC")
    fun observeReports(): Flow<List<ResearchReportEntity>>

    @Query("SELECT * FROM research_reports WHERE isoWeek = :isoWeek")
    suspend fun reportFor(isoWeek: String): ResearchReportEntity?

    @Query("SELECT * FROM research_reports WHERE status = 'PENDING' ORDER BY isoWeek")
    suspend fun pending(): List<ResearchReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ResearchReportEntity)

    /** Stop sharing (M10.5): reports not sent yet are deleted. */
    @Query("DELETE FROM research_reports WHERE status != 'SENT'")
    suspend fun deleteUnsent()

    /** After deleting the data on the server, the copies on the phone go too. */
    @Query("DELETE FROM research_reports")
    suspend fun deleteAll()
}
