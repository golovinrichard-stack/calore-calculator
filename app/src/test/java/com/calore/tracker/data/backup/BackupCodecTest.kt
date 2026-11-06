package com.calore.tracker.data.backup

import com.calore.tracker.domain.newId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupCodecTest {

    private val food = FoodRow(
        id = 1,
        uuid = "0f8fad5b-d9cb-469f-a165-70867728950e",
        name = "Kuřecí prsa \"chlazená\"",
        brand = "Vodňanské",
        barcode = "8590000000001",
        kcalPer100g = 110.0,
        proteinPer100g = 23.0,
        carbsPer100g = 0.0,
        fatPer100g = 1.5,
        fiberPer100g = null,
        sugarPer100g = null,
        servingGrams = 125.0,
        source = "OPEN_FOOD_FACTS",
        lastUsedAt = 1_762_000_000_000,
        updatedAt = 1_761_000_000_000,
    )

    private fun entry(id: Long, uuid: String = newId(), date: String = "2026-11-06") = DiaryEntryRow(
        id = id,
        uuid = uuid,
        date = date,
        meal = "LUNCH",
        foodId = 1,
        name = "Kuřecí prsa",
        grams = 150.0,
        kcal = 165.0,
        protein = 34.5,
        carbs = 0.0,
        fat = 2.25,
        fiber = null,
        sugar = null,
        createdAt = 1_762_400_000_000 + id,
    )

    private val weight = WeightEntryRow(id = 1, uuid = "7c9e6679-7425-40de-944b-e07fc1f90ae7", date = "2026-11-06", kg = 80.4)

    private fun backup(
        foods: List<FoodRow> = listOf(food),
        diary: List<DiaryEntryRow> = listOf(entry(1), entry(2)),
        weights: List<WeightEntryRow> = listOf(weight),
        version: Int = BackupCodec.VERSION,
    ) = BackupFile(
        format = BackupCodec.FORMAT,
        version = version,
        exportedAt = "2026-11-06T18:00:00Z",
        appVersion = "0.2.0",
        tables = BackupTables(foods, diary, weights),
    )

    private fun decode(backup: BackupFile) = BackupCodec.decode(BackupCodec.encode(backup))

    @Test
    fun roundTripKeepsEveryValue() {
        val original = backup()
        assertEquals(BackupReadResult.Ok(original), decode(original))
    }

    @Test
    fun fileHasTheDocumentedShape() {
        val root = Json.parseToJsonElement(BackupCodec.encode(backup())).jsonObject
        assertEquals(setOf("format", "version", "exportedAt", "appVersion", "tables"), root.keys)
        assertEquals("\"calore-backup\"", root["format"].toString())
        assertEquals("1", root["version"].toString())
        assertEquals(setOf("foods", "diary_entries", "weight_entries"), root["tables"]!!.jsonObject.keys)
    }

    @Test
    fun emptyDatabaseRoundTrips() {
        val empty = backup(foods = emptyList(), diary = emptyList(), weights = emptyList())
        val result = decode(empty) as BackupReadResult.Ok
        assertEquals(BackupCounts(0, 0, 0), result.backup.tables.counts)
    }

    @Test
    fun tablesMissingFromAnOlderFileAreEmptyAndUnknownKeysAreIgnored() {
        val text = """{"format":"calore-backup","version":1,"exportedAt":"2026-11-06T18:00:00Z",
            "appVersion":"0.2.0","comment":"hi","tables":{"weight_entries":[
            {"id":1,"uuid":"7c9e6679-7425-40de-944b-e07fc1f90ae7","date":"2026-11-06","kg":80.4,"note":"x"}]}}"""
        val result = BackupCodec.decode(text) as BackupReadResult.Ok
        assertEquals(BackupCounts(foods = 0, diaryEntries = 0, weightEntries = 1), result.backup.tables.counts)
    }

    @Test
    fun foreignFilesAreNotBackups() {
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode(""))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("hello"))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("\u0089PNG\r\n\u001a\n"))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("[1,2,3]"))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("""{"name":"some other app"}"""))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("""{"format":"other-backup","version":1}"""))
    }

    @Test
    fun newerVersionsAreRefused() {
        assertEquals(BackupReadResult.NewerVersion(2), decode(backup(version = 2)))
    }

    @Test
    fun brokenBackupsAreDamaged() {
        val text = BackupCodec.encode(backup())
        assertEquals("cut off", BackupReadResult.Damaged, BackupCodec.decode(text.substring(0, text.length / 2)))
        assertEquals(
            "no version",
            BackupReadResult.Damaged,
            BackupCodec.decode("""{"format":"calore-backup","tables":{}}"""),
        )
        assertEquals("version 0", BackupReadResult.Damaged, decode(backup(version = 0)))
        assertEquals(
            "missing header field",
            BackupReadResult.Damaged,
            BackupCodec.decode("""{"format":"calore-backup","version":1,"tables":{}}"""),
        )
        assertEquals(
            "wrong type",
            BackupReadResult.Damaged,
            BackupCodec.decode(text.replace("\"kg\":80.4", "\"kg\":\"heavy\"")),
        )
        assertEquals(
            "missing row field",
            BackupReadResult.Damaged,
            BackupCodec.decode(text.replace("\"kg\":80.4", "\"weight\":80.4")),
        )
    }

    @Test
    fun rowsTheDatabaseWouldRefuseAreDamaged() {
        val sameUuid = newId()
        val cases = mapOf(
            "duplicate diary uuid" to backup(diary = listOf(entry(1, sameUuid), entry(2, sameUuid))),
            "duplicate diary id" to backup(diary = listOf(entry(1), entry(1))),
            "duplicate barcode" to backup(foods = listOf(food, food.copy(id = 2, uuid = newId()))),
            "duplicate food uuid" to backup(foods = listOf(food, food.copy(id = 2, barcode = null))),
            "two weights on one day" to backup(weights = listOf(weight, weight.copy(id = 2, uuid = newId()))),
            "bad uuid" to backup(diary = listOf(entry(1, uuid = "not-a-uuid"))),
            "empty uuid" to backup(weights = listOf(weight.copy(uuid = ""))),
            "bad date" to backup(diary = listOf(entry(1, date = "2026-13-40"))),
            "negative kcal" to backup(foods = listOf(food.copy(kcalPer100g = -1.0))),
            "negative nutrient" to backup(diary = listOf(entry(1).copy(protein = -3.0))),
            "zero weight" to backup(weights = listOf(weight.copy(kg = 0.0))),
            "zero id" to backup(foods = listOf(food.copy(id = 0))),
        )
        for ((name, broken) in cases) {
            assertEquals(name, BackupReadResult.Damaged, decode(broken))
        }
    }

    @Test
    fun tenThousandDiaryEntriesRoundTrip() {
        val diary = (1L..10_000L).map { entry(it, date = "2026-${"%02d".format(1 + it % 12)}-01") }
        val original = backup(diary = diary)
        val text = BackupCodec.encode(original)
        assertTrue("about 300 bytes per entry", text.length < 5_000_000)
        assertEquals(BackupReadResult.Ok(original), BackupCodec.decode(text))
    }

    @Test
    fun readAtMostStopsAtTheLimit() {
        val bytes = ByteArray(1_000) { 'a'.code.toByte() }
        assertEquals(1_000, ByteArrayInputStream(bytes).readAtMost(1_000)!!.size)
        assertNull(ByteArrayInputStream(bytes).readAtMost(999))
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readAtMost(10)!!.size)
    }
}
