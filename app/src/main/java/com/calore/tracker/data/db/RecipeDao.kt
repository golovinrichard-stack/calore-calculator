package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A recipe with its ingredients and steps, as stored. */
data class StoredRecipe(
    val recipe: RecipeEntity,
    val ingredients: List<RecipeIngredientEntity>,
    val steps: List<RecipeStepEntity>,
)

@Dao
abstract class RecipeDao {

    @Query("SELECT * FROM recipes WHERE ${SoftDelete.NOT_DELETED} ORDER BY COALESCE(nameCs, nameEn, name)")
    abstract fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    abstract suspend fun byId(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE id = :id")
    abstract fun observeById(id: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE `key` = :key")
    abstract suspend fun byKey(key: String): RecipeEntity?

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY position")
    abstract suspend fun ingredients(recipeId: String): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY position")
    abstract fun observeIngredients(recipeId: String): Flow<List<RecipeIngredientEntity>>

    /** Every ingredient of every recipe that isn't deleted, for nutrition in the recipe list. */
    @Query(
        "SELECT i.* FROM recipe_ingredients i JOIN recipes r ON r.id = i.recipeId " +
            "WHERE r.deletedAt IS NULL ORDER BY i.recipeId, i.position",
    )
    abstract fun observeAllIngredients(): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId ORDER BY position")
    abstract suspend fun steps(recipeId: String): List<RecipeStepEntity>

    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId ORDER BY position")
    abstract fun observeSteps(recipeId: String): Flow<List<RecipeStepEntity>>

    /** The foods used in any recipe, deleted recipes included (they may come back). */
    @Query("SELECT DISTINCT f.* FROM foods f JOIN recipe_ingredients i ON i.foodUuid = f.uuid")
    abstract fun observeIngredientFoods(): Flow<List<FoodEntity>>

    @Query(
        "SELECT DISTINCT f.* FROM foods f JOIN recipe_ingredients i ON i.foodUuid = f.uuid " +
            "WHERE i.recipeId = :recipeId",
    )
    abstract fun observeFoodsOf(recipeId: String): Flow<List<FoodEntity>>

    @Query(
        "SELECT DISTINCT f.* FROM foods f JOIN recipe_ingredients i ON i.foodUuid = f.uuid " +
            "WHERE i.recipeId = :recipeId",
    )
    abstract suspend fun foodsOf(recipeId: String): List<FoodEntity>

    /** How many live recipes use the food: deleting it would leave them with a gap. */
    @Query(
        "SELECT COUNT(DISTINCT r.id) FROM recipes r JOIN recipe_ingredients i ON i.recipeId = r.id " +
            "WHERE r.deletedAt IS NULL AND i.foodUuid = :foodUuid",
    )
    abstract suspend fun recipesUsing(foodUuid: String): Int

    /** M6: minutes of the steps that need the cook, per recipe. */
    @Query("SELECT recipeId, SUM(COALESCE(minutes, 0)) AS minutes FROM recipe_steps WHERE passive = 0 GROUP BY recipeId")
    abstract suspend fun activeMinutes(): List<RecipeMinutes>

    @Query("SELECT shortCode FROM recipes WHERE ${SoftDelete.NOT_DELETED}")
    abstract suspend fun shortCodes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertIngredients(rows: List<RecipeIngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSteps(rows: List<RecipeStepEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    protected abstract suspend fun deleteIngredients(recipeId: String)

    @Query("DELETE FROM recipe_steps WHERE recipeId = :recipeId")
    protected abstract suspend fun deleteSteps(recipeId: String)

    @Query("UPDATE recipes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    abstract suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE recipes SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    abstract suspend fun restore(id: String, now: Long)

    /** Saves a recipe with exactly these ingredients and steps, in one transaction. */
    @Transaction
    open suspend fun save(recipe: StoredRecipe) {
        upsertRecipe(recipe.recipe)
        deleteIngredients(recipe.recipe.id)
        deleteSteps(recipe.recipe.id)
        insertIngredients(recipe.ingredients)
        insertSteps(recipe.steps)
    }
}

/** Active cooking minutes of one recipe (M6). */
data class RecipeMinutes(val recipeId: String, val minutes: Int)
