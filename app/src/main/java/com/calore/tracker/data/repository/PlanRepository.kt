package com.calore.tracker.data.repository

import com.calore.tracker.data.db.PlanDao
import com.calore.tracker.data.db.PlanCookEntity
import com.calore.tracker.data.db.PlanEntity
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.domain.MealTarget
import com.calore.tracker.domain.mealTarget
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.PlannedCook
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    /** M6.5: "I'm eating out" for this meal. */
    suspend fun eatOut(slotId: String) = planDao.eatOut(slotId, clock.millis())

    /** M5.11: the meal no longer eats its box (it moved, was frozen or given away). */
    suspend fun freeSlot(slotId: String) = planDao.freeSlot(slotId, clock.millis())

    /** M5.11: plans [boxId] for [memberId] on [date] and [meal] (in that week's plan) and links it. */
    suspend fun planBox(date: LocalDate, meal: MealType, memberId: String, recipeId: String?, target: MealTarget?, boxId: String) {
        val planId = planFor(date)
        setSlot(planId, date, meal, memberId, SlotKind.BOX, recipeId, target)
        planDao.slot(planId, date.toString(), meal.name, memberId)?.let { planDao.linkBox(it.id, boxId, clock.millis()) }
    }

    /** M5.4: the boxes filled for these slots. */
    suspend fun linkBoxes(slotToBox: List<Pair<String, String>>) {
        val now = clock.millis()
        slotToBox.forEach { (slot, box) -> planDao.linkBox(slot, box, now) }
    }

    /** M5.4: a planned cook was cooked, with its batch; the planned row from M6.3 is reused when there is one. */
    suspend fun saveCooked(planId: String, cook: PlannedCook, batchId: String) {
        val now = clock.millis()
        val planned = planDao.cooks(planId).firstOrNull { it.batchId == null && it.date == cook.date.toString() && it.recipeId == cook.recipeId }
        planDao.upsertCook(
            PlanCookEntity(
                id = planned?.id ?: newId(),
                planId = planId,
                date = cook.date.toString(),
                recipeId = cook.recipeId,
                portions = cook.portions,
                targetCookedGrams = cook.targetCookedGrams,
                scale = cook.scale,
                batchId = batchId,
                createdAt = planned?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /** The week's plan id and its slots, now. */
    suspend fun weekNow(weekStart: LocalDate): Pair<String, List<PlanSlot>> {
        val planId = planFor(weekStart)
        return planId to planDao.slots(planId).mapNotNull { it.toDomain() }
    }

    suspend fun cookedNow(planId: String): Set<Pair<LocalDate, String>> = cooked(planId).first()

    /** Recipes each person had planned in the 7 days before [weekStart] (A15 variety): member id → recipe ids. */
    suspend fun recentRecipes(weekStart: LocalDate): Map<String, Set<String>> =
        slotsBetween(weekStart.minusDays(7), weekStart.minusDays(1)).first()
            .filter { it.kind == SlotKind.BOX || it.kind == SlotKind.COOK_FRESH }
            .groupBy { it.memberId }
            .mapValues { (_, slots) -> slots.mapNotNull { it.recipeId }.toSet() }

    /**
     * M6.3 **Accept**: saves [slots] (the kept ones and the planner's; new ones have no id yet), the
     * [cooks] still to do as planned cooks, and the [seed]. Only slots that changed are written.
     */
    suspend fun accept(weekStart: LocalDate, slots: List<PlanSlot>, cooks: List<PlannedCook>, seed: Long) {
        val now = clock.millis()
        val planId = planFor(weekStart)
        val existing = planDao.slots(planId).associateBy { Triple(it.date, it.meal, it.memberId) }
        val changed = slots.mapNotNull { slot ->
            val old = existing[Triple(slot.date.toString(), slot.meal.name, slot.memberId)]
            val merged = slot.copy(id = old?.id ?: slot.id.ifEmpty { newId() }, planId = planId)
            val unchanged = old?.toDomain()?.let { it == merged }
            if (unchanged == true) null else merged.toEntity(createdAt = old?.createdAt ?: now, updatedAt = now)
        }
        val cookRows = cooks.map { cook ->
            PlanCookEntity(
                id = newId(),
                planId = planId,
                date = cook.date.toString(),
                recipeId = cook.recipeId,
                portions = cook.portions,
                targetCookedGrams = cook.targetCookedGrams,
                scale = cook.scale,
                batchId = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        planDao.accept(planId, changed, cookRows, seed, now)
    }

    /**
     * M6.6: [me]'s meals of this week from [today] on that are planned but not eaten, boxed or cooked yet,
     * and whose meal target is not the current one (after a check-in changed the targets).
     */
    suspend fun retargetable(me: Member, today: LocalDate): List<PlanSlot> {
        val week = ReminderSchedule.weekStart(today)
        val cooked = cookedNow(planFor(week)) + cookedNow(planFor(week.minusWeeks(1)))
        val days = CookingSessions.days(me.cookSessionsPerWeek, me.cookDays)
        return slotsBetween(today, week.plusDays(6)).first().filter { slot ->
            if (slot.memberId != me.id || slot.done || slot.boxId != null) return@filter false
            if (slot.kind != SlotKind.BOX && slot.kind != SlotKind.COOK_FRESH) return@filter false
            val recipe = slot.recipeId
            val cookDay = if (slot.kind == SlotKind.BOX) CookingSessions.coveringDay(slot.date, slot.meal, days, me.preppedMeals + slot.meal) else slot.date
            if (recipe != null && cookDay != null && (cookDay to recipe) in cooked) return@filter false
            val new = me.mealTarget(slot.meal) ?: return@filter false
            val old = slot.target ?: return@filter true
            Math.round(old.kcal) != Math.round(new.kcal) || Math.round(old.proteinG) != Math.round(new.proteinG)
        }
    }

    /** M6.6: gives these meals [me]'s current meal targets, so their boxes are sized for the new target. */
    suspend fun retarget(me: Member, slots: List<PlanSlot>) {
        val now = clock.millis()
        for (slot in slots) {
            val row = planDao.slot(slot.planId, slot.date.toString(), slot.meal.name, slot.memberId) ?: continue
            planDao.upsertSlot(slot.copy(target = me.mealTarget(slot.meal)).toEntity(createdAt = row.createdAt, updatedAt = now))
        }
    }

    /** Cooks of this plan already done: (date, recipe). */
    fun cooked(planId: String): Flow<Set<Pair<LocalDate, String>>> = planDao.observeCooks(planId).map { rows ->
        rows.filter { it.batchId != null }.mapNotNull { row -> com.calore.tracker.domain.parseIsoDate(row.date)?.let { it to row.recipeId } }.toSet()
    }

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
