package com.calore.tracker.domain

import java.time.LocalDate
import java.time.YearMonth

/** A thrown-away box: the day, the calories and the cost that were left in it (A18). */
data class WastedBox(val date: LocalDate, val kcal: Double, val costHalers: Double?)

data class MonthStats(
    val month: YearMonth,
    val wastedBoxes: Int,
    val wastedKcal: Double,
    val wastedHalers: Double,
    val spentHalers: Double,
    val perDayHalers: Double,
)

/** Something bought, with what 100 g of protein in it costs. */
data class ProteinSource(val name: String, val per100gProteinHalers: Double)

/** Statistics: waste and money (M5.9, A18). */
object Stats {

    /**
     * One month: boxes and kcal thrown away, money thrown away (boxes and pantry items), money spent
     * (ticked items with prices), and the cost per day so far ([today] for the current month).
     */
    fun month(
        month: YearMonth,
        boxes: List<WastedBox>,
        pantryWaste: List<Pair<LocalDate, Long>>,
        spent: List<Pair<LocalDate, Long>>,
        today: LocalDate,
    ): MonthStats {
        fun LocalDate.inMonth() = YearMonth.from(this) == month
        val wasted = boxes.filter { it.date.inMonth() }
        val spentHalers = spent.filter { it.first.inMonth() }.sumOf { it.second }.toDouble()
        val days = when {
            month == YearMonth.from(today) -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 0
            else -> month.lengthOfMonth()
        }
        return MonthStats(
            month = month,
            wastedBoxes = wasted.size,
            wastedKcal = wasted.sumOf { it.kcal },
            wastedHalers = wasted.sumOf { it.costHalers ?: 0.0 } + pantryWaste.filter { it.first.inMonth() }.sumOf { it.second },
            spentHalers = spentHalers,
            perDayHalers = if (days > 0) spentHalers / days else 0.0,
        )
    }

    /** The cheapest protein among bought products: price per gram ÷ protein per gram × 100 g. */
    fun cheapestProtein(bought: List<Triple<String, Double, Double>>, max: Int = 5): List<ProteinSource> =
        bought.filter { (_, perGram, proteinPer100g) -> perGram > 0 && proteinPer100g >= 5 }
            .map { (name, perGram, protein) -> ProteinSource(name, perGram / (protein / 100) * 100) }
            .distinctBy { it.name }
            .sortedBy { it.per100gProteinHalers }
            .take(max)
}
