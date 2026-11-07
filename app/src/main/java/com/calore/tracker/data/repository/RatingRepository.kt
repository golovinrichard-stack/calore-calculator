package com.calore.tracker.data.repository

import com.calore.tracker.data.db.RatingDao
import com.calore.tracker.data.db.RecipeRatingEntity
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/** 👍 / 👎 for recipes, per person (M6.1). */
class RatingRepository(private val ratingDao: RatingDao, private val clock: Clock) {

    /** Every live rating: person → recipe → 1 or −1. */
    fun observeAll(): Flow<Map<String, Map<String, Int>>> = ratingDao.observeAll().map { rows -> rows.toMap() }

    suspend fun all(): Map<String, Map<String, Int>> = ratingDao.all().toMap()

    /** One person's rating of a recipe: 1, −1 or null. */
    fun observe(memberId: String, recipeId: String): Flow<Int?> = observeAll().map { it[memberId]?.get(recipeId) }

    /** Saves [rating] (1 or −1); null takes the rating back. */
    suspend fun rate(memberId: String, recipeId: String, rating: Int?) {
        require(rating == null || rating == 1 || rating == -1) { "A rating is 1 or -1" }
        val now = clock.millis()
        val existing = ratingDao.find(memberId, recipeId)
        val row = when {
            rating == null -> existing?.copy(updatedAt = now, deletedAt = now) ?: return
            existing != null -> existing.copy(rating = rating, updatedAt = now, deletedAt = null)
            else -> RecipeRatingEntity(newId(), memberId, recipeId, rating, createdAt = now, updatedAt = now)
        }
        ratingDao.upsert(row)
    }

    private fun List<RecipeRatingEntity>.toMap(): Map<String, Map<String, Int>> =
        groupBy { it.memberId }.mapValues { (_, list) -> list.associate { it.recipeId to it.rating } }
}
