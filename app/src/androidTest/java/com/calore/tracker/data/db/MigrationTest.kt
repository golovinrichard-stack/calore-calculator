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
