package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QuickAddTest {

    @Test
    fun caloriesAloneAreEnough() {
        val result = QuickAddDraft(kcal = "650").validate() as QuickAddResult.Valid
        assertEquals("", result.name)
        assertEquals(650.0, result.nutrients.kcal, 0.0)
        assertNull("blank means unknown, not zero", result.nutrients.protein)
    }

    @Test
    fun macrosAndDecimalCommaAreRead() {
        val result = QuickAddDraft(name = "  Pizza v restauraci ", kcal = "850,5", protein = "32", carbs = "95", fat = "35").validate()
        result as QuickAddResult.Valid
        assertEquals("Pizza v restauraci", result.name)
        assertEquals(850.5, result.nutrients.kcal, 0.0)
        assertEquals(32.0, result.nutrients.protein!!, 0.0)
        assertEquals(35.0, result.nutrients.fat!!, 0.0)
    }

    @Test
    fun missingOrImpossibleValuesAreFlagged() {
        assertEquals(QuickAddResult.Invalid(setOf(QuickAddDraft.Field.KCAL)), QuickAddDraft().validate())
        assertEquals(QuickAddResult.Invalid(setOf(QuickAddDraft.Field.KCAL)), QuickAddDraft(kcal = "20000").validate())
        assertEquals(
            QuickAddResult.Invalid(setOf(QuickAddDraft.Field.PROTEIN, QuickAddDraft.Field.FAT)),
            QuickAddDraft(kcal = "500", protein = "-1", fat = "abc").validate(),
        )
        assertEquals(
            QuickAddResult.Invalid(setOf(QuickAddDraft.Field.NAME)),
            QuickAddDraft(name = "x".repeat(Limits.MAX_NAME_LENGTH + 1), kcal = "100").validate(),
        )
    }

    @Test
    fun anEntryFillsTheFormForEditing() {
        val entry = DiaryEntry(
            id = 1,
            date = LocalDate.of(2026, 11, 6),
            meal = MealType.DINNER,
            foodId = null,
            name = "Burger",
            grams = 0.0,
            nutrients = Nutrients(kcal = 720.0, protein = 38.5),
            loggedAt = 1,
            uuid = newId(),
            entryType = EntryType.QUICK_ADD,
        )
        assertEquals(QuickAddDraft(name = "Burger", kcal = "720", protein = "38.5"), QuickAddDraft.from(entry))
    }
}
