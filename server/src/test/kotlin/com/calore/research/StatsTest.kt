package com.calore.research

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsTest {

    private fun row(person: String, kcal: Int, ageBand: String = "25-29", week: String = "2026-W45") = StoredReport(
        UUID.randomUUID().toString(), person, week, week,
        """{"nutrition":{"avgKcal":$kcal,"avgProteinG":100,"avgCarbsG":200,"avgFatG":70,"avgFiberG":25,"avgSugarG":50}}""",
    ) to """{"ageBand":"$ageBand","sex":"FEMALE","region":null}"""

    @Test
    fun groupsSmallerThanTenAreLeftOut() {
        val big = (1..10).map { row("p$it", 2000 + it * 10) }
        val small = (1..9).map { row("q$it", 1800, ageBand = "30-34") }
        val rows = Stats.aggregate(big + small)
        assertEquals(1, rows.size)
        assertEquals("25-29", rows.single().ageBand)
        assertEquals(10, rows.single().participants)
        assertEquals(2055.0, rows.single().averages.getValue("avgKcal"))
        assertEquals("unknown", rows.single().region)
        val csv = Stats.csv(rows)
        assertTrue(csv.startsWith("week,age_band,sex,region,participants,avgKcal"))
        assertTrue(csv.contains("2026-W45,25-29,FEMALE,unknown,10,2055.0"))
    }

    @Test
    fun aPersonCountsOncePerGroup() {
        val rows = Stats.aggregate((1..10).map { row("same", 2000) } + (1..9).map { row("p$it", 2000) })
        assertEquals(10, rows.single().participants)
    }
}
