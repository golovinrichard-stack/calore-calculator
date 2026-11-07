package com.calore.tracker.domain.cycle

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Cycle phases (data-model enums). */
enum class CyclePhase { PERIOD, FOLLICULAR, OVULATION, LUTEAL }

/** How heavy the bleeding is on a day. */
enum class Flow { NONE, LIGHT, MEDIUM, HEAVY }

enum class Symptom { CRAMPS, BLOATING, CRAVINGS, LOW_MOOD, LOW_ENERGY, POOR_SLEEP, HEADACHE }

enum class Contraception(val hormonal: Boolean) {
    NONE(false), PILL(true), HORMONAL_IUD(true), COPPER_IUD(false), IMPLANT(true), OTHER_HORMONAL(true),
}

enum class Menopause { NO, PERIMENOPAUSE, MENOPAUSE }

/** "Lock the cycle section" or "Lock the whole app" (M7.6). */
enum class AppLock { OFF, CYCLE, APP }

/** One period: logged start, and its end once known. Private, never synced. */
data class CyclePeriod(val id: String, val start: LocalDate, val end: LocalDate? = null, val createdAt: Long = 0, val updatedAt: Long = 0)

/** What was logged on one day. The note never leaves the phone. */
data class CycleDay(
    val id: String,
    val date: LocalDate,
    val flow: Flow? = null,
    val symptoms: Set<Symptom> = emptySet(),
    val note: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Cycle settings (DataStore `cycle_prefs`). */
data class CycleSettings(
    val enabled: Boolean = false,
    val contraception: Contraception = Contraception.NONE,
    val pregnant: Boolean = false,
    val breastfeeding: Boolean = false,
    val menopause: Menopause = Menopause.NO,
    val defaultCycleLength: Int = DEFAULT_CYCLE_LENGTH,
    val defaultPeriodLength: Int = DEFAULT_PERIOD_LENGTH,
    val appLock: AppLock = AppLock.OFF,
) {
    /** A19: predictions pause with hormonal contraception, pregnancy, breastfeeding, perimenopause or menopause. */
    val paused: Boolean get() = contraception.hormonal || pregnant || breastfeeding || menopause != Menopause.NO

    companion object {
        const val DEFAULT_CYCLE_LENGTH = 28
        const val DEFAULT_PERIOD_LENGTH = 5
        val CYCLE_LENGTHS = 15..60
        val PERIOD_LENGTHS = 1..14
    }
}

/** Average cycle length and how sure it is (A19). */
data class CycleStats(
    /** Weighted average, not rounded. */
    val average: Double,
    /** ± days around the next start. */
    val rangeDays: Double,
    val irregular: Boolean,
    /** The complete cycles used, oldest first. */
    val lengths: List<Int>,
) {
    val averageDays: Int get() = average.roundToLong().toInt()
}

/** One cycle's days (A19): inclusive ranges; follicular can be empty (null). */
data class CycleDays(
    val start: LocalDate,
    val nextStart: LocalDate,
    val period: ClosedRange<LocalDate>,
    val follicular: ClosedRange<LocalDate>?,
    val ovulation: ClosedRange<LocalDate>,
    val luteal: ClosedRange<LocalDate>,
    /** Likely water-retention days around the next start. */
    val water: ClosedRange<LocalDate>,
) {
    fun phaseOn(date: LocalDate): CyclePhase? = when {
        date in period -> CyclePhase.PERIOD
        follicular?.contains(date) == true -> CyclePhase.FOLLICULAR
        date in ovulation -> CyclePhase.OVULATION
        date in luteal -> CyclePhase.LUTEAL
        else -> null
    }
}

/** Where a day is in the cycle, for the Today card. */
data class CycleToday(
    /** Day 1 is the first day of the period. */
    val day: Int,
    val phase: CyclePhase?,
    /** Days until the earliest and latest likely next start; negative when the period is late. */
    val nextInFrom: Long,
    val nextInTo: Long,
)

/** Cycle prediction and phases (algorithms A19). */
object Cycles {

    /** Cycles used for the average (A19). */
    const val MAX_CYCLES = 6

    /** Above this spread (longest − shortest), the cycle is irregular. */
    const val IRREGULAR_SPREAD = 7

    /** The smallest range shown. */
    const val MIN_RANGE_DAYS = 1.0

    /** Before 2 complete cycles are logged, the range is this wide (*tunable*). */
    const val UNKNOWN_RANGE_DAYS = 3.0

    /** Likely water-retention days: next start − 5 to next start + 2 (*tunable*). */
    const val WATER_BEFORE = 5L
    const val WATER_AFTER = 2L

    /** Ovulation is 14 days before the next start; its phase runs from 2 days before to 1 day after. */
    const val LUTEAL_DAYS = 14L

    /** Lengths of complete cycles (start to next start), oldest first. */
    fun lengths(periods: List<CyclePeriod>): List<Int> =
        periods.map { it.start }.distinct().sorted().zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }

    /** A19: weighted average of the last cycles (weights 1, 2, 3 … newest highest) and the range. */
    fun stats(periods: List<CyclePeriod>, defaultLength: Int = CycleSettings.DEFAULT_CYCLE_LENGTH): CycleStats {
        val last = lengths(periods).takeLast(MAX_CYCLES)
        if (last.size < 2) return CycleStats(defaultLength.toDouble(), UNKNOWN_RANGE_DAYS, irregular = false, lengths = last)
        val weights = last.indices.map { it + 1.0 }
        val average = last.indices.sumOf { last[it] * weights[it] } / weights.sum()
        val spread = last.max() - last.min()
        if (spread > IRREGULAR_SPREAD) return CycleStats(average, spread / 2.0, irregular = true, lengths = last)
        val mean = last.average()
        val sd = sqrt(last.sumOf { (it - mean) * (it - mean) } / last.size)
        return CycleStats(average, maxOf(MIN_RANGE_DAYS, sd), irregular = false, lengths = last)
    }

    /** Average logged period length (start to end, inclusive), or the default. */
    fun periodLength(periods: List<CyclePeriod>, defaultLength: Int = CycleSettings.DEFAULT_PERIOD_LENGTH): Int {
        val ended = periods.mapNotNull { p -> p.end?.let { ChronoUnit.DAYS.between(p.start, it).toInt() + 1 } }.filter { it > 0 }
        return if (ended.isEmpty()) defaultLength else ended.average().roundToLong().toInt()
    }

    /** A19: the next period starts at the last start + the average, rounded. */
    fun nextStart(lastStart: LocalDate, stats: CycleStats): LocalDate = lastStart.plusDays(stats.averageDays.toLong())

    /** A19 phases of the cycle that starts on [start] and ends the day before [nextStart]. */
    fun days(start: LocalDate, nextStart: LocalDate, periodLength: Int): CycleDays {
        val periodEnd = start.plusDays(periodLength.coerceAtLeast(1) - 1L)
        val ovulationDay = nextStart.minusDays(LUTEAL_DAYS)
        val ovulation = ovulationDay.minusDays(2)..ovulationDay.plusDays(1)
        val follicularStart = periodEnd.plusDays(1)
        val follicularEnd = ovulation.start.minusDays(1)
        return CycleDays(
            start = start,
            nextStart = nextStart,
            period = start..periodEnd,
            follicular = if (follicularStart.isAfter(follicularEnd)) null else follicularStart..follicularEnd,
            ovulation = ovulation,
            luteal = ovulation.endInclusive.plusDays(1)..nextStart.minusDays(1),
            water = nextStart.minusDays(WATER_BEFORE)..nextStart.plusDays(WATER_AFTER),
        )
    }

    /**
     * The cycle a [date] belongs to: logged cycles use their logged next start; after the last logged
     * start, cycles are predicted every average length. Null before the first logged period.
     */
    fun cycleOn(date: LocalDate, periods: List<CyclePeriod>, settings: CycleSettings): CycleDays? {
        val starts = periods.map { it.start }.distinct().sorted()
        val first = starts.firstOrNull() ?: return null
        if (date.isBefore(first)) return null
        val stats = stats(periods, settings.defaultCycleLength)
        val length = periodLength(periods, settings.defaultPeriodLength)
        val i = starts.indexOfLast { !it.isAfter(date) }
        if (i < starts.size - 1) {
            val own = periods.firstOrNull { it.start == starts[i] }?.end?.let { ChronoUnit.DAYS.between(starts[i], it).toInt() + 1 }
            return days(starts[i], starts[i + 1], own ?: length)
        }
        // After the last logged start: predicted cycles of the average length.
        val last = starts.last()
        val avg = stats.averageDays.coerceAtLeast(1).toLong()
        val k = ChronoUnit.DAYS.between(last, date) / avg
        val start = last.plusDays(k * avg)
        val own = if (k == 0L) periods.firstOrNull { it.start == last }?.end?.let { ChronoUnit.DAYS.between(last, it).toInt() + 1 } else null
        return days(start, start.plusDays(avg), own ?: length)
    }

    /** The phase on [date]; null when paused, before any logged period, or when there is nothing to say. */
    fun phaseOn(date: LocalDate, periods: List<CyclePeriod>, settings: CycleSettings): CyclePhase? {
        if (!settings.enabled || settings.paused) return null
        return cycleOn(date, periods, settings)?.phaseOn(date)
    }

    /** Likely water-retention days between [from] and [to] (A19), around logged and predicted starts. */
    fun waterDays(from: LocalDate, to: LocalDate, periods: List<CyclePeriod>, settings: CycleSettings): Set<LocalDate> {
        if (!settings.enabled || settings.paused || periods.isEmpty()) return emptySet()
        val starts = periods.map { it.start }.distinct().sorted().toMutableList()
        val avg = stats(periods, settings.defaultCycleLength).averageDays.coerceAtLeast(1).toLong()
        var next = starts.last().plusDays(avg)
        while (!next.isAfter(to.plusDays(WATER_BEFORE))) {
            starts += next
            next = next.plusDays(avg)
        }
        return starts.flatMap { s -> generateSequence(s.minusDays(WATER_BEFORE)) { it.plusDays(1) }.takeWhile { !it.isAfter(s.plusDays(WATER_AFTER)) }.toList() }
            .filter { !it.isBefore(from) && !it.isAfter(to) }
            .toSet()
    }

    /** "Day 18 · luteal phase · next period in 9–11 days" (M7.3). Null without a logged period. */
    fun today(today: LocalDate, periods: List<CyclePeriod>, settings: CycleSettings): CycleToday? {
        val last = periods.map { it.start }.filter { !it.isAfter(today) }.maxOrNull() ?: return null
        val stats = stats(periods, settings.defaultCycleLength)
        val next = nextStart(last, stats)
        val range = stats.rangeDays
        val day = ChronoUnit.DAYS.between(last, today).toInt() + 1
        val phase = if (!settings.enabled || settings.paused) null else days(last, next, periodLength(periods, settings.defaultPeriodLength)).phaseOn(today)
        val toNext = ChronoUnit.DAYS.between(today, next)
        return CycleToday(day, phase, toNext - Math.ceil(range).toLong(), toNext + Math.ceil(range).toLong())
    }

    /** The earliest and latest likely next start (A19 range), for the calendar. */
    fun nextRange(periods: List<CyclePeriod>, settings: CycleSettings): ClosedRange<LocalDate>? {
        val last = periods.maxOfOrNull { it.start } ?: return null
        val stats = stats(periods, settings.defaultCycleLength)
        val next = nextStart(last, stats)
        val r = Math.ceil(stats.rangeDays).toLong()
        return next.minusDays(r)..next.plusDays(r)
    }
}
