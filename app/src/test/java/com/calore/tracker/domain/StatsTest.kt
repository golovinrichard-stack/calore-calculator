package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** M5.9: monthly waste and money (A18). */
class StatsTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val november = YearMonth.of(2026, 11)

    @Test
    fun aMonthOfWasteAndSpending() {
        val stats = Stats.month(
            november,
            boxes = listOf(WastedBox(LocalDate.of(2026, 11, 3), 600.0, 4000.0), WastedBox(LocalDate.of(2026, 11, 5), 300.0, null), WastedBox(LocalDate.of(2026, 10, 30), 500.0, 3000.0)),
            pantryWaste = listOf(LocalDate.of(2026, 11, 4) to 2990L, LocalDate.of(2026, 10, 2) to 1000L),
            spent = listOf(LocalDate.of(2026, 11, 2) to 50_000L, LocalDate.of(2026, 11, 9) to 30_000L, LocalDate.of(2026, 10, 9) to 99_000L),
            today = today,
        )
        assertEquals(2, stats.wastedBoxes)
        assertEquals(900.0, stats.wastedKcal, 0.0)
        assertEquals(6990.0, stats.wastedHalers, 0.0)
        assertEquals(80_000.0, stats.spentHalers, 0.0)
        assertEquals("10 days so far this month", 8000.0, stats.perDayHalers, 1e-9)
        assertEquals("a past month counts all its days", 99_000.0 / 31, Stats.month(YearMonth.of(2026, 10), emptyList(), emptyList(), listOf(LocalDate.of(2026, 10, 9) to 99_000L), today).perDayHalers, 1e-9)
    }

    @Test
    fun cheapestProteinFirst() {
        val sources = Stats.cheapestProtein(
            listOf(
                Triple("Chicken", 0.28, 22.5),
                Triple("Eggs", 0.45, 12.6),
                Triple("Quark", 0.12, 12.0),
                Triple("Rice", 0.04, 2.7),
            ),
        )
        assertEquals(listOf("Quark", "Chicken", "Eggs"), sources.map { it.name })
        assertEquals(100.0, sources.first().per100gProteinHalers, 1e-9)
    }
}
