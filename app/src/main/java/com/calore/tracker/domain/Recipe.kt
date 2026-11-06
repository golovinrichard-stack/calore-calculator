package com.calore.tracker.domain

import java.util.Locale

/** Who may see a shared row once the household syncs (M11). Until then everything is SHARED. */
enum class Visibility { MINE, SHARED, UP_FOR_GRABS }

/** A recipe: the user's own, or one of the starter pack (M3). Shared with the household in M11. */
data class Recipe(
    val id: String,
    /** Starter recipes: `starter:cz:<slug>`; null for the user's own. */
    val key: String? = null,
    val name: String,
    val nameCs: String? = null,
    val nameEn: String? = null,
    /** A starter recipe the user changed is never overwritten by a newer starter pack. */
    val userEdited: Boolean = false,
    /** 1–2 letters for box codes (C → C1, C2 …). */
    val shortCode: String,
    val servings: Int,
    val mealTypes: Set<MealType> = emptySet(),
    val cuisine: String? = null,
    val tags: List<String> = emptyList(),
    val equipment: Set<Equipment> = emptySet(),
    val fridgeDays: Int = DEFAULT_FRIDGE_DAYS,
    val freezable: Boolean = false,
    val freezerMonths: Int = DEFAULT_FREEZER_MONTHS,
    val reheatNote: String? = null,
    val mealPrepFriendly: Boolean = false,
    /** Cooked grams ÷ raw grams, learned from weighing (A9). */
    val learnedYield: Double? = null,
    val learnedYieldCount: Int = 0,
    /** Null: the whole household's recipe (M11). */
    val ownerMemberId: String? = null,
    val visibility: Visibility = Visibility.SHARED,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    fun displayName(locale: Locale = Locale.getDefault()): String = when (locale.language) {
        "cs" -> nameCs ?: nameEn ?: name
        else -> nameEn ?: nameCs ?: name
    }

    companion object {
        /** Czech and US advice for cooked food is 3–4 days; the safer end (DATA_SOURCES §8). */
        const val DEFAULT_FRIDGE_DAYS = 3
        const val DEFAULT_FREEZER_MONTHS = 3
        val SERVINGS_RANGE = 1..50
    }
}

/** One ingredient with its raw weight. References the food by uuid (data-model conventions). */
data class RecipeIngredient(
    val id: String,
    val recipeId: String,
    val position: Int,
    val foodUuid: String,
    val grams: Double,
    val note: String? = null,
    /** An optional ingredient (a garnish, say): counted for allergens, not for nutrition. */
    val optional: Boolean = false,
)

/** One step. [passive] steps (oven, simmering) let cook mode run other steps meanwhile (M4). */
data class RecipeStep(
    val id: String,
    val recipeId: String,
    val position: Int,
    val text: String,
    val textCs: String? = null,
    val textEn: String? = null,
    val minutes: Int? = null,
    val passive: Boolean = false,
    /** An [Equipment] name or `STOVE`. */
    val equipment: String? = null,
) {
    fun displayText(locale: Locale = Locale.getDefault()): String = when (locale.language) {
        "cs" -> textCs ?: textEn ?: text
        else -> textEn ?: textCs ?: text
    }
}

/** Nutrition of a recipe (M3.1). [per100gCooked] only once the cooked weight is known (A9). */
data class RecipeNutrition(
    val total: Nutrients,
    val perServing: Nutrients,
    val per100gCooked: Nutrients?,
    /** Raw weight of the counted ingredients. */
    val rawGrams: Double,
    /** Ingredients whose food is missing (deleted); their nutrients are not counted. */
    val missingFoods: Int,
)

/** Recipe maths and the hard diet filters (M3.1, algorithms A9 and A15). */
object Recipes {

    /**
     * Total, per serving and per 100 g cooked. Optional ingredients are left out. A nutrient no
     * counted ingredient knows stays unknown; one that some ingredients know is their sum.
     */
    fun nutrition(
        ingredients: List<RecipeIngredient>,
        foods: Map<String, Food>,
        servings: Int,
        learnedYield: Double?,
    ): RecipeNutrition {
        val counted = ingredients.filter { !it.optional }
        var missing = 0
        var total: Nutrients? = null
        var rawGrams = 0.0
        for (ingredient in counted) {
            val food = foods[ingredient.foodUuid]
            if (food == null) {
                missing++
                continue
            }
            val portion = food.per100g.forGramsOfPer100g(ingredient.grams)
            total = total?.plus(portion) ?: portion
            rawGrams += ingredient.grams
        }
        val sum = total ?: Nutrients(kcal = 0.0)
        val perServing = sum.scaledBy(1.0 / servings.coerceAtLeast(1))
        val cookedGrams = learnedYield?.takeIf { it > 0 }?.let { rawGrams * it }
        val per100g = cookedGrams?.takeIf { it > 0 }?.let { sum.scaledBy(100.0 / it) }
        return RecipeNutrition(sum, perServing, per100g, rawGrams, missing)
    }

    /** All allergens of all ingredients, optional ones included (better safe). */
    fun allergens(ingredients: List<RecipeIngredient>, foods: Map<String, Food>): Set<Allergen> =
        ingredients.flatMap { foods[it.foodUuid]?.allergens.orEmpty() }.toSet()

    fun animalFlags(ingredients: List<RecipeIngredient>, foods: Map<String, Food>): Set<AnimalFlag> =
        ingredients.flatMap { foods[it.foodUuid]?.animalFlags.orEmpty() }.toSet()

    /** A15 hard filter: may someone on [diet] eat food with these animal flags? */
    fun fitsDiet(flags: Set<AnimalFlag>, diet: Diet): Boolean = when (diet) {
        Diet.OMNIVORE -> true
        Diet.VEGAN -> flags.isEmpty()
        Diet.VEGETARIAN -> flags.none { it == AnimalFlag.FISH || it == AnimalFlag.SHELLFISH || it == AnimalFlag.MEAT || it == AnimalFlag.PORK }
        Diet.PESCATARIAN -> flags.none { it == AnimalFlag.MEAT || it == AnimalFlag.PORK }
        Diet.NO_PORK -> AnimalFlag.PORK !in flags
    }

    /** A suggested box code from the name: the first letter, or the first letters of two words. */
    fun suggestShortCode(name: String, taken: Set<String> = emptySet()): String {
        val words = name.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val letters = words.mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        val first = letters.firstOrNull()?.toString() ?: "R"
        if (first !in taken) return first
        val two = letters.take(2).joinToString("")
        if (two.length == 2 && two !in taken) return two
        val fromWord = words.firstOrNull()?.filter { it.isLetter() }?.take(2)?.uppercase()
        return fromWord?.takeIf { it.length == 2 && it !in taken } ?: first
    }
}
