package com.calore.tracker.domain

import java.time.LocalDate
import java.time.LocalTime

/** A planned meal on the widget: "Lunch: C2", and the box to eat if it has one. */
data class WidgetMeal(val slot: PlanSlot, val label: String, val box: BoxView?)

/** What the "Today" widget shows (M9.1). */
data class TodayWidgetData(
    val kcal: Double,
    val kcalTarget: Int,
    val protein: Double,
    val proteinTarget: Int,
    /** The next planned meal still to eat today. */
    val next: WidgetMeal?,
    /** The next planned meal with a filled box, for **Ate it**. */
    val nextBox: WidgetMeal?,
    /** My fridge boxes that should be eaten today (eat-by today or earlier). */
    val boxesToEat: Int,
) {
    /** 0–1 for the ring. */
    val kcalProgress: Float get() = if (kcalTarget > 0) (kcal / kcalTarget).toFloat().coerceIn(0f, 1f) else 0f
}

/** The widget's content from the diary, the plan and the boxes (M9.1). */
object TodayWidgetModel {

    fun build(
        today: LocalDate,
        now: LocalTime,
        meId: String,
        totals: Nutrients,
        targets: NutritionGoals,
        slots: List<PlanSlot>,
        boxes: List<BoxView>,
        recipeNames: Map<String, String>,
    ): TodayWidgetData {
        val boxById = boxes.associateBy { it.box.id }
        val open = slots
            .filter { it.date == today && it.memberId == meId && !it.done && (it.kind == SlotKind.BOX || it.kind == SlotKind.COOK_FRESH) }
            .sortedBy { CookingSessions.MEAL_ORDER.indexOf(it.meal) }
        fun meal(slot: PlanSlot): WidgetMeal {
            val box = slot.boxId?.let(boxById::get)
            val label = box?.box?.code ?: slot.recipeId?.let(recipeNames::get).orEmpty()
            return WidgetMeal(slot, label, box)
        }
        // The meal for now or later; if every planned meal's time has passed, the first one not eaten.
        val nowIndex = CookingSessions.MEAL_ORDER.indexOf(mealAt(now))
        val upcoming = open.filter { CookingSessions.MEAL_ORDER.indexOf(it.meal) >= nowIndex }.ifEmpty { open }
        val mine = boxes.filter { it.box.ownerMemberId == null || it.box.ownerMemberId == meId }
        return TodayWidgetData(
            kcal = totals.kcal,
            kcalTarget = targets.kcal,
            protein = totals.protein ?: 0.0,
            proteinTarget = targets.proteinG,
            next = upcoming.firstOrNull()?.let(::meal),
            nextBox = upcoming.map(::meal).firstOrNull { it.box != null } ?: open.map(::meal).firstOrNull { it.box != null },
            boxesToEat = mine.count { it.box.storage != Storage.FREEZER && !it.box.eatByDate.isAfter(today) },
        )
    }
}
