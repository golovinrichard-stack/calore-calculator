package com.calore.tracker.data.foods

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.domain.Nutrients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** M2.2: first import, re-import of a newer version, the user's foods untouched, search in both languages. */
@RunWith(AndroidJUnit4::class)
class GenericFoodImporterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var prefs: AppPrefs
    private lateinit var foods: FoodRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "p-${UUID.randomUUID()}.preferences_pb") })
        foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(userAgent = "test"), clock)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun dataset(version: Int, chickenKcal: Int, extra: String = "") = """
        {"format":"calore-generic-foods","datasetVersion":$version,"country":"CZ","sources":[],"foods":[
         {"key":"generic:chicken_breast_raw","nameCs":"Kuřecí prsa bez kůže, syrová","nameEn":"Chicken breast, skinless, raw",
          "section":"MEAT","sourceRef":"usda:171077","animalFlags":["MEAT"],"per100g":{"kcal":$chickenKcal,"protein":22.5,"carbs":0,"fat":2.62}},
         {"key":"generic:rice_white_raw","nameCs":"Rýže bílá dlouhozrnná, syrová","nameEn":"White rice, raw",
          "section":"GRAINS","sourceRef":"usda:169756","per100g":{"kcal":365,"protein":7.13,"carbs":80,"fat":0.66}}$extra
        ]}
    """.trimIndent()

    private fun importer(json: String) = GenericFoodImporter(db.foodDao(), prefs, clock) { json.byteInputStream() }

    @Test
    fun firstImportThenANewerVersionUpdatesInPlace() = runTest {
        val userFoodId = foods.saveCustom(null, "Babiččin guláš", null, Nutrients(kcal = 150.0), null)

        assertEquals(2, importer(dataset(1, chickenKcal = 120)).importIfNewer())
        assertEquals(1, prefs.settings.first().genericFoodsVersion)
        val chicken = db.foodDao().getByKey("generic:chicken_breast_raw")!!
        assertTrue(chicken.isGeneric)
        foods.markUsed(chicken.id)

        assertEquals("same version: nothing to do", 0, importer(dataset(1, chickenKcal = 999)).importIfNewer())

        val extra = """,{"key":"generic:oats","nameCs":"Ovesné vločky","nameEn":"Rolled oats","section":"GRAINS",
            "sourceRef":"usda:173904","allergens":["GLUTEN"],"per100g":{"kcal":379,"protein":13.2,"carbs":67.7,"fat":6.5}}"""
        assertEquals(3, importer(dataset(2, chickenKcal = 121, extra = extra)).importIfNewer())

        val updated = db.foodDao().getByKey("generic:chicken_breast_raw")!!
        assertEquals("same row", chicken.id, updated.id)
        assertEquals("same uuid", chicken.uuid, updated.uuid)
        assertEquals(121.0, updated.kcalPer100g, 0.0)
        assertTrue("last use is kept", updated.lastUsedAt > 0)
        assertEquals(2, updated.datasetVersion)
        assertNotNull(db.foodDao().getByKey("generic:oats"))

        val userFood = db.foodDao().getById(userFoodId)!!
        assertEquals("Babiččin guláš", userFood.name)
        assertEquals(null, userFood.key)
    }

    @Test
    fun czechAndEnglishSearchFindTheSameFood() = runTest {
        importer(dataset(1, chickenKcal = 120)).importIfNewer()
        val czech = foods.searchLocal("kuřecí prsa")
        val english = foods.searchLocal("chicken breast")
        assertEquals(1, czech.size)
        assertEquals(czech.single().id, english.single().id)
        assertTrue("basic foods only appear in Recent after use", foods.recentFoods().first().isEmpty())
    }

    @Test
    fun theBundledAssetImports() = runTest {
        val bundled = GenericFoodImporter(db.foodDao(), prefs, clock) { context.assets.open(GenericFoodImporter.ASSET_PATH) }
        assertTrue(bundled.importIfNewer() > 250)
        assertTrue(foods.searchLocal("rýže").isNotEmpty())
    }
}
