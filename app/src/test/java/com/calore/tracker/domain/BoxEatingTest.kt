package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** M4.7: eating a whole box or part of it. */
class BoxEatingTest {

    private val box = Box(
        id = "b", code = "C1", ownerMemberId = null, storage = Storage.FRIDGE, state = BoxState.READY,
        remainingFraction = 1.0, eatByDate = LocalDate.of(2026, 11, 11),
    )

    @Test
    fun eatingEverythingEmptiesTheBox() {
        assertEquals(Triple(1.0, 0.0, BoxState.EATEN), BoxEating.eat(box))
    }

    @Test
    fun eatingPartLeavesTheRest() {
        val (eaten, left, state) = BoxEating.eat(box, share = BoxEating.shareForGrams(200.0, fullGrams = 500.0))
        assertEquals(0.4, eaten, 1e-12)
        assertEquals(0.6, left, 1e-12)
        assertEquals(BoxState.PART_EATEN, state)

        val rest = BoxEating.eat(box.copy(remainingFraction = left, state = state))
        assertEquals(0.6, rest.first, 1e-12)
        assertEquals(BoxState.EATEN, rest.third)
    }

    @Test
    fun youCannotEatMoreThanIsLeft() {
        val (eaten, left, state) = BoxEating.eat(box.copy(remainingFraction = 0.3), share = 0.5)
        assertEquals(0.3, eaten, 1e-12)
        assertEquals(0.0, left, 0.0)
        assertEquals(BoxState.EATEN, state)
    }

    @Test
    fun aBoxViewAddsItsParts() {
        val chicken = Batch("c", null, "Kuře", 0, 600.0, 450.0, Nutrients(kcal = 165.0, protein = 31.0))
        val rice = Batch("r", null, "Rýže", 0, 300.0, 750.0, Nutrients(kcal = 130.0, protein = 2.7))
        val view = BoxView(
            box.copy(remainingFraction = 0.5),
            listOf(BoxPart("p1", "b", "c", 130.0) to chicken, BoxPart("p2", "b", "r", 280.0) to rice),
        )
        assertEquals(214.5 + 364.0, view.fullNutrients.kcal, 1e-9)
        assertEquals((214.5 + 364.0) / 2, view.remainingNutrients.kcal, 1e-9)
        assertEquals(410.0, view.fullGrams, 0.0)
        assertEquals("Kuře + Rýže", view.name)
    }
}
