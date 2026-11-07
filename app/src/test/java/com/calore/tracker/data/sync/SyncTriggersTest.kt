package com.calore.tracker.data.sync

import com.calore.tracker.data.db.SyncScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** M11.3: change capture covers exactly the shared tables, with columns that exist in the current schema. */
class SyncTriggersTest {

    private val schema = File("schemas/com.calore.tracker.data.db.CaloreDatabase")
        .listFiles { f -> f.name.endsWith(".json") }!!
        .maxBy { it.nameWithoutExtension.toInt() }

    private val columns: Map<String, List<String>> = Json.parseToJsonElement(schema.readText()).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
        .associate { e ->
            e.jsonObject["tableName"]!!.jsonPrimitive.content to
                e.jsonObject["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        }

    @Test
    fun onlySharedTablesHaveTriggers() {
        assertEquals(SyncScope.SHARED, SyncTriggers.SPECS.map { it.table }.toSet())
        for (table in SyncScope.PRIVATE + SyncScope.LOCAL) assertEquals(table, null, SyncTriggers.spec(table))
    }

    @Test
    fun keysAndPhoneOnlyColumnsExist() {
        for (spec in SyncTriggers.SPECS) {
            val cols = columns[spec.table]!!
            assertTrue(spec.table, spec.key in cols)
            assertTrue(spec.table, cols.containsAll(spec.localColumns))
            assertTrue(spec.table, spec.localColumns.containsAll(spec.insertDefaults.keys))
            assertFalse(spec.table, spec.key in spec.localColumns)
        }
    }

    @Test
    fun everyTableGetsInsertUpdateAndDeleteTriggers() {
        for (spec in SyncTriggers.SPECS) {
            val sql = SyncTriggers.sql(spec, columns[spec.table]!!)
            assertEquals(6, sql.size)
            val creates = sql.filter { it.startsWith("CREATE TEMP TRIGGER") }
            assertEquals(listOf("INSERT", "UPDATE", "DELETE"), creates.map { it.substringAfter("AFTER ").substringBefore(" ON") })
            creates.forEach { assertTrue(it, "(SELECT mode FROM sync_state WHERE id = 1) = 1" in it) }
        }
    }

    @Test
    fun phoneOnlyColumnsAreNeverRecorded() {
        val foods = SyncTriggers.spec("foods")!!
        val update = SyncTriggers.sql(foods, columns["foods"]!!).single { it.contains("AFTER UPDATE") }
        assertFalse(update.contains("'lastUsedAt'"))
        assertFalse(update.contains("'id'"))
        assertTrue(update.contains("'name'"))
        // Basic foods (with a key) come with the app and never sync.
        assertTrue(update.contains("NEW.`key` IS NULL"))
        val devices = SyncTriggers.sql(SyncTriggers.spec("household_devices")!!, columns["household_devices"]!!).single { it.contains("AFTER UPDATE") }
        assertFalse(devices.contains("'lastSeenAt'") || devices.contains("'seenVector'"))
        assertTrue(devices.contains("'memberName'"))
    }
}
