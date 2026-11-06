package com.calore.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.CalculatorInputs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MacroMode
import com.calore.tracker.domain.MacroSplit
import com.calore.tracker.domain.MemberKind
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.WeightGoal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** M1.1: the 0.x goals and calculator inputs move into the ME row, and the targets stay the same. */
@RunWith(AndroidJUnit4::class)
class ProfileMoveTest {

    private lateinit var db: CaloreDatabase
    private lateinit var settings: SettingsStore
    private lateinit var appPrefs: AppPrefs
    private lateinit var members: MemberRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T09:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 6)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb")
        }
        settings = SettingsStore(store("settings"))
        appPrefs = AppPrefs(store("app_prefs"))
        members = MemberRepository(db.memberDao(), db.weightDao(), settings, appPrefs, clock)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun upgradeKeepsTheSameTargets() = runTest {
        val oldGoals = NutritionGoals(kcal = 2260, proteinG = 170, carbsG = 198, fatG = 88)
        settings.setGoals(oldGoals)
        settings.setCalculatorInputs(
            CalculatorInputs(
                sex = Sex.MALE,
                ageYears = 30,
                heightCm = 180.0,
                weightKg = 80.0,
                activity = ActivityLevel.MODERATE,
                weightGoal = WeightGoal.LOSE,
                split = MacroSplit(30, 35, 35),
            ),
        )

        val me = members.ensureMe()

        assertEquals(MemberKind.ME, me.kind)
        assertEquals(oldGoals, me.targets)
        assertEquals(oldGoals, GoalsRepository(members).goals.first())
        assertEquals(Sex.MALE, me.sex)
        assertEquals(LocalDate.of(1996, 7, 1), me.birthDate)
        assertEquals(180.0, me.heightCm!!, 0.0)
        assertEquals(ActivityLevel.MODERATE, me.activity)
        assertEquals(GoalType.CUT, me.goalType)
        assertEquals(0.5, me.goalRatePct!!, 0.0)
        assertEquals(MacroMode.SPLIT, me.macroMode)
        assertEquals(MacroSplit(30, 35, 35), me.split)

        val weight = WeightRepository(db.weightDao()).entries().first().single()
        assertEquals(today, weight.date)
        assertEquals(80.0, weight.kg, 0.0)

        val prefs = appPrefs.settings.first()
        assertTrue("existing users skip the setup", prefs.firstRunDone)
        assertTrue(prefs.whatsNewPending)
        assertTrue(prefs.birthDateToConfirm)
        assertFalse("old keys are removed after the copy", settings.legacyProfile().hasAnyValue)
    }

    @Test
    fun runningAgainKeepsOneMeRow() = runTest {
        val first = members.ensureMe()
        val second = members.ensureMe()
        assertEquals(first.id, second.id)
        assertEquals(first.id, members.me().first().id)
    }

    @Test
    fun aLoggedWeightIsNotOverwritten() = runTest {
        WeightRepository(db.weightDao()).save(today.minusDays(3), 82.0)
        settings.setCalculatorInputs(CalculatorInputs(weightKg = 80.0))

        members.ensureMe()

        val weights = WeightRepository(db.weightDao()).entries().first()
        assertEquals(listOf(82.0), weights.map { it.kg })
    }

    @Test
    fun freshInstallGetsDefaultsAndTheSetup() = runTest {
        val me = members.ensureMe()

        assertNull(me.targets)
        assertEquals(NutritionGoals.DEFAULT, me.effectiveTargets)
        assertEquals(MacroMode.AUTO, me.macroMode)
        assertNull(me.birthDate)
        val prefs = appPrefs.settings.first()
        assertFalse(prefs.firstRunDone)
        assertFalse(prefs.whatsNewPending)
        assertFalse(prefs.birthDateToConfirm)
    }

    @Test
    fun updatingMeKeepsItsIdentity() = runTest {
        val me = members.ensureMe()
        val updated = members.updateMe { it.copy(id = "other", heightCm = 170.0) }
        assertEquals(me.id, updated.id)
        assertEquals(170.0, members.me().first().heightCm!!, 0.0)
    }
}
