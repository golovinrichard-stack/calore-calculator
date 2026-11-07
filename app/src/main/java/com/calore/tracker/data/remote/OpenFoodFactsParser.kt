package com.calore.tracker.data.remote

import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Limits
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.ShopSection
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** A product from Open Food Facts that has enough data to be logged. Values are per 100 g. */
data class RemoteFood(
    val barcode: String,
    val name: String,
    val brand: String?,
    val per100g: Nutrients,
    val servingGrams: Double?,
    /** Pack size in grams (or ml, counted as grams), when the product lists one. */
    val packGrams: Double? = null,
    /** Store tags such as `lidl` or `albert`, as Open Food Facts spells them. */
    val stores: List<String> = emptyList(),
    /** Best guess from the product's categories; [ShopSection.OTHER] when unknown. */
    val section: ShopSection = ShopSection.OTHER,
    val allergens: Set<Allergen> = emptySet(),
)

/**
 * Reads Open Food Facts search responses as plain JSON trees.
 *
 * The database is crowd-sourced, so fields are often missing, sometimes strings instead
 * of numbers, and occasionally nonsense. Products without a name or calories are
 * dropped; individual impossible values are discarded rather than trusted.
 */
object OpenFoodFactsParser {
    private const val KJ_PER_KCAL = 4.184
    private const val MG_PER_G = 1000.0

    /** No food has more than 10 g of iron, magnesium or vitamin C per 100 g. */
    private const val MAX_MICRO_G_PER_100G = 10.0
    private const val MAX_STORES = 10
    private val SERVING_GRAMS = Regex("""(\d+(?:[.,]\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)
    private val PACK_QUANTITY = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|g|ml|cl|l)\b""", RegexOption.IGNORE_CASE)

    /** `categories_tags` to shop sections, most specific first (best effort, M2.3). */
    private val CATEGORY_SECTIONS: List<Pair<String, ShopSection>> = listOf(
        "en:dietary-supplements" to ShopSection.SUPPLEMENTS,
        "en:frozen-foods" to ShopSection.FROZEN,
        "en:canned-foods" to ShopSection.CANNED,
        "en:eggs" to ShopSection.EGGS,
        "en:cheeses" to ShopSection.DAIRY,
        "en:yogurts" to ShopSection.DAIRY,
        "en:milks" to ShopSection.DAIRY,
        "en:dairies" to ShopSection.DAIRY,
        "en:fishes" to ShopSection.FISH,
        "en:seafood" to ShopSection.FISH,
        "en:meats" to ShopSection.MEAT,
        "en:poultries" to ShopSection.MEAT,
        "en:breads" to ShopSection.BAKERY,
        "en:pastries" to ShopSection.BAKERY,
        "en:legumes" to ShopSection.LEGUMES,
        "en:pastas" to ShopSection.GRAINS,
        "en:rices" to ShopSection.GRAINS,
        "en:cereals-and-their-products" to ShopSection.GRAINS,
        "en:fruits" to ShopSection.FRUIT,
        "en:vegetables" to ShopSection.VEGETABLES,
        "en:vegetable-oils" to ShopSection.OILS,
        "en:fats" to ShopSection.OILS,
        "en:sauces" to ShopSection.SPICES_SAUCES,
        "en:condiments" to ShopSection.SPICES_SAUCES,
        "en:spices" to ShopSection.SPICES_SAUCES,
        "en:chocolates" to ShopSection.SNACKS,
        "en:sweet-snacks" to ShopSection.SNACKS,
        "en:salty-snacks" to ShopSection.SNACKS,
        "en:snacks" to ShopSection.SNACKS,
        "en:beverages" to ShopSection.DRINKS,
    )

    private val ALLERGEN_TAGS: Map<String, Allergen> = mapOf(
        "en:gluten" to Allergen.GLUTEN,
        "en:crustaceans" to Allergen.CRUSTACEANS,
        "en:eggs" to Allergen.EGGS,
        "en:fish" to Allergen.FISH,
        "en:peanuts" to Allergen.PEANUTS,
        "en:soybeans" to Allergen.SOY,
        "en:milk" to Allergen.MILK,
        "en:nuts" to Allergen.NUTS,
        "en:celery" to Allergen.CELERY,
        "en:mustard" to Allergen.MUSTARD,
        "en:sesame-seeds" to Allergen.SESAME,
        "en:sulphur-dioxide-and-sulphites" to Allergen.SULPHITES,
        "en:lupin" to Allergen.LUPIN,
        "en:molluscs" to Allergen.MOLLUSCS,
    )

    /** The answer to a barcode lookup (M2.4). */
    sealed interface ProductLookup {
        data class Found(val food: RemoteFood) : ProductLookup

        /** Open Food Facts doesn't know the barcode, or the product has no calories yet. */
        data object NotFound : ProductLookup

        data object Unreadable : ProductLookup
    }

    /** Reads `/api/v2/product/<barcode>`: `{"status": 1, "product": {...}}` or `status 0` for unknown. */
    fun parseProduct(body: String, barcode: String): ProductLookup {
        val root = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: return ProductLookup.Unreadable
        val status = (root["status"] as? JsonPrimitive)?.content
        if (status == "0") return ProductLookup.NotFound
        val product = root["product"] as? JsonObject ?: return ProductLookup.NotFound
        // The product object of this endpoint has no "code"; use the one asked for.
        val withCode = JsonObject(product + ("code" to JsonPrimitive(barcode)))
        return parseProduct(withCode)?.let { ProductLookup.Found(it) } ?: ProductLookup.NotFound
    }

    /** Returns null when the body is not a search response at all. */
    fun parseSearch(body: String): List<RemoteFood>? {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            return null
        }
        val hits = (root as? JsonObject)?.get("hits") as? JsonArray ?: return null
        return hits
            .mapNotNull { (it as? JsonObject)?.let(::parseProduct) }
            .distinctBy { it.barcode }
    }

    internal fun parseProduct(product: JsonObject): RemoteFood? {
        val barcode = product.text("code") ?: return null
        val name = product.text("product_name") ?: return null
        val nutriments = product["nutriments"] as? JsonObject ?: return null

        val kcal = nutriments.number("energy-kcal_100g")
            ?: nutriments.number("energy-kj_100g")?.div(KJ_PER_KCAL)
            ?: nutriments.number("energy_100g")?.div(KJ_PER_KCAL)
            ?: return null
        if (kcal < 0.0 || kcal > Limits.MAX_KCAL_PER_100G) return null

        return RemoteFood(
            barcode = barcode,
            name = name,
            brand = product.brand(),
            per100g = Nutrients(
                kcal = kcal,
                protein = nutriments.gramsPer100g("proteins_100g"),
                carbs = nutriments.gramsPer100g("carbohydrates_100g"),
                fat = nutriments.gramsPer100g("fat_100g"),
                fiber = nutriments.gramsPer100g("fiber_100g"),
                sugar = nutriments.gramsPer100g("sugars_100g"),
                ironMg = nutriments.milligramsPer100g("iron_100g"),
                magnesiumMg = nutriments.milligramsPer100g("magnesium_100g"),
                vitaminCMg = nutriments.milligramsPer100g("vitamin-c_100g"),
            ),
            servingGrams = product.servingGrams(),
            packGrams = product.packGrams(),
            stores = product.tags("stores_tags").take(MAX_STORES),
            section = product.section(),
            allergens = product.tags("allergens_tags").mapNotNull { ALLERGEN_TAGS[it] }.toSet(),
        )
    }

    /** `product_quantity` when it is in g or ml, otherwise the `quantity` text such as "250 g" or "1,5 l". */
    private fun JsonObject.packGrams(): Double? {
        val unit = text("product_quantity_unit")?.lowercase()
        val quantity = number("product_quantity")
        if (quantity != null && (unit == null || unit == "g" || unit == "ml")) {
            return quantity.takeIf { it > 0.0 && it <= MAX_PACK_GRAMS }
        }
        val match = text("quantity")?.let { PACK_QUANTITY.find(it) } ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val grams = when (match.groupValues[2].lowercase()) {
            "kg", "l" -> amount * 1000.0
            "cl" -> amount * 10.0
            else -> amount
        }
        return grams.takeIf { it > 0.0 && it <= MAX_PACK_GRAMS }
    }

    private fun JsonObject.section(): ShopSection {
        val categories = tags("categories_tags").toSet()
        return CATEGORY_SECTIONS.firstOrNull { (tag, _) -> tag in categories }?.second ?: ShopSection.OTHER
    }

    private fun JsonObject.tags(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.textOrNull() }.orEmpty()

    /** Open Food Facts stores minerals and vitamins in grams per 100 g. */
    private fun JsonObject.milligramsPer100g(key: String): Double? =
        number(key)?.takeIf { it >= 0.0 && it <= MAX_MICRO_G_PER_100G }?.times(MG_PER_G)

    private const val MAX_PACK_GRAMS = 25_000.0

    /** OFF returns brands as a list in search results and a comma-separated string elsewhere. */
    private fun JsonObject.brand(): String? = when (val brands = this["brands"]) {
        is JsonArray -> brands.firstNotNullOfOrNull { (it as? JsonPrimitive)?.textOrNull() }
        is JsonPrimitive -> brands.textOrNull()?.split(',')?.firstNotNullOfOrNull { it.trim().ifEmpty { null } }
        else -> null
    }

    private fun JsonObject.servingGrams(): Double? {
        val unit = text("serving_quantity_unit")
        val quantity = number("serving_quantity")
        if (quantity != null && (unit == null || unit.equals("g", ignoreCase = true))) {
            return quantity.takeIf { it > 0.0 && it <= Limits.MAX_PORTION_GRAMS }
        }
        val match = text("serving_size")?.let { SERVING_GRAMS.find(it) } ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()
            ?.takeIf { it > 0.0 && it <= Limits.MAX_PORTION_GRAMS }
    }

    private fun JsonObject.gramsPer100g(key: String): Double? =
        number(key)?.takeIf { it >= 0.0 && it <= Limits.MAX_GRAMS_PER_100G }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.textOrNull()

    private fun JsonPrimitive.textOrNull(): String? =
        if (isString) content.trim().ifEmpty { null } else null

    /** Accepts JSON numbers and numeric strings ("12,5" too); rejects NaN and infinity. */
    private fun JsonObject.number(key: String): Double? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        val value = if (primitive.isString) {
            primitive.content.trim().replace(',', '.').toDoubleOrNull()
        } else {
            primitive.doubleOrNull
        }
        return value?.takeIf { it.isFinite() }
    }
}
