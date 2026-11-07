package com.calore.tracker.ui.plan

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.foods.GenericFoodImporter
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.recipes.StarterRecipeImporter
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PantryRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.RatingRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.ShopRepository
import com.calore.tracker.data.repository.ShoppingRepository
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** M6.3 "done when": a new user taps **Plan my week** and gets a full plan and a shopping list, with no other step. */
@RunWith(AndroidJUnit4::class)
class PlanMyWeekTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC)
    private val monday = LocalDate.of(2026, 11, 9)
    private lateinit var db: CaloreDatabase
    private lateinit var prefs: AppPrefs
    private lateinit var members: MemberRepository
    private lateinit var plans: PlanRepository
    private lateinit var recipes: RecipeRepository
    private lateinit var shops: ShopRepository
    private lateinit var pantry: PantryRepository
    private lateinit var shopping: ShoppingRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb") }
        prefs = AppPrefs(store("a"))
        GenericFoodImporter(db.foodDao(), prefs, clock) { context.assets.open(GenericFoodImporter.ASSET_PATH) }.importIfNewer()
        StarterRecipeImporter(db.recipeDao(), db.foodDao(), prefs, clock) { context.assets.open(StarterRecipeImporter.ASSET_PATH) }.importIfNewer()
        members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), prefs, clock)
        members.ensureMe()
        plans = PlanRepository(db.planDao(), clock)
        recipes = RecipeRepository(db.recipeDao(), db.foodDao(), clock)
        shops = ShopRepository(db.shopDao(), db.foodDao(), prefs, clock)
        context.assets.open(ShopRepository.STORES_ASSET).use { shops.importStores(it) }
        pantry = PantryRepository(db.pantryDao(), db.foodDao(), clock)
        shopping = ShoppingRepository(db.shoppingDao(), db.foodDao(), plans, recipes, pantry, shops, clock)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun viewModel() = PlanWeekViewModel(
        SavedStateHandle(mapOf(Routes.ARG_WEEK_START to monday.toString())),
        plans, members, recipes, RatingRepository(db.ratingDao(), clock), pantry, shops, shopping, prefs, clock,
    )

    private suspend fun PlanWeekViewModel.waitFor(phase: PlanWeekPhase): PlanWeekUiState =
        withTimeout(30_000) { uiState.first { it.phase == phase } }

    @Test
    fun aNewUserGetsAFullPlanAndAShoppingListWithoutAnyOtherStep() = runBlocking {
        val vm = withContext(Dispatchers.Main) { viewModel() }
        val proposal = vm.waitFor(PlanWeekPhase.PROPOSAL)
        assertEquals(emptyList<Any>(), proposal.unplanned)
        assertEquals(28, proposal.planned.size)
        // The checklist of missing preferences is only a card on top of the plan.
        assertTrue(proposal.showChecklist)
        assertTrue(proposal.cooks.isNotEmpty())

        withContext(Dispatchers.Main) { vm.accept() }
        vm.waitFor(PlanWeekPhase.DONE)

        val (planId, slots) = plans.weekNow(monday)
        assertEquals(28, slots.count { it.recipeId != null })
        assertEquals(14, slots.count { it.kind == SlotKind.BOX })
        assertTrue(db.planDao().cooks(planId).isNotEmpty())
        assertTrue(shopping.items().first().isNotEmpty())
    }

    @Test
    fun replanningKeepsLockedAndEatOutMeals() = runBlocking {
        val first = withContext(Dispatchers.Main) { viewModel() }
        val proposal = first.waitFor(PlanWeekPhase.PROPOSAL)
        val lockedKey = proposal.slots.first { it.kind == SlotKind.BOX }.let { com.calore.tracker.domain.plan.SlotKey(it.date, it.meal, it.memberId) }
        withContext(Dispatchers.Main) {
            first.toggleLock(lockedKey)
            first.accept()
        }
        first.waitFor(PlanWeekPhase.DONE)
        val (_, saved) = plans.weekNow(monday)
        val locked = saved.single { it.date == lockedKey.date && it.meal == lockedKey.meal && it.memberId == lockedKey.memberId }
        assertTrue(locked.locked)
        val out = saved.first { it.kind == SlotKind.COOK_FRESH }
        plans.eatOut(out.id)

        // Planning the same week again asks first, then keeps the locked and eat-out meals.
        val again = withContext(Dispatchers.Main) { viewModel() }
        again.waitFor(PlanWeekPhase.ASK_REPLAN)
        withContext(Dispatchers.Main) { again.replanTheRest() }
        val replanned = again.waitFor(PlanWeekPhase.PROPOSAL)
        assertEquals(locked.recipeId, replanned.slots.single { it.id == locked.id }.recipeId)
        assertEquals(SlotKind.EAT_OUT, replanned.slots.single { it.id == out.id }.kind)
        assertTrue(replanned.planned.none { it.date == out.date && it.meal == out.meal })
    }
}
