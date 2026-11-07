package com.calore.tracker.domain.plan

import com.calore.tracker.domain.AmountUnit
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.PantryItem
import com.calore.tracker.domain.PantryState
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeNutrition
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.Storage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UseItUpTest {

    private val today = LocalDate.of(2026, 11, 7)

    private fun item(id: String, food: String?, bestBefore: LocalDate?, state: PantryState = PantryState.AVAILABLE) =
        PantryItem(id, food, null, id, 100.0, AmountUnit.G, Storage.FRIDGE, today.minusDays(1), bestBefore, state = state)

    private fun recipe(id: String, vararg foods: Pair<String, Double>, optional: String? = null) = PlannerRecipe(
        recipe = Recipe(id = id, name = id, shortCode = "R", servings = 2),
        nutrition = RecipeNutrition(Nutrients(kcal = 1000.0), Nutrients(kcal = 500.0), null, 0.0, 0),
        ingredients = foods.map { (food, grams) -> PlannerIngredient(food, null, ShopSection.OTHER, grams, optional = food == optional) },
        allergens = emptySet(),
        animalFlags = emptySet(),
    )

    @Test
    fun itemsGoingOffWithinFourDaysSoonestFirst() {
        val items = listOf(
            item("later", "a", today.plusDays(5)),
            item("in4", "b", today.plusDays(4)),
            item("today", "c", today),
            item("gone", "d", today.minusDays(1)),
            item("nodate", "e", null),
            item("nofood", null, today.plusDays(1)),
            item("used", "f", today.plusDays(1), PantryState.USED_UP),
        )
        assertEquals(listOf("today", "in4"), UseItUp.expiring(items, today).map { it.id })
    }

    @Test
    fun recipesUsingAFoodMostFirstAndNotAsAnOptionalIngredient() {
        val recipes = listOf(
            recipe("little", "spinach" to 50.0, "rice" to 450.0),
            recipe("lots", "spinach" to 300.0, "rice" to 200.0),
            recipe("garnish", "spinach" to 10.0, "rice" to 400.0, optional = "spinach"),
            recipe("none", "rice" to 500.0),
        )
        assertEquals(listOf("lots", "little"), UseItUp.recipesUsing("spinach", recipes).map { it.id })
    }
}
