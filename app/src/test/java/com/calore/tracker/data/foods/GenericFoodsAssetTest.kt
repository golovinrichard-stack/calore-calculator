package com.calore.tracker.data.foods

import com.calore.tracker.data.db.JsonColumns
import com.calore.tracker.data.foods.GenericFoodImporter.Companion.toEntity
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.AnimalFlag
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** M2.2: the bundled basic foods file is complete and consistent. */
class GenericFoodsAssetTest {

    private val file = File("src/main/assets/foods/generic-cz.json").inputStream().use { GenericFoodImporter.parse(it) }

    @Test
    fun aboutThreeHundredFoodsWithUniqueKeys() {
        assertTrue("${file.foods.size} foods", file.foods.size in 250..350)
        assertEquals(file.foods.size, file.foods.map { it.key }.toSet().size)
        assertTrue(file.foods.all { it.key.startsWith("generic:") })
        assertTrue(file.datasetVersion >= 1)
        assertEquals("CZ", file.country)
    }

    @Test
    fun everyFoodHasNamesSourceAndMacros() {
        for (food in file.foods) {
            assertTrue(food.key, food.nameCs.isNotBlank() && food.nameEn.isNotBlank())
            assertTrue(food.key, food.sourceRef.startsWith("usda:") || food.sourceRef.startsWith("off:"))
            val n = food.per100g
            assertTrue(food.key, n.kcal in 0.0..1000.0)
            assertNotNull(food.key, n.protein)
            assertNotNull(food.key, n.carbs)
            assertNotNull(food.key, n.fat)
            assertTrue(food.key, n.protein!! + n.carbs!! + n.fat!! <= 101.0)
        }
    }

    @Test
    fun enumValuesAreKnown() {
        for (food in file.foods) {
            assertNotNull(food.key, JsonColumns.enumByName<ShopSection>(food.section))
            food.allergens.forEach { assertNotNull(food.key, JsonColumns.enumByName<Allergen>(it)) }
            food.animalFlags.forEach { assertNotNull(food.key, JsonColumns.enumByName<AnimalFlag>(it)) }
            food.storage?.let { assertNotNull(food.key, JsonColumns.enumByName<Storage>(it)) }
        }
    }

    @Test
    fun everyShopSectionIsCovered() {
        val sections = file.foods.map { it.section }.toSet()
        val expected = ShopSection.entries.map { it.name }.toSet()
        assertEquals(expected, sections)
    }

    @Test
    fun knownFoodsHaveTheirCzechNamesAndMicronutrients() {
        val chicken = file.foods.single { it.key == "generic:chicken_breast_raw" }
        assertTrue(chicken.nameCs.startsWith("Kuřecí prsa"))
        assertTrue(chicken.nameEn.startsWith("Chicken breast"))
        assertEquals(120.0, chicken.per100g.kcal, 0.0)
        assertNotNull("iron for the cycle tips", chicken.per100g.ironMg)

        val tvaroh = file.foods.single { it.key == "generic:tvaroh_semi" }
        assertTrue(tvaroh.sourceRef.startsWith("off:"))
        assertEquals(listOf("MILK"), tvaroh.allergens)
    }

    @Test
    fun rowsAreMarkedAsBasicFoods() {
        val entity = file.foods.first().toEntity(datasetVersion = 7, now = 1)
        assertTrue(entity.isGeneric)
        assertEquals(7, entity.datasetVersion)
        assertEquals(0, entity.lastUsedAt)
    }
}
