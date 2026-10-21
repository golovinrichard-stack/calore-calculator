package com.calore.tracker.domain

/** Physical and sanity bounds shared by input validation and data import. */
object Limits {
    /** Pure fat is about 900 kcal per 100 g; the margin allows for label rounding. */
    const val MAX_KCAL_PER_100G = 1000.0

    /** No single nutrient can exceed the weight of the food it is in. */
    const val MAX_GRAMS_PER_100G = 100.0

    /** Protein, carbs and fat together, with room for label rounding. */
    const val MAX_MACROS_SUM_PER_100G = 101.0

    const val MAX_PORTION_GRAMS = 10_000.0

    const val MAX_NAME_LENGTH = 120

    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 500.0

    const val MIN_GOAL_KCAL = 500
    const val MAX_GOAL_KCAL = 10_000
    const val MAX_GOAL_MACRO_G = 1_000
}
