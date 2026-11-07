package com.calore.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.CyclePrefs
import com.calore.tracker.data.prefs.ResearchPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.remote.ResearchClient
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.research.ResearchCategory
import com.calore.tracker.domain.research.ResearchReports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** M10.3–M10.5: consent, the stored report, sending, and stop and delete offline and online. */
@RunWith(AndroidJUnit4::class)
class ResearchRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-09T06:00:00Z"), ZoneOffset.UTC)
    private val lastMonday = LocalDate.of(2026, 11, 2)
    private lateinit var db: CaloreDatabase
    private lateinit var diary: DiaryRepository
    private val server = FakeServer()

    /** Online or offline, and what was sent. */
    class FakeServer : ResearchClient("https://example.invalid", "test") {
        var online = false
        val registered = mutableListOf<String>()
        val reports = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        private fun result(): Result = if (online) Result.Ok else Result.Failed(IOException("offline"))
        override suspend fun register(body: String) = result().also { if (online) registered += body }
        override suspend fun sendReport(secret: String, body: String) = result().also { if (online) reports += body }
        override suspend fun deleteParticipant(participantId: String, secret: String) = result().also { if (online) deleted += participantId }
    }

    private lateinit var research: ResearchRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb") }
        val prefs = AppPrefs(store("a"))
        val members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), prefs, clock)
        members.ensureMe()
        val weights = WeightRepository(db.weightDao())
        val body = BodyRepository(db.bodyDao(), members, clock)
        val plans = PlanRepository(db.planDao(), clock)
        val recipes = RecipeRepository(db.recipeDao(), db.foodDao(), clock)
        diary = DiaryRepository(db.diaryDao(), clock)
        research = ResearchRepository(
            ResearchPrefs(store("r")), db.researchDao(), server, db.diaryDao(), db.foodDao(), db.cookingDao(), members, weights, body, plans,
            recipes, CycleRepository(db.cycleDao(), CyclePrefs(store("c")), clock), SupplementRepository(db.supplementDao(), db.diaryDao(), clock),
            "0.12.0", clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private suspend fun logAWeek() {
        for (d in 0L..4L) diary.quickAdd("Lunch", Nutrients(kcal = 1800.0, protein = 90.0), MealType.LUNCH, lastMonday.plusDays(d))
    }

    @Test
    fun agreeingOfflineKeepsTheRegistrationForLaterAndStoresTheReportExactly() = runTest {
        assertEquals(ResearchOutcome.WAITING, research.agree(setOf(ResearchCategory.NUTRITION), "CZ-64"))
        val consent = research.consent.first()
        assertTrue(consent.active && consent.registrationPending)
        assertEquals(setOf(ResearchCategory.NUTRITION), consent.categories)

        logAWeek()
        val report = research.buildReport(lastMonday)!!
        assertEquals("2026-W45", report.isoWeek)
        val json = Json.parseToJsonElement(report.payload)
        assertEquals(emptySet<String>(), ResearchReports.normalisedPaths(json) - ResearchReports.ALLOWED_REPORT_FIELDS)
        assertNull("one report per week", research.buildReport(lastMonday))

        // Online: the registration goes first, then the report, exactly as stored.
        server.online = true
        assertEquals(ResearchOutcome.DONE, research.sendPending())
        assertEquals(1, server.registered.size)
        assertFalse(server.registered.single().contains("Lunch"))
        assertEquals(listOf(report.payload), server.reports)
        assertEquals(ResearchRepository.STATUS_SENT, research.reports().first().single().status)
    }

    @Test
    fun aReportFailsAfterFiveAttemptsAndIsTriedAgainNextWeek() = runTest {
        server.online = true
        research.agree(setOf(ResearchCategory.NUTRITION), null)
        logAWeek()
        research.buildReport(lastMonday)
        server.online = false
        repeat(4) { assertEquals(ResearchOutcome.WAITING, research.sendPending()) }
        assertEquals(ResearchOutcome.DONE, research.sendPending())
        assertEquals(ResearchRepository.STATUS_FAILED, research.reports().first().single().status)
        research.retryFailed()
        assertEquals(ResearchRepository.STATUS_PENDING, research.reports().first().single().status)
    }

    @Test
    fun stopAndDeleteWorkOfflineAndOnline() = runTest {
        server.online = true
        research.agree(setOf(ResearchCategory.NUTRITION, ResearchCategory.BODY), null)
        val id = research.consent.first().participantId!!
        logAWeek()
        research.buildReport(lastMonday)

        // Stop: the unsent report goes, nothing more is built.
        research.stop()
        assertTrue(research.reports().first().isEmpty())
        assertNotNull(research.consent.first().withdrawnAt)
        assertNull(research.buildReport(lastMonday.plusWeeks(1)))

        // Delete while offline: waiting; online: done, and the id and secret are gone.
        server.online = false
        assertEquals(ResearchOutcome.WAITING, research.deleteFromServer())
        assertTrue(research.consent.first().pendingDeletion)
        server.online = true
        assertEquals(ResearchOutcome.DONE, research.processPendingDeletion())
        assertEquals(listOf(id), server.deleted)
        val after = research.consent.first()
        assertNull(after.participantId)
        assertNull(after.participantSecret)
        assertFalse(after.pendingDeletion)
    }
}
