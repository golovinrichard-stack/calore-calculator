package com.calore.tracker.ui.body

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.repository.BodyRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.BmrFormula
import com.calore.tracker.domain.BodyFatMethod
import com.calore.tracker.domain.Sex
import com.calore.tracker.ui.calculator.CalculatorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

/** M1.4: after body fat is entered, the calculator switches to Katch-McArdle. */
@RunWith(AndroidJUnit4::class)
class BodyFatToCalculatorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var members: MemberRepository
    private lateinit var weights: WeightRepository
    private lateinit var body: BodyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb")
        }
        members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), AppPrefs(store("a")), clock)
        weights = WeightRepository(db.weightDao())
        body = BodyRepository(db.bodyDao(), members, clock)
        runBlocking {
            members.updateMe {
                it.copy(sex = Sex.MALE, birthDate = LocalDate.of(1996, 1, 1), heightCm = 180.0, activity = ActivityLevel.LIGHT)
            }
            weights.save(LocalDate.of(2026, 11, 6), 80.0)
        }
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun calculatorUsesKatchMcArdleAfterABodyFatMeasurement() = runBlocking {
        val before = CalculatorViewModel(members, weights, clock, body.latestBodyFat())
        withTimeout(5_000) { while (before.form.value.weightKg.isEmpty()) kotlinx.coroutines.delay(20) }
        assertEquals(BmrFormula.MIFFLIN_ST_JEOR, before.form.value.result(before.today)!!.formula)

        val first = body.saveBodyFat(BodyFatMethod.MANUAL, bodyFatPct = 15.0)
        assertTrue("the first measurement offers a reminder", first)
        assertEquals(15.0, body.latestBodyFat().first()!!.pct, 0.0)

        val after = CalculatorViewModel(members, weights, clock, body.latestBodyFat())
        withTimeout(5_000) { while (after.form.value.bodyFat == null) kotlinx.coroutines.delay(20) }
        val result = after.form.value.result(after.today)!!
        assertEquals(BmrFormula.KATCH_MCARDLE, result.formula)
        assertEquals("370 + 21.6 × 68 kg lean mass", 1839, result.bmr)
    }
}
