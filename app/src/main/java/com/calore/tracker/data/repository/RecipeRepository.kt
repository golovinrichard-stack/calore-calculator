package com.calore.tracker.data.repository

import com.calore.tracker.data.db.FoodDao
import com.calore.tracker.data.db.RecipeDao
import com.calore.tracker.data.db.StoredRecipe
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.AnimalFlag
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
import com.calore.tracker.domain.RecipeNutrition
import com.calore.tracker.domain.RecipeStep
import com.calore.tracker.domain.Recipes
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock

/** A recipe with everything needed to show it: ingredients, steps and the foods they use. */
data class RecipeDetails(
    val recipe: Recipe,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    /** By `foods.uuid`; an ingredient whose food was deleted is missing here. */
    val foods: Map<String, Food>,
) {
    val nutrition: RecipeNutrition by lazy {
        Recipes.nutrition(ingredients, foods, recipe.servings, recipe.learnedYield)
    }
    val allergens: Set<Allergen> by lazy { Recipes.allergens(ingredients, foods) }
    val animalFlags: Set<AnimalFlag> by lazy { Recipes.animalFlags(ingredients, foods) }
}

/** Recipes (M3): the user's own and the starter pack. */
class RecipeRepository(
    private val recipeDao: RecipeDao,
    private val foodDao: FoodDao,
    private val clock: Clock,
) {
    /** Every live recipe with its nutrition and allergens, for the recipe list and filters. */
    fun observeAll(): Flow<List<RecipeDetails>> = combine(
        recipeDao.observeAll(),
        recipeDao.observeAllIngredients(),
        recipeDao.observeIngredientFoods(),
    ) { recipes, ingredients, foods ->
        val byRecipe = ingredients.groupBy({ it.recipeId }, { it.toDomain() })
        val foodsByUuid = foods.associate { it.uuid to it.toDomain() }
        recipes.map { recipe ->
            val own = byRecipe[recipe.id].orEmpty()
            RecipeDetails(
                recipe = recipe.toDomain(),
                ingredients = own,
                steps = emptyList(),
                foods = own.mapNotNull { i -> foodsByUuid[i.foodUuid]?.let { i.foodUuid to it } }.toMap(),
            )
        }
    }

    /** One recipe with steps; null once it is deleted or if it doesn't exist. */
    fun observe(id: String): Flow<RecipeDetails?> = combine(
        recipeDao.observeById(id),
        recipeDao.observeIngredients(id),
        recipeDao.observeSteps(id),
        recipeDao.observeFoodsOf(id),
    ) { recipe, ingredients, steps, foods ->
        recipe?.takeIf { it.deletedAt == null }?.let {
            RecipeDetails(
                recipe = it.toDomain(),
                ingredients = ingredients.map { row -> row.toDomain() },
                steps = steps.map { row -> row.toDomain() },
                foods = foods.associate { food -> food.uuid to food.toDomain() },
            )
        }
    }

    suspend fun details(id: String): RecipeDetails? {
        val recipe = recipeDao.byId(id)?.takeIf { it.deletedAt == null } ?: return null
        return RecipeDetails(
            recipe = recipe.toDomain(),
            ingredients = recipeDao.ingredients(id).map { it.toDomain() },
            steps = recipeDao.steps(id).map { it.toDomain() },
            foods = recipeDao.foodsOf(id).associate { it.uuid to it.toDomain() },
        )
    }

    suspend fun food(uuid: String): Food? = foodDao.getByUuid(uuid)?.toDomain()

    /** Box codes already used by live recipes. */
    suspend fun shortCodes(): Set<String> = recipeDao.shortCodes().toSet()

    /**
     * Saves [recipe] with exactly these ingredients and steps (positions follow the list order)
     * and returns its id. A new recipe ([Recipe.id] blank) gets an id. A starter recipe the user
     * saves is marked [Recipe.userEdited], so a newer starter pack never overwrites it.
     */
    suspend fun save(recipe: Recipe, ingredients: List<RecipeIngredient>, steps: List<RecipeStep>): String {
        val now = clock.millis()
        val existing = recipe.id.takeIf { it.isNotBlank() }?.let { recipeDao.byId(it) }
        val id = existing?.id ?: newId()
        val saved = recipe.copy(
            id = id,
            userEdited = recipe.userEdited || recipe.key != null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        recipeDao.save(storedRecipe(saved, ingredients, steps))
        return id
    }

    /**
     * Saves [details] with [ingredients] as a new recipe of the user's own named [name] (M3.6
     * "Save as a copy"); the original stays as it is. Returns the new id.
     */
    suspend fun saveCopy(details: RecipeDetails, ingredients: List<RecipeIngredient>, name: String): String {
        val copy = details.recipe.copy(
            id = "",
            key = null,
            name = name,
            nameCs = null,
            nameEn = null,
            userEdited = false,
            shortCode = Recipes.suggestShortCode(name, shortCodes()),
            // Different ingredients cook down differently; the copy learns its own yield.
            learnedYield = null,
            learnedYieldCount = 0,
        )
        return save(copy, ingredients.map { it.copy(id = "") }, details.steps.map { it.copy(id = "") })
    }

    /** Writes a recipe as given, timestamps included (the starter pack importer). */
    internal suspend fun saveExactly(recipe: Recipe, ingredients: List<RecipeIngredient>, steps: List<RecipeStep>) {
        recipeDao.save(storedRecipe(recipe, ingredients, steps))
    }

    private fun storedRecipe(recipe: Recipe, ingredients: List<RecipeIngredient>, steps: List<RecipeStep>) = StoredRecipe(
        recipe = recipe.toEntity(),
        ingredients = ingredients.mapIndexed { index, ingredient ->
            ingredient.copy(id = ingredient.id.ifBlank { newId() }, recipeId = recipe.id, position = index).toEntity()
        },
        steps = steps.mapIndexed { index, step ->
            step.copy(id = step.id.ifBlank { newId() }, recipeId = recipe.id, position = index).toEntity()
        },
    )

    /** Soft delete, so it can be undone (and synced later, M11). */
    suspend fun delete(id: String) = recipeDao.softDelete(id, clock.millis())

    suspend fun restore(id: String) = recipeDao.restore(id, clock.millis())

    /** How many live recipes use the food. */
    suspend fun recipesUsing(foodUuid: String): Int = recipeDao.recipesUsing(foodUuid)
}
