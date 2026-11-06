package com.calore.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.CheckinOutcome
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MacroMode
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/** M1.7 with a fixed clock: the check-in is saved once a week, and "Use it" updates targets and macros. */
@RunWith(AndroidJUnit4::class)
class CheckinServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val monday = LocalDate.of(2026, 11, 9)
    private val clock = Clock.fixed(Instant.parse("2026-11-09T07:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var members: MemberRepository
    private lateinit var service: CheckinService

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb")
        }
        members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), AppPrefs(store("a")), clock)
        val weights = WeightRepository(db.weightDao())
        service = CheckinService(db.diaryDao(), weights, members, BodyRepository(db.bodyDao(), members, clock), clock)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private suspend fun logDays(days: Int, kcal: Double) {
        val diary = DiaryRepository(db.diaryDao(), clock)
        val food = Food(0, "Meal", null, null, Nutrients(kcal = kcal), null, FoodSource.CUSTOM, newId())
        for (back in 1..days) diary.log(food, grams = 100.0, meal = MealType.LUNCH, date = monday.minusDays(back.toLong()))
    }

    @Test
    fun notEnoughDataSavesNothing() = runTest {
        members.ensureMe()
        logDays(9, 2500.0)
        assertTrue(service.evaluate() is CheckinOutcome.NotEnoughData)
        assertNull(service.checkInThisWeek())
    }

    @Test
    fun aWeekOfDataGivesACheckinAndUseItUpdatesTargetsAndMacros() = runTest {
        members.updateMe {
            it.copy(
                sex = Sex.MALE,
                birthDate = LocalDate.of(1996, 1, 1),
                heightCm = 180.0,
                activity = ActivityLevel.LIGHT,
                goalType = GoalType.MAINTAIN,
                macroMode = MacroMode.AUTO,
                targets = NutritionGoals(2200, 128, 270, 61),
            )
        }
        logDays(28, 2500.0)
        val weights = WeightRepository(db.weightDao())
        for (back in 0L..27L step 2) weights.save(monday.minusDays(back), 80.0)

        val checkin = service.checkInThisWeek()
        assertNotNull(checkin)
        checkin!!
        assertEquals(monday, checkin.weekStart)
        assertEquals(28, checkin.countedDays)
        assertEquals(2500, checkin.estimatedMaintenanceKcal)
        assertEquals("at most +150 per week", 2350, checkin.proposedTargetKcal)
        assertEquals("the same week gives the same check-in", checkin.id, service.checkInThisWeek()!!.id)

        service.accept(checkin)

        val targets = members.me().first().targets!!
        assertEquals(2350, targets.kcal)
        assertEquals("1.6 g/kg for maintenance × 80 kg", 128, targets.proteinG)
        assertEquals(65, targets.fatG)
        assertTrue(service.checkInThisWeek()!!.accepted)
    }
}
