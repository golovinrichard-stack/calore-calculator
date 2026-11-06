package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** M4.5 "done when": chili 6 × 500 g; by person 4 × 500 g + 2 × 375 g + a 250 g box; chicken-rice 130 g + 280 g. */
class BoxFillingTest {

    private val chili = Batch("chili", "r", "Chili", 0, 3200.0, 3000.0, Nutrients(kcal = 120.0, protein = 9.0))

    private fun person(id: String, kcal: Double) = PersonMeal(id, MealType.LUNCH, MealTarget(kcal, 40.0, 60.0, 20.0))

    @Test
    fun chiliMakesSixBoxesOf500g() {
        val result = BoxFilling.singleBatch(chili, FillMode.BY_CALORIES, targetKcal = 600.0, owner = "me")
        assertEquals(List(6) { 500.0 }, result.boxes.map { it.grams })
        assertEquals(600.0, result.boxes.first().nutrients.kcal, 1e-9)
        assertEquals(0.0, result.leftoverGrams, 0.0)
    }

    @Test
    fun byPersonWithALeftoverBox() {
        val people = List(4) { person("a$it", 600.0) } + List(2) { person("b$it", 450.0) }
        val result = BoxFilling.singleBatch(chili, FillMode.BY_PERSON, people = people, owner = "me")
        assertEquals(listOf(500.0, 500.0, 500.0, 500.0, 375.0, 375.0, 250.0), result.boxes.map { it.grams }.map { Math.round(it * 1e6) / 1e6 })
        assertEquals(listOf("a0", "a1", "a2", "a3", "b0", "b1", "me"), result.boxes.map { it.ownerMemberId })
        assertEquals(MealType.LUNCH, result.boxes.first().plannedMeal)
    }

    @Test
    fun aSmallLeftoverIsSpreadOverTheBoxes() {
        val pot = chili.copy(cookedGrams = 3060.0)
        val result = BoxFilling.singleBatch(pot, FillMode.BY_CALORIES, targetKcal = 600.0)
        assertEquals(6, result.boxes.size)
        assertEquals(510.0, result.boxes.first().grams, 1e-9)
        assertFalse(result.offerLeftoverBox)
    }

    @Test
    fun chickenAndRiceMixedBoxes() {
        val chicken = Batch("c", null, "Kuře", 0, 600.0, 450.0, Nutrients(kcal = 165.0, protein = 31.0, carbs = 0.0, fat = 3.6))
        val rice = Batch("r", null, "Rýže", 0, 300.0, 750.0, Nutrients(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3))
        val broccoli = Batch("b", null, "Brokolice", 0, 300.0, 300.0, Nutrients(kcal = 35.0, protein = 2.4, carbs = 7.2, fat = 0.4))
        val oil = Batch("o", null, "Olej", 0, 100.0, 100.0, Nutrients(kcal = 884.0, protein = 0.0, carbs = 0.0, fat = 100.0))
        val target = PersonMeal("me", MealType.LUNCH, MealTarget(kcal = 700.0, proteinG = 50.0, carbsG = 0.0, fatG = 0.0))
        val result = BoxFilling.mixed(listOf(chicken, rice, broccoli, oil), mapOf("b" to 100.0, "o" to 10.0), listOf(target))
        val grams = result.boxes.single().parts.toMap()
        assertEquals(130.0, grams.getValue("c"), 5.0)
        assertEquals(280.0, grams.getValue("r"), 5.0)
        assertFalse(result.boxes.single().missesTarget)
        assertTrue(result.notEnough.isEmpty())

        val many = BoxFilling.mixed(listOf(chicken, rice), emptyMap(), List(5) { target })
        assertEquals("five boxes need more chicken and rice than was cooked", setOf("c", "r"), many.notEnough)
    }

    @Test
    fun planningDaysFromAStartDate() {
        val boxes = BoxFilling.singleBatch(chili, FillMode.EQUAL, count = 4).boxes
        val planned = BoxFilling.planDays(boxes, LocalDate.of(2026, 11, 9), MealType.LUNCH)
        assertEquals((9..12).map { LocalDate.of(2026, 11, it) }, planned.map { it.plannedDate })
        assertTrue(planned.all { it.plannedMeal == MealType.LUNCH })
    }

    @Test
    fun aMembersMealTarget() {
        val member = Member(
            id = "m", kind = MemberKind.MANAGED, name = "Eva", colour = 0,
            targets = NutritionGoals(2000, 120, 200, 70),
            mealSplit = mapOf(MealType.BREAKFAST to 25, MealType.LUNCH to 35, MealType.DINNER to 30, MealType.SNACK to 10),
        )
        assertEquals(700.0, member.mealTarget(MealType.LUNCH)!!.kcal, 1e-9)
        assertEquals(42.0, member.mealTarget(MealType.LUNCH)!!.proteinG, 1e-9)
    }
}
