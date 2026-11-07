package com.calore.tracker.data.repository

import com.calore.tracker.data.db.CycleDao
import com.calore.tracker.data.db.CycleDayEntity
import com.calore.tracker.data.db.CyclePeriodEntity
import com.calore.tracker.data.db.JsonColumns
import com.calore.tracker.data.prefs.CyclePrefs
import com.calore.tracker.domain.cycle.CycleDay
import com.calore.tracker.domain.cycle.CyclePeriod
import com.calore.tracker.domain.cycle.CycleSettings
import com.calore.tracker.domain.cycle.Flow
import com.calore.tracker.domain.cycle.Symptom
import com.calore.tracker.domain.newId
import com.calore.tracker.domain.parseIsoDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** Cycle tracking (M7): periods, logged days and settings. Everything stays on the phone. */
class CycleRepository(private val cycleDao: CycleDao, private val prefs: CyclePrefs, private val clock: Clock) {

    val settings: kotlinx.coroutines.flow.Flow<CycleSettings> = prefs.settings
    val asked: kotlinx.coroutines.flow.Flow<Boolean> = prefs.asked

    fun periods(): kotlinx.coroutines.flow.Flow<List<CyclePeriod>> = cycleDao.observePeriods().map { rows -> rows.mapNotNull { it.toDomain() } }

    fun days(): kotlinx.coroutines.flow.Flow<List<CycleDay>> = cycleDao.observeDays().map { rows -> rows.mapNotNull { it.toDomain() } }

    suspend fun periodsNow(): List<CyclePeriod> = cycleDao.periods().mapNotNull { it.toDomain() }

    suspend fun saveSettings(settings: CycleSettings) = prefs.save(settings)

    suspend fun setEnabled(on: Boolean) {
        prefs.save(prefs.settings.first().copy(enabled = on))
        prefs.setAsked()
    }

    suspend fun setAsked() = prefs.setAsked()

    /**
     * "Period started" on [date]. A start within a few days of an open period's start replaces it
     * (a correction); otherwise the open period ends the day before and a new one begins.
     */
    suspend fun startPeriod(date: LocalDate) {
        val now = clock.millis()
        val periods = periodsNow()
        if (periods.any { it.start == date }) return
        val previous = periods.filter { it.start.isBefore(date) }.maxByOrNull { it.start }
        if (previous != null && previous.end == null && !date.isAfter(previous.start.plusDays(CORRECTION_DAYS))) {
            cycleDao.upsertPeriod(CyclePeriodEntity(previous.id, date.toString(), null, previous.createdAt, now))
            return
        }
        cycleDao.upsertPeriod(CyclePeriodEntity(newId(), date.toString(), null, now, now))
    }

    /** "Period ended" on [date]: ends the latest period that started on or before it. */
    suspend fun endPeriod(date: LocalDate) {
        val period = periodsNow().filter { !it.start.isAfter(date) }.maxByOrNull { it.start } ?: return
        cycleDao.upsertPeriod(CyclePeriodEntity(period.id, period.start.toString(), date.toString(), period.createdAt, clock.millis()))
    }

    suspend fun deletePeriod(id: String) = cycleDao.deletePeriod(id)

    /** Flow, symptoms and a note for [date]; an empty log removes the day. */
    suspend fun logDay(date: LocalDate, flow: Flow?, symptoms: Set<Symptom>, note: String?) {
        val text = note?.trim()?.takeIf { it.isNotEmpty() }
        if (flow == null && symptoms.isEmpty() && text == null) {
            cycleDao.deleteDay(date.toString())
            return
        }
        val now = clock.millis()
        val existing = cycleDao.day(date.toString())
        cycleDao.upsertDay(
            CycleDayEntity(
                id = existing?.id ?: newId(),
                date = date.toString(),
                flow = flow?.name,
                symptoms = symptoms.sortedBy { it.ordinal }.map { it.name },
                note = text,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /** M7.6: deletes every period, logged day and cycle health setting. Tracking stays on or off as it is. */
    suspend fun deleteAll() {
        cycleDao.deleteAll()
        prefs.clearHealthSettings()
    }

    private companion object {
        /** A second "started" this close to an open period's start corrects it. */
        const val CORRECTION_DAYS = 3L
    }
}

private fun CyclePeriodEntity.toDomain(): CyclePeriod? {
    val start = parseIsoDate(startDate) ?: return null
    return CyclePeriod(id, start, endDate?.let(::parseIsoDate), createdAt, updatedAt)
}

private fun CycleDayEntity.toDomain(): CycleDay? {
    val day = parseIsoDate(date) ?: return null
    return CycleDay(
        id = id,
        date = day,
        flow = flow?.let { JsonColumns.enumByName<Flow>(it) },
        symptoms = symptoms.mapNotNull { JsonColumns.enumByName<Symptom>(it) }.toSet(),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
