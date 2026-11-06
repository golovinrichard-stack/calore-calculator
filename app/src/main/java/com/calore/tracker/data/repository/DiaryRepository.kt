package com.calore.tracker.data.repository

import com.calore.tracker.data.db.DiaryDao
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.data.db.toStored
import com.calore.tracker.domain.DiaryEntry
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.EntryType
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

class DiaryRepository(
    private val diaryDao: DiaryDao,
    private val clock: Clock,
) {

    fun entriesFor(date: LocalDate): Flow<List<DiaryEntry>> =
        diaryDao.forDate(date.toStored()).map { rows -> rows.mapNotNull { it.toDomain() } }

    /** Logs [grams] of [food], copying its nutrients so later edits to the food don't rewrite history. */
    suspend fun log(food: Food, grams: Double, meal: MealType, date: LocalDate): Long {
        val entry = DiaryEntry(
            id = 0,
            date = date,
            meal = meal,
            foodId = food.id,
            name = food.diaryName,
            grams = grams,
            nutrients = food.per100g.forGramsOfPer100g(grams),
            loggedAt = clock.millis(),
            uuid = newId(),
        )
        return diaryDao.insert(entry.toEntity())
    }

    /** Logs calories (and macros if known) without a food, e.g. a meal out (M2.5). */
    suspend fun quickAdd(name: String, nutrients: Nutrients, meal: MealType, date: LocalDate): Long {
        val entry = DiaryEntry(
            id = 0,
            date = date,
            meal = meal,
            foodId = null,
            name = name,
            grams = 0.0,
            nutrients = nutrients,
            loggedAt = clock.millis(),
            uuid = newId(),
            entryType = EntryType.QUICK_ADD,
        )
        return diaryDao.insert(entry.toEntity())
    }

    /** Edits a quick add: its name and values, never grams. */
    suspend fun updateQuickAdd(entry: DiaryEntry, name: String, nutrients: Nutrients) {
        if (entry.entryType != EntryType.QUICK_ADD) return
        diaryDao.update(entry.copy(name = name, nutrients = nutrients).toEntity())
    }

    /** Changes the portion size, scaling the stored nutrients in proportion. */
    suspend fun changeGrams(entry: DiaryEntry, newGrams: Double) {
        if (entry.grams <= 0.0 || newGrams <= 0.0) return
        val updated = entry.copy(
            grams = newGrams,
            nutrients = entry.nutrients.scaledBy(newGrams / entry.grams),
        )
        diaryDao.update(updated.toEntity())
    }

    suspend fun delete(entry: DiaryEntry) = diaryDao.delete(entry.id)

    /** Puts back an entry removed by [delete], keeping its id and position in the day. */
    suspend fun restore(entry: DiaryEntry) {
        diaryDao.insert(entry.toEntity())
    }
}
