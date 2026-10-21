package com.calore.tracker.data.repository

import com.calore.tracker.data.db.WeightDao
import com.calore.tracker.data.db.WeightEntryEntity
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toStored
import com.calore.tracker.domain.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class WeightRepository(private val weightDao: WeightDao) {

    /** All entries, oldest first. */
    fun entries(): Flow<List<WeightEntry>> =
        weightDao.all().map { rows -> rows.mapNotNull { it.toDomain() } }

    suspend fun latest(): WeightEntry? = weightDao.latest()?.toDomain()

    /** Saves the weight for [date], replacing any earlier value for that day. */
    suspend fun save(date: LocalDate, kg: Double) {
        weightDao.upsert(WeightEntryEntity(date = date.toStored(), kg = kg))
    }

    suspend fun delete(entry: WeightEntry) = weightDao.delete(entry.id)
}
