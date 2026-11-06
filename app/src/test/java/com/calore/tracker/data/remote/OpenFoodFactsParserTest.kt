package com.calore.tracker.data.remote

import org.junit.Assert.assertEquals
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.Allergen
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsParserTest {

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("off/$name")
        assertNotNull("missing fixture $name", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun parse(name: String): List<RemoteFood> {
        val foods = OpenFoodFactsParser.parseSearch(fixture(name))
        assertNotNull(foods)
        return foods!!
    }

    @Test
    fun parsesRealSearchResponse() {
        val foods = parse("search_oats.json")

        assertEquals(3, foods.size)
        val wholeOats = foods.first { it.barcode == "6281055001080" }
        assertEquals("Hanaa Whole Oats", wholeOats.name)
        assertEquals("Hanaa", wholeOats.brand)
        assertEquals(370.0, wholeOats.per100g.kcal, 1e-9)
        assertEquals(12.1, wholeOats.per100g.protein!!, 1e-9)
        assertEquals(56.1, wholeOats.per100g.carbs!!, 1e-9)
        assertEquals(8.4, wholeOats.per100g.fat!!, 1e-9)
        assertEquals(10.8, wholeOats.per100g.fiber!!, 1e-9)
        assertEquals(1.0, wholeOats.per100g.sugar!!, 1e-9)
    }

    @Test
    fun brandListEntriesAreTrimmed() {
        val quaker = parse("search_oats.json").first { it.barcode == "5000108236832" }

        assertEquals("Quaker", quaker.brand)
        assertNull("fiber is absent in this product", quaker.per100g.fiber)
    }

    @Test
    fun productsWithoutCaloriesAreSkipped() {
        val foods = parse("search_nutella.json")

        // 10 hits, 3 of which have no nutrition data at all.
        assertEquals(7, foods.size)
        assertTrue(foods.none { it.barcode == "12214181" })
    }

    @Test
    fun convertsKilojoulesAndReadsNumericStrings() {
        val food = parse("search_edge_cases.json").first { it.barcode == "1000000000001" }

        assertEquals("Kilojoules only", food.name)
        assertEquals("Brand A", food.brand)
        assertEquals(100.0, food.per100g.kcal, 1e-9)
        assertEquals(3.5, food.per100g.protein!!, 1e-9)
        assertNull("non-numeric fat is unknown", food.per100g.fat)
        assertEquals(56.0, food.servingGrams!!, 1e-9)
    }

    @Test
    fun dropsImpossibleValues() {
        val foods = parse("search_edge_cases.json")

        assertTrue("5000 kcal per 100 g is impossible", foods.none { it.barcode == "1000000000002" })

        val badMacros = foods.first { it.barcode == "1000000000003" }
        assertEquals("Impossible protein", badMacros.name)
        assertEquals("Real Brand", badMacros.brand)
        assertNull(badMacros.per100g.protein)
        assertNull(badMacros.per100g.carbs)
        assertEquals(30.0, badMacros.servingGrams!!, 1e-9)
    }

    @Test
    fun skipsMissingNamesBarcodesAndDuplicates() {
        val barcodes = parse("search_edge_cases.json").map { it.barcode }

        assertEquals(listOf("1000000000001", "1000000000003", "1000000000006"), barcodes)
    }

    @Test
    fun ignoresNonGramServings() {
        val juice = parse("search_edge_cases.json").first { it.barcode == "1000000000006" }

        assertNull(juice.servingGrams)
        assertNull(juice.per100g.protein)
    }

    @Test
    fun malformedBodiesReturnNull() {
        assertNull(OpenFoodFactsParser.parseSearch(""))
        assertNull(OpenFoodFactsParser.parseSearch("<html>Page temporarily unavailable</html>"))
        assertNull(OpenFoodFactsParser.parseSearch("[1, 2, 3]"))
        assertNull(OpenFoodFactsParser.parseSearch("""{"hits": "nope"}"""))
    }

    @Test
    fun emptyHitsIsAnEmptyList() {
        assertEquals(emptyList<RemoteFood>(), OpenFoodFactsParser.parseSearch("""{"hits": []}"""))
    }

    @Test
    fun czechSearchReadsPackSizeSectionAndAllergens() {
        val foods = parse("search_tvaroh_cz.json")
        assertEquals("one of the four real products has no nutrition data and is skipped", 3, foods.size)
        val first = foods.first()
        assertEquals("Tvaroh z Poděbrad polotučný", first.name)
        assertEquals(250.0, first.packGrams!!, 0.0)
        assertEquals(ShopSection.DAIRY, first.section)
        assertEquals(setOf(Allergen.MILK), first.allergens)
        assertEquals("no categories", ShopSection.OTHER, foods.last().section)
    }

    @Test
    fun packSizesStoresAllergensAndMicronutrients() {
        val foods = parse("search_details.json").associateBy { it.barcode }

        val biscuits = foods.getValue("1111111111116")
        assertEquals("kg is not g or ml, so the quantity text is used", 1500.0, biscuits.packGrams!!, 1e-9)
        assertEquals(listOf("lidl", "albert"), biscuits.stores)
        assertEquals("unknown allergen tags are skipped", setOf(Allergen.GLUTEN, Allergen.SOY), biscuits.allergens)
        assertEquals(ShopSection.SNACKS, biscuits.section)
        assertEquals("grams become milligrams", 2.1, biscuits.per100g.ironMg!!, 1e-9)
        assertEquals(50.0, biscuits.per100g.vitaminCMg!!, 1e-9)
        assertNull("20 g of magnesium per 100 g is impossible", biscuits.per100g.magnesiumMg)

        val milk = foods.getValue("2222222222222")
        assertEquals(500.0, milk.packGrams!!, 0.0)
        assertEquals(ShopSection.DAIRY, milk.section)

        val water = foods.getValue("3333333333338")
        assertEquals(1500.0, water.packGrams!!, 1e-9)
        assertEquals(ShopSection.DRINKS, water.section)

        assertNull(foods.getValue("4444444444444").packGrams)
    }

    @Test
    fun searchWordsDropQuerySyntax() {
        assertEquals("tvaroh", OpenFoodFactsClient.searchWords("  tvaroh  "))
        assertEquals("brands Milko tvaroh", OpenFoodFactsClient.searchWords("brands:\"Milko\" (tvaroh)"))
        assertEquals("kuřecí prsa", OpenFoodFactsClient.searchWords("kuřecí*  prsa?"))
        assertEquals("", OpenFoodFactsClient.searchWords(":::"))
    }

    @Test
    fun barcodeLookupReadsTheProduct() {
        val lookup = OpenFoodFactsParser.parseProduct(fixture("product_tvaroh.json"), "85936126")
        val food = (lookup as OpenFoodFactsParser.ProductLookup.Found).food
        assertEquals("85936126", food.barcode)
        assertEquals("Tvaroh z Poděbrad polotučný", food.name)
        assertEquals("Milko", food.brand)
        assertEquals(90.0, food.per100g.kcal, 0.0)
        assertEquals(10.0, food.per100g.protein!!, 0.0)
        assertEquals(250.0, food.packGrams!!, 0.0)
        assertEquals(setOf(Allergen.MILK), food.allergens)
        assertEquals(ShopSection.DAIRY, food.section)
    }

    @Test
    fun unknownBarcodesAndBrokenBodies() {
        assertEquals(
            OpenFoodFactsParser.ProductLookup.NotFound,
            OpenFoodFactsParser.parseProduct(fixture("product_not_found.json"), "8590000000000"),
        )
        assertEquals(
            "a product without calories can't be logged",
            OpenFoodFactsParser.ProductLookup.NotFound,
            OpenFoodFactsParser.parseProduct("""{"status":1,"product":{"product_name":"X","nutriments":{}}}""", "1"),
        )
        assertEquals(OpenFoodFactsParser.ProductLookup.Unreadable, OpenFoodFactsParser.parseProduct("<html>", "1"))
    }
}
