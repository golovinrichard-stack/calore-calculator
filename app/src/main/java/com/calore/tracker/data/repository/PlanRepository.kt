package com.calore.tracker.data.repository

import com.calore.tracker.data.db.PlanDao
import com.calore.tracker.data.db.PlanEntity
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.domain.MealTarget
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PlanTemplate(val id: String, val name: String)

/** Week plans (M5.3): one plan per week (starting Monday), its slots, and templates. */
class PlanRepository(private val planDao: PlanDao, private val clock: Clock) {

    /** The plan for the week that starts on [weekStart], created when needed. */
    suspend fun planFor(weekStart: LocalDate): String {
        val monday = ReminderSchedule.weekStart(weekStart)
        planDao.planForWeek(monday.toString())?.let { return it.id }
        val now = clock.millis()
        val plan = PlanEntity(id = newId(), weekStart = monday.toString(), templateName = null, createdAt = now, updatedAt = now)
        planDao.upsertPlan(plan)
        return plan.id
    }

    fun slots(planId: String): Flow<List<PlanSlot>> = planDao.observeSlots(planId).map { rows -> rows.mapNotNull { it.toDomain() } }

    /** Planned slots of every week between two dates (Today, M5.11). */
    fun slotsBetween(from: LocalDate, to: LocalDate): Flow<List<PlanSlot>> =
        planDao.observeSlotsBetween(from.toString(), to.toString()).map { rows -> rows.mapNotNull { it.toDomain() } }

    /** Plans one meal; an existing slot for the same person and meal is replaced. */
    suspend fun setSlot(planId: String, date: LocalDate, meal: MealType, memberId: String, kind: SlotKind, recipeId: String?, target: MealTarget?) {
        val now = clock.millis()
        val existing = planDao.slot(planId, date.toString(), meal.name, memberId)
        val slot = PlanSlot(
            id = existing?.id ?: newId(),
            planId = planId,
            date = date,
            meal = meal,
            memberId = memberId,
            kind = kind,
            recipeId = recipeId.takeIf { kind == SlotKind.BOX || kind == SlotKind.COOK_FRESH },
            target = target,
        )
        planDao.upsertSlot(slot.toEntity(createdAt = existing?.createdAt ?: now, updatedAt = now))
    }

    suspend fun clearSlot(slotId: String) = planDao.deleteSlot(slotId, clock.millis())

    /** Copies last week's slots into this week (one week later), without boxes or "done". Returns how many. */
    suspend fun repeatLastWeek(weekStart: LocalDate): Int {
        val previous = planDao.planForWeek(weekStart.minusWeeks(1).toString()) ?: return 0
        return copySlots(previous.id, planFor(weekStart), days = 7)
    }

    /** Saves this week as a template named [name]. */
    suspend fun saveAsTemplate(weekStart: LocalDate, name: String) {
        val now = clock.millis()
        val template = PlanEntity(id = newId(), weekStart = null, templateName = name.trim(), createdAt = now, updatedAt = now)
        planDao.upsertPlan(template)
        // Template slots keep their original dates; using the template shifts them to the target week.
        copySlots(planFor(weekStart), template.id, days = 0)
    }

    fun templates(): Flow<List<PlanTemplate>> =
        planDao.observeTemplates().map { rows -> rows.map { PlanTemplate(it.id, it.templateName.orEmpty()) } }

    /** Replaces this week's slots with a template's. Returns how many. */
    suspend fun useTemplate(templateId: String, weekStart: LocalDate): Int {
        val slots = planDao.slots(templateId).mapNotNull { it.toDomain() }
        val first = slots.minOfOrNull { it.date } ?: return 0
        val shift = ChronoUnit.DAYS.between(ReminderSchedule.weekStart(first), ReminderSchedule.weekStart(weekStart))
        return copySlots(templateId, planFor(weekStart), shift)
    }

    private suspend fun copySlots(fromPlan: String, toPlan: String, days: Long): Int {
        val now = clock.millis()
        val copies = planDao.slots(fromPlan).mapNotNull { it.toDomain() }.map { slot ->
            slot.copy(id = newId(), planId = toPlan, date = slot.date.plusDays(days), boxId = null, done = false)
                .toEntity(createdAt = now, updatedAt = now)
        }
        planDao.replaceSlots(toPlan, copies, now)
        return copies.size
    }
}
