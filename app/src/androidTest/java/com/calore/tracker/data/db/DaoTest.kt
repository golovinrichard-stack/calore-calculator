package com.calore.tracker.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.repository.DiaryRepository
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: CaloreDatabase
    private val day = LocalDate.of(2026, 10, 21)
    private val clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CaloreDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun food(name: String, barcode: String? = null, kcal: Double = 100.0, lastUsedAt: Long = 0) = FoodEntity(
        name = name,
        brand = null,
        barcode = barcode,
        kcalPer100g = kcal,
        proteinPer100g = 10.0,
        carbsPer100g = null,
        fatPer100g = 1.0,
        fiberPer100g = null,
        sugarPer100g = null,
        servingGrams = null,
        source = FoodSource.CUSTOM.name,
        lastUsedAt = lastUsedAt,
    )

    @Test
    fun upsertByBarcodeUpdatesInsteadOfDuplicating() = runTest {
        val dao = db.foodDao()
        val firstId = dao.upsertByBarcode(food("Oats", barcode = "123", kcal = 370.0))
        val secondId = dao.upsertByBarcode(food("Oats (new label)", barcode = "123", kcal = 375.0))

        assertEquals(firstId, secondId)
        val stored = dao.getById(firstId)
        assertEquals("Oats (new label)", stored?.name)
        assertEquals(375.0, stored!!.kcalPer100g, 0.0)
    }

    @Test
    fun foodsWithoutBarcodeAreAlwaysInserted() = runTest {
        val dao = db.foodDao()
        val a = dao.upsertByBarcode(food("Homemade soup"))
        val b = dao.upsertByBarcode(food("Homemade soup"))

        assertTrue(a != b)
    }

    @Test
    fun searchTreatsWildcardsLiterally() = runTest {
        val dao = db.foodDao()
        dao.insert(food("100% juice"))
        dao.insert(food("Apple juice"))
        dao.insert(food("snake_case bar"))

        assertEquals(listOf("100% juice"), dao.search("%\\%%", 10).map { it.name })
        assertEquals(listOf("snake_case bar"), dao.search("%\\_%", 10).map { it.name })
        assertEquals(2, dao.search("%juice%", 10).size)
        assertEquals("LIKE is case-insensitive for ASCII", 2, dao.search("%JUICE%", 10).size)
    }

    @Test
    fun recentFoodsAreOrderedByLastUse() = runTest {
        val dao = db.foodDao()
        val old = dao.insert(food("Old", lastUsedAt = 1))
        dao.insert(food("Newer", lastUsedAt = 2))
        dao.touch(old, 3)

        assertEquals(listOf("Old", "Newer"), dao.recent(10).first().map { it.name })
    }

    @Test
    fun diaryKeepsSnapshotWhenFoodIsDeleted() = runTest {
        val repo = DiaryRepository(db.diaryDao(), clock)
        val foodId = db.foodDao().insert(food("Rice", kcal = 130.0))
        val rice = Food(
            id = foodId,
            name = "Rice",
            brand = null,
            barcode = null,
            per100g = Nutrients(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3),
            servingGrams = null,
            source = FoodSource.CUSTOM,
        )

        repo.log(rice, grams = 200.0, meal = MealType.DINNER, date = day)
        db.foodDao().delete(foodId)

        val entries = repo.entriesFor(day).first()
        assertEquals(1, entries.size)
        assertEquals(260.0, entries[0].nutrients.kcal, 1e-9)
        assertEquals(56.0, entries[0].nutrients.carbs!!, 1e-9)
        assertEquals(MealType.DINNER, entries[0].meal)
        assertNull(db.foodDao().getById(foodId))
    }

    @Test
    fun changingGramsScalesNutrientsAndUndoRestores() = runTest {
        val repo = DiaryRepository(db.diaryDao(), clock)
        val bread = Food(1, "Bread", null, null, Nutrients(kcal = 250.0, protein = 9.0), null, FoodSource.CUSTOM)
        repo.log(bread, grams = 50.0, meal = MealType.BREAKFAST, date = day)
        val logged = repo.entriesFor(day).first().single()

        repo.changeGrams(logged, 100.0)
        val doubled = repo.entriesFor(day).first().single()
        assertEquals(250.0, doubled.nutrients.kcal, 1e-9)
        assertEquals(9.0, doubled.nutrients.protein!!, 1e-9)

        repo.delete(doubled)
        assertTrue(repo.entriesFor(day).first().isEmpty())
        repo.restore(doubled)
        assertEquals(doubled, repo.entriesFor(day).first().single())
    }

    @Test
    fun entriesAreScopedToTheirDay() = runTest {
        val repo = DiaryRepository(db.diaryDao(), clock)
        val apple = Food(1, "Apple", null, null, Nutrients(kcal = 52.0), null, FoodSource.CUSTOM)
        repo.log(apple, 100.0, MealType.SNACK, day)
        repo.log(apple, 100.0, MealType.SNACK, day.plusDays(1))

        assertEquals(1, repo.entriesFor(day).first().size)
        assertEquals(1, repo.entriesFor(day.plusDays(1)).first().size)
        assertTrue(repo.entriesFor(day.minusDays(1)).first().isEmpty())
    }

    @Test
    fun weightIsOnePerDay() = runTest {
        val dao = db.weightDao()
        dao.upsert(WeightEntryEntity(date = "2026-10-20", kg = 80.0))
        dao.upsert(WeightEntryEntity(date = "2026-10-21", kg = 79.5))
        dao.upsert(WeightEntryEntity(date = "2026-10-21", kg = 79.0))

        val all = dao.all().first()
        assertEquals(listOf("2026-10-20", "2026-10-21"), all.map { it.date })
        assertEquals(79.0, all.last().kg, 0.0)
        assertEquals(79.0, dao.latest()!!.kg, 0.0)
    }
}
