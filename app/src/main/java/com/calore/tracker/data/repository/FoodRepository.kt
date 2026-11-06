package com.calore.tracker.data.repository

import com.calore.tracker.data.db.FoodDao
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.data.remote.OpenFoodFactsParser
import com.calore.tracker.data.remote.RemoteFood
import com.calore.tracker.data.remote.RemoteResult
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

class FoodRepository(
    private val foodDao: FoodDao,
    private val client: OpenFoodFactsClient,
    private val clock: Clock,
    /** The app's country (M0.6), so online search can prefer products sold there (M2.3). */
    private val country: suspend () -> String? = { null },
) {

    fun recentFoods(): Flow<List<Food>> =
        foodDao.recent(RECENT_LIMIT).map { foods -> foods.map { it.toDomain() } }

    suspend fun searchLocal(query: String): List<Food> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return foodDao.search(likePattern(trimmed), LOCAL_SEARCH_LIMIT).map { it.toDomain() }
    }

    suspend fun searchOnline(query: String): RemoteResult<List<RemoteFood>> =
        client.search(query.trim(), country = country())

    suspend fun getFood(id: Long): Food? = foodDao.getById(id)?.toDomain()

    /** Emits the food and every later change to it; null once it is deleted or if it never existed. */
    fun observeFood(id: Long): Flow<Food?> = foodDao.observeById(id).map { it?.toDomain() }

    /** Stores a picked Open Food Facts product so it can be logged and found offline later. */
    suspend fun saveRemote(remote: RemoteFood): Long {
        val food = Food(
            id = 0,
            name = remote.name,
            brand = remote.brand,
            barcode = remote.barcode,
            per100g = remote.per100g,
            servingGrams = remote.servingGrams,
            source = FoodSource.OPEN_FOOD_FACTS,
            uuid = newId(),
            section = remote.section,
            sourceRef = "off:${remote.barcode}",
            allergens = remote.allergens,
            defaultPackGrams = remote.packGrams,
        )
        val now = clock.millis()
        return foodDao.upsertByBarcode(food.toEntity(lastUsedAt = now, updatedAt = now))
    }

    /**
     * Finds a scanned product (M2.4): on the phone first, so foods used before work offline,
     * then on Open Food Facts, where a found product is saved for next time.
     */
    suspend fun findByBarcode(barcode: String): BarcodeResult {
        foodDao.getByBarcode(barcode)?.let { return BarcodeResult.Found(it.id) }
        return when (val result = client.product(barcode)) {
            is RemoteResult.Success -> when (val lookup = result.value) {
                is OpenFoodFactsParser.ProductLookup.Found -> BarcodeResult.Found(saveRemote(lookup.food))
                else -> BarcodeResult.NotFound
            }
            RemoteResult.NetworkError -> BarcodeResult.Offline
            RemoteResult.BadResponse -> BarcodeResult.Failed
        }
    }

    /** Creates a custom food, or updates it when [existingId] is given. Returns its id. */
    suspend fun saveCustom(
        existingId: Long?,
        name: String,
        brand: String?,
        per100g: Nutrients,
        servingGrams: Double?,
        barcode: String? = null,
    ): Long {
        val existing = existingId?.let { foodDao.getById(it) }
        val base = existing?.toDomain() ?: Food(
            id = 0,
            name = name,
            brand = brand,
            barcode = barcode,
            per100g = per100g,
            servingGrams = servingGrams,
            source = FoodSource.CUSTOM,
            uuid = newId(),
            sourceRef = SOURCE_USER,
        )
        // Keep everything the form doesn't show (section, allergens …) when editing.
        val food = base.copy(name = name, brand = brand, per100g = per100g, servingGrams = servingGrams)
        val now = clock.millis()
        val entity = food.toEntity(lastUsedAt = now, updatedAt = now)
        return if (existing == null) {
            foodDao.insert(entity)
        } else {
            foodDao.update(entity)
            existing.id
        }
    }

    suspend fun markUsed(id: Long) = foodDao.touch(id, clock.millis())

    /** Removes the food from the list. Diary entries keep their own copy and are unaffected. */
    suspend fun deleteFood(id: Long) = foodDao.delete(id)

    private companion object {
        const val SOURCE_USER = "user"
        const val RECENT_LIMIT = 30
        const val LOCAL_SEARCH_LIMIT = 50

        fun likePattern(query: String): String {
            val escaped = query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }
    }
}

/** What a barcode scan found. */
sealed interface BarcodeResult {
    data class Found(val foodId: Long) : BarcodeResult

    /** Not on the phone and not on Open Food Facts: the user can add it. */
    data object NotFound : BarcodeResult

    /** Not on the phone, and Open Food Facts couldn't be reached. */
    data object Offline : BarcodeResult

    data object Failed : BarcodeResult
}
