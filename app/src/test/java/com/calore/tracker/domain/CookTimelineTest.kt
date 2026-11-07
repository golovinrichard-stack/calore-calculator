package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4.4: one timeline for several recipes. */
class CookTimelineTest {

    private fun step(minutes: Int?, passive: Boolean = false, text: String = "") =
        RecipeStep(id = text, recipeId = "", position = 0, text = text, minutes = minutes, passive = passive)

    private val roast = listOf(step(10, text = "prep"), step(40, passive = true, text = "oven"), step(2, text = "serve"))
    private val stew = listOf(step(10, text = "chop"), step(20, passive = true, text = "simmer"), step(5, text = "finish"))

    @Test
    fun theOtherRecipesActiveStepsRunDuringTheOvenTime() {
        val timeline = CookTimeline.schedule(listOf(roast, stew))
        val oven = timeline.single { it.step.text == "oven" }
        val chop = timeline.single { it.step.text == "chop" }
        val finish = timeline.single { it.step.text == "finish" }
        assertEquals(10, oven.start)
        assertEquals(50, oven.end)
        assertTrue("chopping happens while the oven works", chop.start >= oven.start && chop.end <= oven.end)
        assertTrue(finish.start >= oven.start && finish.end <= oven.end)
        assertEquals(52, CookTimeline.totalMinutes(timeline))
    }

    @Test
    fun stepsKeepTheirOrderAndTheCookDoesOneActiveStepAtATime() {
        val timeline = CookTimeline.schedule(listOf(roast, stew))
        for (recipe in 0..1) {
            val own = timeline.filter { it.recipeIndex == recipe }.sortedBy { it.start }
            assertEquals(own.map { it.stepIndex }, own.map { it.stepIndex }.sorted())
            own.zipWithNext().forEach { (a, b) -> assertTrue(b.start >= a.end) }
        }
        val active = timeline.filter { !it.passive }.sortedBy { it.start }
        active.zipWithNext().forEach { (a, b) -> assertTrue("no two active steps at once", b.start >= a.end) }
    }

    @Test
    fun theRecipeWithTheMostTimeLeftStartsFirst() {
        val timeline = CookTimeline.schedule(listOf(stew, roast))
        assertEquals("prep", timeline.first().step.text)
    }

    @Test
    fun nowAndNext() {
        val timeline = CookTimeline.schedule(listOf(roast, stew))
        assertEquals(setOf("oven", "chop"), CookTimeline.now(timeline, 15).map { it.step.text }.toSet())
        assertEquals("simmer", CookTimeline.next(timeline, 15)!!.step.text)
        assertEquals("a step without minutes takes 5", 5, CookTimeline.totalMinutes(CookTimeline.schedule(listOf(listOf(step(null))))))
    }
}
