package com.calore.tracker.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** M11.3: local writes to shared tables become ops; ops apply on another phone; private data never becomes an op. */
@RunWith(AndroidJUnit4::class)
class SyncStoreTest {

    private lateinit var phoneA: CaloreDatabase
    private lateinit var phoneB: CaloreDatabase
    private lateinit var a: SyncStore
    private lateinit var b: SyncStore
    private val clock = Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC)

    private fun open(): CaloreDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).addCallback(CaloreDatabase.SyncCallback).build()
    }

    private fun CaloreDatabase.sql(sql: String, vararg args: Any?) = openHelper.writableDatabase.execSQL(sql, args)

    private fun CaloreDatabase.text(sql: String, vararg args: Any?): String? =
        openHelper.writableDatabase.query(sql, args).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    private fun CaloreDatabase.addStore(id: String, name: String) =
        sql("INSERT INTO stores (id, name, sectionOrder, favorite, defaultForSections, createdAt, updatedAt) VALUES (?, ?, '', 0, '', 1, 1)", id, name)

    @Before
    fun setUp() = runBlocking {
        phoneA = open()
        phoneB = open()
        a = SyncStore(phoneA, clock)
        b = SyncStore(phoneB, clock)
        a.setRecording(true)
        b.setRecording(true)
    }

    @After
    fun tearDown() {
        phoneA.close()
        phoneB.close()
    }

    @Test
    fun aNewSharedRowReachesTheOtherPhoneOnceAndIsNotSentBack() = runBlocking {
        phoneA.addStore("s1", "Albert")
        assertEquals(1, a.flush("A"))
        val ops = a.opsFor(emptyMap())
        assertEquals(1, ops.size)

        val outcome = b.apply("B", ops)
        assertEquals(1, outcome.applied)
        assertEquals("Albert", phoneB.text("SELECT name FROM stores WHERE id = 's1'"))
        assertEquals(0, b.flush("B"))
        assertEquals(mapOf("A" to 1L), b.versionVector())

        // The same ops again change nothing.
        assertEquals(0, b.apply("B", ops).applied)
        assertEquals(emptyList<Any>(), a.opsFor(b.versionVector()))
    }

    @Test
    fun privateDataNeverBecomesAnOp() = runBlocking {
        phoneA.sql("INSERT INTO weight_entries (date, kg, uuid) VALUES ('2026-11-07', 80.5, 'w1')")
        phoneA.sql("INSERT INTO diary_entries (date, meal, name, grams, kcal, createdAt, uuid) VALUES ('2026-11-07', 'LUNCH', 'x', 1, 1, 1, 'd1')")
        assertEquals(0, a.flush("A"))
        assertEquals(emptyList<Any>(), a.opsFor(emptyMap()))
    }

    @Test
    fun onlyTheChangedColumnIsSent() = runBlocking {
        phoneA.addStore("s1", "Albert")
        a.flush("A")
        b.apply("B", a.opsFor(emptyMap()))
        phoneA.sql("UPDATE stores SET favorite = 1 WHERE id = 's1'")
        assertEquals(1, a.flush("A"))
        val update = a.opsFor(mapOf("A" to 1L)).single()
        assertEquals(setOf("favorite"), update.changes.keys)
        b.apply("B", listOf(update))
        assertEquals("1", phoneB.text("SELECT favorite FROM stores WHERE id = 's1'"))
    }

    @Test
    fun editsOnBothPhonesEndTheSameEverywhere() = runBlocking {
        phoneA.addStore("s1", "Albert")
        a.flush("A")
        b.apply("B", a.opsFor(emptyMap()))
        phoneA.sql("UPDATE stores SET name = 'Albert Hypermarket' WHERE id = 's1'")
        phoneB.sql("UPDATE stores SET name = 'Albert Centrum' WHERE id = 's1'")
        a.flush("A")
        b.flush("B")
        val fromA = a.opsFor(emptyMap())
        val fromB = b.opsFor(emptyMap())
        a.apply("A", fromB)
        b.apply("B", fromA)
        assertEquals(phoneA.text("SELECT name FROM stores WHERE id = 's1'"), phoneB.text("SELECT name FROM stores WHERE id = 's1'"))
    }

    @Test
    fun aChangeForARowThatIsNotHereYetWaits() = runBlocking {
        phoneA.addStore("s1", "Albert")
        a.flush("A")
        phoneA.sql("UPDATE stores SET name = 'Lidl' WHERE id = 's1'")
        a.flush("A")
        val (insert, update) = a.opsFor(emptyMap())

        val first = b.apply("B", listOf(update))
        assertEquals(0, first.applied)
        assertEquals(1, first.parked)
        assertEquals(mapOf<String, Long>(), b.versionVector().filterValues { it > 0 })

        val second = b.apply("B", listOf(insert))
        assertEquals(2, second.applied)
        assertEquals(0, second.parked)
        assertEquals("Lidl", phoneB.text("SELECT name FROM stores WHERE id = 's1'"))
        assertEquals(mapOf("A" to 2L), b.versionVector())
    }

    @Test
    fun aRemovedRowStaysRemoved() = runBlocking {
        phoneA.addStore("s1", "Albert")
        a.flush("A")
        b.apply("B", a.opsFor(emptyMap()))
        phoneB.sql("UPDATE stores SET name = 'Late edit' WHERE id = 's1'")
        b.flush("B")
        phoneA.sql("DELETE FROM stores WHERE id = 's1'")
        a.flush("A")
        b.apply("B", a.opsFor(mapOf("A" to 1L)))
        a.apply("A", b.opsFor(mapOf("A" to 2L)))
        assertNull(phoneA.text("SELECT name FROM stores WHERE id = 's1'"))
        assertNull(phoneB.text("SELECT name FROM stores WHERE id = 's1'"))
    }

    @Test
    fun aFoodIsSharedOnlyOnceSomethingSharedUsesIt() = runBlocking {
        phoneA.sql("INSERT INTO foods (name, kcalPer100g, source, lastUsedAt, uuid) VALUES ('Granola', 450, 'CUSTOM', 999, 'f1')")
        phoneA.sql("INSERT INTO foods (name, kcalPer100g, source, lastUsedAt, uuid, `key`) VALUES ('Oats', 370, 'BASIC', 1, 'f2', 'oats')")
        // Only in the diary so far: nothing leaves the phone.
        assertEquals(0, a.flush("A"))

        phoneA.sql("INSERT INTO shopping_items (id, name, section, foodUuid, createdAt, updatedAt) VALUES ('i1', 'Granola', 'OTHER', 'f1', 1, 1)")
        phoneA.sql("INSERT INTO shopping_items (id, name, section, foodUuid, createdAt, updatedAt) VALUES ('i2', 'Oats', 'OTHER', 'f2', 1, 1)")
        assertEquals(3, a.flush("A"))
        val ops = a.opsFor(emptyMap())
        assertEquals(listOf("foods", "shopping_items", "shopping_items"), ops.map { it.table })
        val food = ops.first()
        assertEquals("f1", food.rowId)
        assertEquals(false, food.changes.containsKey("lastUsedAt") || food.changes.containsKey("id"))

        // Later changes to the food are shared now.
        phoneA.sql("UPDATE foods SET kcalPer100g = 460 WHERE uuid = 'f1'")
        assertEquals(1, a.flush("A"))

        b.apply("B", a.opsFor(emptyMap()))
        assertEquals("Granola", phoneB.text("SELECT name FROM foods WHERE uuid = 'f1'"))
        assertEquals("460.0", phoneB.text("SELECT kcalPer100g FROM foods WHERE uuid = 'f1'"))
        assertEquals("0", phoneB.text("SELECT lastUsedAt FROM foods WHERE uuid = 'f1'"))
    }

    @Test
    fun nothingIsRecordedOutsideAHousehold() = runBlocking {
        a.setRecording(false)
        phoneA.addStore("s1", "Albert")
        assertEquals(0, a.flush("A"))
        a.setRecording(true)
        a.snapshotAll()
        assertEquals(1, a.flush("A"))
    }
}
