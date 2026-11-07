package com.calore.tracker.data.repository

import com.calore.tracker.data.db.FoodDao
import com.calore.tracker.data.db.ShoppingDao
import com.calore.tracker.data.db.ShoppingItemEntity
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.domain.AmountUnit
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.Need
import com.calore.tracker.domain.PlanCooks
import com.calore.tracker.domain.PlannedCook
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.Shopping
import com.calore.tracker.domain.newId
import com.calore.tracker.data.db.JsonColumns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** A shopping line as shown (M5.7). Generated lines have a food; manual ones only a name. */
data class ShoppingItem(
    val id: String,
    val foodUuid: String?,
    val storeProductId: String?,
    val storeId: String?,
    val name: String,
    val amountNeeded: Double?,
    val packs: Int?,
    val section: ShopSection,
    val manual: Boolean,
    val checkedAt: Long?,
    val priceHalers: Long?,
)

/** The shopping list (M5.7): generated from the plan with A16, plus manual items. */
class ShoppingRepository(
    private val shoppingDao: ShoppingDao,
    private val foodDao: FoodDao,
    private val plans: PlanRepository,
    private val recipes: RecipeRepository,
    private val pantry: PantryRepository,
    private val shops: ShopRepository,
    private val clock: Clock,
) {
    fun items(): Flow<List<ShoppingItem>> = shoppingDao.observeItems().map { rows -> rows.map { it.toItem() } }

    /**
     * The cooks still to do for the week of [date] and the next one, from [date] on (A16 step 1),
     * for [me]'s cooking days.
     */
    suspend fun upcomingCooks(me: Member, date: LocalDate): List<PlannedCook> {
        val week = ReminderSchedule.weekStart(date)
        val all = recipes.observeAll().first().associateBy { it.recipe.id }
        val cooked = plans.cooked(plans.planFor(week)).first() + plans.cooked(plans.planFor(week.plusWeeks(1))).first()
        val slots = plans.slots(plans.planFor(week)).first() + plans.slots(plans.planFor(week.plusWeeks(1))).first()
        return PlanCooks.compute(
            slots,
            all.mapValues { (_, d) -> d.recipe to d.nutrition },
            CookingSessions.days(me.cookSessionsPerWeek, me.cookDays),
            me.preppedMeals,
        ).filter { !it.date.isBefore(date) && (it.date to it.recipeId) !in cooked }
    }

    /** What the cooks need, per food (ingredient grams × scale); optional ingredients are left out. */
    suspend fun needs(cooks: List<PlannedCook>): List<Need> = cooks.flatMap { cook ->
        val details = recipes.details(cook.recipeId) ?: return@flatMap emptyList()
        details.ingredients.filter { !it.optional }.mapNotNull { ingredient ->
            val food = details.foods[ingredient.foodUuid] ?: return@mapNotNull null
            Need(food.uuid, ingredient.grams * cook.scale, food.section, cook.date)
        }
    }

    /** Regenerates the list (A16); manual items and ticks stay. Returns the number of lines. */
    suspend fun regenerate(me: Member, today: LocalDate): Int {
        val needs = needs(upcomingCooks(me, today))
        val products = shops.productsNow()
        val lines = Shopping.lines(needs, pantry.lots(), products.mapNotNull { it.option() }, shops.storesNow().map { it.info })
        val now = clock.millis()
        val rows = lines.map { line ->
            val product = line.product?.let { option -> products.firstOrNull { it.id == option.id } }
            val food = foodDao.getByUuid(line.foodUuid)?.toDomain()
            ShoppingItemEntity(
                id = newId(),
                foodUuid = line.foodUuid,
                storeProductId = product?.id,
                storeId = line.storeId,
                name = product?.name ?: food?.displayName().orEmpty(),
                amountNeeded = line.toBuyGrams,
                unit = AmountUnit.G.name,
                packs = line.packs,
                section = line.section.name,
                fromPlanId = null,
                manual = false,
                checkedAt = null,
                checkedByMemberId = null,
                requestedByMemberId = null,
                priceHalers = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        shoppingDao.replaceGenerated(rows, now)
        return rows.size
    }

    suspend fun addManual(name: String, section: ShopSection = ShopSection.OTHER, storeId: String? = null) {
        val now = clock.millis()
        shoppingDao.insert(
            ShoppingItemEntity(
                id = newId(), foodUuid = null, storeProductId = null, storeId = storeId, name = name.trim(), amountNeeded = null,
                unit = null, packs = null, section = section.name, fromPlanId = null, manual = true, checkedAt = null,
                checkedByMemberId = null, requestedByMemberId = null, priceHalers = null, createdAt = now, updatedAt = now,
            ),
        )
    }

    /**
     * Ticks a line: it goes in the basket and becomes pantry food (packs × pack size, or the grams;
     * storage and best-before from the food, A17), with the price paid if given (A18).
     */
    suspend fun tick(item: ShoppingItem, priceHalers: Long?) {
        val now = clock.millis()
        val row = shoppingDao.items().firstOrNull { it.id == item.id } ?: return
        shoppingDao.update(row.copy(checkedAt = now, priceHalers = priceHalers, updatedAt = now))
        val product = item.storeProductId?.let { id -> shops.productsNow().firstOrNull { it.id == id } }
        if (product != null && priceHalers != null) shops.addPrice(product.id, priceHalers)
        val food = item.foodUuid?.let { foodDao.getByUuid(it)?.toDomain() }
        if (food == null && item.manual) {
            pantry.add(null, item.name, 1.0, AmountUnit.PIECE, costHalers = priceHalers)
            return
        }
        val grams = if (product != null && item.packs != null) item.packs * product.packAmount else item.amountNeeded ?: return
        pantry.add(food, product?.name ?: item.name, grams, AmountUnit.G, costHalers = priceHalers, storeProductId = product?.id)
    }

    /** A product picked for this line (M5.6 source 2) is used on the line right away. */
    suspend fun useProduct(item: ShoppingItem, productId: String) {
        val product = shops.productsNow().firstOrNull { it.id == productId } ?: return
        val row = shoppingDao.items().firstOrNull { it.id == item.id } ?: return
        val grams = item.amountNeeded ?: product.packAmount
        shoppingDao.update(
            row.copy(
                storeProductId = product.id, storeId = product.storeId, name = product.name,
                packs = Shopping.packs(grams, product.packAmount), updatedAt = clock.millis(),
            ),
        )
    }

    suspend fun clearTicked() = shoppingDao.deleteTicked(clock.millis())

    suspend fun delete(item: ShoppingItem) = shoppingDao.delete(item.id, clock.millis())

    private fun ShoppingItemEntity.toItem() = ShoppingItem(
        id = id, foodUuid = foodUuid, storeProductId = storeProductId, storeId = storeId, name = name, amountNeeded = amountNeeded,
        packs = packs, section = JsonColumns.enumByName<ShopSection>(section) ?: ShopSection.OTHER, manual = manual,
        checkedAt = checkedAt, priceHalers = priceHalers,
    )
}
