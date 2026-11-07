package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Week plans, their slots, and templates (M5.3). */
@Dao
abstract class PlanDao {

    @Query("SELECT * FROM plans WHERE weekStart = :weekStart AND ${SoftDelete.NOT_DELETED} LIMIT 1")
    abstract suspend fun planForWeek(weekStart: String): PlanEntity?

    @Query("SELECT * FROM plans WHERE templateName IS NOT NULL AND ${SoftDelete.NOT_DELETED} ORDER BY templateName")
    abstract fun observeTemplates(): Flow<List<PlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPlan(plan: PlanEntity)

    @Query("SELECT * FROM plan_slots WHERE planId = :planId AND ${SoftDelete.NOT_DELETED}")
    abstract fun observeSlots(planId: String): Flow<List<PlanSlotEntity>>

    @Query("SELECT * FROM plan_slots WHERE planId = :planId AND ${SoftDelete.NOT_DELETED}")
    abstract suspend fun slots(planId: String): List<PlanSlotEntity>

    /** Slots of every week plan between two ISO dates, for Today (M5.11). */
    @Query(
        "SELECT s.* FROM plan_slots s JOIN plans p ON p.id = s.planId " +
            "WHERE p.weekStart IS NOT NULL AND p.deletedAt IS NULL AND s.deletedAt IS NULL AND s.date BETWEEN :from AND :to",
    )
    abstract fun observeSlotsBetween(from: String, to: String): Flow<List<PlanSlotEntity>>

    @Query("SELECT * FROM plan_slots WHERE planId = :planId AND date = :date AND meal = :meal AND memberId = :memberId AND ${SoftDelete.NOT_DELETED} LIMIT 1")
    abstract suspend fun slot(planId: String, date: String, meal: String, memberId: String): PlanSlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSlot(slot: PlanSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertSlots(slots: List<PlanSlotEntity>)

    @Query("UPDATE plan_slots SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    abstract suspend fun deleteSlot(id: String, now: Long)

    @Query("UPDATE plan_slots SET boxId = :boxId, updatedAt = :now WHERE id = :slotId")
    abstract suspend fun linkBox(slotId: String, boxId: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCook(cook: PlanCookEntity)

    @Query("SELECT * FROM plan_cooks WHERE planId = :planId AND ${SoftDelete.NOT_DELETED}")
    abstract fun observeCooks(planId: String): Flow<List<PlanCookEntity>>

    @Query("UPDATE plan_slots SET deletedAt = :now, updatedAt = :now WHERE planId = :planId AND deletedAt IS NULL")
    protected abstract suspend fun deleteSlotsOf(planId: String, now: Long)

    /** Replaces every slot of [planId] with [slots], in one transaction. */
    @Transaction
    open suspend fun replaceSlots(planId: String, slots: List<PlanSlotEntity>, now: Long) {
        deleteSlotsOf(planId, now)
        upsertSlots(slots)
    }
}
