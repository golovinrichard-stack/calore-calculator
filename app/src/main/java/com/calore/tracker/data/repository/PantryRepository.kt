package com.calore.tracker.data.repository

import com.calore.tracker.data.db.FoodDao
import com.calore.tracker.data.db.PantryDao
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.domain.AmountUnit
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.Pantry
import com.calore.tracker.domain.PantryItem
import com.calore.tracker.domain.PantryLot
import com.calore.tracker.domain.PantryState
import com.calore.tracker.domain.Storage
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** A pantry item with its food, when it has one. */
data class PantryEntry(val item: PantryItem, val food: Food?)

/** The pantry (M5.5): what is at home, until it is used up or thrown away. */
class PantryRepository(private val pantryDao: PantryDao, private val foodDao: FoodDao, private val clock: Clock) {

    private fun today(): LocalDate = LocalDate.now(clock)

    fun available(): Flow<List<PantryEntry>> = pantryDao.observeAvailable().map { rows ->
        rows.mapNotNull { it.toDomain() }.map { PantryEntry(it, it.foodUuid?.let { uuid -> foodDao.getByUuid(uuid)?.toDomain() }) }
    }

    /** The day an item was changed last, which for a wasted item is when it was thrown away. */
    fun dayOf(item: PantryItem): LocalDate = java.time.Instant.ofEpochMilli(item.updatedAt).atZone(clock.zone).toLocalDate()

    fun wasted(): Flow<List<PantryItem>> = pantryDao.observeWasted().map { rows -> rows.mapNotNull { it.toDomain() } }

    /**
     * Adds food bought today. Without a date or storage, the defaults for the food's shop
     * section apply (A17); the date on the pack always wins.
     */
    suspend fun add(
        food: Food?,
        name: String,
        amount: Double,
        unit: AmountUnit = AmountUnit.G,
        storage: Storage? = null,
        bestBefore: LocalDate? = null,
        costHalers: Long? = null,
        storeProductId: String? = null,
    ): PantryItem {
        val now = clock.millis()
        val bought = today()
        val section = food?.section ?: com.calore.tracker.domain.ShopSection.OTHER
        val (_, defaultStorage) = Pantry.defaults(section)
        val item = PantryItem(
            id = newId(),
            foodUuid = food?.uuid,
            storeProductId = storeProductId,
            name = name.ifBlank { food?.displayName().orEmpty() },
            amount = amount,
            unit = unit,
            storage = storage ?: food?.storage ?: defaultStorage,
            boughtDate = bought,
            bestBeforeDate = bestBefore ?: Pantry.defaultBestBefore(section, bought),
            costHalers = costHalers,
            createdAt = now,
            updatedAt = now,
        )
        pantryDao.insert(item.toEntity())
        return item
    }

    suspend fun update(item: PantryItem) = pantryDao.update(item.copy(updatedAt = clock.millis()).toEntity())

    suspend fun usedUp(item: PantryItem) = update(item.copy(state = PantryState.USED_UP))

    suspend fun throwAway(item: PantryItem) = update(item.copy(state = PantryState.WASTED))

    /** Usable lots for the shopping list (A16), in grams. */
    suspend fun lots(): List<PantryLot> = pantryDao.available().mapNotNull { it.toDomain() }.mapNotNull { item ->
        val uuid = item.foodUuid ?: return@mapNotNull null
        val grams = item.grams(foodDao.getByUuid(uuid)?.gramsPerPiece) ?: return@mapNotNull null
        PantryLot(item.id, uuid, grams, item.effectiveBestBefore, item.boughtDate, item.reservedForCookId != null)
    }

    /**
     * Cooking takes the ingredients from the pantry, first-in-first-out (A17). Returns the foods
     * the pantry had some of, but less than the recipe needed.
     */
    suspend fun takeForCooking(ingredients: List<Pair<Food, Double>>): List<Food> {
        val short = mutableListOf<Food>()
        for ((food, grams) in ingredients.groupBy { it.first.uuid }.map { (_, list) -> list.first().first to list.sumOf { it.second } }) {
            val items = pantryDao.availableFor(food.uuid).mapNotNull { it.toDomain() }
            if (items.isEmpty()) continue
            val lots = items.mapNotNull { item -> item.grams(food.gramsPerPiece)?.let { PantryLot(item.id, food.uuid, it, item.effectiveBestBefore, item.boughtDate) } }
            val (taken, missing) = Pantry.takeFifo(lots, grams)
            for ((id, takenGrams) in taken) {
                val item = items.first { it.id == id }
                val perUnit = if (item.unit == AmountUnit.PIECE) food.gramsPerPiece ?: continue else 1.0
                val left = item.amount - takenGrams / perUnit
                // The cost follows the amount, so throwing the rest away later counts only its share (A18).
                val cost = item.costHalers?.let { if (item.amount > 0) Math.round(it * left.coerceAtLeast(0.0) / item.amount) else 0L }
                update(if (left <= 0.0001) item.copy(amount = 0.0, state = PantryState.USED_UP, costHalers = 0) else item.copy(amount = left, costHalers = cost))
            }
            if (missing > 0) short += food
        }
        return short
    }
}
