package com.calore.tracker.data.foods

import com.calore.tracker.data.db.FoodDao
import com.calore.tracker.data.db.FoodEntity
import com.calore.tracker.data.db.JsonColumns
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.AnimalFlag
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.StableIds
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.time.Clock

/** `assets/foods/generic-cz.json`, written by `tools/generic-foods/build.py` (M2.2). */
@Serializable
data class GenericFoodsFile(
    val format: String,
    val datasetVersion: Int,
    val country: String,
    val foods: List<GenericFood>,
)

@Serializable
data class GenericFood(
    val key: String,
    val nameCs: String,
    val nameEn: String,
    val section: String,
    val sourceRef: String,
    val allergens: List<String> = emptyList(),
    val animalFlags: List<String> = emptyList(),
    val defaultPackGrams: Double? = null,
    val storage: String? = null,
    val shelfLifeDays: Int? = null,
    val gramsPerPiece: Double? = null,
    val per100g: GenericNutrients,
)

@Serializable
data class GenericNutrients(
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val ironMg: Double? = null,
    val magnesiumMg: Double? = null,
    val vitaminCMg: Double? = null,
)

/**
 * Imports the bundled basic foods when the asset's `datasetVersion` is newer than the one
 * imported before. Upserts by `key`, so ids, uuids and "last used" stay stable; foods without a
 * key (the user's own and Open Food Facts products) are never touched.
 */
class GenericFoodImporter(
    private val foodDao: FoodDao,
    private val appPrefs: AppPrefs,
    private val clock: Clock,
    private val openAsset: () -> InputStream,
) {
    /** Returns how many foods were imported; 0 when the stored dataset is already current. */
    suspend fun importIfNewer(): Int {
        val file = openAsset().use { parse(it) }
        if (file.datasetVersion <= appPrefs.settings.first().genericFoodsVersion) return 0
        val now = clock.millis()
        foodDao.upsertGeneric(file.foods.map { it.toEntity(file.datasetVersion, now) })
        appPrefs.setGenericFoodsVersion(file.datasetVersion)
        return file.foods.size
    }

    companion object {
        const val ASSET_PATH = "foods/generic-cz.json"
        private const val FORMAT = "calore-generic-foods"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(input: InputStream): GenericFoodsFile {
            val file = json.decodeFromString(GenericFoodsFile.serializer(), input.readBytes().toString(Charsets.UTF_8))
            require(file.format == FORMAT) { "Not a Calore basic foods file" }
            return file
        }

        /** The row to store; [FoodDao.upsertGeneric] keeps the stored id, uuid and last use. */
        fun GenericFood.toEntity(datasetVersion: Int, now: Long) = FoodEntity(
            name = nameEn,
            brand = null,
            barcode = null,
            kcalPer100g = per100g.kcal,
            proteinPer100g = per100g.protein,
            carbsPer100g = per100g.carbs,
            fatPer100g = per100g.fat,
            fiberPer100g = per100g.fiber,
            sugarPer100g = per100g.sugar,
            servingGrams = gramsPerPiece,
            source = FoodSource.CUSTOM.name,
            lastUsedAt = 0,
            uuid = StableIds.food(key),
            updatedAt = now,
            key = key,
            nameCs = nameCs,
            nameEn = nameEn,
            section = section,
            isGeneric = true,
            sourceRef = sourceRef,
            allergens = allergens.mapNotNull { JsonColumns.enumByName<Allergen>(it) },
            animalFlags = animalFlags.mapNotNull { JsonColumns.enumByName<AnimalFlag>(it) },
            defaultPackGrams = defaultPackGrams,
            storage = storage,
            shelfLifeDays = shelfLifeDays,
            gramsPerPiece = gramsPerPiece,
            ironMgPer100g = per100g.ironMg,
            magnesiumMgPer100g = per100g.magnesiumMg,
            vitaminCMgPer100g = per100g.vitaminCMg,
            datasetVersion = datasetVersion,
        )
    }
}
