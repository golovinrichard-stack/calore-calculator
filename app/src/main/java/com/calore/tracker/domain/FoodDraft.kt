package com.calore.tracker.domain

enum class FoodField { NAME, KCAL, PROTEIN, CARBS, FAT, FIBER, SUGAR, SERVING }

/** Raw text from the custom food form. Nutrient values are per 100 g. */
data class FoodDraft(
    val name: String = "",
    val brand: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val sugar: String = "",
    val servingGrams: String = "",
)

sealed interface FoodDraftResult {
    data class Valid(
        val name: String,
        val brand: String?,
        val per100g: Nutrients,
        val servingGrams: Double?,
    ) : FoodDraftResult

    data class Invalid(val fields: Set<FoodField>) : FoodDraftResult
}

/**
 * Checks the form. Name and calories are required; every other field may be left
 * blank, which records the value as unknown rather than zero.
 */
fun FoodDraft.validate(): FoodDraftResult {
    val errors = mutableSetOf<FoodField>()

    val cleanName = name.trim()
    if (cleanName.isEmpty() || cleanName.length > Limits.MAX_NAME_LENGTH) errors += FoodField.NAME

    val kcalValue = parseDecimalIn(kcal, 0.0..Limits.MAX_KCAL_PER_100G)
    if (kcalValue == null) errors += FoodField.KCAL

    val proteinValue = optionalGrams(protein, FoodField.PROTEIN, errors)
    val carbsValue = optionalGrams(carbs, FoodField.CARBS, errors)
    val fatValue = optionalGrams(fat, FoodField.FAT, errors)
    val fiberValue = optionalGrams(fiber, FoodField.FIBER, errors)
    val sugarValue = optionalGrams(sugar, FoodField.SUGAR, errors)

    val macroSum = listOfNotNull(proteinValue, carbsValue, fatValue).sum()
    if (macroSum > Limits.MAX_MACROS_SUM_PER_100G) {
        errors += setOf(FoodField.PROTEIN, FoodField.CARBS, FoodField.FAT)
    }

    val servingValue = if (servingGrams.isBlank()) {
        null
    } else {
        parsePortionGrams(servingGrams).also { if (it == null) errors += FoodField.SERVING }
    }

    if (errors.isNotEmpty() || kcalValue == null) return FoodDraftResult.Invalid(errors)

    return FoodDraftResult.Valid(
        name = cleanName,
        brand = brand.trim().takeIf { it.isNotEmpty() },
        per100g = Nutrients(
            kcal = kcalValue,
            protein = proteinValue,
            carbs = carbsValue,
            fat = fatValue,
            fiber = fiberValue,
            sugar = sugarValue,
        ),
        servingGrams = servingValue,
    )
}

private fun optionalGrams(text: String, field: FoodField, errors: MutableSet<FoodField>): Double? {
    if (text.isBlank()) return null
    return parseDecimalIn(text, 0.0..Limits.MAX_GRAMS_PER_100G).also { if (it == null) errors += field }
}

/** Fills the form from an existing food, for editing. */
fun Food.toDraft(format: (Double?) -> String): FoodDraft = FoodDraft(
    name = name,
    brand = brand.orEmpty(),
    kcal = format(per100g.kcal),
    protein = format(per100g.protein),
    carbs = format(per100g.carbs),
    fat = format(per100g.fat),
    fiber = format(per100g.fiber),
    sugar = format(per100g.sugar),
    servingGrams = format(servingGrams),
)
