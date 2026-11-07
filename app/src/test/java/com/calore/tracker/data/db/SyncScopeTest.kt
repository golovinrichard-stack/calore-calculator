package com.calore.tracker.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** M7.6: cycle tables never sync; every table of the current schema is either private or shared. */
class SyncScopeTest {

    private val schema = File("schemas/com.calore.tracker.data.db.CaloreDatabase")
        .listFiles { f -> f.name.endsWith(".json") }!!
        .maxBy { it.nameWithoutExtension.toInt() }

    private val tables: Set<String> = Json.parseToJsonElement(schema.readText()).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
        .map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet()

    @Test
    fun cycleDataIsPrivate() {
        assertTrue(schema.name, "cycle_periods" in tables && "cycle_days" in tables)
        assertTrue("cycle_periods" in SyncScope.PRIVATE && "cycle_days" in SyncScope.PRIVATE)
        assertTrue(SyncScope.SHARED.none { it.startsWith("cycle") })
    }

    @Test
    fun everyTableIsEitherPrivateOrShared() {
        assertEquals(emptySet<String>(), SyncScope.PRIVATE intersect SyncScope.SHARED)
        assertEquals(tables, SyncScope.PRIVATE + SyncScope.SHARED)
    }
}
