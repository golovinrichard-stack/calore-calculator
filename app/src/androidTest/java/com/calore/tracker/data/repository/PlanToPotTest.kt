package com.calore.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.domain.MealTarget
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.PlanCooks
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** M5.4 and M4.7: plan → cook → boxes linked to slots → eating marks the slot done, undo clears it. */
@RunWith(AndroidJUnit4::class)
class PlanToPotTest {

    private lateinit var db: CaloreDatabase
    private val sunday = LocalDate.of(2026, 11, 8)
    private val clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CaloreDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun planCookFillAndEat() = runTest {
        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(userAgent = "test"), clock)
        val recipes = RecipeRepository(db.recipeDao(), db.foodDao(), clock)
        val cooking = CookingRepository(db.cookingDao(), clock)
        val plans = PlanRepository(db.planDao(), clock)
        val mix = foods.getFood(foods.saveCustom(null, "Chili mix", null, Nutrients(kcal = 112.5, protein = 8.0), null))!!
        val recipeId = recipes.save(
            Recipe(id = "", name = "Chili", shortCode = "C", servings = 6, learnedYield = 3000.0 / 3200.0),
            listOf(RecipeIngredient("", "", 0, mix.uuid, 3200.0)),
            emptyList(),
        )
        val me = newId()
        val planId = plans.planFor(sunday.plusDays(1))
        val target = MealTarget(600.0, 45.0, 60.0, 20.0)
        plans.setSlot(planId, sunday.plusDays(1), MealType.LUNCH, me, SlotKind.BOX, recipeId, target)
        plans.setSlot(planId, sunday.plusDays(2), MealType.LUNCH, me, SlotKind.BOX, recipeId, target)

        val details = recipes.details(recipeId)!!
        val cook = PlanCooks.compute(
            plans.slots(planId).first(),
            mapOf(recipeId to (details.recipe to details.nutrition)),
            listOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
            setOf(MealType.LUNCH, MealType.DINNER),
        ).single()
        assertEquals(sunday, cook.date)
        assertEquals(1000.0, cook.targetCookedGrams, 1e-9)

        val batch = cooking.saveBatch(recipeId, details.recipe.learnedYield, "Chili", listOf(mix to 1066.7), cookedGrams = 1000.0, cookedByMemberId = me)
        val boxes = cooking.fillBoxes(
            "C",
            cook.slots.map { BoxSpec(listOf(batch.id to 500.0), me, it.date, it.meal, it.id) },
            mapOf(batch.id to 3),
        )
        plans.linkBoxes(boxes.map { it.planSlotId!! to it.id })
        plans.saveCooked(planId, cook, batch.id)
        assertEquals(setOf(sunday to recipeId), plans.cooked(planId).first())
        assertEquals(boxes.map { it.id }.toSet(), plans.slots(planId).first().mapNotNull { it.boxId }.toSet())

        val view = cooking.activeBoxes().first().first { it.box.id == boxes.first().id }
        val eaten = cooking.eat(view, null, MealType.LUNCH, sunday.plusDays(1), ownerIsMe = true)
        assertTrue(plans.slots(planId).first().single { it.boxId == view.box.id }.done)
        cooking.undoEat(eaten)
        assertFalse(plans.slots(planId).first().single { it.boxId == view.box.id }.done)

        // M5.11: moving the box a day later frees its old slot and plans it on the new day.
        val oldSlot = plans.slots(planId).first().single { it.boxId == view.box.id }
        val newDay = oldSlot.date.plusDays(1)
        cooking.move(view.box, newDay, oldSlot.meal)
        plans.freeSlot(oldSlot.id)
        plans.planBox(newDay, oldSlot.meal, oldSlot.memberId, oldSlot.recipeId, oldSlot.target, view.box.id)
        val slots = plans.slots(planId).first()
        assertEquals(SlotKind.FREE, slots.single { it.id == oldSlot.id }.kind)
        assertEquals(view.box.id, slots.single { it.date == newDay && it.meal == oldSlot.meal }.boxId)
        assertEquals(newDay, cooking.activeBoxes().first().single { it.box.id == view.box.id }.box.plannedDate)
    }
}
