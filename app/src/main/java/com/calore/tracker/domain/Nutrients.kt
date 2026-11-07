package com.calore.tracker.domain

/**
 * Energy and macronutrients for some amount of food.
 *
 * Calories are always known. The other values can be missing when a food database
 * entry is incomplete, which is different from a known value of zero.
 */
data class Nutrients(
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    /** Micronutrients for the cycle tips (M7), in milligrams. Mostly known for basic foods only. */
    val ironMg: Double? = null,
    val magnesiumMg: Double? = null,
    val vitaminCMg: Double? = null,
) {
    /** Adds two amounts. A missing value on one side keeps the known value of the other. */
    operator fun plus(other: Nutrients): Nutrients = Nutrients(
        kcal = kcal + other.kcal,
        protein = protein.plusKnown(other.protein),
        carbs = carbs.plusKnown(other.carbs),
        fat = fat.plusKnown(other.fat),
        fiber = fiber.plusKnown(other.fiber),
        sugar = sugar.plusKnown(other.sugar),
        ironMg = ironMg.plusKnown(other.ironMg),
        magnesiumMg = magnesiumMg.plusKnown(other.magnesiumMg),
        vitaminCMg = vitaminCMg.plusKnown(other.vitaminCMg),
    )

    fun scaledBy(factor: Double): Nutrients = Nutrients(
        kcal = kcal * factor,
        protein = protein?.times(factor),
        carbs = carbs?.times(factor),
        fat = fat?.times(factor),
        fiber = fiber?.times(factor),
        sugar = sugar?.times(factor),
        ironMg = ironMg?.times(factor),
        magnesiumMg = magnesiumMg?.times(factor),
        vitaminCMg = vitaminCMg?.times(factor),
    )

    /** Treats these values as "per 100 g" and returns the amount contained in [grams]. */
    fun forGramsOfPer100g(grams: Double): Nutrients = scaledBy(grams / 100.0)

    companion object {
        /**
         * The starting point for day totals. Micronutrients stay unknown until a food with known
         * values is added, because most products don't list them.
         */
        val ZERO = Nutrients(kcal = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0, fiber = 0.0, sugar = 0.0)
    }
}

private fun Double?.plusKnown(other: Double?): Double? = when {
    this == null -> other
    other == null -> this
    else -> this + other
}
