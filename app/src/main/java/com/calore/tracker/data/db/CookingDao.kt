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
}
