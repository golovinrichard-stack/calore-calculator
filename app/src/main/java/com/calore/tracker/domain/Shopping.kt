package com.calore.tracker.domain

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

/** Food at home, in grams (pieces already converted with `gramsPerPiece`). */
data class PantryLot(
    val id: String,
    val foodUuid: String,
    val grams: Double,
    val bestBefore: LocalDate?,
    val bought: LocalDate,
    /** Set aside for another planned cook (A16 step 2). */
    val reservedForOtherCook: Boolean = false,
)

/** An exact product at a shop that can be bought for a food. */
data class ProductOption(
    val id: String,
    val storeId: String,
    val foodUuid: String,
    val packGrams: Double,
    val preferred: Boolean = false,
    /** Latest price per gram in haléře, if known (A18). */
    val pricePerGram: Double? = null,
)

data class StoreInfo(
    val id: String,
    val favorite: Boolean,
    val defaultForSections: Set<ShopSection>,
    val sectionOrder: List<ShopSection>,
)

/** One food the plan needs: grams, its shop section, and the first cook day that needs it. */
data class Need(val foodUuid: String, val grams: Double, val section: ShopSection, val cookDate: LocalDate)

/** A generated shopping line (A16). Without a product it shows the grams and "Pick a product". */
data class ShoppingLine(
    val foodUuid: String,
    val section: ShopSection,
    val toBuyGrams: Double,
    val product: ProductOption?,
    val packs: Int?,
    val storeId: String?,
)

/** The shopping list (algorithms A16). */
object Shopping {

    /** Usable pantry food for a cook on [cookDate]: not reserved, and good until then (or no date). */
    fun available(lots: List<PantryLot>, foodUuid: String, cookDate: LocalDate): Double =
        lots.filter { it.foodUuid == foodUuid && !it.reservedForOtherCook && (it.bestBefore == null || !it.bestBefore.isBefore(cookDate)) }
            .sumOf { it.grams }

    fun toBuy(need: Double, available: Double): Double = max(0.0, need - available)

    /**
     * The product to buy: the preferred one at the shop that is the default for this section;
     * otherwise the cheapest known price per gram at a favourite shop; otherwise none.
     */
    fun chooseProduct(section: ShopSection, products: List<ProductOption>, stores: List<StoreInfo>): ProductOption? {
        val defaultStore = stores.firstOrNull { section in it.defaultForSections }
        if (defaultStore != null) {
            products.firstOrNull { it.storeId == defaultStore.id && it.preferred }?.let { return it }
        }
        val favourites = stores.filter { it.favorite }.map { it.id }.toSet()
        return products.filter { it.storeId in favourites && it.pricePerGram != null }.minByOrNull { it.pricePerGram!! }
    }

    fun packs(toBuyGrams: Double, packGrams: Double): Int = ceil(toBuyGrams / packGrams - 1e-9).toInt()

    /** Every food to buy, grouped by shop and sorted by the shop's section order. */
    fun lines(needs: List<Need>, lots: List<PantryLot>, products: List<ProductOption>, stores: List<StoreInfo>): List<ShoppingLine> {
        val lines = needs.groupBy { it.foodUuid }.mapNotNull { (food, list) ->
            val grams = list.sumOf { it.grams }
            val first = list.minBy { it.cookDate }
            val buy = toBuy(grams, available(lots, food, first.cookDate))
            if (buy <= 0.0) return@mapNotNull null
            val product = chooseProduct(first.section, products.filter { it.foodUuid == food }, stores)
            ShoppingLine(food, first.section, buy, product, product?.let { packs(buy, it.packGrams) }, product?.storeId)
        }
        val orderOf = stores.associate { it.id to it.sectionOrder }
        return lines.sortedWith(
            compareBy<ShoppingLine>({ it.storeId == null }, { it.storeId })
                .thenBy { line -> (orderOf[line.storeId] ?: ShopSection.entries).indexOf(line.section).let { if (it < 0) Int.MAX_VALUE else it } },
        )
    }
}

/** Pantry rules (algorithms A17). */
object Pantry {

    /** A fridge item gets at most this many more days once opened (*tunable*). */
    const val OPENED_FRIDGE_DAYS = 3L

    /** Rough default best-before and storage by shop section, when the pack's date isn't entered. */
    fun defaults(section: ShopSection): Pair<Long, Storage> = when (section) {
        ShopSection.MEAT, ShopSection.FISH -> 2L to Storage.FRIDGE
        ShopSection.DAIRY, ShopSection.VEGETABLES -> 5L to Storage.FRIDGE
        ShopSection.FRUIT -> 5L to Storage.CUPBOARD
        ShopSection.BAKERY -> 3L to Storage.CUPBOARD
        ShopSection.EGGS -> 21L to Storage.FRIDGE
        ShopSection.FROZEN -> 90L to Storage.FREEZER
        else -> 180L to Storage.CUPBOARD
    }

    fun defaultBestBefore(section: ShopSection, bought: LocalDate): LocalDate = bought.plusDays(defaults(section).first)

    /** After opening, a fridge item is good for at most 3 more days (never longer than its date). */
    fun bestBeforeAfterOpening(bestBefore: LocalDate?, opened: LocalDate, storage: Storage): LocalDate? {
        if (storage != Storage.FRIDGE) return bestBefore
        val limit = opened.plusDays(OPENED_FRIDGE_DAYS)
        return if (bestBefore == null || limit.isBefore(bestBefore)) limit else bestBefore
    }

    /**
     * Takes [needGrams] first-in-first-out: earliest best-before first (no date last), then
     * earliest bought. Returns the grams taken per lot and what was missing.
     */
    fun takeFifo(lots: List<PantryLot>, needGrams: Double): Pair<List<Pair<String, Double>>, Double> {
        var left = needGrams
        val taken = mutableListOf<Pair<String, Double>>()
        for (lot in lots.sortedWith(compareBy<PantryLot>({ it.bestBefore == null }, { it.bestBefore }, { it.bought }))) {
            if (left <= 0) break
            val grams = minOf(lot.grams, left)
            taken += lot.id to grams
            left -= grams
        }
        return taken to max(0.0, left)
    }
}

/** Costs (algorithms A18), in haléře. */
object Costs {

    fun pricePerGram(priceHalers: Long, packGrams: Double): Double = priceHalers / packGrams

    /** Sum of grams × price per gram; incomplete when a price is missing. */
    fun batchCost(ingredients: List<Pair<Double, Double?>>): Pair<Double, Boolean> =
        ingredients.sumOf { (grams, perGram) -> grams * (perGram ?: 0.0) } to ingredients.any { it.second == null }

    /** Box cost = batch cost × part grams ÷ cooked grams, over its parts. */
    fun boxCost(parts: List<Triple<Double, Double, Double>>): Double = parts.sumOf { (grams, batchCost, cookedGrams) -> batchCost * grams / cookedGrams }

    fun costPer100gProtein(cost: Double, proteinGrams: Double): Double? = if (proteinGrams > 0) cost / proteinGrams * 100 else null
}

/** What a recipe costs (A18), from the price per gram of each food; [missing] foods have no price yet. */
data class RecipeCost(val totalHalers: Double, val perServingHalers: Double, val per100gProteinHalers: Double?, val missing: Int) {
    val complete: Boolean get() = missing == 0
}

object RecipeCosts {

    /** The food's price per gram (haléře): its preferred product's latest price, otherwise the cheapest known. */
    fun pricePerGramByFood(products: List<ProductOption>): Map<String, Double> =
        products.filter { it.pricePerGram != null }.groupBy { it.foodUuid }.mapValues { (_, options) ->
            (options.firstOrNull { it.preferred } ?: options.minBy { it.pricePerGram!! }).pricePerGram!!
        }

    fun cost(ingredients: List<RecipeIngredient>, nutrition: RecipeNutrition, servings: Int, prices: Map<String, Double>): RecipeCost {
        val counted = ingredients.filter { !it.optional }
        val (total, _) = Costs.batchCost(counted.map { it.grams to prices[it.foodUuid] })
        val missing = counted.count { prices[it.foodUuid] == null }
        return RecipeCost(total, total / servings.coerceAtLeast(1), nutrition.total.protein?.let { Costs.costPer100gProtein(total, it) }, missing)
    }
}
