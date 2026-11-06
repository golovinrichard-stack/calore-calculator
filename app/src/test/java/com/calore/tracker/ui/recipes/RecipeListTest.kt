package com.calore.tracker.ui.recipes

import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.AnimalFlag
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** M3.3: search, meal and diet filters, and recipes with my allergens hidden by default. */
class RecipeListTest {

    private val czech = Locale.forLanguageTag("cs")

    private fun food(uuid: String, allergens: Set<Allergen> = emptySet(), flags: Set<AnimalFlag> = emptySet()) = Food(
        id = 0, name = uuid, brand = null, barcode = null, per100g = Nutrients(kcal = 100.0), servingGrams = null,
        source = FoodSource.CUSTOM, uuid = uuid, allergens = allergens, animalFlags = flags,
    )

    private fun recipe(id: String, cs: String, en: String, meals: Set<MealType>, vararg foods: Food) = RecipeDetails(
        recipe = Recipe(id = id, name = cs, nameCs = cs, nameEn = en, shortCode = "X", servings = 1, mealTypes = meals),
        ingredients = foods.mapIndexed { i, f -> RecipeIngredient(id = "$id$i", recipeId = id, position = i, foodUuid = f.uuid, grams = 100.0) },
        steps = emptyList(),
        foods = foods.associateBy { it.uuid },
    )

    private val beans = food("beans")
    private val milk = food("milk", setOf(Allergen.MILK), setOf(AnimalFlag.DAIRY))
    private val chicken = food("chicken", flags = setOf(AnimalFlag.MEAT))

    private val all = listOf(
        recipe("1", "Čočka na kyselo", "Sour lentils", setOf(MealType.LUNCH), beans),
        recipe("2", "Ovesná kaše", "Oat porridge", setOf(MealType.BREAKFAST), milk),
        recipe("3", "Kuře s rýží", "Chicken and rice", setOf(MealType.LUNCH, MealType.DINNER), chicken),
    )

    private fun names(filters: RecipeFilters = RecipeFilters(), diet: Diet = Diet.OMNIVORE, allergens: Set<Allergen> = emptySet(), locale: Locale = czech) =
        buildRecipeList(all, diet, allergens, filters, locale).items.map { it.name }

    @Test
    fun sortedByNameInTheAppLanguage() {
        assertEquals(listOf("Čočka na kyselo", "Kuře s rýží", "Ovesná kaše"), names())
        assertEquals(listOf("Chicken and rice", "Oat porridge", "Sour lentils"), names(locale = Locale.ENGLISH))
    }

    @Test
    fun searchFindsBothLanguagesWithoutAccents() {
        assertEquals(listOf("Čočka na kyselo"), names(RecipeFilters(query = "cocka")))
        assertEquals(listOf("Čočka na kyselo"), names(RecipeFilters(query = "lentils")))
    }

    @Test
    fun mealAndDietFilters() {
        assertEquals(listOf("Čočka na kyselo", "Kuře s rýží"), names(RecipeFilters(meal = MealType.LUNCH)))
        assertEquals(listOf("Čočka na kyselo", "Ovesná kaše"), names(RecipeFilters(fitsDietOnly = true), diet = Diet.VEGETARIAN))
        assertEquals(listOf("Čočka na kyselo"), names(RecipeFilters(fitsDietOnly = true), diet = Diet.VEGAN))
    }

    @Test
    fun recipesWithMyAllergensAreHiddenUntilShowAll() {
        val hidden = buildRecipeList(all, Diet.OMNIVORE, setOf(Allergen.MILK), RecipeFilters(), czech)
        assertEquals(listOf("Čočka na kyselo", "Kuře s rýží"), hidden.items.map { it.name })
        assertEquals(1, hidden.hiddenForAllergens)

        val shown = buildRecipeList(all, Diet.OMNIVORE, setOf(Allergen.MILK), RecipeFilters(showAll = true), czech)
        assertEquals(3, shown.items.size)
        assertEquals(0, shown.hiddenForAllergens)
        assertEquals(setOf(Allergen.MILK), shown.items.single { it.id == "2" }.myAllergens)
    }
}
