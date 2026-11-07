package com.calore.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.MemberKind
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.PlanTotals
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.mealTarget
import com.calore.tracker.domain.newId
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** M5.3 "done when": a week for two people with 2 cooking days; daily totals match the slot targets. */
@RunWith(AndroidJUnit4::class)
class PlanRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC)
    private val monday = LocalDate.of(2026, 11, 9)
    private lateinit var db: CaloreDatabase
    private lateinit var plans: PlanRepository
    private lateinit var members: MemberRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb") }
        members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), AppPrefs(store("a")), clock)
        plans = PlanRepository(db.planDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun twoPeopleTwoCookingDays() = runTest {
        val me = members.updateMe {
            it.copy(targets = NutritionGoals(2400, 160, 260, 80), cookSessionsPerWeek = 2, preppedMeals = setOf(MealType.LUNCH, MealType.DINNER))
        }
        val eva = Member(id = newId(), kind = MemberKind.MANAGED, name = "Eva", colour = 0, targets = NutritionGoals(1800, 110, 200, 60))
        members.save(eva)
        assertEquals(listOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY), CookingSessions.days(me.cookSessionsPerWeek, me.cookDays))

        val planId = plans.planFor(monday.plusDays(3))
        for (day in 0L..6L) {
            for (person in listOf(me, eva)) {
                val kind = if (day % 2 == 0L) SlotKind.BOX else SlotKind.COOK_FRESH
                plans.setSlot(planId, monday.plusDays(day), MealType.LUNCH, person.id, kind, "chili", person.mealTarget(MealType.LUNCH))
                plans.setSlot(planId, monday.plusDays(day), MealType.DINNER, person.id, SlotKind.BOX, "curry", person.mealTarget(MealType.DINNER))
                plans.setSlot(planId, monday.plusDays(day), MealType.SNACK, person.id, SlotKind.FREE, null, person.mealTarget(MealType.SNACK))
            }
        }
        val slots = plans.slots(planId).first()
        assertEquals(7 * 2 * 3, slots.size)
        for (person in listOf(me, eva)) {
            val day = slots.filter { it.memberId == person.id && it.date == monday }
            val expected = person.mealTarget(MealType.LUNCH)!!.kcal + person.mealTarget(MealType.DINNER)!!.kcal
            assertEquals(person.name, Math.round(expected).toDouble(), PlanTotals.planned(day).kcal, 1.0)
        }

        // Changing a slot replaces it.
        plans.setSlot(planId, monday, MealType.LUNCH, me.id, SlotKind.EAT_OUT, "ignored", me.mealTarget(MealType.LUNCH))
        val lunch = plans.slots(planId).first().single { it.memberId == me.id && it.date == monday && it.meal == MealType.LUNCH }
        assertEquals(SlotKind.EAT_OUT, lunch.kind)
        assertEquals(null, lunch.recipeId)
    }

    @Test
    fun repeatLastWeekAndTemplates() = runTest {
        val me = members.ensureMe()
        val planId = plans.planFor(monday)
        plans.setSlot(planId, monday, MealType.LUNCH, me.id, SlotKind.BOX, "chili", null)
        plans.setSlot(planId, monday.plusDays(2), MealType.DINNER, me.id, SlotKind.EAT_OUT, null, null)

        val next = monday.plusWeeks(1)
        assertEquals(2, plans.repeatLastWeek(next))
        assertEquals(setOf(next, next.plusDays(2)), plans.slots(plans.planFor(next)).first().map { it.date }.toSet())

        plans.saveAsTemplate(monday, "Busy week")
        val template = plans.templates().first().single()
        assertEquals("Busy week", template.name)
        val later = monday.plusWeeks(5)
        assertEquals(2, plans.useTemplate(template.id, later))
        assertEquals(setOf(later, later.plusDays(2)), plans.slots(plans.planFor(later)).first().map { it.date }.toSet())
        assertEquals("nothing before an empty week", 0, plans.repeatLastWeek(monday.plusWeeks(10)))
    }

    @Test
    fun eatingOutClearsTheRecipeAndTheBox() = runTest {
        val me = members.ensureMe()
        val planId = plans.planFor(monday)
        plans.setSlot(planId, monday, MealType.DINNER, me.id, SlotKind.BOX, "curry", me.mealTarget(MealType.DINNER))
        val slot = plans.slots(planId).first().single()
        plans.linkBoxes(listOf(slot.id to "box-1"))
        plans.eatOut(slot.id)
        val after = plans.slots(planId).first().single()
        assertEquals(SlotKind.EAT_OUT, after.kind)
        assertEquals(null, after.recipeId)
        assertEquals(null, after.boxId)
    }

    @Test
    fun aNewTargetResizesOnlyMealsNotCookedOrEatenYet() = runTest {
        val old = members.updateMe { it.copy(targets = NutritionGoals(2400, 160, 260, 80)) }
        val planId = plans.planFor(monday)
        for (day in 0L..2L) plans.setSlot(planId, monday.plusDays(day), MealType.LUNCH, old.id, SlotKind.COOK_FRESH, "chili", old.mealTarget(MealType.LUNCH))
        plans.setSlot(planId, monday, MealType.DINNER, old.id, SlotKind.EAT_OUT, null, old.mealTarget(MealType.DINNER))
        // Tuesday's lunch is already in a box.
        val tuesday = plans.slots(planId).first().single { it.date == monday.plusDays(1) && it.meal == MealType.LUNCH }
        plans.linkBoxes(listOf(tuesday.id to "box-1"))

        val me = members.updateMe { it.copy(targets = NutritionGoals(2000, 150, 200, 70)) }
        val resizable = plans.retargetable(me, monday)
        assertEquals(listOf(monday, monday.plusDays(2)), resizable.map { it.date }.sorted())
        plans.retarget(me, resizable)
        val slots = plans.slots(planId).first()
        val newLunch = Math.round(me.mealTarget(MealType.LUNCH)!!.kcal).toDouble()
        assertEquals(newLunch, slots.single { it.date == monday && it.meal == MealType.LUNCH }.target!!.kcal, 0.0)
        assertEquals(Math.round(old.mealTarget(MealType.LUNCH)!!.kcal).toDouble(), slots.single { it.id == tuesday.id }.target!!.kcal, 0.0)
        assertEquals(emptyList<Any>(), plans.retargetable(me, monday))
    }
}
