package com.calore.tracker.domain.sync

/**
 * A hybrid logical clock value (algorithms A22): wall time, a counter for events in the same
 * millisecond, and the device, compared in that order. [encode] sorts like the clock.
 */
data class Hlc(val wallMillis: Long, val counter: Int, val deviceId: String) : Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int = compareValuesBy(this, other, { it.wallMillis }, { it.counter }, { it.deviceId })

    fun encode(): String = "%013d-%05d-%s".format(wallMillis, counter, deviceId)

    companion object {
        fun parse(text: String): Hlc? {
            val parts = text.split('-', limit = 3)
            if (parts.size != 3 || parts[0].length != 13 || parts[1].length != 5) return null
            val wall = parts[0].toLongOrNull() ?: return null
            val counter = parts[1].toIntOrNull() ?: return null
            return Hlc(wall, counter, parts[2]).takeIf { parts[2].isNotEmpty() }
        }
    }
}

/** The clock of one device: [tick] for local changes, [receive] for clocks seen in incoming ops. */
class HlcClock(private val deviceId: String, private val now: () -> Long, start: Hlc? = null) {
    var last: Hlc = start ?: Hlc(0, 0, deviceId)
        private set

    fun tick(): Hlc {
        val wall = maxOf(now(), last.wallMillis)
        last = Hlc(wall, if (wall == last.wallMillis) last.counter + 1 else 0, deviceId)
        return last
    }

    fun receive(remote: Hlc): Hlc {
        val physical = now()
        val wall = maxOf(physical, last.wallMillis, remote.wallMillis)
        val counter = when {
            wall == last.wallMillis && wall == remote.wallMillis -> maxOf(last.counter, remote.counter) + 1
            wall == last.wallMillis -> last.counter + 1
            wall == remote.wallMillis -> remote.counter + 1
            else -> 0
        }
        last = Hlc(wall, counter, deviceId)
        return last
    }
}

/**
 * One change to a shared row (A22): the changed columns with their new values (as JSON scalars
 * in text, null for SQL NULL). `seq` counts up per device.
 */
data class SyncOp(
    val opId: String,
    val deviceId: String,
    val seq: Long,
    val hlc: Hlc,
    val table: String,
    val rowId: String,
    val changes: Map<String, String?>,
)

/** "Anna had already taken box C3": a box this device took after someone else (A22). */
data class TakeConflict(val boxId: String, val earlierDevice: String)

/** What applying one op did. */
data class ApplyResult(val applied: Map<String, String?>, val conflict: TakeConflict? = null)

/** Where the engine reads and writes rows and per-column clocks; the database in the app, a map in tests. */
interface SyncTarget {
    fun fieldClock(table: String, rowId: String, column: String): Hlc?
    fun value(table: String, rowId: String, column: String): String?
    fun write(table: String, rowId: String, values: Map<String, String?>, clocks: Map<String, Hlc>)
}

/** The sync rules (algorithms A22). */
object SyncEngine {

    /** Box states that mean "taken". */
    val TAKEN_STATES = setOf("\"EATEN\"", "\"PART_EATEN\"")

    /** Ops older than this that every known device has seen may be deleted. */
    const val COMPACT_AFTER_DAYS = 30L

    /**
     * Applies [op] for the device [myDeviceId]: each column is written only if the op's clock is newer
     * than the column's (newest change per column wins), so applying the same op again changes nothing
     * and any order of ops ends in the same state. A box this device took that someone else had taken
     * earlier is reported as a [TakeConflict]; the values themselves still follow the column rule.
     */
    fun apply(op: SyncOp, target: SyncTarget, myDeviceId: String): ApplyResult {
        val conflict = takeConflict(op, target, myDeviceId)
        val winning = op.changes.filter { (column, _) -> target.fieldClock(op.table, op.rowId, column)?.let { op.hlc > it } ?: true }
        if (winning.isNotEmpty()) target.write(op.table, op.rowId, winning, winning.keys.associateWith { op.hlc })
        return ApplyResult(winning, conflict)
    }

    private fun takeConflict(op: SyncOp, target: SyncTarget, me: String): TakeConflict? {
        if (op.table != "boxes" || op.deviceId == me || op.changes["state"] !in TAKEN_STATES) return null
        val mine = target.fieldClock(op.table, op.rowId, "state") ?: return null
        val taken = target.value(op.table, op.rowId, "state") in TAKEN_STATES
        return if (taken && mine.deviceId == me && op.hlc < mine) TakeConflict(op.rowId, op.deviceId) else null
    }

    /** The ops a device with [theirs] (device → highest seq seen) is missing, in seq order per device. */
    fun missing(log: List<SyncOp>, theirs: Map<String, Long>): List<SyncOp> =
        log.filter { it.seq > (theirs[it.deviceId] ?: 0L) }.sortedWith(compareBy({ it.deviceId }, { it.seq }))

    /** The highest seq per device with no gaps before it, from the ops known. */
    fun versionVector(log: List<SyncOp>): Map<String, Long> = log.groupBy { it.deviceId }.mapValues { (_, ops) ->
        var expected = 1L
        for (seq in ops.map { it.seq }.distinct().sorted()) {
            if (seq != expected) break
            expected++
        }
        expected - 1
    }

    /** A22 compaction: older than 30 days and seen by every known device ([peers]: device → its version vector). */
    fun compactable(log: List<SyncOp>, nowMillis: Long, peers: Map<String, Map<String, Long>>): List<SyncOp> {
        val cutoff = nowMillis - COMPACT_AFTER_DAYS * 24 * 60 * 60 * 1000
        return log.filter { op -> op.hlc.wallMillis < cutoff && peers.values.all { (it[op.deviceId] ?: 0L) >= op.seq } }
    }
}
