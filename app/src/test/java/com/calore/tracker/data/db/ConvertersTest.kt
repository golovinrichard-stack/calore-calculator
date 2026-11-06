package com.calore.tracker.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ConvertersTest {

    private enum class Colour { RED, GREEN, BLUE }

    private val converters = Converters()

    @Test
    fun stringListRoundTrip() {
        val values = listOf("česká kuchyně", "with \"quotes\", commas", "", "emoji 🍲")
        assertEquals(values, converters.jsonToStringList(converters.stringListToJson(values)))
        assertEquals(emptyList<String>(), converters.jsonToStringList(converters.stringListToJson(emptyList())))
    }

    @Test
    fun stringIntMapRoundTrip() {
        val split = mapOf("BREAKFAST" to 20, "LUNCH" to 30, "DINNER" to 30, "SNACK" to 20)
        val json = converters.stringIntMapToJson(split)
        assertEquals("""{"BREAKFAST":20,"LUNCH":30,"DINNER":30,"SNACK":20}""", json)
        assertEquals(split, converters.jsonToStringIntMap(json))
    }

    @Test
    fun enumListRoundTripKeepsOrder() {
        val values = listOf(Colour.BLUE, Colour.RED)
        val json = JsonColumns.encodeEnums(values)
        assertEquals("""["BLUE","RED"]""", json)
        assertEquals(values, JsonColumns.decodeEnums<Colour>(json))

        val days = listOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY)
        assertEquals(days, JsonColumns.decodeEnums<DayOfWeek>(JsonColumns.encodeEnums(days)))
    }

    @Test
    fun unknownEnumValueIsIgnored() {
        assertEquals(listOf(Colour.RED, Colour.BLUE), JsonColumns.decodeEnums<Colour>("""["RED","PURPLE","BLUE"]"""))
        assertEquals(
            mapOf(Colour.GREEN to 2),
            JsonColumns.decodeEnumIntMap<Colour>("""{"GREEN":2,"ULTRAVIOLET":7}"""),
        )
    }

    @Test
    fun enumMapRoundTrip() {
        val map = mapOf(Colour.RED to 1, Colour.BLUE to 3)
        assertEquals(map, JsonColumns.decodeEnumIntMap<Colour>(JsonColumns.encodeEnumIntMap(map)))
    }

    @Test
    fun blankOrBrokenTextGivesEmptyValues() {
        assertTrue(converters.jsonToStringList(null).isEmpty())
        assertTrue(converters.jsonToStringList("").isEmpty())
        assertTrue(converters.jsonToStringList("not json").isEmpty())
        assertTrue(converters.jsonToStringList("""{"a":1}""").isEmpty())
        assertTrue(converters.jsonToStringIntMap("[1,2]").isEmpty())
        assertTrue(JsonColumns.decodeEnums<Colour>("[1,2").isEmpty())
    }
}
