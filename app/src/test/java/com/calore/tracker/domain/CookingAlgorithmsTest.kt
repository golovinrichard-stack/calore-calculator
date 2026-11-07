package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** The test cases of algorithms.md A9–A13, exactly as written there. */
class CookingAlgorithmsTest {

    // A9. Batch nutrition and learned yield

    @Test
    fun a9ChiliIs120KcalPer100g() {
        val pot = Nutrients(kcal = 3600.0)
        assertEquals(120.0, Batches.per100gCooked(pot, cookedGrams = 3000.0).kcal, 1e-9)
    }

    @Test
    fun a9NetWeight() {
        assertEquals(3000.0, Batches.netWeight(fullGrams = 4850.0, emptyPotGrams = 1850.0, rawGrams = 3200.0)!!, 0.0)
        assertNull("≤ 0 is rejected", Batches.netWeight(1800.0, 1850.0, 3200.0))
        assertNull("more than 5 × raw is rejected", Batches.netWeight(20_000.0, 1850.0, 3000.0))
    }

    @Test
    fun a9LearnedYield() {
        assertEquals(0.85, Batches.learnedYield(previous = 0.90, latest = 0.80), 1e-12)
        assertEquals("first time: the latest", 0.80, Batches.learnedYield(previous = null, latest = 0.80), 0.0)
        assertEquals(1200.0, Batches.estimatedCookedGrams(1200.0, learnedYield = null), 0.0)
    }

    @Test
    fun a9PotNutrientsSumTheIngredients() {
        val beef = Food(0, "beef", null, null, Nutrients(kcal = 215.0), null, FoodSource.CUSTOM, uuid = "b")
        val rice = Food(0, "rice", null, null, Nutrients(kcal = 365.0), null, FoodSource.CUSTOM, uuid = "r")
        assertEquals(1720.0 + 1533.0, Batches.potNutrients(listOf(beef to 800.0, rice to 420.0)).kcal, 1e-9)
    }

    // A10. Splitting a batch into boxes

    @Test
    fun a10ByCalorieTarget() {
        val split = BoxSplitter.byCalorieTarget(cookedGrams = 3000.0, kcalPer100g = 120.0, targetKcal = 600.0)
        assertEquals(List(6) { 500.0 }, split.boxGrams)
        assertEquals(0.0, split.leftoverGrams, 0.0)
        assertFalse(split.offerLeftoverBox)
    }

    @Test
    fun a10ByPerson() {
        val split = BoxSplitter.byPerson(3000.0, 120.0, listOf(600.0, 600.0, 600.0, 600.0, 450.0, 450.0))
        assertEquals(listOf(500.0, 500.0, 500.0, 500.0, 375.0, 375.0), split.boxGrams)
        assertEquals(250.0, split.leftoverGrams, 1e-9)
        assertTrue("offered as an extra box", split.offerLeftoverBox)
        assertFalse(split.scaledDown)
    }

    @Test
    fun a10Equal() {
        assertEquals(listOf(335.0, 335.0, 330.0), BoxSplitter.equal(1000.0, 3).boxGrams)
    }

    @Test
    fun a10SmallLeftoverIsSpreadAndATooSmallPotScalesDown() {
        val split = BoxSplitter.byCalorieTarget(3100.0, 120.0, 600.0)
        assertEquals(100.0, split.leftoverGrams, 1e-9)
        assertFalse("100 g is under 40 % of 500 g", split.offerLeftoverBox)
        val small = BoxSplitter.byPerson(900.0, 120.0, listOf(600.0, 600.0))
        assertTrue(small.scaledDown)
        assertEquals(450.0, small.boxGrams[0], 1e-9)
        assertEquals(450.0, small.boxGrams[1], 1e-9)
    }

    @Test
    fun a10BoxCodes() {
        assertEquals(listOf("C1", "C2", "C3"), BoxSplitter.nextCodes("C", 3, emptySet()))
        assertEquals(listOf("C4", "C5"), BoxSplitter.nextCodes("C", 2, setOf("C1", "C3", "CH2")))
        assertEquals("C", BoxSplitter.prefixFor("chili", "C", "Chili", mapOf("C" to "chili")))
        assertEquals("CC", BoxSplitter.prefixFor("curry", "C", "Chicken curry", mapOf("C" to "chili")))
    }

    // A11. Box solver

    private val chicken = Nutrients(kcal = 165.0, protein = 31.0, carbs = 0.0, fat = 3.6)
    private val rice = Nutrients(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3)
    private val broccoli = Nutrients(kcal = 35.0, protein = 2.4, carbs = 7.2, fat = 0.4)
    private val oliveOil = Nutrients(kcal = 884.0, protein = 0.0, carbs = 0.0, fat = 100.0)

    @Test
    fun a11ChickenRiceWithFixedBroccoliAndOil() {
        val solution = BoxSolver.solve(
            parts = listOf(
                BoxPartInput(chicken),
                BoxPartInput(rice),
                BoxPartInput(broccoli, fixedGrams = 100.0),
                BoxPartInput(oliveOil, fixedGrams = 10.0),
            ),
            targets = BoxTargets(kcal = 700.0, protein = 50.0),
        )
        assertEquals(130.0, solution.grams[0], 5.0)
        assertEquals(280.0, solution.grams[1], 5.0)
        assertEquals(listOf(100.0, 10.0), solution.grams.drop(2))
        assertEquals(702.0, solution.predicted.kcal, 5.0)
        assertEquals(50.3, solution.predicted.protein!!, 1.0)
        assertFalse(solution.cannotReachTarget)
    }

    @Test
    fun a11OnePartOnly() {
        val solution = BoxSolver.solve(listOf(BoxPartInput(Nutrients(kcal = 120.0))), BoxTargets(kcal = 600.0))
        assertEquals(listOf(500.0), solution.grams)
        assertFalse(solution.cannotReachTarget)
    }

    @Test
    fun a11RiceOnlyCannotReachTheProteinTarget() {
        val solution = BoxSolver.solve(listOf(BoxPartInput(rice, maxGrams = 600.0)), BoxTargets(protein = 50.0))
        assertTrue(solution.cannotReachTarget)
        assertEquals(600.0, solution.grams.single(), 0.0)
    }

    // A12. Eat-by dates and colours

    private val sunday = LocalDate.of(2026, 11, 8)

    @Test
    fun a12EatByDateAndColours() {
        val eatBy = Freshness.eatBy(sunday, fridgeDays = 3)
        assertEquals(LocalDate.of(2026, 11, 11), eatBy)
        assertEquals(FreshnessColour.GREEN, Freshness.colour(eatBy, sunday.plusDays(1)))
        assertEquals(FreshnessColour.YELLOW, Freshness.colour(eatBy, sunday.plusDays(2)))
        assertEquals(FreshnessColour.RED, Freshness.colour(eatBy, sunday.plusDays(4)))
        assertEquals("on the day itself: yellow", FreshnessColour.YELLOW, Freshness.colour(eatBy, eatBy))
    }

    @Test
    fun a12SeveralPartsTakeTheEarliestDate() {
        val chickenPart = Freshness.eatBy(sunday, 3)
        val ricePart = Freshness.eatBy(sunday.plusDays(1), 1)
        assertEquals(LocalDate.of(2026, 11, 10), Freshness.eatByOfParts(listOf(chickenPart, ricePart)))
    }

    @Test
    fun a12ThawedBoxIsEatenTheNextDay() {
        val monday = sunday.plusDays(1)
        val nextTuesday = monday.plusDays(8)
        assertEquals(nextTuesday.plusDays(1), Freshness.eatByAfterThaw(nextTuesday))
        assertEquals(java.time.DayOfWeek.WEDNESDAY, Freshness.eatByAfterThaw(nextTuesday).dayOfWeek)
    }

    @Test
    fun a12FreezingRules() {
        val eatBy = Freshness.eatBy(sunday, 3)
        assertTrue(Freshness.canFreeze(eatBy, eatBy))
        assertFalse("not after the eat-by date", Freshness.canFreeze(eatBy, eatBy.plusDays(1)))
        assertFalse(Freshness.freezerQualityOver(sunday, 3, sunday.plusMonths(3).minusDays(1)))
        assertTrue(Freshness.freezerQualityOver(sunday, 3, sunday.plusMonths(3)))
    }

    // A13. Freeze and thaw planning

    @Test
    fun a13BoxesPlannedAfterTheEatByDateAreFrozen() {
        val eatBy = Freshness.eatBy(sunday, 3)
        val planned = (1..4).map { sunday.plusDays(it.toLong()) } // Monday to Thursday
        assertEquals(listOf(false, false, false, true), planned.map { Freshness.goesToFreezer(it, eatBy) })
        assertEquals(LocalDateTime.of(2026, 11, 11, 19, 0), Freshness.thawReminderAt(planned.last()))
    }
}
