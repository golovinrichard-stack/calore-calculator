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
 * The planner with every bundled recipe (the starter pack and the big pack, 0.14): full weeks for
 * every diet, and still fast with about a thousand recipes.
 */
class RecipePackPlannerTest {

    private val packs = listOf("starter-cz.json", "pack-cz.json").flatMap { name ->
        File("src/main/assets/recipes/$name").inputStream().use { StarterRecipeImporter.parse(it) }.recipes
    }
    private val genericFoods = File("src/main/assets/foods/generic-cz.json").inputStream().use { GenericFoodImporter.parse(it) }
    private val foodsByKey = genericFoods.foods.associate { it.key to it.toEntity(genericFoods.datasetVersion, now = 0).toDomain() }

    private val recipes: List<PlannerRecipe> = packs.map { starter ->
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
        assertTrue(recipes.size >= 1000)
        val inp = input(newUser())
        val result = Planner.plan(inp, seed = 2026)
        assertEquals(emptyList<Any>(), result.unplanned)
        assertEquals(28, result.newKeys.size)
        val cooks = PlanEstimates.cooks(inp, result.slots, inp.today)
        assertTrue(PlanEstimates.needs(cooks, recipes.associateBy { it.id }).isNotEmpty())
    }

    @Test
    fun everyDietGetsAFullWeek() {
        for (diet in Diet.entries) {
            val result = Planner.plan(input(newUser(diet = diet)), seed = 11)
            assertEquals(diet.name, emptyList<Any>(), result.unplanned)
        }
        val family = Planner.plan(input(newUser(), newUser("kid", Diet.VEGETARIAN), newUser("gran", Diet.NO_PORK)), seed = 7)
        assertEquals(emptyList<Any>(), family.unplanned)
        assertEquals(84, family.newKeys.size)
    }

    @Test
    fun plansAThousandRecipesQuickly() {
        Planner.plan(input(newUser()), seed = 1) // warm up the JVM
        val runs = 3
        val started = System.nanoTime()
        repeat(runs) { Planner.plan(input(newUser(), newUser("kid", Diet.VEGETARIAN)), seed = it.toLong()) }
        val ms = (System.nanoTime() - started) / 1_000_000 / runs
        println("Planner, ${recipes.size} recipes, 2 people: $ms ms per plan")
        assertTrue("$ms ms per plan", ms < 2000)
    }
}
