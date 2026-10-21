package com.calore.tracker.data.remote

import com.calore.tracker.domain.Limits
import com.calore.tracker.domain.Nutrients
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
    private val SERVING_GRAMS = Regex("""(\d+(?:[.,]\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)

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
            ),
            servingGrams = product.servingGrams(),
        )
    }

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
