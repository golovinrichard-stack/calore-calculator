package com.calore.tracker.data.repository

import com.calore.tracker.data.db.WeightDao
import com.calore.tracker.data.db.WeightEntryEntity
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.data.db.toStored
import com.calore.tracker.domain.WeightEntry
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class WeightRepository(private val weightDao: WeightDao) {

    /** All entries, oldest first. */
    fun entries(): Flow<List<WeightEntry>> =
        weightDao.all().map { rows -> rows.mapNotNull { it.toDomain() } }

    suspend fun latest(): WeightEntry? = weightDao.latest()?.toDomain()

    /** Saves the weight for [date], replacing any earlier value for that day but keeping its ids. */
    suspend fun save(date: LocalDate, kg: Double) {
        val stored = date.toStored()
        val existing = weightDao.forDate(stored)
        weightDao.upsert(
            WeightEntryEntity(
                id = existing?.id ?: 0,
                date = stored,
                kg = kg,
                uuid = existing?.uuid ?: newId(),
            ),
        )
    }

    /** Puts back an entry removed by [delete], with the same ids. */
    suspend fun restore(entry: WeightEntry) {
        weightDao.upsert(entry.toEntity())
    }

    suspend fun delete(entry: WeightEntry) = weightDao.delete(entry.id)
}
