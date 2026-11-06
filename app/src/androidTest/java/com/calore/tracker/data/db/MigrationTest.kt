package com.calore.tracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    private fun insertVersion1Rows(db: androidx.sqlite.db.SupportSQLiteDatabase) {
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
    }
}
