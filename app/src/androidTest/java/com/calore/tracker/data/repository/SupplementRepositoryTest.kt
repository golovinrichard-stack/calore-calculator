package com.calore.tracker.data.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.domain.DoseUnit
import com.calore.tracker.domain.EntryType
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Supplement
import com.calore.tracker.domain.SupplementKind
import com.calore.tracker.domain.SupplementSchedule
import com.calore.tracker.domain.SupplementStatus
import com.calore.tracker.notifications.SupplementReceiver
import com.calore.tracker.notifications.SupplementReminders
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** M8: Taken / Skip / undo, the diary entry of a supplement with calories (M8.5), and the reminder alarm (M8.4). */
@RunWith(AndroidJUnit4::class)
class SupplementRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-11-09T07:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 9)
    private lateinit var db: CaloreDatabase
    private lateinit var supplements: SupplementRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        supplements = SupplementRepository(db.supplementDao(), db.diaryDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private val protein = Supplement(
        id = "",
        name = "Protein",
        kind = SupplementKind.PROTEIN_POWDER,
        doseAmount = 1.0,
        doseUnit = DoseUnit.SCOOP,
        times = listOf(LocalTime.of(8, 0)),
        startDate = today,
        nutrients = Nutrients(kcal = 120.0, protein = 24.0),
    )

    @Test
    fun takingASupplementWithCaloriesLogsItAndUndoRemovesIt() = runTest {
        val id = supplements.save(protein)
        val dose = SupplementSchedule.doses(supplements.supplement(id)!!, today, clock.zone).single()

        supplements.take(dose)
        val entry = db.diaryDao().forDate(today.toString()).first().single()
        assertEquals(EntryType.SUPPLEMENT.name, entry.entryType)
        assertEquals(120.0, entry.kcal, 0.0)
        assertEquals(MealType.BREAKFAST.name, entry.meal)
        assertEquals(SupplementStatus.TAKEN, supplements.logsSince(today).first().single().status)

        // Skipping instead removes the entry; undo opens the dose again.
        supplements.skip(dose)
        assertTrue(db.diaryDao().forDate(today.toString()).first().isEmpty())
        supplements.take(dose)
        supplements.undo(dose)
        assertTrue(db.diaryDao().forDate(today.toString()).first().isEmpty())
        assertTrue(supplements.logsSince(today).first().isEmpty())
    }

    @Test
    fun aSupplementWithoutCaloriesMakesNoDiaryEntryAndDeletingKeepsTheDiary() = runTest {
        val vitaminD = supplements.save(protein.copy(name = "Vitamin D", nutrients = null))
        supplements.take(SupplementSchedule.doses(supplements.supplement(vitaminD)!!, today, clock.zone).single())
        assertTrue(db.diaryDao().forDate(today.toString()).first().isEmpty())

        val powder = supplements.save(protein)
        supplements.take(SupplementSchedule.doses(supplements.supplement(powder)!!, today, clock.zone).single())
        supplements.delete(powder)
        assertNull(supplements.supplement(powder))
        assertEquals(1, db.diaryDao().forDate(today.toString()).first().size)
    }

    @Test
    fun aReminderIsSetOnlyWhenSwitchedOn() = runTest {
        val reminders = SupplementReminders(context, supplements, clock)
        val id = supplements.save(protein.copy(remindersOn = true))
        reminders.reschedule(id)
        assertNotNull(alarm(id))

        supplements.save(supplements.supplement(id)!!.copy(remindersOn = false))
        reminders.reschedule(id)
        assertNull(alarm(id))
    }

    /** The reminder's PendingIntent, if one is set. */
    private fun alarm(id: String): PendingIntent? = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, SupplementReceiver::class.java).setAction("com.calore.tracker.SUPPLEMENT_ALARM"),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )
}
