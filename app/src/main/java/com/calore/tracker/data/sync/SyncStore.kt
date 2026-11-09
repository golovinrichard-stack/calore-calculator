package com.calore.tracker.data.sync

import android.database.Cursor
import android.database.SQLException
import androidx.sqlite.db.SupportSQLiteDatabase
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.domain.newId
import com.calore.tracker.domain.sync.ApplyResult
import com.calore.tracker.domain.sync.Hlc
import com.calore.tracker.domain.sync.HlcClock
import com.calore.tracker.domain.sync.SyncEngine
import com.calore.tracker.domain.sync.SyncOp
import com.calore.tracker.domain.sync.SyncTarget
import com.calore.tracker.domain.sync.TakeConflict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Clock

/** A row an incoming op changed: which columns, and which phone made the change. */
data class AppliedRow(val table: String, val rowId: String, val columns: Set<String>, val deviceId: String)

/** What applying a batch of incoming ops did. */
data class ApplyOutcome(val applied: Int, val parked: Int, val conflicts: List<TakeConflict>, val rows: List<AppliedRow>) {
    val tables: Set<String> get() = rows.mapTo(HashSet()) { it.table }
}

/**
 * The sync bookkeeping in the database (M11.3, A22): turns recorded local changes into ops
 * ([flush], the `SyncWriter`), applies incoming ops ([apply]), and answers what a peer is missing.
 * Writes from applying ops are not recorded again (`sync_state.mode` = 2 meanwhile).
 */
class SyncStore(private val database: CaloreDatabase, private val clock: Clock) {

    private val db: SupportSQLiteDatabase get() = database.openHelper.writableDatabase
    private var hlc: HlcClock? = null

    private suspend fun <T> transaction(block: SupportSQLiteDatabase.() -> T): T = withContext(Dispatchers.IO) {
        database.runInTransaction<T> { db.block() }
    }

    /** 0: not in a household (nothing recorded), 1: record local changes. */
    suspend fun setRecording(on: Boolean) = transaction { execSQL("UPDATE sync_state SET mode = ? WHERE id = 1", arrayOf(if (on) 1 else 0)) }

    private fun clockFor(deviceId: String): HlcClock = hlc?.takeIf { it.last.deviceId == deviceId } ?: run {
        val latest = db.query("SELECT MAX(hlc) FROM sync_ops").use { c -> if (c.moveToFirst() && !c.isNull(0)) Hlc.parse(c.getString(0)) else null }
        HlcClock(deviceId, clock::millis, latest?.copy(deviceId = deviceId)).also { hlc = it }
    }

    /** Every shared row as changed, so it goes to the household (creating or joining one). */
    suspend fun snapshotAll() = transaction {
        for (spec in SyncTriggers.SPECS) {
            val filter = spec.rowFilter?.let { " WHERE " + it.replace("ROW.", "") }.orEmpty()
            execSQL("INSERT INTO sync_changes (tableName, rowId, `column`) SELECT '${spec.table}', `${spec.key}`, '${SyncTriggers.ALL}' FROM `${spec.table}`$filter")
        }
    }

    /** The `SyncWriter`: recorded changes become ops with this device's next seq and a new clock. Returns how many. */
    suspend fun flush(deviceId: String): Int = transaction {
        val changes = query("SELECT id, tableName, rowId, `column` FROM sync_changes ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1) to c.getString(2), c.getString(3))) }
        }
        if (changes.isEmpty()) return@transaction 0
        val clockNow = clockFor(deviceId)
        var seq = query("SELECT COALESCE(MAX(seq), 0) FROM sync_ops WHERE deviceId = ?", arrayOf(deviceId)).use { it.moveToFirst(); it.getLong(0) }
        var made = 0
        val sent = HashSet<String>()
        fun emit(table: String, rowId: String, values: Map<String, String?>) {
            val op = SyncOp(newId(), deviceId, ++seq, clockNow.tick(), table, rowId, values)
            insertOp(this, op, applied = true)
            values.keys.forEach { setFieldClock(this, table, rowId, it, op.hlc) }
            made++
        }
        val foods = SyncTriggers.spec("foods")!!
        fun everSynced(table: String, rowId: String) =
            query("SELECT 1 FROM sync_field_clock WHERE tableName = ? AND rowId = ? LIMIT 1", arrayOf(table, rowId)).use { it.moveToFirst() }
        for ((key, group) in changes.groupBy({ it.second }, { it.third })) {
            val (table, rowId) = key
            val spec = SyncTriggers.spec(table) ?: continue
            if (table == "foods" && (rowId in sent || (!everSynced(table, rowId) && !foodInUse(this, rowId)))) continue
            val row = readRow(this, spec, rowId)
            val values: Map<String, String?> = when {
                row == null && SyncTriggers.DELETED in group -> mapOf(SyncTriggers.DELETED to "true")
                row == null -> continue
                SyncTriggers.ALL in group || SyncTriggers.DELETED in group -> row
                else -> group.filter { it in row }.associateWith { row[it] }
            }
            if (values.isEmpty()) continue
            // The food a shared row points at goes first, the first time.
            val foodUuid = SyncTriggers.FOOD_REFS[table]?.let { values[it] }?.let { decode(it) as? String }
            if (foodUuid != null && foodUuid !in sent && !everSynced("foods", foodUuid)) {
                readRow(this, foods, foodUuid)?.takeIf { it["key"] == null }?.let { food ->
                    emit("foods", foodUuid, food)
                    sent += foodUuid
                }
            }
            emit(table, rowId, values)
            if (table == "foods") sent += rowId
        }
        execSQL("DELETE FROM sync_changes WHERE id <= ?", arrayOf(changes.last().first))
        if (made > 0) setMaxSeen(this, deviceId, seq)
        made
    }

    private fun foodInUse(db: SupportSQLiteDatabase, uuid: String): Boolean = SyncTriggers.FOOD_REFS.any { (table, column) ->
        db.query("SELECT 1 FROM `$table` WHERE `$column` = ? LIMIT 1", arrayOf(uuid)).use { it.moveToFirst() }
    }

    /** Applies incoming ops (A22) in clock order; ops whose row isn't here yet wait and are tried again. */
    suspend fun apply(myDeviceId: String, ops: List<SyncOp>): ApplyOutcome = transaction {
        execSQL("UPDATE sync_state SET mode = 2 WHERE id = 1")
        val clockNow = clockFor(myDeviceId)
        val conflicts = ArrayList<TakeConflict>()
        val rows = ArrayList<AppliedRow>()
        var applied = 0
        try {
            for (op in ops.sortedBy { it.hlc }) {
                if (SyncTriggers.spec(op.table) == null) continue
                val known = query("SELECT applied FROM sync_ops WHERE opId = ?", arrayOf(op.opId)).use { if (it.moveToFirst()) it.getInt(0) == 1 else null }
                if (known == true) continue
                clockNow.receive(op.hlc)
                val result = tryApply(this, op, myDeviceId)
                if (known == null) insertOp(this, op, applied = result != null) else if (result != null) markApplied(this, op.opId)
                advanceSeen(this, op.deviceId)
                if (result != null) {
                    applied++
                    if (result.applied.isNotEmpty()) rows += AppliedRow(op.table, op.rowId, result.applied.keys, op.deviceId)
                    result.conflict?.let(conflicts::add)
                }
            }
            // Parked ops: their rows may be here now.
            var progress = true
            while (progress) {
                progress = false
                for (op in parkedOps(this)) {
                    val result = tryApply(this, op, myDeviceId) ?: continue
                    markApplied(this, op.opId)
                    applied++
                    if (result.applied.isNotEmpty()) rows += AppliedRow(op.table, op.rowId, result.applied.keys, op.deviceId)
                    result.conflict?.let(conflicts::add)
                    progress = true
                }
            }
        } finally {
            execSQL("UPDATE sync_state SET mode = 1 WHERE id = 1")
        }
        ApplyOutcome(applied, parkedOps(this).size, conflicts, rows)
    }

    /** This phone's version vector (A22). */
    suspend fun versionVector(): Map<String, Long> = transaction {
        query("SELECT deviceId, maxSeqSeen FROM sync_peers").use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) } }
    }

    /** The ops a peer with [theirs] is missing, oldest clock first, at most [limit] (M11.4: 500 per batch). */
    suspend fun opsFor(theirs: Map<String, Long>, limit: Int = 500): List<SyncOp> = transaction {
        query("SELECT opId, deviceId, seq, hlc, tableName, rowId, payload FROM sync_ops ORDER BY hlc").use { c ->
            buildList {
                while (c.moveToNext() && size < limit) {
                    val op = readOp(c) ?: continue
                    if (op.seq > (theirs[op.deviceId] ?: 0L)) add(op)
                }
            }
        }
    }

    /** Changes of this phone the hub hasn't confirmed yet (M11.10: "3 changes waiting for home Wi-Fi"). */
    suspend fun waiting(deviceId: String, hubSeen: Long): Int = transaction {
        val ops = query("SELECT COUNT(*) FROM sync_ops WHERE deviceId = ? AND seq > ?", arrayOf<Any>(deviceId, hubSeen)).use { it.moveToFirst(); it.getInt(0) }
        val recorded = query("SELECT COUNT(DISTINCT tableName || ':' || rowId) FROM sync_changes").use { it.moveToFirst(); it.getInt(0) }
        ops + recorded
    }

    /** A22 compaction: applied ops older than 30 days that every known phone has seen. */
    suspend fun compact(peers: Map<String, Map<String, Long>>): Int = transaction {
        val all = query("SELECT opId, deviceId, seq, hlc, tableName, rowId, payload FROM sync_ops WHERE applied = 1").use { c ->
            buildList { while (c.moveToNext()) readOp(c)?.let(::add) }
        }
        val old = SyncEngine.compactable(all, clock.millis(), peers)
        old.forEach { execSQL("DELETE FROM sync_ops WHERE opId = ?", arrayOf(it.opId)) }
        old.size
    }

    /** Leaving the household: the bookkeeping goes; the data stays as it is. */
    suspend fun reset() = transaction {
        listOf("sync_ops", "sync_field_clock", "sync_peers", "sync_changes").forEach { execSQL("DELETE FROM $it") }
        execSQL("UPDATE sync_state SET mode = 0 WHERE id = 1")
        hlc = null
    }

    // --- Helpers (inside a transaction) ---

    private fun readRow(db: SupportSQLiteDatabase, spec: SyncTableSpec, rowId: String): Map<String, String?>? =
        db.query("SELECT * FROM `${spec.table}` WHERE `${spec.key}` = ?", arrayOf(rowId)).use { c ->
            if (!c.moveToFirst()) return null
            buildMap {
                for (i in 0 until c.columnCount) {
                    val name = c.getColumnName(i)
                    if (name in spec.localColumns) continue
                    put(name, encode(c, i))
                }
            }
        }

    private fun encode(c: Cursor, i: Int): String? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(c.getDouble(i)).toString()
        else -> JsonPrimitive(c.getString(i)).toString()
    }

    private fun decode(value: String?): Any? {
        if (value == null) return null
        val p = Json.parseToJsonElement(value) as? JsonPrimitive ?: return value
        return when {
            p is JsonNull -> null
            p.isString -> p.content
            p.longOrNull != null -> p.longOrNull
            else -> p.doubleOrNull
        }
    }

    /** A [SyncTarget] over the database; a row that isn't here and can't be made from the op parks it. */
    private class Parked : Exception()

    private fun tryApply(db: SupportSQLiteDatabase, op: SyncOp, me: String): ApplyResult? {
        val spec = SyncTriggers.spec(op.table) ?: return null
        val target = object : SyncTarget {
            override fun fieldClock(table: String, rowId: String, column: String): Hlc? =
                db.query("SELECT hlc FROM sync_field_clock WHERE tableName = ? AND rowId = ? AND `column` = ?", arrayOf(table, rowId, column))
                    .use { if (it.moveToFirst()) Hlc.parse(it.getString(0)) else null }

            override fun value(table: String, rowId: String, column: String): String? =
                if (column in spec.localColumns) null else readRow(db, spec, rowId)?.get(column)

            override fun write(table: String, rowId: String, values: Map<String, String?>, clocks: Map<String, Hlc>) {
                if (values[SyncTriggers.DELETED] == null && fieldClock(table, rowId, SyncTriggers.DELETED) != null) {
                    return // Removed on some phone: a later change of a column doesn't bring it back.
                }
                if (values[SyncTriggers.DELETED] == "true") {
                    db.execSQL("DELETE FROM `$table` WHERE `${spec.key}` = ?", arrayOf(rowId))
                } else {
                    val columns = values.keys.filter { it != SyncTriggers.DELETED && it !in spec.localColumns }
                    val exists = db.query("SELECT 1 FROM `$table` WHERE `${spec.key}` = ?", arrayOf(rowId)).use { it.moveToFirst() }
                    if (exists) {
                        if (columns.isNotEmpty()) {
                            db.execSQL(
                                "UPDATE `$table` SET ${columns.joinToString { "`$it` = ?" }} WHERE `${spec.key}` = ?",
                                (columns.map { decode(values[it]) } + rowId).toTypedArray(),
                            )
                        }
                    } else {
                        val all = (columns + spec.key + spec.insertDefaults.keys).distinct()
                        val args = all.map { if (it == spec.key) rowId else spec.insertDefaults[it] ?: decode(values[it]) }
                        try {
                            db.execSQL("INSERT INTO `$table` (${all.joinToString { "`$it`" }}) VALUES (${all.joinToString { "?" }})", args.toTypedArray())
                        } catch (e: SQLException) {
                            throw Parked()
                        }
                    }
                }
                clocks.forEach { (column, h) -> setFieldClock(db, table, rowId, column, h) }
            }
        }
        return try {
            SyncEngine.apply(op, target, me)
        } catch (e: Parked) {
            null
        }
    }

    private fun insertOp(db: SupportSQLiteDatabase, op: SyncOp, applied: Boolean) {
        db.execSQL(
            "INSERT OR IGNORE INTO sync_ops (opId, deviceId, seq, hlc, tableName, rowId, payload, receivedAt, applied) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(op.opId, op.deviceId, op.seq, op.hlc.encode(), op.table, op.rowId, payload(op), clock.millis(), if (applied) 1 else 0),
        )
    }

    private fun markApplied(db: SupportSQLiteDatabase, opId: String) = db.execSQL("UPDATE sync_ops SET applied = 1 WHERE opId = ?", arrayOf(opId))

    private fun parkedOps(db: SupportSQLiteDatabase): List<SyncOp> =
        db.query("SELECT opId, deviceId, seq, hlc, tableName, rowId, payload FROM sync_ops WHERE applied = 0 ORDER BY hlc").use { c ->
            buildList { while (c.moveToNext()) readOp(c)?.let(::add) }
        }

    private fun setFieldClock(db: SupportSQLiteDatabase, table: String, rowId: String, column: String, h: Hlc) =
        db.execSQL("INSERT OR REPLACE INTO sync_field_clock (tableName, rowId, `column`, hlc) VALUES (?, ?, ?, ?)", arrayOf(table, rowId, column, h.encode()))

    private fun setMaxSeen(db: SupportSQLiteDatabase, deviceId: String, seq: Long) =
        db.execSQL("INSERT OR REPLACE INTO sync_peers (deviceId, maxSeqSeen) VALUES (?, ?)", arrayOf<Any>(deviceId, seq))

    /** The highest seq from [deviceId] with no gap before it. */
    private fun advanceSeen(db: SupportSQLiteDatabase, deviceId: String) {
        var seen = db.query("SELECT maxSeqSeen FROM sync_peers WHERE deviceId = ?", arrayOf(deviceId)).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        while (db.query("SELECT 1 FROM sync_ops WHERE deviceId = ? AND seq = ?", arrayOf<Any>(deviceId, seen + 1)).use { it.moveToFirst() }) seen++
        setMaxSeen(db, deviceId, seen)
    }

    companion object {
        fun payload(op: SyncOp): String = JsonObject(op.changes.mapValues { (_, v) -> v?.let { Json.parseToJsonElement(it) } ?: JsonNull }).toString()

        fun readOp(c: Cursor): SyncOp? {
            val h = Hlc.parse(c.getString(3)) ?: return null
            val changes = Json.parseToJsonElement(c.getString(6)).jsonObject.mapValues { (_, v) -> if (v is JsonNull) null else v.toString() }
            return SyncOp(c.getString(0), c.getString(1), c.getLong(2), h, c.getString(4), c.getString(5), changes)
        }
    }
}
