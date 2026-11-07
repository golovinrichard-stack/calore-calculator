package com.calore.tracker.domain.plan

import com.calore.tracker.domain.MealTarget
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.ProductOption
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeNutrition
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.ShoppingLine
import com.calore.tracker.domain.SlotKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PlanEstimatesTest {

    private val monday = LocalDate.of(2026, 11, 9)
    private val sunday = LocalDate.of(2026, 11, 8)

    private val curry = PlannerRecipe(
        recipe = Recipe(id = "curry", name = "Curry", shortCode = "C", servings = 4, mealPrepFriendly = true, freezable = true),
        // 2,000 kcal in 1,000 g raw = 1,000 g cooked (yield 1.0): 200 kcal per 100 g.
        nutrition = Nutrients(kcal = 2000.0, protein = 150.0).let { RecipeNutrition(it, it.scaledBy(0.25), null, 1000.0, 0) },
        ingredients = listOf(
            PlannerIngredient("chicken", null, ShopSection.MEAT, 600.0),
            PlannerIngredient("rice", null, ShopSection.GRAINS, 400.0),
            PlannerIngredient("coriander", null, ShopSection.VEGETABLES, 10.0, optional = true),
        ),
        allergens = emptySet(),
        animalFlags = emptySet(),
        activeMinutes = 30,
    )

    private val input = PlannerInput(
        weekStart = monday,
        today = LocalDate.of(2026, 11, 7),
        members = emptyList(),
        recipes = listOf(curry),
        cookingDays = listOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
        prepped = setOf(MealType.LUNCH, MealType.DINNER),
        minutesPerSession = 120,
        variety = 1,
    )

    private fun box(date: LocalDate, meal: MealType, kcal: Double) =
        PlanSlot("", "", date, meal, "me", SlotKind.BOX, "curry", target = MealTarget(kcal, 40.0, 60.0, 20.0))

    @Test
    fun cooksNeedsAndMinutesFollowTheSlots() {
        // Monday lunch and dinner and Tuesday lunch come from Sunday's session: 600 kcal each = 300 g each.
        val slots = listOf(box(monday, MealType.LUNCH, 600.0), box(monday, MealType.DINNER, 600.0), box(monday.plusDays(1), MealType.LUNCH, 600.0))
        val cooks = PlanEstimates.cooks(input, slots, from = LocalDate.of(2026, 11, 7))
        assertEquals(1, cooks.size)
        assertEquals(sunday, cooks.single().date)
        assertEquals(3, cooks.single().portions)
        assertEquals(900.0, cooks.single().targetCookedGrams, 1e-9)
        assertEquals(0.9, cooks.single().scale, 1e-9)
        val needs = PlanEstimates.needs(cooks, mapOf("curry" to curry))
        assertEquals(listOf("chicken" to 540.0, "rice" to 360.0), needs.map { it.foodUuid to it.grams })
        assertEquals(mapOf(sunday to 30), PlanEstimates.sessionMinutes(cooks, mapOf("curry" to curry)))
        // Already cooked, or before the day: not a cook any more.
        assertEquals(emptyList<Any>(), PlanEstimates.cooks(input, slots, from = LocalDate.of(2026, 11, 7), cooked = setOf(sunday to "curry")))
        assertEquals(emptyList<Any>(), PlanEstimates.cooks(input, slots, from = monday))
    }

    @Test
    fun shoppingCostUsesPacksThenFoodPrices() {
        val lines = listOf(
            // 2 packs of 500 g at 0.30 Kč/g = 300 Kč.
            ShoppingLine("chicken", ShopSection.MEAT, 540.0, ProductOption("p", "s", "chicken", 500.0, pricePerGram = 30.0), 2, "s"),
            // No product: 360 g at 0.05 Kč/g = 18 Kč.
            ShoppingLine("rice", ShopSection.GRAINS, 360.0, null, null, null),
            // No price at all.
            ShoppingLine("salt", ShopSection.SPICES_SAUCES, 5.0, null, null, null),
        )
        assertEquals(ShoppingEstimate(30_000.0 + 1_800.0, 1), PlanEstimates.cost(lines, mapOf("rice" to 5.0)))
    }
}
