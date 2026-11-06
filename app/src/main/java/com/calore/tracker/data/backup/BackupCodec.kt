package com.calore.tracker.data.backup

import com.calore.tracker.domain.parseIsoDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** What reading a file gave: a backup that is safe to import, or why it isn't. */
sealed interface BackupReadResult {
    data class Ok(val backup: BackupFile) : BackupReadResult

    /** Not JSON, or JSON from something other than Calore. */
    data object NotABackup : BackupReadResult

    /** Made by a newer app version that may hold data this version would drop. */
    data class NewerVersion(val version: Int) : BackupReadResult

    /** A Calore backup, but cut off, edited by hand or otherwise broken. */
    data object Damaged : BackupReadResult
}

/** Turns backups into JSON text and back, and checks a file completely before it is imported. */
object BackupCodec {
    const val FORMAT = "calore-backup"
    /**
     * 1: foods, diary, weights (M0). 2: + members, body measurements, check-ins, diary memberId (M1).
     * 3: + basic-food columns and micronutrients on foods, entry type and micronutrients on diary entries (M2).
     * 4: + recipes, recipe ingredients and steps, diary recipeId and servings (M3).
     * 5: + pots, batches, batch ingredients, boxes, box parts, diary boxId (M4).
     * 6: + plans, plan slots and cooks, pantry items, stores, store products, prices, shopping items (M5).
     */
    const val VERSION = 6

    /** Far above any real backup (10,000 diary entries are about 3 MB), and small enough to read safely. */
    const val MAX_BYTES = 64 * 1024 * 1024

    /** The header is written first, so the format name is near the start of a real backup. */
    private const val HEADER_SNIFF_CHARS = 200

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    fun decode(text: String): BackupReadResult {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException. A file that starts like a
            // Calore backup but isn't valid JSON was most likely cut off while copying.
            val looksLikeBackup = text.take(HEADER_SNIFF_CHARS).contains("\"$FORMAT\"")
            return if (looksLikeBackup) BackupReadResult.Damaged else BackupReadResult.NotABackup
        }
        val header = root as? JsonObject ?: return BackupReadResult.NotABackup
        if ((header["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) return BackupReadResult.NotABackup

        val version = (header["version"] as? JsonPrimitive)?.intOrNull ?: return BackupReadResult.Damaged
        if (version > VERSION) return BackupReadResult.NewerVersion(version)
        if (version < 1) return BackupReadResult.Damaged

        val backup = try {
            json.decodeFromJsonElement(BackupFile.serializer(), header)
        } catch (e: IllegalArgumentException) {
            return BackupReadResult.Damaged
        }
        return if (isConsistent(backup.tables)) BackupReadResult.Ok(backup) else BackupReadResult.Damaged
    }

    /**
     * Checks everything the database would refuse (duplicate ids, uuids, barcodes or weight days)
     * and values the app could not show, so an import either works completely or isn't started.
     */
    internal fun isConsistent(tables: BackupTables): Boolean {
        val foods = tables.foods
        val diary = tables.diaryEntries
        val weights = tables.weightEntries
        return foods.all { food ->
            food.id > 0 && food.isValidUuid() &&
                food.kcalPer100g.isAmount() &&
                listOf(food.proteinPer100g, food.carbsPer100g, food.fatPer100g, food.fiberPer100g, food.sugarPer100g, food.servingGrams)
                    .all { it == null || it.isAmount() }
        } &&
            foods.isUnique { it.id } && foods.isUnique { it.uuid.lowercase() } &&
            foods.mapNotNull { it.barcode }.let { it.size == it.toSet().size } &&
            foods.mapNotNull { it.key }.let { it.size == it.toSet().size } &&
            diary.all { entry ->
                entry.id > 0 && uuidPattern.matches(entry.uuid) && parseIsoDate(entry.date) != null &&
                    entry.grams.isAmount() && entry.kcal.isAmount() &&
                    listOf(entry.protein, entry.carbs, entry.fat, entry.fiber, entry.sugar, entry.servings).all { it == null || it.isAmount() }
            } &&
            diary.isUnique { it.id } && diary.isUnique { it.uuid.lowercase() } &&
            weights.all { weight ->
                weight.id > 0 && uuidPattern.matches(weight.uuid) && parseIsoDate(weight.date) != null &&
                    weight.kg.isFinite() && weight.kg > 0
            } &&
            weights.isUnique { it.id } && weights.isUnique { it.uuid.lowercase() } && weights.isUnique { it.date } &&
            diary.all { it.memberId == null || uuidPattern.matches(it.memberId) } &&
            areProfilesConsistent(tables) &&
            areRecipesConsistent(tables) &&
            areCookingTablesConsistent(tables) &&
            arePlanAndShopTablesConsistent(tables)
    }

    /** Every row has a uuid id, ids are unique, and the dates can be read. */
    private fun arePlanAndShopTablesConsistent(tables: BackupTables): Boolean {
        fun <T> List<T>?.ok(id: (T) -> String, valid: (T) -> Boolean = { true }): Boolean =
            orEmpty().all { uuidPattern.matches(id(it)) && valid(it) } && orEmpty().isUnique { id(it).lowercase() }
        return tables.plans.ok({ it.id }) { it.weekStart == null || parseIsoDate(it.weekStart) != null } &&
            tables.planSlots.ok({ it.id }) { parseIsoDate(it.date) != null } &&
            tables.planCooks.ok({ it.id }) { parseIsoDate(it.date) != null && it.scale.isAmount() } &&
            tables.pantryItems.ok({ it.id }) { it.amount.isAmount() && parseIsoDate(it.boughtDate) != null } &&
            tables.stores.ok({ it.id }) &&
            tables.storeProducts.ok({ it.id }) { it.packAmount > 0 } &&
            tables.prices.ok({ it.id }) { it.priceHalers >= 0 && parseIsoDate(it.date) != null } &&
            tables.shoppingItems.ok({ it.id })
    }

    private fun areCookingTablesConsistent(tables: BackupTables): Boolean {
        val pots = tables.pots.orEmpty()
        val batches = tables.batches.orEmpty()
        val ingredients = tables.batchIngredients.orEmpty()
        val boxes = tables.boxes.orEmpty()
        val parts = tables.boxParts.orEmpty()
        return pots.all { uuidPattern.matches(it.id) && it.emptyGrams.isAmount() } && pots.isUnique { it.id.lowercase() } &&
            batches.all { uuidPattern.matches(it.id) && it.rawGrams.isAmount() && it.cookedGrams > 0 && it.kcalPer100g.isAmount() } &&
            batches.isUnique { it.id.lowercase() } &&
            ingredients.all { uuidPattern.matches(it.id) && it.grams.isAmount() } && ingredients.isUnique { it.id.lowercase() } &&
            boxes.all { box ->
                uuidPattern.matches(box.id) && box.code.isNotBlank() && parseIsoDate(box.eatByDate) != null &&
                    box.remainingFraction in 0.0..1.0 && (box.plannedDate == null || parseIsoDate(box.plannedDate) != null)
            } &&
            boxes.isUnique { it.id.lowercase() } &&
            parts.all { uuidPattern.matches(it.id) && it.grams.isAmount() } && parts.isUnique { it.id.lowercase() }
    }

    private fun areRecipesConsistent(tables: BackupTables): Boolean {
        val recipes = tables.recipes.orEmpty()
        val ingredients = tables.recipeIngredients.orEmpty()
        val steps = tables.recipeSteps.orEmpty()
        return recipes.all { recipe ->
            uuidPattern.matches(recipe.id) && recipe.servings > 0 && recipe.shortCode.isNotBlank() &&
                (recipe.learnedYield == null || recipe.learnedYield.isAmount())
        } &&
            recipes.isUnique { it.id.lowercase() } &&
            recipes.mapNotNull { it.key }.let { it.size == it.toSet().size } &&
            ingredients.all { uuidPattern.matches(it.id) && it.grams.isAmount() } &&
            ingredients.isUnique { it.id.lowercase() } &&
            steps.all { uuidPattern.matches(it.id) } &&
            steps.isUnique { it.id.lowercase() }
    }

    private fun areProfilesConsistent(tables: BackupTables): Boolean {
        val members = tables.members.orEmpty()
        val measurements = tables.bodyMeasurements.orEmpty()
        val checkins = tables.checkins.orEmpty()
        return members.all { member ->
            uuidPattern.matches(member.id) && (member.birthDate == null || parseIsoDate(member.birthDate) != null) &&
                listOfNotNull(member.heightCm, member.weightKg, member.goalRatePct, member.proteinPerKg).all { it.isAmount() }
        } &&
            members.isUnique { it.id.lowercase() } &&
            measurements.all { row ->
                uuidPattern.matches(row.id) && uuidPattern.matches(row.memberId) && parseIsoDate(row.date) != null &&
                    listOfNotNull(row.neckCm, row.waistCm, row.hipCm, row.bodyFatPct).all { it.isAmount() }
            } &&
            measurements.isUnique { it.id.lowercase() } &&
            checkins.all { row ->
                uuidPattern.matches(row.id) && uuidPattern.matches(row.memberId) && parseIsoDate(row.weekStart) != null
            } &&
            checkins.isUnique { it.id.lowercase() } && checkins.isUnique { it.memberId to it.weekStart }
    }

    private fun FoodRow.isValidUuid() = uuidPattern.matches(uuid)

    private fun Double.isAmount() = isFinite() && this >= 0.0

    private inline fun <T, K> List<T>.isUnique(key: (T) -> K): Boolean = mapTo(HashSet(size)) { key(it) }.size == size
}
