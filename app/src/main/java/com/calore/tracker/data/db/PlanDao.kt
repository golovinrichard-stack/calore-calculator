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

    @Query("UPDATE plan_slots SET kind = 'FREE', recipeId = NULL, boxId = NULL, updatedAt = :now WHERE id = :slotId")
    abstract suspend fun freeSlot(slotId: String, now: Long)

    /** M6.5: "I'm eating out": the meal is eaten out; its recipe and box are no longer planned for it. */
    @Query("UPDATE plan_slots SET kind = 'EAT_OUT', recipeId = NULL, boxId = NULL, locked = 0, updatedAt = :now WHERE id = :slotId")
    abstract suspend fun eatOut(slotId: String, now: Long)

    @Query("UPDATE plan_slots SET boxId = :boxId, updatedAt = :now WHERE id = :slotId")
    abstract suspend fun linkBox(slotId: String, boxId: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCook(cook: PlanCookEntity)

    @Query("SELECT * FROM plan_cooks WHERE planId = :planId AND ${SoftDelete.NOT_DELETED}")
    abstract fun observeCooks(planId: String): Flow<List<PlanCookEntity>>

    @Query("SELECT * FROM plan_cooks WHERE planId = :planId AND ${SoftDelete.NOT_DELETED}")
    abstract suspend fun cooks(planId: String): List<PlanCookEntity>

    /** M11.6: cooks not cooked yet from [from] on, for reserving pantry food. */
    @Query("SELECT * FROM plan_cooks WHERE date >= :from AND batchId IS NULL AND ${SoftDelete.NOT_DELETED} ORDER BY date")
    abstract fun observeCooksFrom(from: String): Flow<List<PlanCookEntity>>

    /** M6.3: planned cooks not cooked yet; an accepted plan replaces them. */
    @Query("UPDATE plan_cooks SET deletedAt = :now, updatedAt = :now WHERE planId = :planId AND batchId IS NULL AND deletedAt IS NULL")
    protected abstract suspend fun deletePlannedCooks(planId: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCooks(cooks: List<PlanCookEntity>)

    @Query("UPDATE plans SET generatorSeed = :seed, updatedAt = :now WHERE id = :planId")
    protected abstract suspend fun setSeed(planId: String, seed: Long, now: Long)

    @Query("UPDATE plan_slots SET locked = :locked, updatedAt = :now WHERE id = :slotId")
    abstract suspend fun setLocked(slotId: String, locked: Boolean, now: Long)

    /** M6.3 **Accept**: the changed slots, the planned cooks and the planner's seed, in one transaction. */
    @Transaction
    open suspend fun accept(planId: String, slots: List<PlanSlotEntity>, cooks: List<PlanCookEntity>, seed: Long, now: Long) {
        upsertSlots(slots)
        deletePlannedCooks(planId, now)
        insertCooks(cooks)
        setSeed(planId, seed, now)
    }

    @Query("UPDATE plan_slots SET deletedAt = :now, updatedAt = :now WHERE planId = :planId AND deletedAt IS NULL")
    protected abstract suspend fun deleteSlotsOf(planId: String, now: Long)

    /** Replaces every slot of [planId] with [slots], in one transaction. */
    @Transaction
    open suspend fun replaceSlots(planId: String, slots: List<PlanSlotEntity>, now: Long) {
        deleteSlotsOf(planId, now)
        upsertSlots(slots)
    }
}
