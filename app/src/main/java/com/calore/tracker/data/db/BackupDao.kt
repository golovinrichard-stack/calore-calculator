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
    val pots: List<PotEntity>? = emptyList(),
    val batches: List<BatchEntity>? = emptyList(),
    val batchIngredients: List<BatchIngredientEntity>? = emptyList(),
    val boxes: List<BoxEntity>? = emptyList(),
    val boxParts: List<BoxPartEntity>? = emptyList(),
    val plans: List<PlanEntity>? = emptyList(),
    val planSlots: List<PlanSlotEntity>? = emptyList(),
    val planCooks: List<PlanCookEntity>? = emptyList(),
    val pantryItems: List<PantryItemEntity>? = emptyList(),
    val stores: List<StoreEntity>? = emptyList(),
    val storeProducts: List<StoreProductEntity>? = emptyList(),
    val prices: List<PriceEntity>? = emptyList(),
    val shoppingItems: List<ShoppingItemEntity>? = emptyList(),
    val recipeRatings: List<RecipeRatingEntity>? = emptyList(),
    val cyclePeriods: List<CyclePeriodEntity>? = emptyList(),
    val cycleDays: List<CycleDayEntity>? = emptyList(),
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

    @Query("SELECT * FROM pots ORDER BY createdAt, id")
    protected abstract suspend fun allPots(): List<PotEntity>

    @Query("DELETE FROM pots")
    protected abstract suspend fun deleteAllPots()

    @Insert
    protected abstract suspend fun insertPots(rows: List<PotEntity>)

    @Query("SELECT * FROM batches ORDER BY cookedAt, id")
    protected abstract suspend fun allBatches(): List<BatchEntity>

    @Query("DELETE FROM batches")
    protected abstract suspend fun deleteAllBatches()

    @Insert
    protected abstract suspend fun insertBatches(rows: List<BatchEntity>)

    @Query("SELECT * FROM batch_ingredients ORDER BY batchId, id")
    protected abstract suspend fun allBatchIngredients(): List<BatchIngredientEntity>

    @Query("DELETE FROM batch_ingredients")
    protected abstract suspend fun deleteAllBatchIngredients()

    @Insert
    protected abstract suspend fun insertBatchIngredients(rows: List<BatchIngredientEntity>)

    @Query("SELECT * FROM boxes ORDER BY createdAt, id")
    protected abstract suspend fun allBoxes(): List<BoxEntity>

    @Query("DELETE FROM boxes")
    protected abstract suspend fun deleteAllBoxes()

    @Insert
    protected abstract suspend fun insertBoxes(rows: List<BoxEntity>)

    @Query("SELECT * FROM box_parts ORDER BY boxId, id")
    protected abstract suspend fun allBoxParts(): List<BoxPartEntity>

    @Query("DELETE FROM box_parts")
    protected abstract suspend fun deleteAllBoxParts()

    @Insert
    protected abstract suspend fun insertBoxParts(rows: List<BoxPartEntity>)

    @Query("SELECT * FROM plans ORDER BY createdAt, id")
    protected abstract suspend fun allPlans(): List<PlanEntity>

    @Query("DELETE FROM plans")
    protected abstract suspend fun deleteAllPlans()

    @Insert
    protected abstract suspend fun insertPlans(rows: List<PlanEntity>)

    @Query("SELECT * FROM plan_slots ORDER BY createdAt, id")
    protected abstract suspend fun allPlanSlots(): List<PlanSlotEntity>

    @Query("DELETE FROM plan_slots")
    protected abstract suspend fun deleteAllPlanSlots()

    @Insert
    protected abstract suspend fun insertPlanSlots(rows: List<PlanSlotEntity>)

    @Query("SELECT * FROM plan_cooks ORDER BY createdAt, id")
    protected abstract suspend fun allPlanCooks(): List<PlanCookEntity>

    @Query("DELETE FROM plan_cooks")
    protected abstract suspend fun deleteAllPlanCooks()

    @Insert
    protected abstract suspend fun insertPlanCooks(rows: List<PlanCookEntity>)

    @Query("SELECT * FROM pantry_items ORDER BY createdAt, id")
    protected abstract suspend fun allPantryItems(): List<PantryItemEntity>

    @Query("DELETE FROM pantry_items")
    protected abstract suspend fun deleteAllPantryItems()

    @Insert
    protected abstract suspend fun insertPantryItems(rows: List<PantryItemEntity>)

    @Query("SELECT * FROM stores ORDER BY createdAt, id")
    protected abstract suspend fun allStores(): List<StoreEntity>

    @Query("DELETE FROM stores")
    protected abstract suspend fun deleteAllStores()

    @Insert
    protected abstract suspend fun insertStores(rows: List<StoreEntity>)

    @Query("SELECT * FROM store_products ORDER BY createdAt, id")
    protected abstract suspend fun allStoreProducts(): List<StoreProductEntity>

    @Query("DELETE FROM store_products")
    protected abstract suspend fun deleteAllStoreProducts()

    @Insert
    protected abstract suspend fun insertStoreProducts(rows: List<StoreProductEntity>)

    @Query("SELECT * FROM prices ORDER BY createdAt, id")
    protected abstract suspend fun allPrices(): List<PriceEntity>

    @Query("DELETE FROM prices")
    protected abstract suspend fun deleteAllPrices()

    @Insert
    protected abstract suspend fun insertPrices(rows: List<PriceEntity>)

    @Query("SELECT * FROM shopping_items ORDER BY createdAt, id")
    protected abstract suspend fun allShoppingItems(): List<ShoppingItemEntity>

    @Query("DELETE FROM shopping_items")
    protected abstract suspend fun deleteAllShoppingItems()

    @Insert
    protected abstract suspend fun insertShoppingItems(rows: List<ShoppingItemEntity>)

    @Query("SELECT * FROM recipe_ratings ORDER BY createdAt, id")
    protected abstract suspend fun allRecipeRatings(): List<RecipeRatingEntity>

    @Query("DELETE FROM recipe_ratings")
    protected abstract suspend fun deleteAllRecipeRatings()

    @Insert
    protected abstract suspend fun insertRecipeRatings(rows: List<RecipeRatingEntity>)

    @Query("SELECT * FROM cycle_periods ORDER BY startDate")
    protected abstract suspend fun allCyclePeriods(): List<CyclePeriodEntity>

    @Query("DELETE FROM cycle_periods")
    protected abstract suspend fun deleteAllCyclePeriods()

    @Insert
    protected abstract suspend fun insertCyclePeriods(rows: List<CyclePeriodEntity>)

    @Query("SELECT * FROM cycle_days ORDER BY date")
    protected abstract suspend fun allCycleDays(): List<CycleDayEntity>

    @Query("DELETE FROM cycle_days")
    protected abstract suspend fun deleteAllCycleDays()

    @Insert
    protected abstract suspend fun insertCycleDays(rows: List<CycleDayEntity>)

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
        pots = allPots(),
        batches = allBatches(),
        batchIngredients = allBatchIngredients(),
        boxes = allBoxes(),
        boxParts = allBoxParts(),
        plans = allPlans(),
        planSlots = allPlanSlots(),
        planCooks = allPlanCooks(),
        pantryItems = allPantryItems(),
        stores = allStores(),
        storeProducts = allStoreProducts(),
        prices = allPrices(),
        shoppingItems = allShoppingItems(),
        recipeRatings = allRecipeRatings(),
        cyclePeriods = allCyclePeriods(),
        cycleDays = allCycleDays(),
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
        snapshot.pots?.let {
            deleteAllPots()
            insertPots(it)
        }
        snapshot.batches?.let {
            deleteAllBatches()
            insertBatches(it)
        }
        snapshot.batchIngredients?.let {
            deleteAllBatchIngredients()
            insertBatchIngredients(it)
        }
        snapshot.boxes?.let {
            deleteAllBoxes()
            insertBoxes(it)
        }
        snapshot.boxParts?.let {
            deleteAllBoxParts()
            insertBoxParts(it)
        }
        snapshot.plans?.let {
            deleteAllPlans()
            insertPlans(it)
        }
        snapshot.planSlots?.let {
            deleteAllPlanSlots()
            insertPlanSlots(it)
        }
        snapshot.planCooks?.let {
            deleteAllPlanCooks()
            insertPlanCooks(it)
        }
        snapshot.pantryItems?.let {
            deleteAllPantryItems()
            insertPantryItems(it)
        }
        snapshot.stores?.let {
            deleteAllStores()
            insertStores(it)
        }
        snapshot.storeProducts?.let {
            deleteAllStoreProducts()
            insertStoreProducts(it)
        }
        snapshot.prices?.let {
            deleteAllPrices()
            insertPrices(it)
        }
        snapshot.shoppingItems?.let {
            deleteAllShoppingItems()
            insertShoppingItems(it)
        }
        snapshot.recipeRatings?.let {
            deleteAllRecipeRatings()
            insertRecipeRatings(it)
        }
        snapshot.cyclePeriods?.let {
            deleteAllCyclePeriods()
            insertCyclePeriods(it)
        }
        snapshot.cycleDays?.let {
            deleteAllCycleDays()
            insertCycleDays(it)
        }
    }
}
