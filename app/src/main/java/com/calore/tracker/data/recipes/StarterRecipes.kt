package com.calore.tracker.data.recipes

import com.calore.tracker.data.db.FoodDao
import com.calore.tracker.data.db.JsonColumns
import com.calore.tracker.data.db.RecipeDao
import com.calore.tracker.data.db.RecipeEntity
import com.calore.tracker.data.db.RecipeIngredientEntity
import com.calore.tracker.data.db.RecipeStepEntity
import com.calore.tracker.data.db.StoredRecipe
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.time.Clock

/** `assets/recipes/starter-cz.json` (M3.4). Both languages are stored, so switching the phone's language just works. */
@Serializable
data class StarterRecipesFile(
    val format: String,
    val version: Int,
    val country: String,
    val recipes: List<StarterRecipe>,
)

@Serializable
data class StarterRecipe(
    val key: String,
    val nameCs: String,
    val nameEn: String,
    val shortCode: String,
    val servings: Int,
    val mealTypes: List<String> = emptyList(),
    val cuisine: String? = null,
    val tags: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val fridgeDays: Int = Recipe.DEFAULT_FRIDGE_DAYS,
    val freezable: Boolean = false,
    val freezerMonths: Int = Recipe.DEFAULT_FREEZER_MONTHS,
    val mealPrepFriendly: Boolean = false,
    val ingredients: List<StarterIngredient>,
    val stepsCs: List<StarterStep>,
    val stepsEn: List<StarterStep>,
)

@Serializable
data class StarterIngredient(
    /** A basic food `key` (M2.2). */
    val food: String,
    val grams: Double,
    val optional: Boolean = false,
)

@Serializable
data class StarterStep(
    val text: String,
    val minutes: Int? = null,
    val passive: Boolean = false,
    val equipment: String? = null,
)

/**
 * Imports the starter recipes when the bundled `version` is newer than the one imported before.
 * Matches recipes by `key`. A recipe the user edited or deleted is never touched. Runs after the
 * basic foods import, because ingredients point at basic foods; a recipe whose foods are missing
 * is skipped (the next app start tries again, since the version is only stored when all worked).
 */
class StarterRecipeImporter(
    private val recipeDao: RecipeDao,
    private val foodDao: FoodDao,
    private val appPrefs: AppPrefs,
    private val clock: Clock,
    private val openAsset: () -> InputStream,
) {
    /** Returns how many recipes were written; 0 when the stored pack is already current. */
    suspend fun importIfNewer(): Int {
        val file = openAsset().use { parse(it) }
        if (file.version <= appPrefs.settings.first().starterRecipesVersion) return 0
        val now = clock.millis()
        var written = 0
        var complete = true
        for (starter in file.recipes) {
            val existing = recipeDao.byKey(starter.key)
            if (existing != null && (existing.userEdited || existing.deletedAt != null)) continue
            val foodUuids = starter.ingredients.map { foodDao.getByKey(it.food)?.uuid }
            if (foodUuids.any { it == null }) {
                complete = false
                continue
            }
            recipeDao.save(starter.toStored(existing, foodUuids.filterNotNull(), now))
            written++
        }
        if (complete) appPrefs.setStarterRecipesVersion(file.version)
        return written
    }

    companion object {
        const val ASSET_PATH = "recipes/starter-cz.json"
        private const val FORMAT = "calore-starter-recipes"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(input: InputStream): StarterRecipesFile {
            val file = json.decodeFromString(StarterRecipesFile.serializer(), input.readBytes().toString(Charsets.UTF_8))
            require(file.format == FORMAT) { "Not a Calore starter recipes file" }
            return file
        }

        /** The rows to store; keeps the id and creation time of an earlier import. */
        internal fun StarterRecipe.toStored(existing: RecipeEntity?, foodUuids: List<String>, now: Long): StoredRecipe {
            require(stepsCs.size == stepsEn.size) { "$key: Czech and English steps differ" }
            val id = existing?.id ?: newId()
            return StoredRecipe(
                recipe = RecipeEntity(
                    id = id,
                    key = key,
                    name = nameCs,
                    nameCs = nameCs,
                    nameEn = nameEn,
                    userEdited = false,
                    shortCode = shortCode,
                    servings = servings,
                    mealTypes = mealTypes.mapNotNull { JsonColumns.enumByName<MealType>(it) },
                    cuisine = cuisine,
                    tags = tags,
                    equipment = equipment.mapNotNull { JsonColumns.enumByName<Equipment>(it) },
                    fridgeDays = fridgeDays,
                    freezable = freezable,
                    freezerMonths = freezerMonths,
                    reheatNote = null,
                    mealPrepFriendly = mealPrepFriendly,
                    // Learned from the user's own cooking (A9); a new pack doesn't forget it.
                    learnedYield = existing?.learnedYield,
                    learnedYieldCount = existing?.learnedYieldCount ?: 0,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
                ingredients = ingredients.mapIndexed { index, ingredient ->
                    RecipeIngredientEntity(
                        id = newId(),
                        recipeId = id,
                        position = index,
                        foodUuid = foodUuids[index],
                        grams = ingredient.grams,
                        note = null,
                        optional = ingredient.optional,
                    )
                },
                steps = stepsCs.zip(stepsEn).mapIndexed { index, (cs, en) ->
                    RecipeStepEntity(
                        id = newId(),
                        recipeId = id,
                        position = index,
                        text = cs.text,
                        textCs = cs.text,
                        textEn = en.text,
                        minutes = cs.minutes,
                        passive = cs.passive,
                        equipment = cs.equipment,
                    )
                },
            )
        }
    }
}
