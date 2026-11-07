package com.calore.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.foods.GenericFoodImporter
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.SlotKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** M5.7 "done when": the A16 test cases end to end, plan → list → tick → pantry. */
@RunWith(AndroidJUnit4::class)
class ShoppingEndToEndTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val today = LocalDate.of(2026, 11, 9)
    private val cookDay = LocalDate.of(2026, 11, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-09T08:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var shopping: ShoppingRepository
    private lateinit var pantry: PantryRepository
    private lateinit var me: Member
    private lateinit var chickenUuid: String

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb") }
        val prefs = AppPrefs(store("a"))
        val foods = """{"format":"calore-generic-foods","datasetVersion":1,"country":"CZ","sources":[],"foods":[
            {"key":"generic:chicken_breast_raw","nameCs":"Kuřecí prsa","nameEn":"Chicken breast","section":"MEAT",
             "sourceRef":"usda:171077","per100g":{"kcal":120,"protein":22.5,"carbs":0,"fat":2.6}}]}"""
        GenericFoodImporter(db.foodDao(), prefs, clock) { foods.byteInputStream() }.importIfNewer()
        chickenUuid = db.foodDao().getByKey("generic:chicken_breast_raw")!!.uuid
        val members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), prefs, clock)
        me = members.ensureMe()
        val recipes = RecipeRepository(db.recipeDao(), db.foodDao(), clock)
        val plans = PlanRepository(db.planDao(), clock)
        val shops = ShopRepository(db.shopDao(), db.foodDao(), prefs, clock)
        pantry = PantryRepository(db.pantryDao(), db.foodDao(), clock)
        shopping = ShoppingRepository(db.shoppingDao(), db.foodDao(), plans, recipes, pantry, shops, clock)
        context.assets.open(ShopRepository.STORES_ASSET).use { shops.importStores(it) }
        val lidl = shops.storesNow().single { it.key == "cz:lidl" }
        shops.setDefault(ShopSection.MEAT, lidl)
        shops.pickProduct(lidl.id, chickenUuid, "Kuřecí prsní řízky", null, null, 500.0)
        this@ShoppingEndToEndTest.recipes = recipes
        this@ShoppingEndToEndTest.plans = plans
    }

    private lateinit var recipes: RecipeRepository
    private lateinit var plans: PlanRepository

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    /** One fresh cook on Wednesday that needs [grams] of chicken (one serving, no target, yield 1). */
    private suspend fun planChicken(grams: Double) {
        val id = recipes.save(Recipe(id = "", name = "Kuře", shortCode = "K", servings = 1), listOf(RecipeIngredient("", "", 0, chickenUuid, grams)), emptyList())
        plans.setSlot(plans.planFor(cookDay), cookDay, MealType.DINNER, me.id, SlotKind.COOK_FRESH, id, null)
    }

    private suspend fun chickenLine() = shopping.items().first().single { it.foodUuid == chickenUuid }

    @Test
    fun nothingAtHomeThreePacksAndTickingFillsThePantry() = runTest {
        planChicken(1150.0)
        shopping.regenerate(me, today)
        val line = chickenLine()
        assertEquals(3, line.packs)
        assertEquals("Kuřecí prsní řízky", line.name)

        shopping.tick(line, priceHalers = 41_970)
        assertEquals(1500.0, pantry.available().first().sumOf { it.item.amount }, 0.0)
        assertEquals(41_970L, pantry.available().first().single().item.costHalers)

        // Regenerating keeps the tick; the 1,500 g at home now cover the plan.
        shopping.regenerate(me, today)
        val items = shopping.items().first()
        assertEquals(1, items.size)
        assertEquals(line.id, items.single().id)
    }

    @Test
    fun twoHundredGramsAtHomeMeansTwoPacks() = runTest {
        planChicken(1150.0)
        val chicken = FoodRepository(db.foodDao(), com.calore.tracker.data.remote.OpenFoodFactsClient(userAgent = "test"), clock)
            .searchLocal("Chicken").single()
        pantry.add(chicken, "Kuřecí prsa", 200.0, bestBefore = cookDay.plusDays(1))
        shopping.regenerate(me, today)
        assertEquals(2, chickenLine().packs)
    }

    @Test
    fun foodThatExpiresBeforeTheCookDoesNotCount() = runTest {
        planChicken(300.0)
        val chicken = FoodRepository(db.foodDao(), com.calore.tracker.data.remote.OpenFoodFactsClient(userAgent = "test"), clock)
            .searchLocal("Chicken").single()
        pantry.add(chicken, "Kuřecí prsa", 400.0, bestBefore = cookDay.minusDays(1))
        shopping.regenerate(me, today)
        assertEquals(1, chickenLine().packs)
    }

    @Test
    fun manualItemsStayWhenTheListIsRegenerated() = runTest {
        shopping.addManual("Toaletní papír")
        planChicken(1150.0)
        shopping.regenerate(me, today)
        shopping.regenerate(me, today)
        val items = shopping.items().first()
        assertEquals(2, items.size)
        assertEquals(1, items.count { it.manual })
    }
}
