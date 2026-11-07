package com.calore.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.CyclePrefs
import com.calore.tracker.domain.cycle.Contraception
import com.calore.tracker.domain.cycle.Flow
import com.calore.tracker.domain.cycle.Symptom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

/** M7.2 and M7.6: logging periods and days, settings, and deleting all cycle data. */
@RunWith(AndroidJUnit4::class)
class CycleRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var cycle: CycleRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        val prefs = CyclePrefs(PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "cycle-${UUID.randomUUID()}.preferences_pb") })
        cycle = CycleRepository(db.cycleDao(), prefs, clock)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun startEndCorrectAndLog() = runTest {
        cycle.startPeriod(LocalDate.of(2026, 10, 1))
        // Two days later: a correction of the open period, not a new one.
        cycle.startPeriod(LocalDate.of(2026, 10, 3))
        assertEquals(listOf(LocalDate.of(2026, 10, 3)), cycle.periodsNow().map { it.start })
        cycle.endPeriod(LocalDate.of(2026, 10, 7))
        cycle.startPeriod(LocalDate.of(2026, 10, 31))
        val periods = cycle.periodsNow()
        assertEquals(listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 31)), periods.map { it.start })
        assertEquals(LocalDate.of(2026, 10, 7), periods.first().end)

        cycle.logDay(LocalDate.of(2026, 10, 31), Flow.HEAVY, setOf(Symptom.CRAMPS), " tired ")
        val day = cycle.days().first().single()
        assertEquals(Flow.HEAVY, day.flow)
        assertEquals(setOf(Symptom.CRAMPS), day.symptoms)
        assertEquals("tired", day.note)
        // An empty log removes the day.
        cycle.logDay(LocalDate.of(2026, 10, 31), null, emptySet(), "")
        assertTrue(cycle.days().first().isEmpty())
    }

    @Test
    fun deleteAllRemovesEverythingButKeepsTrackingOn() = runTest {
        cycle.setEnabled(true)
        cycle.saveSettings(cycle.settings.first().copy(contraception = Contraception.COPPER_IUD, defaultCycleLength = 31))
        cycle.startPeriod(LocalDate.of(2026, 10, 1))
        cycle.logDay(LocalDate.of(2026, 10, 1), Flow.LIGHT, emptySet(), null)

        cycle.deleteAll()
        assertTrue(cycle.periodsNow().isEmpty())
        assertTrue(cycle.days().first().isEmpty())
        val settings = cycle.settings.first()
        assertTrue(settings.enabled)
        assertEquals(Contraception.NONE, settings.contraception)
        assertEquals(28, settings.defaultCycleLength)
    }
}
