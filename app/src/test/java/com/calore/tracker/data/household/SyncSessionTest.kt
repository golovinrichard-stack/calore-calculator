package com.calore.tracker.data.household

import com.calore.tracker.data.sync.AppliedRow
import com.calore.tracker.data.sync.ApplyOutcome
import com.calore.tracker.data.sync.BadFrameException
import com.calore.tracker.domain.sync.Hlc
import com.calore.tracker.domain.sync.HlcClock
import com.calore.tracker.domain.sync.SyncEngine
import com.calore.tracker.domain.sync.SyncOp
import com.calore.tracker.domain.sync.SyncTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** M11.4: the conversation between a phone and the hub, over a pipe instead of a socket. */
class SyncSessionTest {

    private val key = ByteArray(32) { it.toByte() }
    private val household = "household-1"

    /** A phone's data in memory, with the A22 rules. */
    private class Phone(override val deviceId: String) : SyncEndpoint {
        val log = mutableListOf<SyncOp>()
        val rows = HashMap<Pair<String, String>, MutableMap<String, String?>>()
        val clocks = HashMap<Triple<String, String, String>, Hlc>()
        private var seq = 0L
        private val clock = HlcClock(deviceId, { 1_000L })
        private val target = object : SyncTarget {
            override fun fieldClock(table: String, rowId: String, column: String) = clocks[Triple(table, rowId, column)]
            override fun value(table: String, rowId: String, column: String) = rows[table to rowId]?.get(column)
            override fun write(table: String, rowId: String, values: Map<String, String?>, clocks: Map<String, Hlc>) {
                rows.getOrPut(table to rowId) { HashMap() }.putAll(values)
                clocks.forEach { (c, h) -> this@Phone.clocks[Triple(table, rowId, c)] = h }
            }
        }

        fun write(table: String, rowId: String, values: Map<String, String?>) {
            val op = SyncOp(UUID.randomUUID().toString(), deviceId, ++seq, clock.tick(), table, rowId, values)
            log += op
            SyncEngine.apply(op, target, deviceId)
        }

        override suspend fun prepare() = Unit
        override suspend fun versionVector() = SyncEngine.versionVector(log)
        override suspend fun opsFor(theirs: Map<String, Long>) = SyncEngine.missing(log, theirs)
        override suspend fun apply(ops: List<SyncOp>): ApplyOutcome {
            val rows = ArrayList<AppliedRow>()
            for (op in ops.sortedBy { it.hlc }) {
                if (log.any { it.opId == op.opId }) continue
                clock.receive(op.hlc)
                val result = SyncEngine.apply(op, target, deviceId)
                log += op
                if (result.applied.isNotEmpty()) rows += AppliedRow(op.table, op.rowId, result.applied.keys, op.deviceId)
            }
            return ApplyOutcome(rows.size, 0, emptyList(), rows)
        }
    }

    /** Runs one session; returns (phone result, hub result) or throws what either side threw. */
    private fun sync(
        phone: Phone,
        hub: Phone,
        phoneKey: ByteArray = key,
        admission: Admission = Admission.MEMBER,
    ): Pair<SessionResult?, SessionResult?> {
        val toHub = PipedOutputStream()
        val hubIn = PipedInputStream(toHub, 1 shl 21)
        val toPhone = PipedOutputStream()
        val phoneIn = PipedInputStream(toPhone, 1 shl 21)
        val hubResult = AtomicReference<SessionResult?>()
        val hubError = AtomicReference<Throwable?>()
        val hubThread = thread {
            try {
                hubResult.set(runBlocking { SyncSession(key, household).runHub(DataInputStream(hubIn), DataOutputStream(toPhone), hub) { admission } })
            } catch (e: Throwable) {
                hubError.set(e)
            } finally {
                toPhone.close()
            }
        }
        var phoneError: Throwable? = null
        val phoneResult = try {
            runBlocking { SyncSession(phoneKey, household).runPhone(DataInputStream(phoneIn), DataOutputStream(toHub), phone) }
        } catch (e: Throwable) {
            phoneError = e
            null
        } finally {
            toHub.close()
        }
        hubThread.join(10_000)
        (hubError.get() ?: phoneError)?.let { throw it }
        return phoneResult to hubResult.get()
    }

    @Test
    fun changesGoThroughTheHubToTheOtherPhones() {
        val hub = Phone("hub")
        val anna = Phone("anna")
        val tom = Phone("tom")
        anna.write("shopping_items", "eggs", mapOf("name" to "\"Eggs\""))
        hub.write("shopping_items", "milk", mapOf("name" to "\"Milk\""))
        val (annaResult, _) = sync(anna, hub)
        assertEquals(1, annaResult!!.sent)
        // Both ways in one session: Anna has the hub's milk, and her eggs don't come back.
        assertEquals(1, annaResult.received!!.applied)
        assertEquals("\"Milk\"", anna.rows["shopping_items" to "milk"]!!["name"])
        val (tomResult, _) = sync(tom, hub)
        assertEquals(2, tomResult!!.received!!.applied)
        assertEquals("\"Eggs\"", tom.rows["shopping_items" to "eggs"]!!["name"])
        val (annaAgain, _) = sync(anna, hub)
        assertEquals(0, annaAgain!!.received!!.applied)
        assertEquals(0, annaAgain.sent)
        assertEquals(SyncEngine.versionVector(hub.log), SyncEngine.versionVector(anna.log))
    }

    @Test
    fun syncingTwiceChangesNothing() {
        val hub = Phone("hub")
        val anna = Phone("anna")
        anna.write("pantry_items", "p1", mapOf("amount" to "2"))
        sync(anna, hub)
        val (again, hubAgain) = sync(anna, hub)
        assertEquals(0, again!!.sent)
        assertEquals(0, again.received!!.applied)
        assertEquals(0, hubAgain!!.received!!.applied)
    }

    @Test
    fun bigChangesGoInBatchesOf500() {
        val hub = Phone("hub")
        val anna = Phone("anna")
        repeat(1_201) { anna.write("shopping_items", "i$it", mapOf("name" to "\"Item $it\"")) }
        val (result, hubResult) = sync(anna, hub)
        assertEquals(1_201, result!!.sent)
        assertEquals(1_201, hubResult!!.received!!.applied)
        assertEquals(1_201, hub.rows.size)
    }

    @Test(expected = BadFrameException::class)
    fun aPhoneWithTheWrongKeyGetsNothing() {
        val hub = Phone("hub")
        hub.write("shopping_items", "milk", mapOf("name" to "\"Milk\""))
        sync(Phone("stranger"), hub, phoneKey = ByteArray(32) { 7 })
    }

    @Test
    fun aWaitingPhoneSendsOnlyItsOwnPhoneRowAndSeesOnlyPhoneRows() {
        val hub = Phone("hub")
        hub.write("household_devices", "hub", mapOf("memberName" to "\"Jana\""))
        hub.write("shopping_items", "milk", mapOf("name" to "\"Milk\""))
        val newPhone = Phone("anna")
        newPhone.write("household_devices", "anna", mapOf("memberName" to "\"Anna\""))
        newPhone.write("recipes", "r1", mapOf("name" to "\"Chili\""))
        newPhone.write("household_devices", "hub", mapOf("memberName" to "\"Not Jana\""))
        sync(newPhone, hub, admission = Admission.WAITING)
        assertEquals(setOf("household_devices" to "hub", "household_devices" to "anna", "shopping_items" to "milk"), hub.rows.keys)
        assertEquals("\"Jana\"", hub.rows["household_devices" to "hub"]!!["memberName"])
        assertEquals(listOf("household_devices"), newPhone.log.filter { it.deviceId == "hub" }.map { it.table })

        // Once the host confirms the phone, the rest goes both ways; still nobody else writes the hub's row.
        val (member, _) = sync(newPhone, hub, admission = Admission.MEMBER)
        assertEquals("\"Chili\"", hub.rows["recipes" to "r1"]!!["name"])
        assertEquals(setOf("shopping_items"), member!!.received!!.tables)
        assertEquals("\"Jana\"", hub.rows["household_devices" to "hub"]!!["memberName"])
    }

    @Test(expected = BadFrameException::class)
    fun aRemovedPhoneIsTurnedAway() {
        val hub = Phone("hub")
        sync(Phone("removed"), hub, admission = Admission.REFUSED)
    }
}
