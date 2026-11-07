package com.calore.tracker.data.household

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.db.HouseholdEntity
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PantryRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.ShopRepository
import com.calore.tracker.data.repository.ShoppingRepository
import com.calore.tracker.domain.AmountUnit
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.Visibility
import com.calore.tracker.domain.household.ActivityType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** M11.7 and M11.8: requests do what they say, and the feed gets a line for each. */
@RunWith(AndroidJUnit4::class)
class RequestRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.systemDefaultZone()
    private lateinit var db: CaloreDatabase
    private lateinit var members: MemberRepository
    private lateinit var pantry: PantryRepository
    private lateinit var shopping: ShoppingRepository
    private lateinit var plans: PlanRepository
    private lateinit var requests: RequestRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).addCallback(CaloreDatabase.SyncCallback).build()
        fun store(name: String) = PreferenceDataStoreFactory.create { File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb") }
        val prefs = AppPrefs(store("a"))
        members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), prefs, clock)
        members.ensureMe()
        plans = PlanRepository(db.planDao(), clock)
        val recipes = RecipeRepository(db.recipeDao(), db.foodDao(), clock)
        val shops = ShopRepository(db.shopDao(), db.foodDao(), prefs, clock)
        pantry = PantryRepository(db.pantryDao(), db.foodDao(), clock)
        shopping = ShoppingRepository(db.shoppingDao(), db.foodDao(), plans, recipes, pantry, shops, clock)
        val log = HouseholdActivityLog(db.householdDao(), db.memberDao(), clock)
        requests = RequestRepository(db.householdDao(), members, CookingRepository(db.cookingDao(), clock), pantry, shopping, plans, log, clock)
        db.householdDao().upsertHousehold(HouseholdEntity("h1", "Home", "hub", 1, 0, 0))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun buyForMePutsTheItemOnTheListWithMyName() = runBlocking {
        val me = members.ensureMe()
        val request = requests.buyForMe("Oat milk", "the barista one")
        val line = shopping.items().first().single()
        assertEquals("Oat milk", line.name)
        assertEquals(me.id, line.requestedByMemberId)
        assertEquals("shopping_items", request.refTable)
        assertEquals(line.id, request.refId)
        assertEquals(ActivityType.REQUESTED.name, db.householdDao().observeActivity(0).first().single().type)
    }

    @Test
    fun yesToCanIHaveGivesTheItemToWhoAsked() = runBlocking {
        val eggs = pantry.add(null, "Eggs", 6.0, AmountUnit.PIECE)
        pantry.setOwnership(eggs, "anna", Visibility.MINE)
        val request = requests.canIHave("pantry_items", eggs.id, "anna", "Eggs")
        requests.answer(db.householdDao().request(request.id)!!, RequestStatus.ACCEPTED)
        assertEquals(request.fromMemberId, pantry.item(eggs.id)!!.ownerMemberId)
        assertEquals(RequestStatus.ACCEPTED.name, db.householdDao().request(request.id)!!.status)
    }

    @Test
    fun acceptingCookForMeAddsItToThePlan() = runBlocking {
        val day = LocalDate.now(clock).plusDays(2)
        val request = requests.cookForMe(null, day, MealType.DINNER, null, null, 2, null)
        requests.answer(db.householdDao().request(request.id)!!, RequestStatus.ACCEPTED)
        val planId = plans.planFor(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        val slot = plans.slots(planId).first().single()
        assertEquals(day, slot.date)
        assertEquals(MealType.DINNER, slot.meal)
        assertEquals(request.fromMemberId, slot.memberId)
        assertEquals(SlotKind.COOK_FRESH, slot.kind)
    }

    @Test
    fun outsideAHouseholdTheFeedStaysEmpty() = runBlocking {
        db.householdDao().deleteHousehold()
        requests.buyForMe("Bread", null)
        assertTrue(db.householdDao().observeActivity(0).first().isEmpty())
    }
}
