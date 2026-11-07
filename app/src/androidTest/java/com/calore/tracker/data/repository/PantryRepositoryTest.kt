package com.calore.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.PantryState
import com.calore.tracker.domain.Storage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** M5.5 "done when": 3 packs of chicken add 1,500 g; cooking 1,150 g leaves 350 g. */
@RunWith(AndroidJUnit4::class)
class PantryRepositoryTest {

    private lateinit var db: CaloreDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CaloreDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun threePacksThenCooking() = runTest {
        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(userAgent = "test"), clock)
        val pantry = PantryRepository(db.pantryDao(), db.foodDao(), clock)
        val chicken = foods.getFood(foods.saveCustom(null, "Kuřecí prsa", null, Nutrients(kcal = 120.0), null))!!

        // Ticking 3 packs of 500 g (M5.7) adds them like this.
        repeat(3) { pantry.add(chicken, chicken.displayName(), 500.0, bestBefore = LocalDate.of(2026, 11, 9 + it)) }
        assertEquals(1500.0, pantry.available().first().sumOf { it.item.amount }, 0.0)

        val short = pantry.takeForCooking(listOf(chicken to 1150.0))
        assertTrue(short.isEmpty())
        val left = pantry.available().first()
        assertEquals(350.0, left.sumOf { it.item.amount }, 1e-9)
        assertEquals("the latest pack is the one left", LocalDate.of(2026, 11, 11), left.single().item.bestBeforeDate)

        assertEquals("the pantry was short", listOf(chicken), pantry.takeForCooking(listOf(chicken to 500.0)))
        assertTrue(pantry.available().first().isEmpty())
    }

    @Test
    fun defaultsAndActions() = runTest {
        val pantry = PantryRepository(db.pantryDao(), db.foodDao(), clock)
        val item = pantry.add(null, "Toaletní papír", 8.0)
        assertEquals("without a food: cupboard and the 180-day default (A17)", Storage.CUPBOARD, item.storage)
        assertEquals(LocalDate.of(2027, 5, 6), item.bestBeforeDate)
        pantry.throwAway(item)
        assertEquals(PantryState.WASTED, pantry.wasted().first().single().state)
    }
}
