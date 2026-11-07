package com.calore.tracker.ui.recipes

import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
import com.calore.tracker.domain.RecipeNutrition
import com.calore.tracker.domain.RecipeStep
import com.calore.tracker.domain.Recipes
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.domain.parsePortionGrams
import com.calore.tracker.domain.parseWholeIn
import java.util.Locale

/** An ingredient row in the editor. [food] is null when the food was deleted since. */
data class IngredientDraft(
    /** Blank for a new row. */
    val id: String,
    val foodUuid: String,
    val food: Food?,
    val gramsText: String,
    val note: String = "",
    val optional: Boolean = false,
) {
    val grams: Double? get() = parsePortionGrams(gramsText)
}

/** A step in the editor. [original] keeps both languages of a starter step the user didn't change. */
data class StepDraft(
    val id: String,
    val text: String,
    val minutesText: String = "",
    val passive: Boolean = false,
    /** An [Equipment] name, [STOVE], or null. */
    val equipment: String? = null,
    val original: RecipeStep? = null,
) {
    val minutes: Int? get() = parseWholeIn(minutesText, 1..MAX_STEP_MINUTES)

    companion object {
        const val STOVE = "STOVE"
        const val MAX_STEP_MINUTES = 24 * 60
    }
}

enum class RecipeField { NAME, SERVINGS, SHORT_CODE, INGREDIENTS, FRIDGE_DAYS, FREEZER_MONTHS }

/** Everything the recipe editor shows, and the rules for saving it (M3.2). Plain Kotlin, so it is unit tested. */
data class RecipeForm(
    /** The stored recipe being edited; null for a new one. */
    val original: Recipe? = null,
    val name: String = "",
    val servingsText: String = "4",
    val shortCode: String = "",
    /** Once the user types a code, changing the name no longer suggests one. */
    val shortCodeEdited: Boolean = false,
    val ingredients: List<IngredientDraft> = emptyList(),
    val steps: List<StepDraft> = emptyList(),
    val fridgeDaysText: String = Recipe.DEFAULT_FRIDGE_DAYS.toString(),
    val freezable: Boolean = false,
    val freezerMonthsText: String = Recipe.DEFAULT_FREEZER_MONTHS.toString(),
    val reheatNote: String = "",
    val mealTypes: Set<MealType> = emptySet(),
    val cuisine: String? = null,
    val tagsText: String = "",
    val equipment: Set<Equipment> = emptySet(),
    val mealPrepFriendly: Boolean = false,
) {
    val servings: Int? get() = parseWholeIn(servingsText, Recipe.SERVINGS_RANGE)
    val fridgeDays: Int? get() = parseWholeIn(fridgeDaysText, FRIDGE_DAYS_RANGE)
    val freezerMonths: Int? get() = parseWholeIn(freezerMonthsText, FREEZER_MONTHS_RANGE)

    /** Rows whose grams are missing or not above 0. */
    val badGramsRows: Set<Int> get() = ingredients.indices.filter { ingredients[it].grams == null }.toSet()

    fun invalidFields(): Set<RecipeField> = buildSet {
        if (name.isBlank()) add(RecipeField.NAME)
        if (servings == null) add(RecipeField.SERVINGS)
        if (!SHORT_CODE.matches(shortCode)) add(RecipeField.SHORT_CODE)
        if (ingredients.isEmpty() || badGramsRows.isNotEmpty()) add(RecipeField.INGREDIENTS)
        if (fridgeDays == null) add(RecipeField.FRIDGE_DAYS)
        if (freezable && freezerMonths == null) add(RecipeField.FREEZER_MONTHS)
    }

    private val foods: Map<String, Food> get() = ingredients.mapNotNull { row -> row.food?.let { row.foodUuid to it } }.toMap()

    /** Nutrition as typed so far: rows without valid grams are left out until they are fixed. */
    fun nutrition(): RecipeNutrition = Recipes.nutrition(
        ingredients = ingredients.mapIndexedNotNull { index, row -> row.grams?.let { row.toIngredient(index, it) } },
        foods = foods,
        servings = servings ?: 1,
        learnedYield = original?.learnedYield,
    )

    fun allergens(): Set<Allergen> = Recipes.allergens(ingredients.mapIndexed { i, row -> row.toIngredient(i, 0.0) }, foods)

    /** A new name also suggests a box code, until the user typed their own. */
    fun withName(newName: String, takenCodes: Set<String>): RecipeForm = copy(
        name = newName,
        shortCode = if (shortCodeEdited) shortCode else Recipes.suggestShortCode(newName, takenCodes),
    )

    fun withShortCode(code: String): RecipeForm =
        copy(shortCode = code.filter { it.isLetter() }.take(2).uppercase(), shortCodeEdited = true)

    /** The recipe to save, or null while a field is invalid. Blank steps are dropped. */
    fun toRecipe(locale: Locale): SavedRecipe? {
        if (invalidFields().isNotEmpty()) return null
        val base = original ?: Recipe(id = "", name = "", shortCode = "", servings = 1)
        // A starter recipe keeps both languages of its name while the user leaves the name alone.
        val sameName = original != null && name.trim() == original.displayName(locale)
        val recipe = base.copy(
            name = if (sameName) base.name else name.trim(),
            nameCs = if (sameName) base.nameCs else null,
            nameEn = if (sameName) base.nameEn else null,
            shortCode = shortCode,
            servings = servings!!,
            mealTypes = mealTypes,
            cuisine = cuisine,
            tags = parseTags(tagsText),
            equipment = equipment,
            fridgeDays = fridgeDays!!,
            freezable = freezable,
            freezerMonths = if (freezable) freezerMonths!! else base.freezerMonths,
            reheatNote = reheatNote.trim().ifEmpty { null },
            mealPrepFriendly = mealPrepFriendly,
        )
        val savedIngredients = ingredients.mapIndexed { index, row -> row.toIngredient(index, row.grams!!) }
        val savedSteps = steps.filter { it.text.isNotBlank() }.mapIndexed { index, step ->
            val original = step.original
            val unchanged = original != null && step.text.trim() == original.displayText(locale)
            RecipeStep(
                id = step.id,
                recipeId = recipe.id,
                position = index,
                text = if (unchanged) original!!.text else step.text.trim(),
                textCs = if (unchanged) original!!.textCs else null,
                textEn = if (unchanged) original!!.textEn else null,
                minutes = step.minutes,
                passive = step.passive,
                equipment = step.equipment,
            )
        }
        return SavedRecipe(recipe, savedIngredients, savedSteps)
    }

    private fun IngredientDraft.toIngredient(position: Int, grams: Double) = RecipeIngredient(
        id = id,
        recipeId = original?.id.orEmpty(),
        position = position,
        foodUuid = foodUuid,
        grams = grams,
        note = note.trim().ifEmpty { null },
        optional = optional,
    )

    companion object {
        val FRIDGE_DAYS_RANGE = 1..14
        val FREEZER_MONTHS_RANGE = 1..12
        private val SHORT_CODE = Regex("""^\p{L}{1,2}$""")

        fun parseTags(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        /** The form for an existing recipe, texts in the app's language. */
        fun from(details: RecipeDetails, locale: Locale): RecipeForm {
            val recipe = details.recipe
            return RecipeForm(
                original = recipe,
                name = recipe.displayName(locale),
                servingsText = recipe.servings.toString(),
                shortCode = recipe.shortCode,
                shortCodeEdited = true,
                ingredients = details.ingredients.map { ingredient ->
                    IngredientDraft(
                        id = ingredient.id,
                        foodUuid = ingredient.foodUuid,
                        food = details.foods[ingredient.foodUuid],
                        gramsText = formatEditable(ingredient.grams),
                        note = ingredient.note.orEmpty(),
                        optional = ingredient.optional,
                    )
                },
                steps = details.steps.map { step ->
                    StepDraft(
                        id = step.id,
                        text = step.displayText(locale),
                        minutesText = step.minutes?.toString().orEmpty(),
                        passive = step.passive,
                        equipment = step.equipment,
                        original = step,
                    )
                },
                fridgeDaysText = recipe.fridgeDays.toString(),
                freezable = recipe.freezable,
                freezerMonthsText = recipe.freezerMonths.toString(),
                reheatNote = recipe.reheatNote.orEmpty(),
                mealTypes = recipe.mealTypes,
                cuisine = recipe.cuisine,
                tagsText = recipe.tags.joinToString(", "),
                equipment = recipe.equipment,
                mealPrepFriendly = recipe.mealPrepFriendly,
            )
        }
    }
}

/** What [RecipeForm.toRecipe] gives: ready for [com.calore.tracker.data.repository.RecipeRepository.save]. */
data class SavedRecipe(val recipe: Recipe, val ingredients: List<RecipeIngredient>, val steps: List<RecipeStep>)
