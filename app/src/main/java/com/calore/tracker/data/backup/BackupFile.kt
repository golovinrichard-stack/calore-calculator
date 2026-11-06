package com.calore.tracker.data.backup

import com.calore.tracker.data.db.BodyMeasurementEntity
import com.calore.tracker.data.db.CheckinEntity
import com.calore.tracker.data.db.DiaryEntryEntity
import com.calore.tracker.data.db.FoodEntity
import com.calore.tracker.data.db.JsonColumns
import com.calore.tracker.data.db.MemberEntity
import com.calore.tracker.data.db.RecipeEntity
import com.calore.tracker.data.db.PotEntity
import com.calore.tracker.data.db.PlanEntity
import com.calore.tracker.data.db.PlanSlotEntity
import com.calore.tracker.data.db.PlanCookEntity
import com.calore.tracker.data.db.PantryItemEntity
import com.calore.tracker.data.db.StoreEntity
import com.calore.tracker.data.db.StoreProductEntity
import com.calore.tracker.data.db.PriceEntity
import com.calore.tracker.data.db.ShoppingItemEntity
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.data.db.BatchEntity
import com.calore.tracker.data.db.BatchIngredientEntity
import com.calore.tracker.data.db.BoxEntity
import com.calore.tracker.data.db.BoxPartEntity
import com.calore.tracker.data.db.RecipeIngredientEntity
import com.calore.tracker.data.db.RecipeStepEntity
import com.calore.tracker.data.db.WeightEntryEntity
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.AnimalFlag
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.MealType
import java.time.DayOfWeek
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The backup file (M0.5). One JSON object with a header and one array per database table.
 * The rows are separate DTOs, not the Room entities, so a database change never silently
 * changes the file format.
 *
 * Every new table is added to [BackupTables] (definition of done). When a table or column is
 * added, raise [BackupCodec.VERSION]: this app reads its own version and older ones (a table
 * missing from an older file is simply empty), and refuses newer files instead of dropping data.
 */
@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    /** ISO-8601 instant, e.g. 2026-11-06T18:00:00Z. */
    val exportedAt: String,
    val appVersion: String,
    val tables: BackupTables,
)

@Serializable
data class BackupTables(
    val foods: List<FoodRow> = emptyList(),
    @SerialName("diary_entries") val diaryEntries: List<DiaryEntryRow> = emptyList(),
    @SerialName("weight_entries") val weightEntries: List<WeightEntryRow> = emptyList(),
    /** Since version 2 (M1). Null in a version-1 file: the import leaves these tables as they are. */
    val members: List<MemberRow>? = null,
    @SerialName("body_measurements") val bodyMeasurements: List<BodyMeasurementRow>? = null,
    val checkins: List<CheckinRow>? = null,
    // Since version 4 (M3). Null in older files: the app's recipes are then left as they are.
    val recipes: List<RecipeRow>? = null,
    @SerialName("recipe_ingredients") val recipeIngredients: List<RecipeIngredientRow>? = null,
    @SerialName("recipe_steps") val recipeSteps: List<RecipeStepRow>? = null,
    // Since version 5 (M4).
    val pots: List<PotRow>? = null,
    val batches: List<BatchRow>? = null,
    @SerialName("batch_ingredients") val batchIngredients: List<BatchIngredientRow>? = null,
    val boxes: List<BoxRow>? = null,
    @SerialName("box_parts") val boxParts: List<BoxPartRow>? = null,
    // Since version 6 (M5).
    val plans: List<PlanRow>? = null,
    @SerialName("plan_slots") val planSlots: List<PlanSlotRow>? = null,
    @SerialName("plan_cooks") val planCooks: List<PlanCookRow>? = null,
    @SerialName("pantry_items") val pantryItems: List<PantryItemRow>? = null,
    val stores: List<StoreRow>? = null,
    @SerialName("store_products") val storeProducts: List<StoreProductRow>? = null,
    val prices: List<PriceRow>? = null,
    @SerialName("shopping_items") val shoppingItems: List<ShoppingItemRow>? = null,
) {
    val counts: BackupCounts get() = BackupCounts(foods.size, diaryEntries.size, weightEntries.size)
}

/** How many rows of each kind a backup holds; shown before an import. */
data class BackupCounts(val foods: Int, val diaryEntries: Int, val weightEntries: Int)

@Serializable
data class FoodRow(
    val id: Long,
    val uuid: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double? = null,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val fiberPer100g: Double? = null,
    val sugarPer100g: Double? = null,
    val servingGrams: Double? = null,
    val source: String,
    val lastUsedAt: Long,
    val updatedAt: Long,
    // Since version 3 (M2).
    val key: String? = null,
    val nameCs: String? = null,
    val nameEn: String? = null,
    val section: String = "OTHER",
    val isGeneric: Boolean = false,
    val sourceRef: String? = null,
    val allergens: List<String> = emptyList(),
    val animalFlags: List<String> = emptyList(),
    val defaultPackGrams: Double? = null,
    val storage: String? = null,
    val shelfLifeDays: Int? = null,
    val gramsPerPiece: Double? = null,
    val ironMgPer100g: Double? = null,
    val magnesiumMgPer100g: Double? = null,
    val vitaminCMgPer100g: Double? = null,
    val datasetVersion: Int? = null,
)

@Serializable
data class DiaryEntryRow(
    val id: Long,
    val uuid: String,
    val date: String,
    val meal: String,
    val foodId: Long? = null,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val createdAt: Long,
    /** Since version 2; null is the phone's user. */
    val memberId: String? = null,
    // Since version 3 (M2).
    val entryType: String = "FOOD",
    val ironMg: Double? = null,
    val magnesiumMg: Double? = null,
    val vitaminCMg: Double? = null,
    // Since version 4 (M3).
    val recipeId: String? = null,
    val servings: Double? = null,
    // Since version 5 (M4).
    val boxId: String? = null,
)

@Serializable
data class WeightEntryRow(
    val id: Long,
    val uuid: String,
    val date: String,
    val kg: Double,
)

@Serializable
data class MemberRow(
    val id: String,
    val kind: String,
    val name: String,
    val colour: Int,
    val sex: String? = null,
    val birthDate: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val activity: String? = null,
    val goalType: String? = null,
    val goalRatePct: Double? = null,
    val macroMode: String,
    val proteinPerKg: Double? = null,
    val fatPct: Int? = null,
    val splitProteinPct: Int? = null,
    val splitCarbsPct: Int? = null,
    val splitFatPct: Int? = null,
    val targetKcal: Int? = null,
    val targetProteinG: Int? = null,
    val targetCarbsG: Int? = null,
    val targetFatG: Int? = null,
    val mealSplit: Map<String, Int>,
    val diet: String,
    val allergens: List<String>,
    val likedFoods: List<String>,
    val dislikedFoods: List<String>,
    val cuisines: List<String>,
    val equipment: List<String>,
    val variety: Int,
    val weeklyBudgetHalers: Long? = null,
    val cookSessionsPerWeek: Int,
    val cookDays: List<String>,
    val minutesPerSession: Int,
    val preppedMeals: List<String>,
    val sharesTargets: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class BodyMeasurementRow(
    val id: String,
    val memberId: String,
    val date: String,
    val neckCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val bodyFatPct: Double? = null,
    val method: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class CheckinRow(
    val id: String,
    val memberId: String,
    val weekStart: String,
    val trendStartKg: Double? = null,
    val trendEndKg: Double? = null,
    val avgIntakeKcal: Double? = null,
    val countedDays: Int,
    val estimatedMaintenanceKcal: Int? = null,
    val oldTargetKcal: Int,
    val proposedTargetKcal: Int,
    val accepted: Boolean,
    val createdAt: Long,
)

@Serializable
data class RecipeRow(
    val id: String,
    val key: String? = null,
    val name: String,
    val nameCs: String? = null,
    val nameEn: String? = null,
    val userEdited: Boolean = false,
    val shortCode: String,
    val servings: Int,
    val mealTypes: List<String> = emptyList(),
    val cuisine: String? = null,
    val tags: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val fridgeDays: Int = 3,
    val freezable: Boolean = false,
    val freezerMonths: Int = 3,
    val reheatNote: String? = null,
    val mealPrepFriendly: Boolean = false,
    val learnedYield: Double? = null,
    val learnedYieldCount: Int = 0,
    val ownerMemberId: String? = null,
    val visibility: String = "SHARED",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class RecipeIngredientRow(
    val id: String,
    val recipeId: String,
    val position: Int,
    val foodUuid: String,
    val grams: Double,
    val note: String? = null,
    val optional: Boolean = false,
)

@Serializable
data class RecipeStepRow(
    val id: String,
    val recipeId: String,
    val position: Int,
    val text: String,
    val textCs: String? = null,
    val textEn: String? = null,
    val minutes: Int? = null,
    val passive: Boolean = false,
    val equipment: String? = null,
)

@Serializable
data class PotRow(
    val id: String,
    val name: String,
    val emptyGrams: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class BatchRow(
    val id: String,
    val recipeId: String? = null,
    val name: String,
    val cookedAt: Long,
    val cookedByMemberId: String? = null,
    val rawGrams: Double,
    val cookedGrams: Double,
    val kcalPer100g: Double,
    val proteinPer100g: Double? = null,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val fiberPer100g: Double? = null,
    val sugarPer100g: Double? = null,
    val ironMgPer100g: Double? = null,
    val magnesiumMgPer100g: Double? = null,
    val vitaminCMgPer100g: Double? = null,
    val costHalers: Long? = null,
    val ownerMemberId: String? = null,
    val visibility: String = "SHARED",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class BatchIngredientRow(
    val id: String,
    val batchId: String,
    val foodUuid: String,
    val grams: Double,
)

@Serializable
data class BoxRow(
    val id: String,
    val code: String,
    val ownerMemberId: String? = null,
    val visibility: String = "SHARED",
    val storage: String,
    val state: String,
    val remainingFraction: Double = 1.0,
    val eatByDate: String,
    val frozenAt: Long? = null,
    val thawedAt: Long? = null,
    val plannedDate: String? = null,
    val plannedMeal: String? = null,
    val planSlotId: String? = null,
    val wastedReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class BoxPartRow(
    val id: String,
    val boxId: String,
    val batchId: String,
    val grams: Double,
)

@Serializable
data class PlanRow(
    val id: String,
    val weekStart: String? = null,
    val templateName: String? = null,
    val generatorSeed: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class PlanSlotRow(
    val id: String,
    val planId: String,
    val date: String,
    val meal: String,
    val memberId: String,
    val kind: String,
    val recipeId: String? = null,
    val boxId: String? = null,
    val targetKcal: Int? = null,
    val targetProteinG: Int? = null,
    val targetCarbsG: Int? = null,
    val targetFatG: Int? = null,
    val locked: Boolean = false,
    val done: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class PlanCookRow(
    val id: String,
    val planId: String,
    val date: String,
    val recipeId: String,
    val portions: Int,
    val targetCookedGrams: Double? = null,
    val scale: Double,
    val batchId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class PantryItemRow(
    val id: String,
    val foodUuid: String? = null,
    val storeProductId: String? = null,
    val name: String,
    val amount: Double,
    val unit: String,
    val storage: String,
    val boughtDate: String,
    val bestBeforeDate: String? = null,
    val openedDate: String? = null,
    val state: String,
    val costHalers: Long? = null,
    val reservedForCookId: String? = null,
    val ownerMemberId: String? = null,
    val visibility: String = "SHARED",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class StoreRow(
    val id: String,
    val key: String? = null,
    val name: String,
    val sectionOrder: List<String>,
    val favorite: Boolean = false,
    val defaultForSections: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class StoreProductRow(
    val id: String,
    val key: String? = null,
    val storeId: String,
    val foodUuid: String? = null,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val packAmount: Double,
    val packUnit: String,
    val preferred: Boolean = false,
    val source: String,
    val sourceNote: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class PriceRow(
    val id: String,
    val storeProductId: String,
    val priceHalers: Long,
    val currency: String,
    val date: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class ShoppingItemRow(
    val id: String,
    val foodUuid: String? = null,
    val storeProductId: String? = null,
    val storeId: String? = null,
    val name: String,
    val amountNeeded: Double? = null,
    val unit: String? = null,
    val packs: Int? = null,
    val section: String,
    val fromPlanId: String? = null,
    val manual: Boolean = false,
    val checkedAt: Long? = null,
    val checkedByMemberId: String? = null,
    val requestedByMemberId: String? = null,
    val priceHalers: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

internal fun FoodEntity.toRow() = FoodRow(
    id = id,
    uuid = uuid,
    name = name,
    brand = brand,
    barcode = barcode,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g,
    fiberPer100g = fiberPer100g,
    sugarPer100g = sugarPer100g,
    servingGrams = servingGrams,
    source = source,
    lastUsedAt = lastUsedAt,
    updatedAt = updatedAt,
    key = key,
    nameCs = nameCs,
    nameEn = nameEn,
    section = section,
    isGeneric = isGeneric,
    sourceRef = sourceRef,
    allergens = allergens.map { it.name },
    animalFlags = animalFlags.map { it.name },
    defaultPackGrams = defaultPackGrams,
    storage = storage,
    shelfLifeDays = shelfLifeDays,
    gramsPerPiece = gramsPerPiece,
    ironMgPer100g = ironMgPer100g,
    magnesiumMgPer100g = magnesiumMgPer100g,
    vitaminCMgPer100g = vitaminCMgPer100g,
    datasetVersion = datasetVersion,
)

internal fun FoodRow.toEntity() = FoodEntity(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g,
    fiberPer100g = fiberPer100g,
    sugarPer100g = sugarPer100g,
    servingGrams = servingGrams,
    source = source,
    lastUsedAt = lastUsedAt,
    uuid = uuid,
    updatedAt = updatedAt,
    key = key,
    nameCs = nameCs,
    nameEn = nameEn,
    section = section,
    isGeneric = isGeneric,
    sourceRef = sourceRef,
    allergens = allergens.mapNotNull { JsonColumns.enumByName<Allergen>(it) },
    animalFlags = animalFlags.mapNotNull { JsonColumns.enumByName<AnimalFlag>(it) },
    defaultPackGrams = defaultPackGrams,
    storage = storage,
    shelfLifeDays = shelfLifeDays,
    gramsPerPiece = gramsPerPiece,
    ironMgPer100g = ironMgPer100g,
    magnesiumMgPer100g = magnesiumMgPer100g,
    vitaminCMgPer100g = vitaminCMgPer100g,
    datasetVersion = datasetVersion,
)

internal fun DiaryEntryEntity.toRow() = DiaryEntryRow(
    id = id,
    uuid = uuid,
    date = date,
    meal = meal,
    foodId = foodId,
    name = name,
    grams = grams,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    sugar = sugar,
    createdAt = createdAt,
    memberId = memberId,
    entryType = entryType,
    ironMg = ironMg,
    magnesiumMg = magnesiumMg,
    vitaminCMg = vitaminCMg,
    recipeId = recipeId,
    servings = servings,
    boxId = boxId,
)

internal fun DiaryEntryRow.toEntity() = DiaryEntryEntity(
    id = id,
    date = date,
    meal = meal,
    foodId = foodId,
    name = name,
    grams = grams,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    sugar = sugar,
    createdAt = createdAt,
    uuid = uuid,
    memberId = memberId,
    entryType = entryType,
    ironMg = ironMg,
    magnesiumMg = magnesiumMg,
    vitaminCMg = vitaminCMg,
    recipeId = recipeId,
    servings = servings,
    boxId = boxId,
)

internal fun WeightEntryEntity.toRow() = WeightEntryRow(id = id, uuid = uuid, date = date, kg = kg)

internal fun WeightEntryRow.toEntity() = WeightEntryEntity(id = id, date = date, kg = kg, uuid = uuid)

private fun <E : Enum<E>> List<E>.names() = map { it.name }

internal fun MemberEntity.toRow() = MemberRow(
    id = id,
    kind = kind,
    name = name,
    colour = colour,
    sex = sex,
    birthDate = birthDate,
    heightCm = heightCm,
    weightKg = weightKg,
    activity = activity,
    goalType = goalType,
    goalRatePct = goalRatePct,
    macroMode = macroMode,
    proteinPerKg = proteinPerKg,
    fatPct = fatPct,
    splitProteinPct = splitProteinPct,
    splitCarbsPct = splitCarbsPct,
    splitFatPct = splitFatPct,
    targetKcal = targetKcal,
    targetProteinG = targetProteinG,
    targetCarbsG = targetCarbsG,
    targetFatG = targetFatG,
    mealSplit = mealSplit.mapKeys { it.key.name },
    diet = diet,
    allergens = allergens.names(),
    likedFoods = likedFoods,
    dislikedFoods = dislikedFoods,
    cuisines = cuisines,
    equipment = equipment.names(),
    variety = variety,
    weeklyBudgetHalers = weeklyBudgetHalers,
    cookSessionsPerWeek = cookSessionsPerWeek,
    cookDays = cookDays.names(),
    minutesPerSession = minutesPerSession,
    preppedMeals = preppedMeals.names(),
    sharesTargets = sharesTargets,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun MemberRow.toEntity() = MemberEntity(
    id = id,
    kind = kind,
    name = name,
    colour = colour,
    sex = sex,
    birthDate = birthDate,
    heightCm = heightCm,
    weightKg = weightKg,
    activity = activity,
    goalType = goalType,
    goalRatePct = goalRatePct,
    macroMode = macroMode,
    proteinPerKg = proteinPerKg,
    fatPct = fatPct,
    splitProteinPct = splitProteinPct,
    splitCarbsPct = splitCarbsPct,
    splitFatPct = splitFatPct,
    targetKcal = targetKcal,
    targetProteinG = targetProteinG,
    targetCarbsG = targetCarbsG,
    targetFatG = targetFatG,
    mealSplit = mealSplit.mapNotNull { (name, pct) -> JsonColumns.enumByName<MealType>(name)?.let { it to pct } }.toMap(),
    diet = diet,
    allergens = allergens.mapNotNull { JsonColumns.enumByName<Allergen>(it) },
    likedFoods = likedFoods,
    dislikedFoods = dislikedFoods,
    cuisines = cuisines,
    equipment = equipment.mapNotNull { JsonColumns.enumByName<Equipment>(it) },
    variety = variety,
    weeklyBudgetHalers = weeklyBudgetHalers,
    cookSessionsPerWeek = cookSessionsPerWeek,
    cookDays = cookDays.mapNotNull { JsonColumns.enumByName<DayOfWeek>(it) },
    minutesPerSession = minutesPerSession,
    preppedMeals = preppedMeals.mapNotNull { JsonColumns.enumByName<MealType>(it) },
    sharesTargets = sharesTargets,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun BodyMeasurementEntity.toRow() = BodyMeasurementRow(
    id = id,
    memberId = memberId,
    date = date,
    neckCm = neckCm,
    waistCm = waistCm,
    hipCm = hipCm,
    bodyFatPct = bodyFatPct,
    method = method,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun BodyMeasurementRow.toEntity() = BodyMeasurementEntity(
    id = id,
    memberId = memberId,
    date = date,
    neckCm = neckCm,
    waistCm = waistCm,
    hipCm = hipCm,
    bodyFatPct = bodyFatPct,
    method = method,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun CheckinEntity.toRow() = CheckinRow(
    id = id,
    memberId = memberId,
    weekStart = weekStart,
    trendStartKg = trendStartKg,
    trendEndKg = trendEndKg,
    avgIntakeKcal = avgIntakeKcal,
    countedDays = countedDays,
    estimatedMaintenanceKcal = estimatedMaintenanceKcal,
    oldTargetKcal = oldTargetKcal,
    proposedTargetKcal = proposedTargetKcal,
    accepted = accepted,
    createdAt = createdAt,
)

internal fun CheckinRow.toEntity() = CheckinEntity(
    id = id,
    memberId = memberId,
    weekStart = weekStart,
    trendStartKg = trendStartKg,
    trendEndKg = trendEndKg,
    avgIntakeKcal = avgIntakeKcal,
    countedDays = countedDays,
    estimatedMaintenanceKcal = estimatedMaintenanceKcal,
    oldTargetKcal = oldTargetKcal,
    proposedTargetKcal = proposedTargetKcal,
    accepted = accepted,
    createdAt = createdAt,
)

internal fun RecipeEntity.toRow() = RecipeRow(
    id = id,
    key = key,
    name = name,
    nameCs = nameCs,
    nameEn = nameEn,
    userEdited = userEdited,
    shortCode = shortCode,
    servings = servings,
    mealTypes = mealTypes.map { it.name },
    cuisine = cuisine,
    tags = tags,
    equipment = equipment.map { it.name },
    fridgeDays = fridgeDays,
    freezable = freezable,
    freezerMonths = freezerMonths,
    reheatNote = reheatNote,
    mealPrepFriendly = mealPrepFriendly,
    learnedYield = learnedYield,
    learnedYieldCount = learnedYieldCount,
    ownerMemberId = ownerMemberId,
    visibility = visibility,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun RecipeRow.toEntity() = RecipeEntity(
    id = id,
    key = key,
    name = name,
    nameCs = nameCs,
    nameEn = nameEn,
    userEdited = userEdited,
    shortCode = shortCode,
    servings = servings,
    mealTypes = mealTypes.mapNotNull { JsonColumns.enumByName<MealType>(it) },
    cuisine = cuisine,
    tags = tags,
    equipment = equipment.mapNotNull { JsonColumns.enumByName<Equipment>(it) },
    fridgeDays = fridgeDays,
    freezable = freezable,
    freezerMonths = freezerMonths,
    reheatNote = reheatNote,
    mealPrepFriendly = mealPrepFriendly,
    learnedYield = learnedYield,
    learnedYieldCount = learnedYieldCount,
    ownerMemberId = ownerMemberId,
    visibility = visibility,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun RecipeIngredientEntity.toRow() = RecipeIngredientRow(id, recipeId, position, foodUuid, grams, note, optional)

internal fun RecipeIngredientRow.toEntity() = RecipeIngredientEntity(id, recipeId, position, foodUuid, grams, note, optional)

internal fun RecipeStepEntity.toRow() = RecipeStepRow(id, recipeId, position, text, textCs, textEn, minutes, passive, equipment)

internal fun RecipeStepRow.toEntity() = RecipeStepEntity(id, recipeId, position, text, textCs, textEn, minutes, passive, equipment)

internal fun PotEntity.toRow() = PotRow(
    id = id,
    name = name,
    emptyGrams = emptyGrams,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PotRow.toEntity() = PotEntity(
    id = id,
    name = name,
    emptyGrams = emptyGrams,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun BatchEntity.toRow() = BatchRow(
    id = id,
    recipeId = recipeId,
    name = name,
    cookedAt = cookedAt,
    cookedByMemberId = cookedByMemberId,
    rawGrams = rawGrams,
    cookedGrams = cookedGrams,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g,
    fiberPer100g = fiberPer100g,
    sugarPer100g = sugarPer100g,
    ironMgPer100g = ironMgPer100g,
    magnesiumMgPer100g = magnesiumMgPer100g,
    vitaminCMgPer100g = vitaminCMgPer100g,
    costHalers = costHalers,
    ownerMemberId = ownerMemberId,
    visibility = visibility,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun BatchRow.toEntity() = BatchEntity(
    id = id,
    recipeId = recipeId,
    name = name,
    cookedAt = cookedAt,
    cookedByMemberId = cookedByMemberId,
    rawGrams = rawGrams,
    cookedGrams = cookedGrams,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g,
    fiberPer100g = fiberPer100g,
    sugarPer100g = sugarPer100g,
    ironMgPer100g = ironMgPer100g,
    magnesiumMgPer100g = magnesiumMgPer100g,
    vitaminCMgPer100g = vitaminCMgPer100g,
    costHalers = costHalers,
    ownerMemberId = ownerMemberId,
    visibility = visibility,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun BatchIngredientEntity.toRow() = BatchIngredientRow(
    id = id,
    batchId = batchId,
    foodUuid = foodUuid,
    grams = grams,
)

internal fun BatchIngredientRow.toEntity() = BatchIngredientEntity(
    id = id,
    batchId = batchId,
    foodUuid = foodUuid,
    grams = grams,
)

internal fun BoxEntity.toRow() = BoxRow(
    id = id,
    code = code,
    ownerMemberId = ownerMemberId,
    visibility = visibility,
    storage = storage,
    state = state,
    remainingFraction = remainingFraction,
    eatByDate = eatByDate,
    frozenAt = frozenAt,
    thawedAt = thawedAt,
    plannedDate = plannedDate,
    plannedMeal = plannedMeal,
    planSlotId = planSlotId,
    wastedReason = wastedReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun BoxRow.toEntity() = BoxEntity(
    id = id,
    code = code,
    ownerMemberId = ownerMemberId,
    visibility = visibility,
    storage = storage,
    state = state,
    remainingFraction = remainingFraction,
    eatByDate = eatByDate,
    frozenAt = frozenAt,
    thawedAt = thawedAt,
    plannedDate = plannedDate,
    plannedMeal = plannedMeal,
    planSlotId = planSlotId,
    wastedReason = wastedReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun BoxPartEntity.toRow() = BoxPartRow(
    id = id,
    boxId = boxId,
    batchId = batchId,
    grams = grams,
)

internal fun BoxPartRow.toEntity() = BoxPartEntity(
    id = id,
    boxId = boxId,
    batchId = batchId,
    grams = grams,
)

internal fun PlanEntity.toRow() = PlanRow(
    id = id,
    weekStart = weekStart,
    templateName = templateName,
    generatorSeed = generatorSeed,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PlanRow.toEntity() = PlanEntity(
    id = id,
    weekStart = weekStart,
    templateName = templateName,
    generatorSeed = generatorSeed,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PlanSlotEntity.toRow() = PlanSlotRow(
    id = id,
    planId = planId,
    date = date,
    meal = meal,
    memberId = memberId,
    kind = kind,
    recipeId = recipeId,
    boxId = boxId,
    targetKcal = targetKcal,
    targetProteinG = targetProteinG,
    targetCarbsG = targetCarbsG,
    targetFatG = targetFatG,
    locked = locked,
    done = done,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PlanSlotRow.toEntity() = PlanSlotEntity(
    id = id,
    planId = planId,
    date = date,
    meal = meal,
    memberId = memberId,
    kind = kind,
    recipeId = recipeId,
    boxId = boxId,
    targetKcal = targetKcal,
    targetProteinG = targetProteinG,
    targetCarbsG = targetCarbsG,
    targetFatG = targetFatG,
    locked = locked,
    done = done,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PlanCookEntity.toRow() = PlanCookRow(
    id = id,
    planId = planId,
    date = date,
    recipeId = recipeId,
    portions = portions,
    targetCookedGrams = targetCookedGrams,
    scale = scale,
    batchId = batchId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PlanCookRow.toEntity() = PlanCookEntity(
    id = id,
    planId = planId,
    date = date,
    recipeId = recipeId,
    portions = portions,
    targetCookedGrams = targetCookedGrams,
    scale = scale,
    batchId = batchId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PantryItemEntity.toRow() = PantryItemRow(
    id = id,
    foodUuid = foodUuid,
    storeProductId = storeProductId,
    name = name,
    amount = amount,
    unit = unit,
    storage = storage,
    boughtDate = boughtDate,
    bestBeforeDate = bestBeforeDate,
    openedDate = openedDate,
    state = state,
    costHalers = costHalers,
    reservedForCookId = reservedForCookId,
    ownerMemberId = ownerMemberId,
    visibility = visibility,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PantryItemRow.toEntity() = PantryItemEntity(
    id = id,
    foodUuid = foodUuid,
    storeProductId = storeProductId,
    name = name,
    amount = amount,
    unit = unit,
    storage = storage,
    boughtDate = boughtDate,
    bestBeforeDate = bestBeforeDate,
    openedDate = openedDate,
    state = state,
    costHalers = costHalers,
    reservedForCookId = reservedForCookId,
    ownerMemberId = ownerMemberId,
    visibility = visibility,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun StoreEntity.toRow() = StoreRow(
    id = id,
    key = key,
    name = name,
    sectionOrder = sectionOrder.map { it.name },
    favorite = favorite,
    defaultForSections = defaultForSections.map { it.name },
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun StoreRow.toEntity() = StoreEntity(
    id = id,
    key = key,
    name = name,
    sectionOrder = sectionOrder.mapNotNull { JsonColumns.enumByName<ShopSection>(it) },
    favorite = favorite,
    defaultForSections = defaultForSections.mapNotNull { JsonColumns.enumByName<ShopSection>(it) },
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun StoreProductEntity.toRow() = StoreProductRow(
    id = id,
    key = key,
    storeId = storeId,
    foodUuid = foodUuid,
    name = name,
    brand = brand,
    barcode = barcode,
    packAmount = packAmount,
    packUnit = packUnit,
    preferred = preferred,
    source = source,
    sourceNote = sourceNote,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun StoreProductRow.toEntity() = StoreProductEntity(
    id = id,
    key = key,
    storeId = storeId,
    foodUuid = foodUuid,
    name = name,
    brand = brand,
    barcode = barcode,
    packAmount = packAmount,
    packUnit = packUnit,
    preferred = preferred,
    source = source,
    sourceNote = sourceNote,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PriceEntity.toRow() = PriceRow(
    id = id,
    storeProductId = storeProductId,
    priceHalers = priceHalers,
    currency = currency,
    date = date,
    source = source,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun PriceRow.toEntity() = PriceEntity(
    id = id,
    storeProductId = storeProductId,
    priceHalers = priceHalers,
    currency = currency,
    date = date,
    source = source,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ShoppingItemEntity.toRow() = ShoppingItemRow(
    id = id,
    foodUuid = foodUuid,
    storeProductId = storeProductId,
    storeId = storeId,
    name = name,
    amountNeeded = amountNeeded,
    unit = unit,
    packs = packs,
    section = section,
    fromPlanId = fromPlanId,
    manual = manual,
    checkedAt = checkedAt,
    checkedByMemberId = checkedByMemberId,
    requestedByMemberId = requestedByMemberId,
    priceHalers = priceHalers,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ShoppingItemRow.toEntity() = ShoppingItemEntity(
    id = id,
    foodUuid = foodUuid,
    storeProductId = storeProductId,
    storeId = storeId,
    name = name,
    amountNeeded = amountNeeded,
    unit = unit,
    packs = packs,
    section = section,
    fromPlanId = fromPlanId,
    manual = manual,
    checkedAt = checkedAt,
    checkedByMemberId = checkedByMemberId,
    requestedByMemberId = requestedByMemberId,
    priceHalers = priceHalers,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
