package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TodayWidgetModelTest {

    private val today = LocalDate.of(2026, 11, 9)
    private val targets = NutritionGoals(kcal = 2000, proteinG = 150, carbsG = 200, fatG = 70)

    private fun box(id: String, code: String, eatBy: LocalDate = today.plusDays(2), owner: String? = "me", storage: Storage = Storage.FRIDGE) =
        BoxView(Box(id, code, owner, storage, BoxState.READY, 1.0, eatBy), emptyList())

    private fun slot(meal: MealType, kind: SlotKind = SlotKind.BOX, boxId: String? = null, done: Boolean = false, member: String = "me", recipe: String? = "curry") =
        PlanSlot("s-$meal-$member", "p", today, meal, member, kind, recipe, boxId, done = done)

    @Test
    fun theNextMealIsTheOneForNowOrLaterAndTheNextBoxHasAnAteIt() {
        val slots = listOf(
            slot(MealType.BREAKFAST, SlotKind.COOK_FRESH, recipe = "oats"),
            slot(MealType.LUNCH, boxId = "b1"),
            slot(MealType.DINNER, boxId = "b2"),
            slot(MealType.DINNER, boxId = "kid-box", member = "kid"),
        )
        val boxes = listOf(box("b1", "C1"), box("b2", "C2"))
        val names = mapOf("oats" to "Oats", "curry" to "Curry")
        val atNoon = TodayWidgetModel.build(today, LocalTime.NOON, "me", Nutrients(kcal = 900.0, protein = 60.0), targets, slots, boxes, names)
        assertEquals(MealType.LUNCH, atNoon.next!!.slot.meal)
        assertEquals("C1", atNoon.next!!.label)
        assertEquals("b1", atNoon.nextBox!!.box!!.box.id)
        assertEquals(0.45f, atNoon.kcalProgress, 0.0001f)

        val morning = TodayWidgetModel.build(today, LocalTime.of(7, 0), "me", Nutrients(kcal = 0.0), targets, slots, boxes, names)
        assertEquals("Oats", morning.next!!.label)
        assertEquals("b1", morning.nextBox!!.box!!.box.id)

        // Lunch eaten: dinner is next.
        val afterLunch = TodayWidgetModel.build(today, LocalTime.of(13, 0), "me", Nutrients(kcal = 1200.0), targets, slots.map { if (it.meal == MealType.LUNCH) it.copy(done = true) else it }, boxes, names)
        assertEquals("C2", afterLunch.next!!.label)
    }

    @Test
    fun boxesToEatTodayAreMyFridgeBoxesDueTodayOrEarlier() {
        val boxes = listOf(
            box("a", "A1", eatBy = today),
            box("b", "B1", eatBy = today.minusDays(1)),
            box("c", "C1", eatBy = today.plusDays(1)),
            box("d", "D1", eatBy = today, storage = Storage.FREEZER),
            box("e", "E1", eatBy = today, owner = "kid"),
            box("f", "F1", eatBy = today, owner = null),
        )
        val data = TodayWidgetModel.build(today, LocalTime.NOON, "me", Nutrients(kcal = 0.0), targets, emptyList(), boxes, emptyMap())
        assertEquals(3, data.boxesToEat)
        assertNull(data.next)
        assertNull(data.nextBox)
    }

    @Test
    fun ringIsCappedAtFull() {
        val data = TodayWidgetModel.build(today, LocalTime.NOON, "me", Nutrients(kcal = 2600.0), targets, emptyList(), emptyList(), emptyMap())
        assertEquals(1f, data.kcalProgress, 0f)
    }
}
