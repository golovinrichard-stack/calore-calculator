package com.calore.tracker.data.repository

import com.calore.tracker.data.db.DiaryDao
import com.calore.tracker.data.db.JsonColumns
import com.calore.tracker.data.db.SupplementDao
import com.calore.tracker.data.db.SupplementEntity
import com.calore.tracker.data.db.SupplementLogEntity
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.domain.DiaryEntry
import com.calore.tracker.domain.DoseUnit
import com.calore.tracker.domain.EntryType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Supplement
import com.calore.tracker.domain.SupplementDose
import com.calore.tracker.domain.SupplementKind
import com.calore.tracker.domain.SupplementLog
import com.calore.tracker.domain.SupplementSchedule
import com.calore.tracker.domain.SupplementStatus
import com.calore.tracker.domain.mealAt
import com.calore.tracker.domain.newId
import com.calore.tracker.domain.parseIsoDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Supplements (M8): the list, Taken / Skip per dose, and the diary entries of those with calories. */
class SupplementRepository(private val dao: SupplementDao, private val diaryDao: DiaryDao, private val clock: Clock) {

    fun supplements(): Flow<List<Supplement>> = dao.observeSupplements().map { rows -> rows.mapNotNull { it.toDomain() } }

    suspend fun supplementsNow(): List<Supplement> = dao.supplements().mapNotNull { it.toDomain() }

    suspend fun supplement(id: String): Supplement? = dao.supplement(id)?.toDomain()

    /** Logs of doses scheduled on or after [from]. */
    fun logsSince(from: LocalDate): Flow<List<SupplementLog>> =
        dao.observeLogsSince(from.atStartOfDay(clock.zone).toInstant().toEpochMilli()).map { rows -> rows.map { it.toDomain() } }

    /** Saves a new supplement (empty id) or changes one. Returns its id. */
    suspend fun save(supplement: Supplement): String {
        val now = clock.millis()
        val existing = supplement.id.takeIf { it.isNotEmpty() }?.let { dao.supplement(it) }
        val saved = supplement.copy(
            id = existing?.id ?: newId(),
            name = supplement.name.trim(),
            times = supplement.times.distinct().sorted().ifEmpty { listOf(Supplement.DEFAULT_TIME) },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsertSupplement(saved.toEntity())
        return saved.id
    }

    suspend fun delete(id: String) = dao.deleteSupplement(id)

    /**
     * **Taken**: logs the dose; one with calories also logs a diary entry with a nutrient snapshot
     * (M8.5), in the meal for the dose's time.
     */
    suspend fun take(dose: SupplementDose) {
        val now = clock.millis()
        val existing = dao.log(dose.supplement.id, dose.scheduledAt)
        if (existing?.status == SupplementStatus.TAKEN.name) return
        existing?.diaryEntryUuid?.let { diaryDao.deleteByUuid(it) }
        val diaryUuid = dose.supplement.nutrients?.takeIf { dose.supplement.logsToDiary }?.let { nutrients -> logToDiary(dose, nutrients, now) }
        dao.upsertLog(
            SupplementLogEntity(
                id = existing?.id ?: newId(),
                supplementId = dose.supplement.id,
                scheduledAt = dose.scheduledAt,
                takenAt = now,
                status = SupplementStatus.TAKEN.name,
                diaryEntryUuid = diaryUuid,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /** **Skip**: logs the dose as skipped; a diary entry from taking it before goes. */
    suspend fun skip(dose: SupplementDose) {
        val now = clock.millis()
        val existing = dao.log(dose.supplement.id, dose.scheduledAt)
        existing?.diaryEntryUuid?.let { diaryDao.deleteByUuid(it) }
        dao.upsertLog(
            SupplementLogEntity(
                id = existing?.id ?: newId(),
                supplementId = dose.supplement.id,
                scheduledAt = dose.scheduledAt,
                takenAt = null,
                status = SupplementStatus.SKIPPED.name,
                diaryEntryUuid = null,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /** Undo: the dose is open again, and its diary entry is removed (M8.5). */
    suspend fun undo(dose: SupplementDose) {
        val existing = dao.log(dose.supplement.id, dose.scheduledAt) ?: return
        existing.diaryEntryUuid?.let { diaryDao.deleteByUuid(it) }
        dao.deleteLog(existing.id)
    }

    /** True when this dose is already taken or skipped. */
    suspend fun logged(dose: SupplementDose): Boolean = dao.log(dose.supplement.id, dose.scheduledAt) != null

    /** For a reminder's Taken / Skip: the dose of [supplementId] at [scheduledAt], if it is still scheduled. */
    suspend fun dose(supplementId: String, scheduledAt: Long): SupplementDose? {
        val supplement = supplement(supplementId) ?: return null
        val date = java.time.Instant.ofEpochMilli(scheduledAt).atZone(clock.zone).toLocalDate()
        return SupplementSchedule.doses(supplement, date, clock.zone).firstOrNull { it.scheduledAt == scheduledAt }
    }

    private suspend fun logToDiary(dose: SupplementDose, nutrients: Nutrients, now: Long): String {
        val uuid = newId()
        val entry = DiaryEntry(
            id = 0,
            date = dose.date,
            meal = mealAt(dose.time),
            foodId = null,
            name = dose.supplement.name,
            grams = 0.0,
            nutrients = nutrients,
            loggedAt = now,
            uuid = uuid,
            entryType = EntryType.SUPPLEMENT,
        )
        diaryDao.insert(entry.toEntity())
        return uuid
    }
}

private fun SupplementEntity.toDomain(): Supplement? {
    val start = parseIsoDate(startDate) ?: return null
    return Supplement(
        id = id,
        name = name,
        kind = JsonColumns.enumByName<SupplementKind>(kind) ?: SupplementKind.OTHER,
        doseAmount = doseAmount,
        doseUnit = JsonColumns.enumByName<DoseUnit>(doseUnit) ?: DoseUnit.TABLET,
        times = times.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }.sorted().ifEmpty { listOf(Supplement.DEFAULT_TIME) },
        daysOfWeek = daysOfWeek.mapNotNull { JsonColumns.enumByName<DayOfWeek>(it) }.toSet(),
        startDate = start,
        endDate = endDate?.let(::parseIsoDate),
        remindersOn = remindersOn,
        nutrients = kcal?.let { Nutrients(kcal = it, protein = protein, carbs = carbs, fat = fat, fiber = fiber, sugar = sugar) },
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun Supplement.toEntity() = SupplementEntity(
    id = id,
    name = name,
    kind = kind.name,
    doseAmount = doseAmount,
    doseUnit = doseUnit.name,
    times = times.map { "%02d:%02d".format(it.hour, it.minute) },
    daysOfWeek = daysOfWeek.sortedBy { it.value }.map { it.name },
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    remindersOn = remindersOn,
    kcal = nutrients?.kcal,
    protein = nutrients?.protein,
    carbs = nutrients?.carbs,
    fat = nutrients?.fat,
    fiber = nutrients?.fiber,
    sugar = nutrients?.sugar,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun SupplementLogEntity.toDomain() = SupplementLog(
    id = id,
    supplementId = supplementId,
    scheduledAt = scheduledAt,
    takenAt = takenAt,
    status = JsonColumns.enumByName<SupplementStatus>(status) ?: SupplementStatus.SKIPPED,
    diaryEntryUuid = diaryEntryUuid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
