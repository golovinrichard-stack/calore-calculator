package com.calore.tracker.data.repository

import com.calore.tracker.data.db.FoodDao
import com.calore.tracker.data.db.JsonColumns
import com.calore.tracker.data.db.PriceEntity
import com.calore.tracker.data.db.ShopDao
import com.calore.tracker.data.db.StoreEntity
import com.calore.tracker.data.db.StoreProductEntity
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.domain.Costs
import com.calore.tracker.domain.ProductOption
import com.calore.tracker.domain.RecipeCosts
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.StoreInfo
import com.calore.tracker.domain.StableIds
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.time.Clock
import java.time.LocalDate

data class Store(
    val id: String,
    val key: String?,
    val name: String,
    val favorite: Boolean,
    val defaultForSections: Set<ShopSection>,
    val sectionOrder: List<ShopSection>,
) {
    val info: StoreInfo get() = StoreInfo(id, favorite, defaultForSections, sectionOrder)
}

enum class ProductSource { OWNER_RESEARCH, USER, OFF }

data class StoreProduct(
    val id: String,
    val storeId: String,
    val foodUuid: String?,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val packAmount: Double,
    val packUnit: String,
    val preferred: Boolean,
    val source: ProductSource,
    /** The latest price (haléře) and its date, if known. */
    val priceHalers: Long? = null,
    val priceDate: LocalDate? = null,
) {
    /** For A16; pieces and millilitres count as grams here. */
    fun option(): ProductOption? = foodUuid?.let {
        ProductOption(id, storeId, it, packAmount, preferred, priceHalers?.let { p -> Costs.pricePerGram(p, packAmount) })
    }
}

@Serializable
data class StoresFile(val format: String, val version: Int, val stores: List<SeedStore>)

@Serializable
data class SeedStore(val key: String, val name: String, val sectionOrder: List<String>)

/** `assets/seed/cz/store-products.json`: shops' own-brand products from Open Food Facts (0.14), later also the owner's research (input #1). */
@Serializable
data class StoreProductsFile(val version: Int, val country: String = "CZ", val products: List<SeedProduct>)

@Serializable
data class SeedProduct(
    val key: String,
    val store: String,
    val food: String? = null,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val packAmount: Double,
    val packUnit: String = "G",
    val priceCzk: Double? = null,
    val priceDate: String? = null,
    val source: String = "owner-research",
)

/** Shops, exact products and their prices (M5.6). */
class ShopRepository(
    private val shopDao: ShopDao,
    private val foodDao: FoodDao,
    private val appPrefs: AppPrefs,
    private val clock: Clock,
) {
    fun stores(): Flow<List<Store>> = shopDao.observeStores().map { rows -> rows.map { it.toStore() } }

    /** Products with their latest price. */
    fun products(): Flow<List<StoreProduct>> = combine(shopDao.observeProducts(), shopDao.observePrices()) { products, prices ->
        val latest = prices.groupBy { it.storeProductId }.mapValues { it.value.first() }
        products.map { it.toProduct(latest[it.id]) }
    }

    suspend fun storesNow(): List<Store> = shopDao.stores().map { it.toStore() }

    /** Each food's price per gram (A18), kept up to date. */
    fun foodPrices(): Flow<Map<String, Double>> = products().map { list -> RecipeCosts.pricePerGramByFood(list.mapNotNull { it.option() }) }

    /** A product's prices, newest first (M5.8). */
    fun priceHistory(productId: String): Flow<List<Pair<LocalDate, Long>>> = shopDao.observePrices().map { rows ->
        rows.filter { it.storeProductId == productId }.mapNotNull { row -> com.calore.tracker.domain.parseIsoDate(row.date)?.let { it to row.priceHalers } }
    }

    suspend fun productsNow(): List<StoreProduct> {
        val latest = shopDao.prices().groupBy { it.storeProductId }.mapValues { it.value.first() }
        return shopDao.products().map { it.toProduct(latest[it.id]) }
    }

    suspend fun setFavorite(store: Store, favorite: Boolean) = save(store.copy(favorite = favorite))

    /** Makes [store] the default for [section] (and no other shop), or clears it with null. */
    suspend fun setDefault(section: ShopSection, store: Store?) {
        val now = clock.millis()
        val all = shopDao.stores()
        shopDao.updateStores(
            all.map { row ->
                val sections = row.defaultForSections.toMutableSet()
                if (row.id == store?.id) sections += section else sections -= section
                row.copy(defaultForSections = sections.sortedBy { it.ordinal }, updatedAt = now)
            },
        )
    }

    private suspend fun save(store: Store) {
        val existing = shopDao.stores().first { it.id == store.id }
        shopDao.upsertStore(existing.copy(favorite = store.favorite, defaultForSections = store.defaultForSections.sortedBy { it.ordinal }, updatedAt = clock.millis()))
    }

    /**
     * The user's own pick (M5.6 source 2): this shop's product for this food, from then on the
     * preferred one. Returns its id.
     */
    suspend fun pickProduct(
        storeId: String,
        foodUuid: String?,
        name: String,
        brand: String?,
        barcode: String?,
        packAmount: Double,
        packUnit: String = "G",
        source: ProductSource = ProductSource.USER,
    ): String {
        val now = clock.millis()
        val product = StoreProductEntity(
            id = newId(), key = null, storeId = storeId, foodUuid = foodUuid, name = name.trim(), brand = brand?.trim()?.ifEmpty { null },
            barcode = barcode, packAmount = packAmount, packUnit = packUnit, preferred = true, source = source.name, sourceNote = null,
            createdAt = now, updatedAt = now,
        )
        shopDao.savePreferred(product, now)
        return product.id
    }

    /** A product already known (from the seed or an earlier pick) becomes this shop's preferred one for its food (0.14). */
    suspend fun preferProduct(productId: String): String? {
        val product = shopDao.products().firstOrNull { it.id == productId } ?: return null
        shopDao.savePreferred(product, clock.millis())
        return product.id
    }

    /** A price paid or typed in (M5.8). */
    suspend fun addPrice(productId: String, priceHalers: Long, date: LocalDate = LocalDate.now(clock), source: String = "USER") {
        val now = clock.millis()
        shopDao.insertPrice(PriceEntity(newId(), productId, priceHalers, "CZK", date.toString(), source, now, now))
    }

    /** Loads the shops once per seed version. */
    suspend fun importStores(input: InputStream): Int {
        val file = json.decodeFromString(StoresFile.serializer(), input.readBytes().toString(Charsets.UTF_8))
        require(file.format == "calore-stores")
        if (file.version <= appPrefs.settings.first().storesSeedVersion) return 0
        val now = clock.millis()
        var added = 0
        for (seed in file.stores) {
            if (shopDao.storeByKey(seed.key) != null) continue
            shopDao.upsertStore(
                StoreEntity(
                    id = StableIds.store(seed.key), key = seed.key, name = seed.name,
                    sectionOrder = seed.sectionOrder.mapNotNull { JsonColumns.enumByName<ShopSection>(it) },
                    favorite = false, defaultForSections = emptyList(), createdAt = now, updatedAt = now,
                ),
            )
            added++
        }
        appPrefs.setStoresSeedVersion(file.version)
        return added
    }

    /**
     * Loads the bundled shop products once per version: shops' own-brand products from Open Food
     * Facts (0.14), later also the owner's research (input #1). Products keep their seed key;
     * unknown shops are skipped; prices only when the file has them.
     */
    suspend fun importProducts(input: InputStream): Int {
        val file = json.decodeFromString(StoreProductsFile.serializer(), input.readBytes().toString(Charsets.UTF_8))
        if (file.version <= appPrefs.settings.first().productsSeedVersion) return 0
        val now = clock.millis()
        var added = 0
        // Shop and food pairs that already have a preferred product (read once, kept up to date below).
        val hasPreferred = shopDao.products().filter { it.preferred }.map { it.storeId to it.foodUuid }.toMutableSet()
        for (seed in file.products) {
            val store = shopDao.storeByKey(seed.store) ?: continue
            val foodUuid = seed.food?.let { foodDao.getByKey(it)?.uuid }
            val existing = shopDao.productByKey(seed.key)
            // The user's own pick stays preferred; otherwise the first seeded product for a food is.
            val preferred = existing?.preferred ?: ((store.id to foodUuid) !in hasPreferred)
            if (preferred) hasPreferred += store.id to foodUuid
            val product = StoreProductEntity(
                id = existing?.id ?: StableIds.product(seed.key), key = seed.key, storeId = store.id, foodUuid = foodUuid, name = seed.name, brand = seed.brand,
                barcode = seed.barcode, packAmount = seed.packAmount, packUnit = seed.packUnit,
                preferred = preferred,
                // Products taken from Open Food Facts (0.14) say so; anything else is the owner's research.
                source = (if (seed.source.startsWith("off")) ProductSource.OFF else ProductSource.OWNER_RESEARCH).name,
                sourceNote = seed.source, createdAt = existing?.createdAt ?: now, updatedAt = now,
            )
            shopDao.upsertProduct(product)
            if (seed.priceCzk != null) {
                shopDao.insertPrice(
                    (seed.priceDate ?: LocalDate.now(clock).toString()).let { date ->
                        PriceEntity(StableIds.seedPrice(seed.key, date), product.id, Math.round(seed.priceCzk * 100), "CZK", date, "SEED", now, now)
                    },
                )
            }
            added++
        }
        appPrefs.setProductsSeedVersion(file.version)
        return added
    }

    private fun StoreEntity.toStore() = Store(id, key, name, favorite, defaultForSections.toSet(), sectionOrder)

    private fun StoreProductEntity.toProduct(price: PriceEntity?) = StoreProduct(
        id = id, storeId = storeId, foodUuid = foodUuid, name = name, brand = brand, barcode = barcode, packAmount = packAmount,
        packUnit = packUnit, preferred = preferred, source = JsonColumns.enumByName<ProductSource>(source) ?: ProductSource.USER,
        priceHalers = price?.priceHalers, priceDate = price?.date?.let { com.calore.tracker.domain.parseIsoDate(it) },
    )

    companion object {
        const val STORES_ASSET = "seed/cz/stores.json"
        const val PRODUCTS_ASSET = "seed/cz/store-products.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
