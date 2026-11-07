package com.calore.tracker.data.recipes

import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.foods.GenericFoodImporter
import com.calore.tracker.data.foods.GenericFoodImporter.Companion.toEntity
import com.calore.tracker.data.recipes.StarterRecipeImporter.Companion.toStored
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.plan.PlanEstimates
import com.calore.tracker.domain.plan.Planner
import com.calore.tracker.domain.plan.PlannerInput
import com.calore.tracker.domain.plan.PlannerMember
import com.calore.tracker.domain.plan.PlannerRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * M6.3 "done when", on the JVM with the real starter pack: a new user (defaults everywhere) gets every
 * meal of the week planned, and the plan has cooks and things to buy.
 */
class StarterPackPlannerTest {

    private val pack = File("src/main/assets/recipes/starter-cz.json").inputStream().use { StarterRecipeImporter.parse(it) }
    private val genericFoods = File("src/main/assets/foods/generic-cz.json").inputStream().use { GenericFoodImporter.parse(it) }
    private val foodsByKey = genericFoods.foods.associate { it.key to it.toEntity(genericFoods.datasetVersion, now = 0).toDomain() }

    private val recipes: List<PlannerRecipe> = pack.recipes.map { starter ->
        val stored = starter.toStored(existing = null, foodUuids = starter.ingredients.map { foodsByKey.getValue(it.food).uuid }, now = 0)
        val foods = starter.ingredients.map { foodsByKey.getValue(it.food) }.associateBy { it.uuid }
        PlannerRecipe.of(stored.recipe.toDomain(), stored.ingredients.map { it.toDomain() }, foods, stored.steps.map { it.toDomain() })
    }

    private val monday = LocalDate.of(2026, 11, 9)

    private fun newUser(id: String = "me", diet: Diet = Diet.OMNIVORE) =
        Member.newMe(id, "", 0).copy(targets = NutritionGoals(kcal = 2200, proteinG = 140, carbsG = 250, fatG = 70), diet = diet)

    private fun input(vararg people: Member): PlannerInput {
        val me = people.first()
        return PlannerInput(
            weekStart = monday,
            today = monday.minusDays(2),
            members = people.map { PlannerMember(it) },
            recipes = recipes,
            cookingDays = CookingSessions.days(me.cookSessionsPerWeek, me.cookDays),
            prepped = me.preppedMeals,
            minutesPerSession = me.minutesPerSession,
            variety = me.variety,
        )
    }

    @Test
    fun aNewUserGetsEveryMealOfTheWeekPlanned() {
        val inp = input(newUser())
        val result = Planner.plan(inp, seed = 2026)
        assertEquals(emptyList<Any>(), result.unplanned)
        // 7 days × 4 meals (the default meal split has all four).
        assertEquals(28, result.newKeys.size)
        assertEquals(14, result.slots.count { it.kind == SlotKind.BOX })
        val cooks = PlanEstimates.cooks(inp, result.slots, inp.today)
        assertTrue(cooks.isNotEmpty())
        assertTrue(PlanEstimates.needs(cooks, recipes.associateBy { it.id }).isNotEmpty())
        // Every meal's planned calories are its target: each day adds up to the daily target.
        for (day in (0L..6L).map { monday.plusDays(it) }) {
            val kcal = result.slots.filter { it.date == day }.sumOf { it.target!!.kcal }
            assertEquals(day.toString(), 2200.0, kcal, 0.5)
        }
    }

    @Test
    fun aVegetarianAndAMeatEaterBothGetFullWeeks() {
        val result = Planner.plan(input(newUser(), newUser("kid", Diet.VEGETARIAN)), seed = 7)
        assertEquals(emptyList<Any>(), result.unplanned)
        assertEquals(56, result.newKeys.size)
    }
}
