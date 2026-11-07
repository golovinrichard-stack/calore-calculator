package com.calore.tracker.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.db.DatabaseSnapshot
import com.calore.tracker.data.repository.DiaryRepository
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
import com.calore.tracker.domain.RecipeStep
import com.calore.tracker.domain.newId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Export, wipe, import: every row must come back identical, uuids included (M0.5). */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var db: CaloreDatabase
    private lateinit var backups: BackupRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-06T18:00:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 11, 6)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        backups = BackupRepository(db.backupDao(), context.contentResolver, clock, appVersion = "test")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun fillDatabase(diaryEntries: Int) {
        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(userAgent = "test"), clock)
        val oatsId = foods.saveCustom(null, "Ovesné vločky", "Emco", Nutrients(370.0, 13.0, 60.0, 7.0, 10.0, null), 40.0)
        val oats = foods.getFood(oatsId)!!
        foods.saveCustom(null, "Polévka", null, Nutrients(kcal = 45.0), null)
        val diary = DiaryRepository(db.diaryDao(), clock)
        repeat(diaryEntries) { i ->
            diary.log(oats, grams = 40.0 + i % 50, meal = MealType.entries[i % 4], date = day.minusDays(i / 10L))
        }
        RecipeRepository(db.recipeDao(), db.foodDao(), clock).save(
            Recipe(id = "", name = "Kaše", shortCode = "K", servings = 2, learnedYield = 2.5),
            listOf(RecipeIngredient(id = "", recipeId = "", position = 0, foodUuid = oats.uuid, grams = 120.0, note = "jemné")),
            listOf(RecipeStep(id = "", recipeId = "", position = 0, text = "Vařte 5 minut.", minutes = 5, passive = true)),
        )
        val weights = WeightRepository(db.weightDao())
        weights.save(day, 80.4)
        weights.save(day.minusDays(1), 80.9)
    }

    private suspend fun exportBytes(): ByteArray = ByteArrayOutputStream().also { backups.export(it) }.toByteArray()

    @Test
    fun exportWipeImportGivesIdenticalRows() = runTest {
        fillDatabase(diaryEntries = 25)
        val before = db.backupDao().snapshot()
        val bytes = exportBytes()

        db.backupDao().replaceAll(DatabaseSnapshot(emptyList(), emptyList(), emptyList()))
        assertTrue(db.backupDao().snapshot().diaryEntries.isEmpty())

        val read = backups.read(ByteArrayInputStream(bytes)) as BackupReadResult.Ok
        assertEquals(BackupCounts(foods = 2, diaryEntries = 25, weightEntries = 2), read.backup.tables.counts)
        assertEquals("2026-11-06T18:00:00Z", read.backup.exportedAt)
        backups.restore(read.backup)

        assertEquals(before, db.backupDao().snapshot())
    }

    @Test
    fun tenThousandDiaryEntriesExportAndImport() = runTest {
        fillDatabase(diaryEntries = 10_000)
        val before = db.backupDao().snapshot()

        val read = backups.read(ByteArrayInputStream(exportBytes())) as BackupReadResult.Ok
        assertEquals(10_000, read.backup.tables.counts.diaryEntries)
        backups.restore(read.backup)

        assertEquals(before, db.backupDao().snapshot())
    }

    @Test
    fun brokenOrForeignFilesChangeNothing() = runTest {
        fillDatabase(diaryEntries = 3)
        val before = db.backupDao().snapshot()
        val good = exportBytes()

        assertEquals(BackupReadResult.NotABackup, backups.read(ByteArrayInputStream("""{"hello":1}""".toByteArray())))
        assertEquals(BackupReadResult.Damaged, backups.read(ByteArrayInputStream(good.copyOf(good.size / 2))))
        assertEquals(before, db.backupDao().snapshot())
    }

    @Test
    fun aFailingImportLeavesTheOldDataInPlace() = runTest {
        fillDatabase(diaryEntries = 3)
        val before = db.backupDao().snapshot()
        val entry = before.diaryEntries.first()
        val clash = DatabaseSnapshot(
            foods = emptyList(),
            diaryEntries = listOf(entry.copy(uuid = newId()), entry.copy(uuid = newId())),
            weightEntries = emptyList(),
        )

        try {
            db.backupDao().replaceAll(clash)
            fail("Two rows with the same id must be refused")
        } catch (expected: Exception) {
            // The whole import is one transaction.
        }
        assertEquals(before, db.backupDao().snapshot())
    }

    @Test
    fun defaultFileNameHasTheDate() {
        assertEquals("calore-backup-2026-11-06.json", backups.defaultFileName())
    }

    @Test
    fun importedFoodsKeepWorking() = runTest {
        fillDatabase(diaryEntries = 1)
        val read = backups.read(ByteArrayInputStream(exportBytes())) as BackupReadResult.Ok
        backups.restore(read.backup)

        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(userAgent = "test"), clock)
        val oats = foods.searchLocal("vločky").single()
        assertEquals("Emco", oats.brand)
        val next = Food(0, "Rýže", null, null, Nutrients(130.0), null, FoodSource.CUSTOM, newId())
        DiaryRepository(db.diaryDao(), clock).log(next, 100.0, MealType.DINNER, day)
    }
}
