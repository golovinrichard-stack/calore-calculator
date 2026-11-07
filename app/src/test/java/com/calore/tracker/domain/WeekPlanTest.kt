package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/** M5.2 and M5.3: cooking sessions (A14) and the plan's daily totals. */
class WeekPlanTest {

    private val lunchDinner = setOf(MealType.LUNCH, MealType.DINNER)

    @Test
    fun a14DefaultDays() {
        assertEquals(listOf(SUNDAY), CookingSessions.days(1, emptyList()))
        assertEquals(listOf(SUNDAY, WEDNESDAY), CookingSessions.days(2, emptyList()))
        assertEquals(listOf(SUNDAY, TUESDAY, THURSDAY), CookingSessions.days(3, emptyList()))
        assertEquals(listOf(SUNDAY, TUESDAY, THURSDAY, SATURDAY), CookingSessions.days(4, emptyList()))
        assertEquals(listOf(SUNDAY, MONDAY, WEDNESDAY, THURSDAY, FRIDAY), CookingSessions.days(5, emptyList()))
        assertEquals(6, CookingSessions.days(6, emptyList()).size)
        assertTrue(SATURDAY !in CookingSessions.days(6, emptyList()))
        assertEquals(7, CookingSessions.days(7, emptyList()).size)
        assertEquals("picked days are used when there are exactly as many", listOf(MONDAY, THURSDAY), CookingSessions.days(2, listOf(THURSDAY, MONDAY)))
        assertEquals("otherwise the defaults", listOf(SUNDAY, WEDNESDAY), CookingSessions.days(2, listOf(MONDAY)))
    }

    @Test
    fun a14TwoSessionsCoverTheWeekBetweenThem() {
        val (sunday, wednesday) = CookingSessions.coverage(listOf(SUNDAY, WEDNESDAY), lunchDinner)
        assertEquals(CoveredMeal(SUNDAY, MealType.DINNER), sunday.meals.first())
        assertEquals(CoveredMeal(WEDNESDAY, MealType.LUNCH), sunday.meals.last())
        assertEquals(6, sunday.meals.size)
        assertEquals(CoveredMeal(WEDNESDAY, MealType.DINNER), wednesday.meals.first())
        assertEquals(CoveredMeal(SUNDAY, MealType.LUNCH), wednesday.meals.last())
        assertEquals("every prepped meal of the week exactly once", 14, sunday.meals.size + wednesday.meals.size)
    }

    @Test
    fun a14OneSessionCoversTheWholeWeekAndWarns() {
        val only = CookingSessions.coverage(listOf(SUNDAY), lunchDinner).single()
        assertEquals(14, only.meals.size)
        assertTrue(CookingSessions.freezerWarning(1))
        assertFalse(CookingSessions.freezerWarning(2))
    }

    @Test
    fun theCoveringCookingDay() {
        val days = listOf(SUNDAY, WEDNESDAY)
        val sunday = LocalDate.of(2026, 11, 8)
        assertEquals(sunday, CookingSessions.coveringDay(LocalDate.of(2026, 11, 10), MealType.LUNCH, days, lunchDinner))
        assertEquals(sunday, CookingSessions.coveringDay(sunday, MealType.DINNER, days, lunchDinner))
        assertEquals("Sunday lunch eats from Wednesday's cook", LocalDate.of(2026, 11, 4), CookingSessions.coveringDay(sunday, MealType.LUNCH, days, lunchDinner))
    }

    @Test
    fun dailyTotalsAreTheSlotTargets() {
        val lunch = MealTarget(700.0, 45.0, 70.0, 25.0)
        val dinner = MealTarget(600.0, 40.0, 60.0, 20.0)
        val day = LocalDate.of(2026, 11, 9)
        val slots = listOf(
            PlanSlot("1", "p", day, MealType.LUNCH, "me", SlotKind.BOX, recipeId = "chili", target = lunch),
            PlanSlot("2", "p", day, MealType.DINNER, "me", SlotKind.COOK_FRESH, recipeId = "soup", target = dinner),
            PlanSlot("3", "p", day, MealType.BREAKFAST, "me", SlotKind.FREE, target = MealTarget(400.0, 20.0, 50.0, 10.0)),
        )
        val totals = PlanTotals.planned(slots)
        assertEquals(1300.0, totals.kcal, 1e-9)
        assertEquals(85.0, totals.proteinG, 1e-9)
    }

    @Test
    fun boxesEatenAfterTheirDateGoToTheFreezer() {
        val sunday = LocalDate.of(2026, 11, 8)
        assertNull(PlanTotals.boxAgeIfFrozen(sunday, sunday.plusDays(3), 3))
        assertEquals(4L, PlanTotals.boxAgeIfFrozen(sunday, sunday.plusDays(4), 3))
    }
}
