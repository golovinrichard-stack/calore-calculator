package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The test cases of algorithms.md A16–A18, exactly as written there. */
class ShoppingAlgorithmsTest {

    private val cookDay = LocalDate.of(2026, 11, 15)
    private val lidl = StoreInfo("lidl", favorite = true, defaultForSections = setOf(ShopSection.MEAT), sectionOrder = ShopSection.entries)
    private val chicken500 = ProductOption("p1", "lidl", "chicken", packGrams = 500.0, preferred = true)
    private fun need(grams: Double) = listOf(Need("chicken", grams, ShopSection.MEAT, cookDay))

    // A16. Shopping list

    @Test
    fun a16NothingAtHome() {
        val line = Shopping.lines(need(1150.0), emptyList(), listOf(chicken500), listOf(lidl)).single()
        assertEquals(3, line.packs)
        assertEquals("350 g extra goes to the pantry", 350.0, line.packs!! * 500.0 - 1150.0, 0.0)
    }

    @Test
    fun a16SomeChickenAtHome() {
        val lots = listOf(PantryLot("l1", "chicken", 200.0, cookDay.plusDays(1), cookDay.minusDays(1)))
        val line = Shopping.lines(need(1150.0), lots, listOf(chicken500), listOf(lidl)).single()
        assertEquals(2, line.packs)
        assertEquals("50 g extra", 50.0, line.packs!! * 500.0 - line.toBuyGrams, 0.0)
    }

    @Test
    fun a16FoodThatExpiresBeforeTheCookDoesNotCount() {
        val lots = listOf(PantryLot("l1", "chicken", 400.0, cookDay.minusDays(1), cookDay.minusDays(3)))
        assertEquals(1, Shopping.lines(need(300.0), lots, listOf(chicken500), listOf(lidl)).single().packs)
    }

    @Test
    fun a16ProductChoice() {
        val tesco = StoreInfo("tesco", favorite = true, defaultForSections = emptySet(), sectionOrder = ShopSection.entries)
        val cheap = ProductOption("t1", "tesco", "chicken", 400.0, pricePerGram = 0.25)
        val dear = ProductOption("t2", "tesco", "chicken", 400.0, pricePerGram = 0.30)
        assertEquals("the default shop's preferred product", chicken500, Shopping.chooseProduct(ShopSection.MEAT, listOf(cheap, chicken500), listOf(lidl, tesco)))
        assertEquals("otherwise the cheapest per gram", cheap, Shopping.chooseProduct(ShopSection.DAIRY, listOf(dear, cheap), listOf(lidl, tesco)))
        assertNull("otherwise none", Shopping.chooseProduct(ShopSection.DAIRY, emptyList(), listOf(lidl)))
        val line = Shopping.lines(listOf(Need("rice", 300.0, ShopSection.GRAINS, cookDay)), emptyList(), emptyList(), listOf(lidl)).single()
        assertNull(line.packs)
        assertEquals(300.0, line.toBuyGrams, 0.0)
    }

    @Test
    fun a16EnoughAtHomeMeansNoLine() {
        val lots = listOf(PantryLot("l1", "chicken", 2000.0, null, cookDay))
        assertTrue(Shopping.lines(need(1150.0), lots, listOf(chicken500), listOf(lidl)).isEmpty())
    }

    // A17. Pantry

    @Test
    fun a17FirstInFirstOutAndShortPantry() {
        val lots = listOf(
            PantryLot("late", "chicken", 500.0, cookDay.plusDays(3), cookDay),
            PantryLot("early", "chicken", 500.0, cookDay.plusDays(1), cookDay),
            PantryLot("nodate", "chicken", 500.0, null, cookDay.minusDays(5)),
        )
        val (taken, short) = Pantry.takeFifo(lots, 1150.0)
        assertEquals(listOf("early" to 500.0, "late" to 500.0, "nodate" to 150.0), taken)
        assertEquals(0.0, short, 0.0)
        assertEquals(150.0, Pantry.takeFifo(lots.take(2), 1150.0).second, 0.0)
    }

    @Test
    fun a17DefaultDates() {
        val bought = LocalDate.of(2026, 11, 6)
        assertEquals(2L to Storage.FRIDGE, Pantry.defaults(ShopSection.MEAT))
        assertEquals(2L to Storage.FRIDGE, Pantry.defaults(ShopSection.FISH))
        assertEquals(5L to Storage.FRIDGE, Pantry.defaults(ShopSection.DAIRY))
        assertEquals(5L to Storage.FRIDGE, Pantry.defaults(ShopSection.VEGETABLES))
        assertEquals(5L to Storage.CUPBOARD, Pantry.defaults(ShopSection.FRUIT))
        assertEquals(3L to Storage.CUPBOARD, Pantry.defaults(ShopSection.BAKERY))
        assertEquals(21L to Storage.FRIDGE, Pantry.defaults(ShopSection.EGGS))
        assertEquals(90L to Storage.FREEZER, Pantry.defaults(ShopSection.FROZEN))
        assertEquals(180L to Storage.CUPBOARD, Pantry.defaults(ShopSection.GRAINS))
        assertEquals(LocalDate.of(2026, 11, 8), Pantry.defaultBestBefore(ShopSection.MEAT, bought))
    }

    @Test
    fun a17AfterOpening() {
        val opened = LocalDate.of(2026, 11, 6)
        assertEquals(LocalDate.of(2026, 11, 9), Pantry.bestBeforeAfterOpening(LocalDate.of(2026, 11, 20), opened, Storage.FRIDGE))
        assertEquals(LocalDate.of(2026, 11, 7), Pantry.bestBeforeAfterOpening(LocalDate.of(2026, 11, 7), opened, Storage.FRIDGE))
        assertEquals(LocalDate.of(2027, 1, 1), Pantry.bestBeforeAfterOpening(LocalDate.of(2027, 1, 1), opened, Storage.CUPBOARD))
    }

    // A18. Costs and waste

    @Test
    fun a18ChickenCost() {
        val perGram = Costs.pricePerGram(13_990, 500.0)
        assertEquals(36.37, Math.round(130 * perGram).toDouble() / 100, 0.0)
        val (cost, incomplete) = Costs.batchCost(listOf(130.0 to perGram, 100.0 to null))
        assertEquals(3637.4, cost, 1e-9)
        assertTrue(incomplete)
        assertEquals(1000.0, Costs.boxCost(listOf(Triple(500.0, 6000.0, 3000.0))), 1e-9)
        assertEquals(50.0, Costs.costPer100gProtein(100.0, 200.0)!!, 1e-9)
    }
}
