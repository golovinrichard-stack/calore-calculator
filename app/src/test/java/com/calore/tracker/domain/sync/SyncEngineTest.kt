package com.calore.tracker.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SyncEngineTest {

    /** One device: its rows, column clocks and every op it knows. */
    private class Replica(val deviceId: String, now: () -> Long) : SyncTarget {
        val rows = HashMap<Pair<String, String>, MutableMap<String, String?>>()
        val clocks = HashMap<Triple<String, String, String>, Hlc>()
        val log = ArrayList<SyncOp>()
        val clock = HlcClock(deviceId, now)
        private var seq = 0L
        val conflicts = ArrayList<TakeConflict>()

        override fun fieldClock(table: String, rowId: String, column: String) = clocks[Triple(table, rowId, column)]
        override fun value(table: String, rowId: String, column: String) = rows[table to rowId]?.get(column)
        override fun write(table: String, rowId: String, values: Map<String, String?>, clocks: Map<String, Hlc>) {
            rows.getOrPut(table to rowId) { HashMap() }.putAll(values)
            clocks.forEach { (c, h) -> this.clocks[Triple(table, rowId, c)] = h }
        }

        fun change(table: String, rowId: String, changes: Map<String, String?>): SyncOp {
            val op = SyncOp("$deviceId-${seq + 1}", deviceId, ++seq, clock.tick(), table, rowId, changes)
            log += op
            SyncEngine.apply(op, this, deviceId)
            return op
        }

        fun receive(ops: List<SyncOp>) {
            for (op in ops) {
                clock.receive(op.hlc)
                if (log.none { it.opId == op.opId }) log += op
                SyncEngine.apply(op, this, deviceId).conflict?.let { conflicts += it }
            }
        }

        /** A sync with another device, both ways (A22). */
        fun syncWith(other: Replica) {
            val forOther = SyncEngine.missing(log, SyncEngine.versionVector(other.log))
            val forMe = SyncEngine.missing(other.log, SyncEngine.versionVector(log))
            other.receive(forOther)
            receive(forMe)
        }
    }

    private var time = 1_000_000L
    private fun replica(id: String) = Replica(id) { time }

    @Test
    fun twoDevicesEditingDifferentColumnsBothKeepTheirChange() {
        val a = replica("A")
        val b = replica("B")
        a.change("recipes", "r1", mapOf("name" to "\"Chili\"", "servings" to "4"))
        a.syncWith(b)
        time += 10
        a.change("recipes", "r1", mapOf("name" to "\"Chili con carne\""))
        b.change("recipes", "r1", mapOf("servings" to "6"))
        a.syncWith(b)
        for (r in listOf(a, b)) assertEquals(mapOf("name" to "\"Chili con carne\"", "servings" to "6"), r.rows["recipes" to "r1"])
    }

    @Test
    fun theSameColumnKeepsTheLaterClock() {
        val a = replica("A")
        val b = replica("B")
        a.change("pantry_items", "p1", mapOf("amount" to "500"))
        time += 5
        b.change("pantry_items", "p1", mapOf("amount" to "300"))
        b.syncWith(a)
        assertEquals("300", a.rows["pantry_items" to "p1"]!!["amount"])
        assertEquals("300", b.rows["pantry_items" to "p1"]!!["amount"])
    }

    @Test
    fun opsAppliedInAnyOrderGiveTheSameState_3devices500ops() {
        val random = Random(22)
        repeat(5) { round ->
            val devices = listOf(replica("A"), replica("B"), replica("C"))
            val tables = listOf("boxes", "pantry_items", "shopping_items")
            val columns = listOf("state", "amount", "name", "storage")
            repeat(500) {
                time += random.nextLong(0, 3) // same milliseconds happen: the counter decides
                val d = devices[random.nextInt(3)]
                val changes = columns.shuffled(random).take(random.nextInt(1, 3)).associateWith { "\"v${random.nextInt(5)}\"" }
                d.change(tables[random.nextInt(3)], "row${random.nextInt(8)}", changes)
                if (random.nextInt(10) == 0) {
                    val (x, y) = devices.shuffled(random).take(2)
                    x.syncWith(y)
                }
            }
            // A device that gets every op in a random order, some twice, ends the same.
            val everything = devices.flatMap { it.log }.distinctBy { it.opId }
            val shuffled = replica("Z").apply { receive((everything + everything.shuffled(random).take(50)).shuffled(random)) }
            repeat(3) { devices[0].syncWith(devices[1]); devices[1].syncWith(devices[2]); devices[2].syncWith(devices[0]) }
            for (d in devices) assertEquals("round $round, ${d.deviceId}", shuffled.rows, d.rows)
            assertEquals(500, everything.size)
        }
    }

    @Test
    fun aReplayedOpChangesNothing() {
        val a = replica("A")
        val b = replica("B")
        val first = a.change("shopping_items", "s1", mapOf("name" to "\"Eggs\""))
        time += 10
        a.change("shopping_items", "s1", mapOf("name" to "\"Milk\""))
        a.syncWith(b)
        val before = b.rows.toMap() to b.clocks.toMap()
        b.receive(listOf(first, first))
        assertEquals(before, b.rows.toMap() to b.clocks.toMap())
        assertTrue(SyncEngine.apply(first, b, "B").applied.isEmpty())
    }

    @Test
    fun takingABoxSomeoneTookEarlierIsReported() {
        val anna = replica("anna")
        val tom = replica("tom")
        anna.change("boxes", "C3", mapOf("state" to "\"READY\""))
        anna.syncWith(tom)
        time += 10
        anna.change("boxes", "C3", mapOf("state" to "\"EATEN\""))
        time += 10
        tom.change("boxes", "C3", mapOf("state" to "\"EATEN\""))
        tom.syncWith(anna)
        // Tom took it later: he is told; Anna is not.
        assertEquals(listOf(TakeConflict("C3", "anna")), tom.conflicts)
        assertTrue(anna.conflicts.isEmpty())
        assertEquals(anna.rows, tom.rows)
    }

    @Test
    fun clocksSortAsTextAndTickPastRemoteClocks() {
        val early = Hlc(1_000, 2, "b")
        val late = Hlc(1_000, 10, "a")
        assertTrue(early < late)
        assertTrue(early.encode() < late.encode())
        assertEquals(late, Hlc.parse(late.encode()))
        assertNull(Hlc.parse("nonsense"))
        val clock = HlcClock("x", now = { 500 })
        val received = clock.receive(Hlc(2_000, 7, "y"))
        assertEquals(Hlc(2_000, 8, "x"), received)
        assertEquals(Hlc(2_000, 9, "x"), clock.tick())
    }

    @Test
    fun versionVectorsAndCompaction() {
        val day = 24 * 60 * 60 * 1000L
        val ops = (1L..5L).map { SyncOp("a$it", "A", it, Hlc(it * day, 0, "A"), "boxes", "b", emptyMap()) } +
            SyncOp("b2", "B", 2, Hlc(day, 0, "B"), "boxes", "b", emptyMap())
        // B's seq 1 is missing: B's vector stays 0.
        assertEquals(mapOf("A" to 5L, "B" to 0L), SyncEngine.versionVector(ops))
        assertEquals(listOf("a4", "a5", "b2"), SyncEngine.missing(ops, mapOf("A" to 3L)).map { it.opId })
        val now = 40 * day
        val peers = mapOf("A" to mapOf("A" to 5L, "B" to 2L), "C" to mapOf("A" to 3L, "B" to 2L))
        // Older than 30 days and seen by everyone.
        assertEquals(listOf("a1", "a2", "a3", "b2"), SyncEngine.compactable(ops, now, peers).map { it.opId })
    }
}
