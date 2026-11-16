package com.calore.tracker.data.shop

import com.calore.tracker.data.foods.GenericFoodImporter
import com.calore.tracker.data.repository.StoreProductsFile
import com.calore.tracker.data.repository.StoresFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 0.14: the bundled shop products (`tools/store-products`) are real Open Food Facts products of a
 * shop's own brand, each matched to a basic food. No prices (DATA_SOURCES section 4).
 */
class StoreProductsSeedTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = json.decodeFromString(StoreProductsFile.serializer(), File("src/main/assets/seed/cz/store-products.json").readText())
    private val stores = json.decodeFromString(StoresFile.serializer(), File("src/main/assets/seed/cz/stores.json").readText()).stores.map { it.key }.toSet()
    private val foods = File("src/main/assets/foods/generic-cz.json").inputStream().use { GenericFoodImporter.parse(it) }.foods.map { it.key }.toSet()

    @Test
    fun everyProductBelongsToAKnownShopAndBasicFood() {
        assertTrue(file.version >= 1)
        assertEquals("CZ", file.country)
        assertTrue("${file.products.size} products", file.products.size >= 100)
        assertEquals(file.products.size, file.products.map { it.key }.toSet().size)
        for (product in file.products) {
            assertTrue(product.key, product.store in stores)
            assertNotNull(product.key, product.food)
            assertTrue(product.key, product.food in foods)
            assertEquals(product.key, "${product.store}:off-${product.barcode}", product.key)
        }
    }

    @Test
    fun packsAreRealAndThereAreNoInventedPrices() {
        for (product in file.products) {
            assertTrue(product.key, product.name.isNotBlank())
            assertTrue(product.key, product.barcode!!.all(Char::isDigit) && product.barcode!!.length in 8..14)
            assertTrue(product.key, product.packAmount > 0 && product.packAmount <= 25_000)
            assertTrue(product.key, product.packUnit in setOf("G", "ML"))
            assertEquals(product.key, "off:${product.barcode}", product.source)
            assertNull(product.key, product.priceCzk)
            assertNull(product.key, product.priceDate)
        }
    }

    @Test
    fun theBigShopsAllHaveProducts() {
        val byStore = file.products.groupBy { it.store }
        for (store in listOf("cz:lidl", "cz:kaufland", "cz:tesco", "cz:albert", "cz:billa")) {
            assertTrue("$store: ${byStore[store]?.size}", (byStore[store]?.size ?: 0) >= 5)
        }
    }
}
