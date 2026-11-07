package com.calore.tracker.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import com.calore.tracker.data.db.SyncScope

/** How one shared table syncs: its row key, columns that stay on this phone, and rows that never sync. */
data class SyncTableSpec(
    val table: String,
    val key: String = "id",
    val localColumns: Set<String> = emptySet(),
    /** A condition on the row (`ROW` stands for NEW or OLD) that must hold for it to sync. */
    val rowFilter: String? = null,
    /** Values for phone-only columns that have no default, used when a row arrives from another phone. */
    val insertDefaults: Map<String, Any> = emptyMap(),
)

/**
 * M11.3 change capture: SQLite triggers on every shared table record which columns a local write
 * changed, in the same transaction as the write. They record only while the phone is in a
 * household and not while incoming ops are applied (`sync_state.mode` = 1). Private tables have no
 * triggers, so their writes can never become ops.
 */
object SyncTriggers {

    const val ALL = "*"
    const val DELETED = "__deleted"

    val SPECS: List<SyncTableSpec> = SyncScope.SHARED.sorted().map { table ->
        when (table) {
            // Basic foods have the same id on every phone and come with the app: never synced.
            "foods" -> SyncTableSpec(
                table,
                key = "uuid",
                localColumns = setOf("id", "lastUsedAt"),
                rowFilter = "ROW.`key` IS NULL",
                insertDefaults = mapOf("lastUsedAt" to 0L),
            )
            "household_devices" -> SyncTableSpec(table, localColumns = setOf("lastSeenAt", "seenVector"))
            else -> SyncTableSpec(table)
        }
    }

    fun spec(table: String): SyncTableSpec? = SPECS.firstOrNull { it.table == table }

    /** The synced columns of a table, given all its columns. */
    fun syncedColumns(spec: SyncTableSpec, columns: List<String>): List<String> = columns.filter { it !in spec.localColumns }

    /** The SQL that creates the three triggers of one table (temporary: made again whenever the database opens). */
    fun sql(spec: SyncTableSpec, columns: List<String>): List<String> {
        val t = spec.table
        val recording = "(SELECT mode FROM sync_state WHERE id = 1) = 1"
        fun filter(row: String) = spec.rowFilter?.let { " AND " + it.replace("ROW", row) }.orEmpty()
        val updated = syncedColumns(spec, columns).filter { it != spec.key }
        require(updated.isNotEmpty()) { "$t has no synced columns" }
        val updates = updated.joinToString(" ") { c ->
            "INSERT INTO sync_changes (tableName, rowId, `column`) SELECT '$t', NEW.`${spec.key}`, '$c' WHERE OLD.`$c` IS NOT NEW.`$c`;"
        }
        return listOf(
            "DROP TRIGGER IF EXISTS temp.`sync_insert_$t`",
            "DROP TRIGGER IF EXISTS temp.`sync_update_$t`",
            "DROP TRIGGER IF EXISTS temp.`sync_delete_$t`",
            "CREATE TEMP TRIGGER `sync_insert_$t` AFTER INSERT ON `$t` WHEN $recording${filter("NEW")} BEGIN " +
                "INSERT INTO sync_changes (tableName, rowId, `column`) VALUES ('$t', NEW.`${spec.key}`, '$ALL'); END",
            "CREATE TEMP TRIGGER `sync_update_$t` AFTER UPDATE ON `$t` WHEN $recording${filter("NEW")} BEGIN $updates END",
            "CREATE TEMP TRIGGER `sync_delete_$t` AFTER DELETE ON `$t` WHEN $recording${filter("OLD")} BEGIN " +
                "INSERT INTO sync_changes (tableName, rowId, `column`) VALUES ('$t', OLD.`${spec.key}`, '$DELETED'); END",
        )
    }

    /** Called when the database opens: the state row, and the triggers of every shared table. */
    fun install(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO sync_state (id, mode) VALUES (1, 0)")
        for (spec in SPECS) {
            val columns = db.query("PRAGMA table_info(`${spec.table}`)").use { c ->
                val name = c.getColumnIndexOrThrow("name")
                buildList { while (c.moveToNext()) add(c.getString(name)) }
            }
            if (columns.isEmpty()) continue
            sql(spec, columns).forEach(db::execSQL)
        }
    }
}
