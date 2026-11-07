package com.calore.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.foods.GenericFoodImporter
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.Shopping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** M5.6: shops from the seed, the owner's products, and the user's own picks. */
@RunWith(AndroidJUnit4::class)
class ShopRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var prefs: AppPrefs
    private lateinit var shops: ShopRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "p-${UUID.randomUUID()}.preferences_pb") })
        shops = ShopRepository(db.shopDao(), db.foodDao(), prefs, clock)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private suspend fun chicken(): String {
        val foods = """{"format":"calore-generic-foods","datasetVersion":1,"country":"CZ","sources":[],"foods":[
            {"key":"generic:chicken_breast_raw","nameCs":"Kuřecí prsa","nameEn":"Chicken breast","section":"MEAT",
             "sourceRef":"usda:171077","per100g":{"kcal":120,"protein":22.5,"carbs":0,"fat":2.6}}]}"""
        GenericFoodImporter(db.foodDao(), prefs, clock) { foods.byteInputStream() }.importIfNewer()
        return db.foodDao().getByKey("generic:chicken_breast_raw")!!.uuid
    }

    @Test
    fun seededShopsAndProducts() = runTest {
        assertEquals(10, context.assets.open(ShopRepository.STORES_ASSET).use { shops.importStores(it) })
        assertEquals("once per version", 0, context.assets.open(ShopRepository.STORES_ASSET).use { shops.importStores(it) })
        val uuid = chicken()
        // A made-up test file in the format of M5.6; the real one comes from the owner's research.
        val research = """{"version":1,"country":"CZ","products":[{"key":"cz:lidl:test-chicken","store":"cz:lidl",
            "food":"generic:chicken_breast_raw","name":"Test chicken","packAmount":500,"packUnit":"G","priceCzk":99.90,
            "priceDate":"2026-11-01","source":"test"}]}"""
        assertEquals(1, shops.importProducts(research.byteInputStream()))
        val product = shops.productsNow().single()
        assertEquals(uuid, product.foodUuid)
        assertTrue(product.preferred)
        assertEquals(9990L, product.priceHalers)

        val lidl = shops.storesNow().single { it.key == "cz:lidl" }
        shops.setDefault(ShopSection.MEAT, lidl)
        val stores = shops.storesNow().map { it.info }
        assertEquals("the default shop's preferred product", product.id, Shopping.chooseProduct(ShopSection.MEAT, shops.productsNow().mapNotNull { it.option() }, stores)?.id)
    }

    @Test
    fun withoutTheSeedAPickIsUsedFromThenOn() = runTest {
        context.assets.open(ShopRepository.STORES_ASSET).use { shops.importStores(it) }
        val uuid = chicken()
        val tesco = shops.storesNow().single { it.key == "cz:tesco" }
        shops.setDefault(ShopSection.MEAT, tesco)
        assertNull(Shopping.chooseProduct(ShopSection.MEAT, shops.productsNow().mapNotNull { it.option() }, shops.storesNow().map { it.info }))

        val first = shops.pickProduct(tesco.id, uuid, "Kuřecí řízky", "Tesco", null, 400.0)
        val second = shops.pickProduct(tesco.id, uuid, "Kuřecí prsa", null, null, 500.0)
        val products = shops.productsNow()
        assertEquals("only the latest pick is preferred", setOf(second), products.filter { it.preferred }.map { it.id }.toSet())
        assertTrue(products.any { it.id == first })
        assertEquals(second, Shopping.chooseProduct(ShopSection.MEAT, products.mapNotNull { it.option() }, shops.storesNow().map { it.info })?.id)
        assertEquals(10, shops.stores().first().size)
    }
}
