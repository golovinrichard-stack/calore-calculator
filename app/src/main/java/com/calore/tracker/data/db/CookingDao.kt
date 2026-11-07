package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A batch with the ingredients that went in. */
data class StoredBatch(val batch: BatchEntity, val ingredients: List<BatchIngredientEntity>)

/** A box with its parts. */
data class StoredBox(val box: BoxEntity, val parts: List<BoxPartEntity>)

/** Pots, batches and boxes (M4). */
@Dao
abstract class CookingDao {

    // Pots

    @Query("SELECT * FROM pots WHERE ${SoftDelete.NOT_DELETED} ORDER BY name")
    abstract fun observePots(): Flow<List<PotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPot(pot: PotEntity)

    @Query("UPDATE pots SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    abstract suspend fun deletePot(id: String, now: Long)

    // Batches

    @Query("SELECT * FROM batches WHERE id = :id")
    abstract suspend fun batch(id: String): BatchEntity?

    @Query("SELECT * FROM batches WHERE id IN (:ids)")
    abstract fun observeBatches(ids: List<String>): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE ${SoftDelete.NOT_DELETED}")
    abstract fun observeAllBatches(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batch_ingredients WHERE batchId = :batchId")
    abstract suspend fun batchIngredients(batchId: String): List<BatchIngredientEntity>

    @Insert
    protected abstract suspend fun insertBatch(batch: BatchEntity)

    @Insert
    protected abstract suspend fun insertBatchIngredients(rows: List<BatchIngredientEntity>)

    @Transaction
    open suspend fun insertBatch(batch: StoredBatch) {
        insertBatch(batch.batch)
        insertBatchIngredients(batch.ingredients)
    }

    // Boxes

    @Query("SELECT * FROM boxes WHERE ${SoftDelete.NOT_DELETED} AND state IN ('READY', 'PART_EATEN') ORDER BY eatByDate, code")
    abstract fun observeActiveBoxes(): Flow<List<BoxEntity>>

    @Query("SELECT * FROM boxes WHERE ${SoftDelete.NOT_DELETED} AND state = 'WASTED' ORDER BY updatedAt DESC")
    abstract fun observeWastedBoxes(): Flow<List<BoxEntity>>

    @Query("SELECT * FROM boxes WHERE id = :id")
    abstract suspend fun box(id: String): BoxEntity?

    @Query("SELECT * FROM boxes WHERE id = :id")
    abstract fun observeBox(id: String): Flow<BoxEntity?>

    @Query("SELECT code FROM boxes WHERE ${SoftDelete.NOT_DELETED} AND state IN ('READY', 'PART_EATEN')")
    abstract suspend fun activeCodes(): List<String>

    @Query("SELECT * FROM box_parts")
    abstract fun observeAllParts(): Flow<List<BoxPartEntity>>

    @Query("SELECT * FROM box_parts WHERE boxId = :boxId")
    abstract suspend fun parts(boxId: String): List<BoxPartEntity>

    @Insert
    protected abstract suspend fun insertBoxRows(rows: List<BoxEntity>)

    @Insert
    protected abstract suspend fun insertParts(rows: List<BoxPartEntity>)

    @Transaction
    open suspend fun insertBoxes(boxes: List<StoredBox>) {
        insertBoxRows(boxes.map { it.box })
        insertParts(boxes.flatMap { it.parts })
    }

    @Update
    abstract suspend fun updateBox(box: BoxEntity)

    @Insert
    protected abstract suspend fun insertDiaryEntry(entry: DiaryEntryEntity): Long

    @Query("DELETE FROM diary_entries WHERE id = :id")
    protected abstract suspend fun deleteDiaryEntry(id: Long)

    /** The plan slot a box is for (M5.4) is done once the box is eaten (M4.7). */
    @Query("UPDATE plan_slots SET done = :done, updatedAt = :now WHERE boxId = :boxId")
    protected abstract suspend fun setSlotDone(boxId: String, done: Boolean, now: Long)

    /** "Ate it" (M4.7): the diary entry, the box and its plan slot change together, or not at all. */
    @Transaction
    open suspend fun eatBox(updated: BoxEntity, entry: DiaryEntryEntity, now: Long): Long {
        updateBox(updated)
        setSlotDone(updated.id, true, now)
        return insertDiaryEntry(entry)
    }

    /** Undo of [eatBox]: the box as it was, its slot not done, and no diary entry. */
    @Transaction
    open suspend fun undoEat(previous: BoxEntity, entryId: Long, now: Long) {
        updateBox(previous)
        setSlotDone(previous.id, false, now)
        deleteDiaryEntry(entryId)
    }

    /** A9: the recipe learns how much it weighs cooked. */
    @Query(
        "UPDATE recipes SET learnedYield = :learnedYield, learnedYieldCount = learnedYieldCount + 1, updatedAt = :now " +
            "WHERE id = :recipeId",
    )
    abstract suspend fun setLearnedYield(recipeId: String, learnedYield: Double, now: Long)
}
