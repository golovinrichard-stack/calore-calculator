package com.calore.tracker.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.calore.tracker.domain.StableIds

/**
 * Part of migration 11 → 12 (M11): rows with a natural key get the same id on every phone
 * ([StableIds]), and every column that refers to them follows. When the new id is taken already
 * (two rows for the same thing), the references move to that row and the extra row is removed.
 */
object StableIdMigration {

    /** Columns that hold a food's uuid. */
    private val FOOD_REFS = listOf(
        "recipe_ingredients" to "foodUuid", "batch_ingredients" to "foodUuid", "pantry_items" to "foodUuid",
        "store_products" to "foodUuid", "shopping_items" to "foodUuid",
    )
    private val RECIPE_REFS = listOf(
        "recipe_ingredients" to "recipeId", "recipe_steps" to "recipeId", "recipe_ratings" to "recipeId", "batches" to "recipeId",
        "plan_slots" to "recipeId", "plan_cooks" to "recipeId", "diary_entries" to "recipeId",
    )
    private val STORE_REFS = listOf("store_products" to "storeId", "shopping_items" to "storeId")
    private val PRODUCT_REFS = listOf("prices" to "storeProductId", "pantry_items" to "storeProductId", "shopping_items" to "storeProductId")
    private val PLAN_REFS = listOf("plan_slots" to "planId", "plan_cooks" to "planId")

    fun run(db: SupportSQLiteDatabase) {
        remap(db, "foods", "uuid", "SELECT uuid, `key` FROM foods WHERE `key` IS NOT NULL", FOOD_REFS) { StableIds.food(it) }
        remap(db, "foods", "uuid", "SELECT uuid, barcode FROM foods WHERE `key` IS NULL AND barcode IS NOT NULL AND barcode != ''", FOOD_REFS) { StableIds.barcode(it) }
        // Ingredients and steps of starter recipes first, while the recipe ids are still the old ones.
        pairs(db, "SELECT i.id, r.`key`, i.position FROM recipe_ingredients i JOIN recipes r ON r.id = i.recipeId WHERE r.`key` IS NOT NULL") { c ->
            c.getString(0) to StableIds.recipeIngredient(c.getString(1), c.getInt(2))
        }.forEach { (old, new) -> move(db, "recipe_ingredients", "id", old, new, emptyList()) }
        pairs(db, "SELECT s.id, r.`key`, s.position FROM recipe_steps s JOIN recipes r ON r.id = s.recipeId WHERE r.`key` IS NOT NULL") { c ->
            c.getString(0) to StableIds.recipeStep(c.getString(1), c.getInt(2))
        }.forEach { (old, new) -> move(db, "recipe_steps", "id", old, new, emptyList()) }
        remap(db, "recipes", "id", "SELECT id, `key` FROM recipes WHERE `key` IS NOT NULL", RECIPE_REFS) { StableIds.recipe(it) }
        remap(db, "stores", "id", "SELECT id, `key` FROM stores WHERE `key` IS NOT NULL", STORE_REFS) { StableIds.store(it) }
        remap(db, "store_products", "id", "SELECT id, `key` FROM store_products WHERE `key` IS NOT NULL", PRODUCT_REFS) { StableIds.product(it) }
        remap(db, "plans", "id", "SELECT id, weekStart FROM plans WHERE weekStart IS NOT NULL", PLAN_REFS) { StableIds.plan(it) }
    }

    private fun remap(
        db: SupportSQLiteDatabase,
        table: String,
        idColumn: String,
        select: String,
        refs: List<Pair<String, String>>,
        stable: (String) -> String,
    ) {
        pairs(db, select) { c -> c.getString(0) to stable(c.getString(1)) }.forEach { (old, new) -> move(db, table, idColumn, old, new, refs) }
    }

    private fun pairs(db: SupportSQLiteDatabase, select: String, map: (android.database.Cursor) -> Pair<String, String>): List<Pair<String, String>> =
        db.query(select).use { c -> buildList { while (c.moveToNext()) add(map(c)) } }

    private fun move(db: SupportSQLiteDatabase, table: String, idColumn: String, old: String, new: String, refs: List<Pair<String, String>>) {
        if (old == new) return
        val taken = db.query("SELECT COUNT(*) FROM `$table` WHERE `$idColumn` = ?", arrayOf(new)).use { it.moveToFirst() && it.getInt(0) > 0 }
        if (taken) {
            db.execSQL("DELETE FROM `$table` WHERE `$idColumn` = ?", arrayOf(old))
        } else {
            db.execSQL("UPDATE `$table` SET `$idColumn` = ? WHERE `$idColumn` = ?", arrayOf(new, old))
        }
        refs.forEach { (refTable, column) -> db.execSQL("UPDATE `$refTable` SET `$column` = ? WHERE `$column` = ?", arrayOf(new, old)) }
    }
}
