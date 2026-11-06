package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Every row of every table, read in one transaction so the copy is consistent. */
data class DatabaseSnapshot(
    val foods: List<FoodEntity>,
    val diaryEntries: List<DiaryEntryEntity>,
    val weightEntries: List<WeightEntryEntity>,
    /** In [BackupDao.replaceAll], null keeps the table as it is (a backup made before it existed). */
    val members: List<MemberEntity>? = emptyList(),
    val bodyMeasurements: List<BodyMeasurementEntity>? = emptyList(),
    val checkins: List<CheckinEntity>? = emptyList(),
    val recipes: List<RecipeEntity>? = emptyList(),
    val recipeIngredients: List<RecipeIngredientEntity>? = emptyList(),
    val recipeSteps: List<RecipeStepEntity>? = emptyList(),
)

/** Whole-database reads and writes for backup export and import (M0.5). */
@Dao
abstract class BackupDao {

    @Query("SELECT * FROM foods ORDER BY id")
    protected abstract suspend fun allFoods(): List<FoodEntity>

    @Query("SELECT * FROM diary_entries ORDER BY id")
    protected abstract suspend fun allDiaryEntries(): List<DiaryEntryEntity>

    @Query("SELECT * FROM weight_entries ORDER BY id")
    protected abstract suspend fun allWeightEntries(): List<WeightEntryEntity>

    @Query("SELECT * FROM members ORDER BY createdAt, id")
    protected abstract suspend fun allMembers(): List<MemberEntity>

    @Query("SELECT * FROM body_measurements ORDER BY date, id")
    protected abstract suspend fun allBodyMeasurements(): List<BodyMeasurementEntity>

    @Query("SELECT * FROM checkins ORDER BY weekStart, id")
    protected abstract suspend fun allCheckins(): List<CheckinEntity>

    @Query("SELECT * FROM recipes ORDER BY createdAt, id")
    protected abstract suspend fun allRecipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredients ORDER BY recipeId, position")
    protected abstract suspend fun allRecipeIngredients(): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_steps ORDER BY recipeId, position")
    protected abstract suspend fun allRecipeSteps(): List<RecipeStepEntity>

    @Query("DELETE FROM recipes")
    protected abstract suspend fun deleteAllRecipes()

    @Query("DELETE FROM recipe_ingredients")
    protected abstract suspend fun deleteAllRecipeIngredients()

    @Query("DELETE FROM recipe_steps")
    protected abstract suspend fun deleteAllRecipeSteps()

    @Insert
    protected abstract suspend fun insertRecipes(rows: List<RecipeEntity>)

    @Insert
    protected abstract suspend fun insertRecipeIngredients(rows: List<RecipeIngredientEntity>)

    @Insert
    protected abstract suspend fun insertRecipeSteps(rows: List<RecipeStepEntity>)

    @Query("DELETE FROM members")
    protected abstract suspend fun deleteAllMembers()

    @Query("DELETE FROM body_measurements")
    protected abstract suspend fun deleteAllBodyMeasurements()

    @Query("DELETE FROM checkins")
    protected abstract suspend fun deleteAllCheckins()

    @Insert
    protected abstract suspend fun insertMembers(rows: List<MemberEntity>)

    @Insert
    protected abstract suspend fun insertBodyMeasurements(rows: List<BodyMeasurementEntity>)

    @Insert
    protected abstract suspend fun insertCheckins(rows: List<CheckinEntity>)

    @Query("DELETE FROM foods")
    protected abstract suspend fun deleteAllFoods()

    @Query("DELETE FROM diary_entries")
    protected abstract suspend fun deleteAllDiaryEntries()

    @Query("DELETE FROM weight_entries")
    protected abstract suspend fun deleteAllWeightEntries()

    @Insert
    protected abstract suspend fun insertFoods(rows: List<FoodEntity>)

    @Insert
    protected abstract suspend fun insertDiaryEntries(rows: List<DiaryEntryEntity>)

    @Insert
    protected abstract suspend fun insertWeightEntries(rows: List<WeightEntryEntity>)

    @Transaction
    open suspend fun snapshot(): DatabaseSnapshot = DatabaseSnapshot(
        foods = allFoods(),
        diaryEntries = allDiaryEntries(),
        weightEntries = allWeightEntries(),
        members = allMembers(),
        bodyMeasurements = allBodyMeasurements(),
        checkins = allCheckins(),
        recipes = allRecipes(),
        recipeIngredients = allRecipeIngredients(),
        recipeSteps = allRecipeSteps(),
    )

    /**
     * Deletes every row and inserts [snapshot] instead, keeping ids and uuids. Tables that are
     * null in [snapshot] are left as they are. All in one
     * transaction: if any insert fails (for example a duplicate id), nothing changes.
     */
    @Transaction
    open suspend fun replaceAll(snapshot: DatabaseSnapshot) {
        deleteAllDiaryEntries()
        deleteAllFoods()
        deleteAllWeightEntries()
        insertFoods(snapshot.foods)
        insertDiaryEntries(snapshot.diaryEntries)
        insertWeightEntries(snapshot.weightEntries)
        snapshot.members?.let {
            deleteAllMembers()
            insertMembers(it)
        }
        snapshot.bodyMeasurements?.let {
            deleteAllBodyMeasurements()
            insertBodyMeasurements(it)
        }
        snapshot.checkins?.let {
            deleteAllCheckins()
            insertCheckins(it)
        }
        snapshot.recipes?.let {
            deleteAllRecipes()
            insertRecipes(it)
        }
        snapshot.recipeIngredients?.let {
            deleteAllRecipeIngredients()
            insertRecipeIngredients(it)
        }
        snapshot.recipeSteps?.let {
            deleteAllRecipeSteps()
            insertRecipeSteps(it)
        }
    }
}
