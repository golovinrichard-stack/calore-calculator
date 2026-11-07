package com.calore.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.domain.BoxState
import com.calore.tracker.domain.EntryType
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
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

/** M4: cook a batch, fill boxes, eat a full and a partly eaten box, and undo (M4.7 "done when"). */
@RunWith(AndroidJUnit4::class)
class CookingRepositoryTest {

    private lateinit var db: CaloreDatabase
    private lateinit var foods: FoodRepository
    private lateinit var recipes: RecipeRepository
    private lateinit var cooking: CookingRepository
    private lateinit var diary: DiaryRepository
    private val sunday = LocalDate.of(2026, 11, 8)
    private val clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CaloreDatabase::class.java).build()
        foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(userAgent = "test"), clock)
        recipes = RecipeRepository(db.recipeDao(), db.foodDao(), clock)
        cooking = CookingRepository(db.cookingDao(), clock)
        diary = DiaryRepository(db.diaryDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cookFillEatAndUndo() = runTest {
        // A chili of 3,600 kcal: 3,200 g raw, 3,000 g cooked → 120 kcal per 100 g (A9).
        val mix = foods.getFood(foods.saveCustom(null, "Chili mix", null, Nutrients(kcal = 112.5, protein = 8.0), null))!!
        val recipeId = recipes.save(
            Recipe(id = "", name = "Chili", shortCode = "C", servings = 6),
            listOf(RecipeIngredient("", "", 0, mix.uuid, 3200.0)),
            emptyList(),
        )
        val batch = cooking.saveBatch(recipeId, null, "Chili", listOf(mix to 3200.0), cookedGrams = 3000.0, cookedByMemberId = null)
        assertEquals(120.0, batch.per100g.kcal, 1e-9)
        assertEquals("the recipe learned its yield", 3000.0 / 3200.0, recipes.details(recipeId)!!.recipe.learnedYield!!, 1e-12)

        // Six boxes of 500 g; the last one is planned for Thursday, after the eat-by date → freezer (A13).
        val specs = (1..6).map { day -> BoxSpec(listOf(batch.id to 500.0), ownerMemberId = null, plannedDate = sunday.plusDays(day.toLong())) }
        val boxes = cooking.fillBoxes("C", specs, mapOf(batch.id to 3))
        assertEquals(listOf("C1", "C2", "C3", "C4", "C5", "C6"), boxes.map { it.code })
        assertEquals(LocalDate.of(2026, 11, 11), boxes.first().eatByDate)
        assertEquals(listOf(Storage.FRIDGE, Storage.FRIDGE, Storage.FRIDGE, Storage.FREEZER, Storage.FREEZER, Storage.FREEZER), boxes.map { it.storage })

        // Eat a full box: 600 kcal in today's lunch, the box is EATEN.
        val c1 = cooking.activeBoxes().first().single { it.box.code == "C1" }
        val eaten = cooking.eat(c1, share = null, meal = MealType.LUNCH, date = sunday, ownerIsMe = true)
        val entry = diary.entriesFor(sunday).first().single()
        assertEquals(EntryType.BOX, entry.entryType)
        assertEquals(600.0, entry.nutrients.kcal, 1e-9)
        assertEquals(500.0, entry.grams, 1e-9)
        assertTrue(cooking.activeBoxes().first().none { it.box.code == "C1" })

        // Eat 200 g of another box: 240 kcal, 60 % left.
        val c2 = cooking.activeBoxes().first().single { it.box.code == "C2" }
        cooking.eat(c2, share = 0.4, meal = MealType.DINNER, date = sunday, ownerIsMe = true)
        assertEquals(840.0, diary.entriesFor(sunday).first().sumOf { it.nutrients.kcal }, 1e-9)
        val partly = cooking.activeBoxes().first().single { it.box.code == "C2" }
        assertEquals(BoxState.PART_EATEN, partly.box.state)
        assertEquals(0.6, partly.box.remainingFraction, 1e-12)
        assertEquals(360.0, partly.remainingNutrients.kcal, 1e-9)

        // Undo the first: the entry goes and C1 is back, full.
        cooking.undoEat(eaten)
        assertEquals(240.0, diary.entriesFor(sunday).first().sumOf { it.nutrients.kcal }, 1e-9)
        val back = cooking.activeBoxes().first().single { it.box.code == "C1" }
        assertEquals(BoxState.READY, back.box.state)
        assertEquals(1.0, back.box.remainingFraction, 0.0)
        assertEquals("C7 is next", listOf("C7"), com.calore.tracker.domain.BoxSplitter.nextCodes("C", 1, cooking.activeCodes()))
    }
}
