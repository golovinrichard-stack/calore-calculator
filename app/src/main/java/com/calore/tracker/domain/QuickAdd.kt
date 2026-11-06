package com.calore.tracker.domain

/** The quick-add form as typed (M2.5): a name, calories, and optionally protein, carbs and fat. */
data class QuickAddDraft(
    val name: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
) {
    enum class Field { NAME, KCAL, PROTEIN, CARBS, FAT }

    /** Blank optional fields are unknown, not zero. */
    fun validate(): QuickAddResult {
        val invalid = mutableSetOf<Field>()
        val trimmedName = name.trim()
        if (trimmedName.length > Limits.MAX_NAME_LENGTH) invalid += Field.NAME
        val kcalValue = parseDecimalIn(kcal, 0.0..MAX_KCAL).also { if (it == null) invalid += Field.KCAL }
        fun optional(text: String, field: Field): Double? {
            if (text.isBlank()) return null
            return parseDecimalIn(text, 0.0..MAX_MACRO_G).also { if (it == null) invalid += field }
        }
        val proteinValue = optional(protein, Field.PROTEIN)
        val carbsValue = optional(carbs, Field.CARBS)
        val fatValue = optional(fat, Field.FAT)
        if (invalid.isNotEmpty() || kcalValue == null) return QuickAddResult.Invalid(invalid)
        return QuickAddResult.Valid(
            name = trimmedName,
            nutrients = Nutrients(kcal = kcalValue, protein = proteinValue, carbs = carbsValue, fat = fatValue),
        )
    }

    companion object {
        /** A whole day's worth at most; more is surely a typo. */
        const val MAX_KCAL = 10_000.0
        const val MAX_MACRO_G = 1_000.0

        fun from(entry: DiaryEntry) = QuickAddDraft(
            name = entry.name,
            kcal = entry.nutrients.kcal.toPlainString(),
            protein = entry.nutrients.protein?.toPlainString().orEmpty(),
            carbs = entry.nutrients.carbs?.toPlainString().orEmpty(),
            fat = entry.nutrients.fat?.toPlainString().orEmpty(),
        )

        private fun Double.toPlainString(): String =
            java.math.BigDecimal(this).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}

sealed interface QuickAddResult {
    data class Valid(val name: String, val nutrients: Nutrients) : QuickAddResult
    data class Invalid(val fields: Set<QuickAddDraft.Field>) : QuickAddResult
}
