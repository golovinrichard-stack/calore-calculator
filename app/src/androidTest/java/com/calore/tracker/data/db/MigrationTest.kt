package com.calore.tracker.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.calore.tracker.domain.MealType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.calore.tracker.domain.StableIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Creates databases from the exported schemas in app/schemas, fills them with rows the way an
 * older app version would have, migrates them and checks that nothing was lost.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CaloreDatabase::class.java,
    )

    @Test
    fun version1DatabaseCanBeCreatedAndReadBack() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }

        helper.runMigrationsAndValidate(TEST_DB, 1, true).use { db ->
            db.query("SELECT name FROM foods ORDER BY id").use { cursor ->
                val names = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertEquals(listOf("Oats", "Rice"), names)
            }
            db.query("SELECT COUNT(*) FROM diary_entries").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            db.query("SELECT kg FROM weight_entries").use { cursor ->
                cursor.moveToFirst()
                assertEquals(80.5, cursor.getDouble(0), 0.0)
            }
        }
    }

    @Test
    fun migration1To2KeepsEveryRowAndGivesEachAUniqueUuid() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, *ALL_MIGRATIONS).use { db ->
            for (table in listOf("foods", "diary_entries", "weight_entries")) {
                val uuids = db.strings("SELECT uuid FROM $table")
                assertTrue("$table has rows", uuids.isNotEmpty())
                uuids.forEach { assertTrue("$it in $table looks like a UUID", UUID_V4.matches(it)) }
                assertEquals("uuids in $table are unique", uuids.size, uuids.toSet().size)
            }
            assertEquals(listOf("1000", "2000"), db.strings("SELECT updatedAt FROM foods ORDER BY id"))
            assertEquals(listOf("Oats", "Rice"), db.strings("SELECT name FROM foods ORDER BY id"))
            assertEquals(listOf("Oats (Brand)", "Soup"), db.strings("SELECT name FROM diary_entries ORDER BY id"))
            assertEquals(listOf("80.5"), db.strings("SELECT kg FROM weight_entries"))
        }
    }

    @Test
    fun migratedDatabaseOpensInTheAppAndKeepsHistory() = runTest {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }

        val room = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CaloreDatabase::class.java,
            TEST_DB,
        ).addMigrations(*ALL_MIGRATIONS).build()
        helper.closeWhenFinished(room)

        val oats = room.foodDao().getById(1)!!.toDomain()
        assertEquals("Oats", oats.name)
        assertEquals(370.0, oats.per100g.kcal, 0.0)
        assertEquals(40.0, oats.servingGrams!!, 0.0)

        val day = LocalDate.of(2026, 10, 21)
        val entries = room.diaryDao().forDate(day.toString()).first().mapNotNull { it.toDomain() }
        assertEquals(listOf(MealType.BREAKFAST, MealType.DINNER), entries.map { it.meal })
        assertEquals(185.0, entries[0].nutrients.kcal, 0.0)
        assertNull("unknown stays unknown", entries[1].nutrients.protein)
        assertTrue(entries.all { UUID_V4.matches(it.uuid) })

        val weight = room.weightDao().latest()!!.toDomain()!!
        assertEquals(80.5, weight.kg, 0.0)
    }

    @Test
    fun migration2To3AddsProfileTablesAndKeepsTheDiary() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, *ALL_MIGRATIONS).close()

        helper.runMigrationsAndValidate(TEST_DB, 3, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Oats (Brand)", "Soup"), db.strings("SELECT name FROM diary_entries ORDER BY id"))
            assertEquals("diary entries belong to the phone's user", listOf<String?>(null, null), db.nullableStrings("SELECT memberId FROM diary_entries"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM members"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM body_measurements"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM checkins"))
        }
    }

    @Test
    fun migration3To4AddsBasicFoodColumnsWithDefaults() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 3, true, *ALL_MIGRATIONS).close()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Oats", "Rice"), db.strings("SELECT name FROM foods ORDER BY id"))
            assertEquals(listOf("OTHER", "OTHER"), db.strings("SELECT section FROM foods ORDER BY id"))
            assertEquals(listOf("[]", "[]"), db.strings("SELECT allergens FROM foods ORDER BY id"))
            assertEquals(listOf("0", "0"), db.strings("SELECT isGeneric FROM foods ORDER BY id"))
            assertEquals(listOf("FOOD", "FOOD"), db.strings("SELECT entryType FROM diary_entries ORDER BY id"))
            assertEquals(listOf<String?>(null, null), db.nullableStrings("SELECT ironMg FROM diary_entries ORDER BY id"))
        }
    }

    @Test
    fun migration4To5AddsRecipeTablesAndKeepsTheDiary() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 4, true, *ALL_MIGRATIONS).close()

        helper.runMigrationsAndValidate(TEST_DB, 5, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Oats", "Rice"), db.strings("SELECT name FROM foods ORDER BY id"))
            assertEquals(listOf<String?>(null, null), db.nullableStrings("SELECT recipeId FROM diary_entries ORDER BY id"))
            assertEquals(listOf<String?>(null, null), db.nullableStrings("SELECT servings FROM diary_entries ORDER BY id"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM recipes"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM recipe_ingredients"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM recipe_steps"))
        }
    }

    @Test
    fun migration5To6AddsCookingTablesAndKeepsTheDiary() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, *ALL_MIGRATIONS).close()

        helper.runMigrationsAndValidate(TEST_DB, 6, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Oats", "Rice"), db.strings("SELECT name FROM foods ORDER BY id"))
            assertEquals(listOf<String?>(null, null), db.nullableStrings("SELECT boxId FROM diary_entries ORDER BY id"))
            for (table in listOf("pots", "batches", "batch_ingredients", "boxes", "box_parts")) {
                assertEquals(table, listOf("0"), db.strings("SELECT COUNT(*) FROM $table"))
            }
        }
    }

    @Test
    fun migration6To7AddsPlanPantryAndShopTables() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, *ALL_MIGRATIONS).close()

        helper.runMigrationsAndValidate(TEST_DB, 7, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Oats", "Rice"), db.strings("SELECT name FROM foods ORDER BY id"))
            for (table in listOf("plans", "plan_slots", "plan_cooks", "pantry_items", "stores", "store_products", "prices", "shopping_items")) {
                assertEquals(table, listOf("0"), db.strings("SELECT COUNT(*) FROM $table"))
            }
        }
    }

    @Test
    fun migration7To8AddsRecipeRatings() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 7, true, *ALL_MIGRATIONS).close()

        helper.runMigrationsAndValidate(TEST_DB, 8, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Oats", "Rice"), db.strings("SELECT name FROM foods ORDER BY id"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM recipe_ratings"))
            db.execSQL("INSERT INTO recipe_ratings (id, memberId, recipeId, rating, createdAt, updatedAt) VALUES ('r1', 'm', 'x', 1, 1, 1)")
            // One rating per person and recipe.
            val duplicate = runCatching {
                db.execSQL("INSERT INTO recipe_ratings (id, memberId, recipeId, rating, createdAt, updatedAt) VALUES ('r2', 'm', 'x', -1, 2, 2)")
            }
            assertEquals(true, duplicate.isFailure)
        }
    }

    @Test
    fun migration8To9AddsCycleTables() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 8, true, *ALL_MIGRATIONS).close()

        helper.runMigrationsAndValidate(TEST_DB, 9, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Oats", "Rice"), db.strings("SELECT name FROM foods ORDER BY id"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM cycle_periods"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM cycle_days"))
        }
    }

    @Test
    fun migration9To10AddsSupplements() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 9, true, *ALL_MIGRATIONS).close()

        helper.runMigrationsAndValidate(TEST_DB, 10, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Oats", "Rice"), db.strings("SELECT name FROM foods ORDER BY id"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM supplements"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM supplement_logs"))
        }
    }

    @Test
    fun migration10To11AddsResearchReports() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 10, true, *ALL_MIGRATIONS).close()

        helper.runMigrationsAndValidate(TEST_DB, 11, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Oats", "Rice"), db.strings("SELECT name FROM foods ORDER BY id"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM research_reports"))
        }
    }

    @Test
    fun migration11To12AddsHouseholdTablesAndGivesNaturalKeysTheSameIdEverywhere() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertVersion1Rows(db) }
        helper.runMigrationsAndValidate(TEST_DB, 11, true, *ALL_MIGRATIONS).use { db ->
            val oats = db.strings("SELECT uuid FROM foods WHERE id = 1").single()
            db.execSQL("UPDATE foods SET `key` = 'generic:rice' WHERE id = 2")
            val rice = db.strings("SELECT uuid FROM foods WHERE id = 2").single()
            db.execSQL(
                "INSERT INTO recipes (id, `key`, name, userEdited, shortCode, servings, mealTypes, tags, equipment, fridgeDays, freezable, " +
                    "freezerMonths, mealPrepFriendly, learnedYieldCount, visibility, createdAt, updatedAt) " +
                    "VALUES ('r-old', 'starter:cz:rice', 'Rýže', 0, 'R', 2, '[]', '[]', '[]', 3, 0, 3, 0, 0, 'SHARED', 1, 1)",
            )
            db.execSQL("INSERT INTO recipe_ingredients (id, recipeId, position, foodUuid, grams, optional) VALUES ('i-old', 'r-old', 0, '$rice', 100, 0)")
            db.execSQL("INSERT INTO recipe_steps (id, recipeId, position, text, passive) VALUES ('s-old', 'r-old', 0, 'Vařit', 0)")
            db.execSQL("INSERT INTO plans (id, weekStart, createdAt, updatedAt) VALUES ('p-old', '2026-11-09', 1, 1)")
            db.execSQL(
                "INSERT INTO plan_slots (id, planId, date, meal, memberId, kind, recipeId, locked, done, createdAt, updatedAt) " +
                    "VALUES ('slot', 'p-old', '2026-11-09', 'LUNCH', 'me', 'BOX', 'r-old', 0, 0, 1, 1)",
            )
            db.execSQL("INSERT INTO stores (id, `key`, name, sectionOrder, favorite, defaultForSections, createdAt, updatedAt) VALUES ('st-old', 'cz:lidl', 'Lidl', '[]', 0, '[]', 1, 1)")
            db.execSQL(
                "INSERT INTO shopping_items (id, foodUuid, storeId, name, section, manual, createdAt, updatedAt) " +
                    "VALUES ('line', '$oats', 'st-old', 'Oats', 'GRAINS', 0, 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 12, true, *ALL_MIGRATIONS).use { db ->
            val oats = StableIds.barcode("123")
            val rice = StableIds.food("generic:rice")
            val recipe = StableIds.recipe("starter:cz:rice")
            assertEquals(listOf(oats, rice), db.strings("SELECT uuid FROM foods ORDER BY id"))
            assertEquals(listOf(recipe), db.strings("SELECT id FROM recipes"))
            assertEquals(listOf(StableIds.recipeIngredient("starter:cz:rice", 0) + " " + recipe + " " + rice), db.strings("SELECT id || ' ' || recipeId || ' ' || foodUuid FROM recipe_ingredients"))
            assertEquals(listOf(StableIds.recipeStep("starter:cz:rice", 0)), db.strings("SELECT id FROM recipe_steps"))
            assertEquals(listOf(StableIds.plan("2026-11-09")), db.strings("SELECT id FROM plans"))
            assertEquals(listOf(StableIds.plan("2026-11-09") + " " + recipe), db.strings("SELECT planId || ' ' || recipeId FROM plan_slots"))
            assertEquals(listOf(StableIds.store("cz:lidl")), db.strings("SELECT id FROM stores"))
            assertEquals(listOf(StableIds.store("cz:lidl") + " " + oats), db.strings("SELECT storeId || ' ' || foodUuid FROM shopping_items"))
            // The diary refers to foods by number and is untouched.
            assertEquals(listOf("1"), db.strings("SELECT foodId FROM diary_entries WHERE foodId IS NOT NULL"))
            for (table in listOf("household", "household_devices", "requests", "activity", "sync_ops", "sync_field_clock", "sync_peers", "sync_changes", "sync_state")) {
                assertEquals(table, listOf("0"), db.strings("SELECT COUNT(*) FROM $table"))
            }
        }
    }

    private fun SupportSQLiteDatabase.nullableStrings(sql: String): List<String?> = query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(if (cursor.isNull(0)) null else cursor.getString(0)) }
    }

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> = query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private fun insertVersion1Rows(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO foods (id, name, brand, barcode, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g,
                fiberPer100g, sugarPer100g, servingGrams, source, lastUsedAt)
            VALUES (1, 'Oats', 'Brand', '123', 370.0, 13.0, 60.0, 7.0, 10.0, 1.0, 40.0, 'OPEN_FOOD_FACTS', 1000),
                   (2, 'Rice', NULL, NULL, 130.0, 2.7, 28.0, 0.3, NULL, NULL, NULL, 'CUSTOM', 2000)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO diary_entries (id, date, meal, foodId, name, grams, kcal, protein, carbs, fat, fiber, sugar, createdAt)
            VALUES (1, '2026-10-21', 'BREAKFAST', 1, 'Oats (Brand)', 50.0, 185.0, 6.5, 30.0, 3.5, 5.0, 0.5, 10),
                   (2, '2026-10-21', 'DINNER', NULL, 'Soup', 300.0, 240.0, NULL, NULL, NULL, NULL, NULL, 20)
            """.trimIndent(),
        )
        db.execSQL("INSERT INTO weight_entries (id, date, kg) VALUES (1, '2026-10-21', 80.5)")
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
