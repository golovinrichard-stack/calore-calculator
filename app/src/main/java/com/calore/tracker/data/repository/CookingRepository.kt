package com.calore.tracker.data.repository

import com.calore.tracker.data.db.BatchEntity
import com.calore.tracker.data.db.BatchIngredientEntity
import com.calore.tracker.data.db.BoxPartEntity
import com.calore.tracker.data.db.CookingDao
import com.calore.tracker.data.db.PotEntity
import com.calore.tracker.data.db.StoredBatch
import com.calore.tracker.data.db.StoredBox
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.domain.Batch
import com.calore.tracker.domain.Batches
import com.calore.tracker.domain.Box
import com.calore.tracker.domain.BoxEating
import com.calore.tracker.domain.BoxSplitter
import com.calore.tracker.domain.BoxState
import com.calore.tracker.domain.BoxView
import com.calore.tracker.domain.DiaryEntry
import com.calore.tracker.domain.EntryType
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.Freshness
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Pot
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.Storage
import com.calore.tracker.domain.WasteReason
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** One box to fill: its parts (batch and grams), who it is for, and when it is planned. */
data class BoxSpec(
    val parts: List<Pair<String, Double>>,
    val ownerMemberId: String?,
    val plannedDate: LocalDate? = null,
    val plannedMeal: MealType? = null,
    /** The plan slot this box is for (M5.4). */
    val planSlotId: String? = null,
)

/** What "Ate it" did, so it can be undone (M4.7). */
data class EatenBox(val previous: Box, val diaryEntryId: Long, val meal: MealType)

/** Pots, cooked batches and prep boxes (M4). */
class CookingRepository(
    private val cookingDao: CookingDao,
    private val clock: Clock,
) {
    private fun today(): LocalDate = LocalDate.now(clock)

    // Pots (M4.2)

    fun pots(): Flow<List<Pot>> = cookingDao.observePots().map { rows -> rows.map { it.toDomain() } }

    suspend fun savePot(id: String?, name: String, emptyGrams: Double) {
        val now = clock.millis()
        cookingDao.upsertPot(PotEntity(id = id ?: newId(), name = name.trim(), emptyGrams = emptyGrams, createdAt = now, updatedAt = now))
    }

    suspend fun deletePot(id: String) = cookingDao.deletePot(id, clock.millis())

    // Batches (M4.3)

    /**
     * Saves a cooked batch with what really went in and its weighed result. A batch from a recipe
     * also teaches the recipe its yield (A9). Returns the batch.
     */
    suspend fun saveBatch(
        recipeId: String?,
        recipeLearnedYield: Double?,
        name: String,
        ingredients: List<Pair<Food, Double>>,
        cookedGrams: Double,
        cookedByMemberId: String?,
        costHalers: Long? = null,
    ): Batch {
        val now = clock.millis()
        val rawGrams = ingredients.sumOf { it.second }
        val per100g = Batches.per100gCooked(Batches.potNutrients(ingredients), cookedGrams)
        val entity = BatchEntity(
            id = newId(),
            recipeId = recipeId,
            name = name,
            cookedAt = now,
            cookedByMemberId = cookedByMemberId,
            rawGrams = rawGrams,
            cookedGrams = cookedGrams,
            kcalPer100g = per100g.kcal,
            proteinPer100g = per100g.protein,
            carbsPer100g = per100g.carbs,
            fatPer100g = per100g.fat,
            fiberPer100g = per100g.fiber,
            sugarPer100g = per100g.sugar,
            ironMgPer100g = per100g.ironMg,
            magnesiumMgPer100g = per100g.magnesiumMg,
            vitaminCMgPer100g = per100g.vitaminCMg,
            costHalers = costHalers,
            createdAt = now,
            updatedAt = now,
        )
        cookingDao.insertBatch(
            StoredBatch(entity, ingredients.map { (food, grams) -> BatchIngredientEntity(newId(), entity.id, food.uuid, grams) }),
        )
        if (recipeId != null && rawGrams > 0) {
            cookingDao.setLearnedYield(recipeId, Batches.learnedYield(recipeLearnedYield, cookedGrams / rawGrams), now)
        }
        return entity.toDomain()
    }

    // Boxes (M4.5)

    /** Codes of boxes not eaten or wasted. */
    suspend fun activeCodes(): Set<String> = cookingDao.activeCodes().toSet()

    /** For each code prefix of active boxes ("C" of "C3"), the recipe of its first batch (A10). */
    suspend fun activePrefixOwners(): Map<String, String?> = activeBoxes().first()
        .associate { view -> view.box.code.takeWhile { it.isLetter() } to view.parts.firstOrNull()?.second?.recipeId }

    /**
     * Fills boxes. Each box's eat-by date is the earliest of its parts (A12: [fridgeDaysByBatch]
     * days after the day each batch was cooked), and a box planned after that date goes straight to the freezer (A13).
     * Returns the new boxes.
     */
    suspend fun fillBoxes(prefix: String, specs: List<BoxSpec>, fridgeDaysByBatch: Map<String, Int>): List<Box> {
        val now = clock.millis()
        val cookedDay = specs.flatMap { spec -> spec.parts.map { it.first } }.distinct()
            .associateWith { batchId -> cookingDao.batch(batchId)?.let { dayOf(it.cookedAt) } ?: today() }
        val codes = BoxSplitter.nextCodes(prefix, specs.size, activeCodes())
        val boxes = specs.mapIndexed { index, spec ->
            val eatBy = Freshness.eatByOfParts(
                spec.parts.map { (batchId, _) ->
                    Freshness.eatBy(cookedDay.getValue(batchId), fridgeDaysByBatch[batchId] ?: Recipe.DEFAULT_FRIDGE_DAYS)
                },
            )
            val frozen = Freshness.goesToFreezer(spec.plannedDate, eatBy)
            val box = Box(
                id = newId(),
                code = codes[index],
                ownerMemberId = spec.ownerMemberId,
                storage = if (frozen) Storage.FREEZER else Storage.FRIDGE,
                state = BoxState.READY,
                remainingFraction = 1.0,
                eatByDate = eatBy,
                frozenAt = if (frozen) now else null,
                plannedDate = spec.plannedDate,
                plannedMeal = spec.plannedMeal,
                planSlotId = spec.planSlotId,
                createdAt = now,
                updatedAt = now,
            )
            StoredBox(box.toEntity(), spec.parts.map { (batchId, grams) -> BoxPartEntity(newId(), box.id, batchId, grams) }) to box
        }
        cookingDao.insertBoxes(boxes.map { it.first })
        return boxes.map { it.second }
    }

    /** Boxes not eaten or wasted, eat first at the top, with their contents. */
    fun activeBoxes(): Flow<List<BoxView>> = combine(
        cookingDao.observeActiveBoxes(),
        cookingDao.observeAllParts(),
        cookingDao.observeAllBatches(),
    ) { boxes, parts, batches ->
        val batchById = batches.associate { it.id to it.toDomain() }
        val partsByBox = parts.groupBy { it.boxId }
        boxes.mapNotNull { entity ->
            entity.toDomain()?.let { box ->
                BoxView(box, partsByBox[box.id].orEmpty().map { it.toDomain() to batchById[it.batchId] })
            }
        }
    }

    // Eating (M4.7)

    /**
     * Logs [share] of the whole box (everything left when null) to the owner's diary as a BOX
     * entry with a nutrient snapshot, and updates the box, in one transaction. [ownerIsMe] puts the
     * entry in the phone's user's diary (`memberId` null).
     */
    suspend fun eat(view: BoxView, share: Double?, meal: MealType, date: LocalDate, ownerIsMe: Boolean): EatenBox {
        val box = view.box
        val (eaten, left, state) = BoxEating.eat(box, share)
        val updated = box.copy(remainingFraction = left, state = state, updatedAt = clock.millis())
        val entry = DiaryEntry(
            id = 0,
            date = date,
            meal = meal,
            foodId = null,
            name = "${box.code} · ${view.name}",
            grams = view.fullGrams * eaten,
            nutrients = view.fullNutrients.scaledBy(eaten),
            loggedAt = clock.millis(),
            uuid = newId(),
            memberId = if (ownerIsMe) null else box.ownerMemberId,
            entryType = EntryType.BOX,
        ).toEntity().copy(boxId = box.id)
        val entryId = cookingDao.eatBox(updated.toEntity(), entry, clock.millis())
        return EatenBox(previous = box, diaryEntryId = entryId, meal = meal)
    }

    /** Undo of [eat]: the diary entry goes, the box comes back as it was. */
    suspend fun undoEat(eaten: EatenBox) = cookingDao.undoEat(eaten.previous.toEntity(), eaten.diaryEntryId, clock.millis())

    // Freezer, waste and moving (M4.6, M4.10)

    /** Freezing stops the clock; returns false after the eat-by date (A12). */
    suspend fun freeze(box: Box): Boolean {
        if (!Freshness.canFreeze(box.eatByDate, today())) return false
        cookingDao.updateBox(box.copy(storage = Storage.FREEZER, frozenAt = clock.millis(), updatedAt = clock.millis()).toEntity())
        return true
    }

    /** Thawed in the fridge: eat by tomorrow (A12). */
    suspend fun thaw(box: Box) {
        val now = clock.millis()
        cookingDao.updateBox(
            box.copy(storage = Storage.FRIDGE, thawedAt = now, eatByDate = Freshness.eatByAfterThaw(today()), updatedAt = now).toEntity(),
        )
    }

    suspend fun throwAway(box: Box, reason: WasteReason) {
        cookingDao.updateBox(box.copy(state = BoxState.WASTED, wastedReason = reason, updatedAt = clock.millis()).toEntity())
    }

    suspend fun move(box: Box, date: LocalDate?, meal: MealType?) {
        cookingDao.updateBox(box.copy(plannedDate = date, plannedMeal = meal, updatedAt = clock.millis()).toEntity())
    }

    /** Thrown-away boxes, newest first (M4.10). */
    fun wastedBoxes(): Flow<List<BoxView>> = combine(
        cookingDao.observeWastedBoxes(),
        cookingDao.observeAllParts(),
        cookingDao.observeAllBatches(),
    ) { boxes, parts, batches ->
        val batchById = batches.associate { it.id to it.toDomain() }
        val partsByBox = parts.groupBy { it.boxId }
        boxes.mapNotNull { entity ->
            entity.toDomain()?.let { box -> BoxView(box, partsByBox[box.id].orEmpty().map { it.toDomain() to batchById[it.batchId] }) }
        }
    }

    /** The calendar day a millisecond time falls on, in this phone's time zone. */
    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalDate()
}
