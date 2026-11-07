package com.calore.tracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class SupplementKind { VITAMIN_D, IRON, MAGNESIUM, CREATINE, PROTEIN_POWDER, OMEGA_3, MULTIVITAMIN, OTHER }

enum class DoseUnit { MG, UG, IU, G, ML, CAPSULE, TABLET, SCOOP }

enum class SupplementStatus { TAKEN, SKIPPED }

/** A supplement the user takes (M8). Private, never synced. */
data class Supplement(
    val id: String,
    val name: String,
    val kind: SupplementKind = SupplementKind.OTHER,
    val doseAmount: Double = 1.0,
    val doseUnit: DoseUnit = DoseUnit.TABLET,
    /** At least one time of day, sorted. */
    val times: List<LocalTime> = listOf(DEFAULT_TIME),
    /** Empty means every day. */
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val remindersOn: Boolean = false,
    /** Nutrition per dose (protein powder, gainers); with calories, taking it also logs a diary entry (M8.5). */
    val nutrients: Nutrients? = null,
    val active: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** Taking it logs a diary entry only when it has calories. */
    val logsToDiary: Boolean get() = nutrients != null && nutrients.kcal > 0

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
    }
}

/** Taken or skipped, for one scheduled dose. */
data class SupplementLog(
    val id: String,
    val supplementId: String,
    /** Epoch millis of the scheduled dose this log is for. */
    val scheduledAt: Long?,
    val takenAt: Long?,
    val status: SupplementStatus,
    /** The diary entry made when taking it (M8.5). */
    val diaryEntryUuid: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** One scheduled dose of a supplement. */
data class SupplementDose(val supplement: Supplement, val date: LocalDate, val time: LocalTime, val scheduledAt: Long)

/** How regularly a supplement was taken over some days (M8.3). */
data class Regularity(val due: Int, val taken: Int, val skipped: Int) {
    /** 0–100, or null with nothing due. */
    val percent: Int? get() = if (due == 0) null else Math.round(taken * 100.0 / due).toInt()
}

/** When supplements are due, and how regularly they were taken (M8). */
object SupplementSchedule {

    fun isDue(supplement: Supplement, date: LocalDate): Boolean =
        supplement.active &&
            !date.isBefore(supplement.startDate) &&
            (supplement.endDate == null || !date.isAfter(supplement.endDate)) &&
            (supplement.daysOfWeek.isEmpty() || date.dayOfWeek in supplement.daysOfWeek)

    /** The doses of [supplement] on [date], in time order. */
    fun doses(supplement: Supplement, date: LocalDate, zone: ZoneId): List<SupplementDose> =
        if (!isDue(supplement, date)) {
            emptyList()
        } else {
            supplement.times.distinct().sorted().map { SupplementDose(supplement, date, it, date.atTime(it).atZone(zone).toInstant().toEpochMilli()) }
        }

    fun dosesOn(supplements: List<Supplement>, date: LocalDate, zone: ZoneId): List<SupplementDose> =
        supplements.flatMap { doses(it, date, zone) }.sortedWith(compareBy({ it.time }, { it.supplement.name }))

    /** The next dose after [now] within a year, for a reminder; null when none is left. */
    fun nextDose(supplement: Supplement, now: Long, zone: ZoneId): SupplementDose? {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (offset in 0L..366L) {
            val dose = doses(supplement, today.plusDays(offset), zone).firstOrNull { it.scheduledAt > now }
            if (dose != null) return dose
        }
        return null
    }

    /** Doses due over the [days] days that end with [today], and how many were taken or skipped. */
    fun regularity(supplement: Supplement, logs: List<SupplementLog>, today: LocalDate, days: Int, zone: ZoneId): Regularity {
        val dueKeys = (0 until days).flatMap { doses(supplement, today.minusDays(it.toLong()), zone) }.map { it.scheduledAt }.toSet()
        val mine = logs.filter { it.supplementId == supplement.id && it.scheduledAt in dueKeys }
        return Regularity(
            due = dueKeys.size,
            taken = mine.count { it.status == SupplementStatus.TAKEN },
            skipped = mine.count { it.status == SupplementStatus.SKIPPED },
        )
    }
}
