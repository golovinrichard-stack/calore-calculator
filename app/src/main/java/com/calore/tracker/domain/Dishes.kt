package com.calore.tracker.domain

/** Logging a dish by hand (M3.5): a recipe eaten by servings, or by grams once its yield is known. */
object Dishes {

    /** Servings someone can log: more than none, and not a whole pot by mistake. */
    val SERVINGS_RANGE = 0.1..20.0

    /** [servings] of a recipe: exactly [servings] × the per-serving values. */
    fun forServings(nutrition: RecipeNutrition, servings: Double): Nutrients = nutrition.perServing.scaledBy(servings)

    /** [grams] of the cooked dish; null until the cooked weight per raw weight is known (A9). */
    fun forGrams(nutrition: RecipeNutrition, grams: Double): Nutrients? = nutrition.per100gCooked?.forGramsOfPer100g(grams)

    /** Parses typed servings ("1,5" works too); null when outside [SERVINGS_RANGE]. */
    fun parseServings(text: String): Double? = parseDecimalIn(text, SERVINGS_RANGE)
}
