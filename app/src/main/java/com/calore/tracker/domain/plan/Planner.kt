package com.calore.tracker.domain.plan

import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.Freshness
import com.calore.tracker.domain.MealTarget
import com.calore.tracker.domain.MealTargets
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.Recipes
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.mealTarget
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/**
 * The smart planner, "Plan my week" (algorithms A15): hard filters → scores → greedy fill →
 * improvement rounds with a seeded random → the proposal with its reasons. Sizes (step 4) come
 * from [com.calore.tracker.domain.PlanCooks] and the shopping list (step 5) from A16.
 *
 * One pot per meal: everyone eating the same meal from the same session (or the same fresh meal)
 * gets the same recipe when one fits them all; otherwise each person gets their own.
 */
object Planner {

    /** A meal still to plan: its kind and cooking day (the session for boxes, the day itself otherwise). */
    data class OpenSlot(
        val key: SlotKey,
        val kind: SlotKind,
        val cookDate: LocalDate,
        val target: MealTarget?,
    )

    /**
     * The meals to plan: every person's meals (those with a share in their meal split) from
     * [PlannerInput.today] to the end of the week, minus the kept ones. Prepped meals are boxes from
     * the session that covers them (A14); when that session is already over, the meal is cooked fresh.
     */
    fun openSlots(input: PlannerInput): List<OpenSlot> {
        val kept = input.kept.map { it.key }.toSet()
        val days = (0L..6L).map { input.weekStart.plusDays(it) }.filter { !it.isBefore(input.today) }
        return buildList {
            for (person in input.members) {
                val meals = MealTargets.normalizedSplit(person.member.mealSplit).keys
                for (date in days) {
                    for (meal in CookingSessions.MEAL_ORDER) {
                        if (meal !in meals) continue
                        val key = SlotKey(date, meal, person.id)
                        if (key in kept) continue
                        val session = if (meal in input.prepped) {
                            CookingSessions.coveringDay(date, meal, input.cookingDays, input.prepped)
                        } else {
                            null
                        }
                        val box = session != null && !session.isBefore(input.today)
                        add(OpenSlot(key, if (box) SlotKind.BOX else SlotKind.COOK_FRESH, if (box) session!! else date, person.member.mealTarget(meal)))
                    }
                }
            }
        }
    }

    /** A15 step 1, the hard filters, for one person and one meal. */
    fun allowed(person: PlannerMember, recipe: PlannerRecipe, meal: MealType, kind: SlotKind, cookDate: LocalDate, date: LocalDate): Boolean =
        allowedForPerson(person, recipe) && fitsSlot(recipe, meal, kind, cookDate, date)

    /** Diet, allergens, disliked foods, 👎 and equipment; and the recipe can be sized (it has calories). */
    fun allowedForPerson(person: PlannerMember, recipe: PlannerRecipe): Boolean {
        val m = person.member
        if (recipe.nutrition.total.kcal <= 0) return false
        if (!Recipes.fitsDiet(recipe.animalFlags, m.diet)) return false
        if (recipe.allergens.any { it in m.allergens }) return false
        if (m.dislikedFoods.any { entry -> recipe.ingredients.any { it.matches(entry) } }) return false
        if (person.ratings[recipe.id] == -1) return false
        if (m.equipment.isNotEmpty() && !m.equipment.containsAll(recipe.recipe.equipment)) return false
        return true
    }

    /** The meal type fits; a box needs a meal-prep recipe whose fridge days cover the day, or one that freezes. */
    fun fitsSlot(recipe: PlannerRecipe, meal: MealType, kind: SlotKind, cookDate: LocalDate, date: LocalDate): Boolean {
        if (meal !in recipe.meals) return false
        if (kind != SlotKind.BOX) return true
        if (!recipe.recipe.mealPrepFriendly) return false
        return !date.isAfter(Freshness.eatBy(cookDate, recipe.recipe.fridgeDays)) || recipe.recipe.freezable
    }

    /**
     * Plans the open meals of the week. The same [seed] and input always give the same plan.
     * [previous] is the last proposal (meal → recipe id) when regenerating: its recipes score lower, and
     * if the new plan still came out the same, the meal with the closest alternative changes.
     */
    fun plan(input: PlannerInput, seed: Long, previous: Map<SlotKey, String> = emptyMap()): PlanResult {
        val ctx = Context(input, previous)
        val random = Random(seed)
        val groups = ctx.buildGroups(random)
        // Greedy fill (step 3.1), bucket by bucket in time order.
        val used = HashMap<String, MutableSet<Int>>()
        for (bucket in groups.groupBy { it.bucket }.toSortedMap().values) {
            for (g in bucket) {
                val taken = g.siblings.mapNotNull { it.chosenRecipe }.toSet()
                var best = -1
                var bestScore = Double.NEGATIVE_INFINITY
                for (i in g.candidates.indices) {
                    if (g.candidates[i] in taken && g.candidates.size > taken.size) continue
                    val s = g.base[i] - ctx.recency(g, g.candidates[i], used)
                    if (s > bestScore) {
                        bestScore = s
                        best = i
                    }
                }
                g.choice = best
            }
            for (g in bucket) g.chosenRecipe?.let { r -> g.slots.forEach { used.getOrPut(it.key.memberId) { HashSet() }.add(r) } }
        }
        // Improve (step 3.2): swap one recipe for another candidate when the week's total gets better.
        var total = ctx.total(groups)
        val movable = groups.filter { it.candidates.size > 1 }
        if (movable.isNotEmpty()) {
            repeat(input.config.iterations) {
                val g = movable[random.nextInt(movable.size)]
                val taken = g.siblings.mapNotNull { it.chosenRecipe }.toSet()
                val pool = g.candidates.indices.take(IMPROVE_POOL).filter { it != g.choice && g.candidates[it] !in taken }
                if (pool.isEmpty()) return@repeat
                val old = g.choice
                g.choice = pool[random.nextInt(pool.size)]
                val t = ctx.total(groups)
                if (t > total) total = t else g.choice = old
            }
        }
        if (previous.isNotEmpty()) total = ctx.forceChange(groups, previous, total)
        return ctx.result(groups, seed, total)
    }

    /** **Swap**: the best [count] other recipes for one meal, with their reasons. */
    fun alternatives(input: PlannerInput, result: PlanResult, key: SlotKey, count: Int = 5): List<PlanAlternative> {
        val slot = result.slots.firstOrNull { it.key == key } ?: return emptyList()
        val ctx = Context(input, emptyMap())
        val person = ctx.memberIndex[key.memberId] ?: return emptyList()
        val kind = if (slot.kind == SlotKind.BOX) SlotKind.BOX else SlotKind.COOK_FRESH
        val cookDate = if (kind == SlotKind.BOX) {
            CookingSessions.coveringDay(key.date, key.meal, input.cookingDays, input.prepped + key.meal) ?: key.date
        } else {
            key.date
        }
        val open = OpenSlot(key, kind, cookDate, slot.target ?: input.members[person].member.mealTarget(key.meal))
        val usedElsewhere = result.slots.filter { it.memberId == key.memberId && it.key != key }.mapNotNull { it.recipeId }.toSet()
        return input.recipes.indices.asSequence()
            .filter { input.recipes[it].id != slot.recipeId && ctx.allowedSlot(person, it, open) }
            .map { r ->
                val f = ctx.slotFactors(person, r, open)
                val recent = if (input.recipes[r].id in usedElsewhere || input.recipes[r].id in input.recent[key.memberId].orEmpty()) {
                    ctx.varietyPenalty(person)
                } else {
                    0.0
                }
                PlanAlternative(input.recipes[r].id, f.sum - recent, ctx.reasons(listOf(f)))
            }
            .sortedWith(compareByDescending<PlanAlternative> { it.score }.thenBy { it.recipeId })
            .take(count)
            .toList()
    }

    /** Candidates tried per improvement round: the best few by score. */
    private const val IMPROVE_POOL = 10

    /** One pot: a recipe for these meals. */
    private class Group(
        val kind: SlotKind,
        val cookDate: LocalDate,
        val bucket: Int,
        /** The run of days within the bucket; pots of different runs get different recipes when possible. */
        val chunk: Int,
        val slots: List<OpenSlot>,
        /** Indices of [PlannerInput.recipes], best first. */
        val candidates: IntArray,
        /** Score of each candidate, parallel to [candidates] (without the variety penalty). */
        val base: DoubleArray,
    ) {
        var choice: Int = -1
        var siblings: List<Group> = emptyList()
        val chosenRecipe: Int? get() = if (choice >= 0) candidates[choice] else null
    }

    /** Points by factor for one meal, so the reasons can be explained. */
    private data class Factors(
        val rated: Double = 0.0,
        val liked: Double = 0.0,
        val likedKeys: List<String> = emptyList(),
        val cuisine: Double = 0.0,
        val cuisineKey: String? = null,
        val protein: Double = 0.0,
        val cost: Double = 0.0,
        val pantry: Double = 0.0,
        val pantryFoods: List<String> = emptyList(),
        val phase: Double = 0.0,
        val freezes: Double = 0.0,
        val regenerate: Double = 0.0,
    ) {
        val sum: Double get() = rated + liked + cuisine + protein + cost + pantry + phase + freezes - regenerate
    }

    private class Context(val input: PlannerInput, val previous: Map<SlotKey, String>) {
        val cfg = input.config
        val recipes = input.recipes
        val memberIndex: Map<String, Int> = input.members.withIndex().associate { (i, m) -> m.id to i }
        private val recipeIndex: Map<String, Int> = recipes.withIndex().associate { (i, r) -> r.id to i }
        private val personOk = Array(input.members.size) { m -> BooleanArray(recipes.size) { r -> allowedForPerson(input.members[m], recipes[r]) } }
        private val statics = arrayOfNulls<Factors>(input.members.size * recipes.size)

        /** Pantry food expiring within the limit: food uuid → grams (A15, "use it up"). */
        private val expiring: Map<String, Double> = input.pantry
            .filter { lot ->
                val bb = lot.bestBefore
                !lot.reservedForOtherCook && bb != null && !bb.isBefore(input.today) && !bb.isAfter(input.today.plusDays(cfg.expiringWithinDays))
            }
            .groupBy { it.foodUuid }.mapValues { (_, lots) -> lots.sumOf { it.grams } }

        private val keptCooking = input.kept.filter { it.kind == SlotKind.BOX || it.kind == SlotKind.COOK_FRESH }

        /** Budget per planned meal of the week, in haléře. */
        private val budgetPerMeal: Double? = input.weeklyBudgetHalers?.takeIf { it > 0 }?.let { budget ->
            val meals = openSlots(input).size + keptCooking.size
            if (meals > 0) budget.toDouble() / meals else null
        }

        fun allowedSlot(person: Int, r: Int, slot: OpenSlot): Boolean =
            personOk[person][r] && fitsSlot(recipes[r], slot.key.meal, slot.kind, slot.cookDate, slot.key.date)

        fun varietyPenalty(person: Int): Double = cfg.recentPerVariety * (input.members[person].member.variety.coerceIn(1, 3) - 1)

        private fun static(person: Int, r: Int): Factors {
            val i = person * recipes.size + r
            statics[i]?.let { return it }
            val pm = input.members[person]
            val m = pm.member
            val recipe = recipes[r]
            val likedKeys = m.likedFoods.filter { entry -> recipe.ingredients.any { it.matches(entry) } }
            val cuisine = recipe.recipe.cuisine?.takeIf { it in m.cuisines }
            val targets = m.effectiveTargets
            val targetShare = if (targets.kcal > 0) 4.0 * targets.proteinG / targets.kcal else null
            val protein = recipe.proteinShare?.let { share ->
                targetShare?.takeIf { it > 0 }?.let { t -> cfg.proteinFitMax * (1 - abs(share - t) / t).coerceIn(0.0, 1.0) }
            } ?: 0.0
            val counted = recipe.ingredients.filter { !it.optional }
            val countedGrams = counted.sumOf { it.grams }
            val fromPantry = counted.filter { it.foodUuid in expiring }
            val pantryGrams = fromPantry.sumOf { minOf(it.grams, expiring.getValue(it.foodUuid)) }
            val f = Factors(
                rated = if (pm.ratings[recipe.id] == 1) cfg.ratedUp else 0.0,
                liked = minOf(cfg.likedFood * likedKeys.size, cfg.likedFoodMax),
                likedKeys = likedKeys,
                cuisine = if (cuisine != null) cfg.cuisine else 0.0,
                cuisineKey = cuisine,
                protein = protein,
                pantry = if (countedGrams > 0) cfg.pantryMax * (pantryGrams / countedGrams).coerceIn(0.0, 1.0) else 0.0,
                pantryFoods = fromPantry.map { it.foodUuid }.distinct(),
            )
            statics[i] = f
            return f
        }

        /** Every factor for one meal (A15 step 2), without the variety penalty. */
        fun slotFactors(person: Int, r: Int, slot: OpenSlot): Factors {
            val recipe = recipes[r]
            val cost = budgetPerMeal?.let { budget ->
                val mealCost = recipe.costPerKcal?.let { perKcal -> slot.target?.kcal?.let { perKcal * it } }
                    ?: recipe.costHalers?.let { it / recipe.recipe.servings.coerceAtLeast(1) }
                mealCost?.let { c ->
                    val ratio = c / budget
                    cfg.costMax * ((cfg.expensiveRatio - ratio) / (cfg.expensiveRatio - cfg.cheapRatio)).coerceIn(0.0, 1.0)
                }
            } ?: 0.0
            val needsFrozen = slot.kind == SlotKind.BOX && slot.key.date.isAfter(Freshness.eatBy(slot.cookDate, Recipe.DEFAULT_FRIDGE_DAYS))
            return static(person, r).copy(
                cost = cost,
                phase = input.phase.bonus(slot.key.memberId, slot.key.date, recipe).coerceIn(0.0, cfg.phaseMax),
                freezes = if (needsFrozen && recipe.recipe.freezable) cfg.freezable else 0.0,
                regenerate = if (previous[slot.key] == recipe.id) cfg.regeneratePenalty else 0.0,
            )
        }

        /** The pots to fill: box sessions split into `k` chunks of days by variety, fresh meals by day. */
        fun buildGroups(random: Random): List<Group> {
            val open = openSlots(input)
            val tieRank = IntArray(recipes.size).also { ranks -> recipes.indices.shuffled(random).forEachIndexed { pos, r -> ranks[r] = pos } }
            data class BucketKey(val cookDate: LocalDate, val meal: MealType, val kind: SlotKind)
            val buckets = open.groupBy { BucketKey(it.cookDate, it.key.meal, it.kind) }.entries
                .sortedWith(compareBy({ it.key.cookDate }, { CookingSessions.MEAL_ORDER.indexOf(it.key.meal) }, { it.key.kind }))
            val groups = ArrayList<Group>()
            buckets.forEachIndexed { bucketIndex, (key, slots) ->
                val dates = slots.map { it.key.date }.distinct().sorted()
                val k = if (key.kind != SlotKind.BOX) {
                    1
                } else {
                    when (input.variety.coerceIn(1, 3)) {
                        1 -> 1
                        2 -> minOf(2, dates.size)
                        else -> dates.size
                    }
                }
                val inBucket = ArrayList<Group>()
                chunks(dates, k).forEachIndexed { chunkIndex, chunk ->
                    val chunkSlots = slots.filter { it.key.date in chunk }.sortedWith(compareBy({ it.key.date }, { memberIndex[it.key.memberId] }))
                    fun make(part: List<OpenSlot>) = group(key.kind, key.cookDate, bucketIndex, chunkIndex, part, tieRank)
                    val shared = make(chunkSlots)
                    if (shared != null) {
                        inBucket += shared
                    } else {
                        // No recipe fits everyone: each person gets their own pot; failing that, each day on its own.
                        for (own in chunkSlots.groupBy { it.key.memberId }.values) {
                            val mine = make(own)
                            if (mine != null) inBucket += mine else own.forEach { slot -> make(listOf(slot))?.let { inBucket += it } }
                        }
                    }
                }
                inBucket.forEach { g -> g.siblings = inBucket.filter { it.chunk != g.chunk } }
                groups += inBucket
            }
            return groups
        }

        private fun group(kind: SlotKind, cookDate: LocalDate, bucket: Int, chunk: Int, slots: List<OpenSlot>, tieRank: IntArray): Group? {
            val people = slots.map { memberIndex.getValue(it.key.memberId) }
            val candidates = recipes.indices.filter { r -> slots.indices.all { allowedSlot(people[it], r, slots[it]) } }
            if (candidates.isEmpty()) return null
            val scored = candidates.map { r -> r to slots.indices.sumOf { slotFactors(people[it], r, slots[it]).sum } }
                .sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { tieRank[it.first] })
            return Group(kind, cookDate, bucket, chunk, slots, IntArray(scored.size) { scored[it].first }, DoubleArray(scored.size) { scored[it].second })
        }

        /** The variety penalty of giving [r] to this pot, given recipes used before it ([used]: member → recipes). */
        fun recency(g: Group, r: Int, used: Map<String, Set<Int>>): Double {
            var penalty = 0.0
            val id = recipes[r].id
            for (slot in g.slots) {
                val person = memberIndex.getValue(slot.key.memberId)
                if (used[slot.key.memberId]?.contains(r) == true || id in input.recent[slot.key.memberId].orEmpty()) penalty += varietyPenalty(person)
            }
            return penalty
        }

        /** The week's total (A15 step 3.2): scores, minus protein days off target, minus sessions over the minutes. */
        fun total(groups: List<Group>): Double {
            var sum = 0.0
            val used = HashMap<String, MutableSet<Int>>()
            var bucket = -1
            val pending = ArrayList<Group>()
            fun flush() {
                for (p in pending) p.chosenRecipe?.let { r -> p.slots.forEach { used.getOrPut(it.key.memberId) { HashSet() }.add(r) } }
                pending.clear()
            }
            for (g in groups) {
                if (g.bucket != bucket) {
                    flush()
                    bucket = g.bucket
                }
                val r = g.chosenRecipe ?: continue
                sum += g.base[g.choice] - recency(g, r, used)
                pending += g
            }
            flush()
            return sum - proteinPenalty(groups) - minutesPenalty(groups)
        }

        private fun proteinPenalty(groups: List<Group>): Double {
            val planned = HashMap<Pair<String, LocalDate>, DoubleArray>()
            fun add(memberId: String, date: LocalDate, target: MealTarget?, r: Int?) {
                if (target == null || r == null) return
                val day = planned.getOrPut(memberId to date) { DoubleArray(2) }
                day[0] += target.kcal * recipes[r].proteinPerKcal
                day[1] += target.proteinG
            }
            for (slot in keptCooking) {
                if (slot.date.isBefore(input.today)) continue
                add(slot.memberId, slot.date, slot.target, slot.recipeId?.let(recipeIndex::get))
            }
            for (g in groups) {
                val r = g.chosenRecipe ?: continue
                for (slot in g.slots) add(slot.key.memberId, slot.key.date, slot.target, r)
            }
            return planned.values.count { (protein, target) -> target > 0 && abs(protein - target) > cfg.proteinTolerance * target } * cfg.proteinDayPenalty
        }

        private val keptSessions: Map<LocalDate, Set<Int>> = input.kept
            .filter { it.kind == SlotKind.BOX && it.boxId == null && !it.done }
            .mapNotNull { slot ->
                val r = slot.recipeId?.let(recipeIndex::get) ?: return@mapNotNull null
                val day = CookingSessions.coveringDay(slot.date, slot.meal, input.cookingDays, input.prepped + slot.meal) ?: return@mapNotNull null
                if (day.isBefore(input.today)) null else day to r
            }
            .groupBy({ it.first }, { it.second }).mapValues { (_, list) -> list.toSet() }

        private fun minutesPenalty(groups: List<Group>): Double {
            val sessions = HashMap<LocalDate, MutableSet<Int>>()
            keptSessions.forEach { (day, rs) -> sessions.getOrPut(day) { HashSet() }.addAll(rs) }
            for (g in groups) if (g.kind == SlotKind.BOX) g.chosenRecipe?.let { sessions.getOrPut(g.cookDate) { HashSet() }.add(it) }
            return sessions.values.count { rs -> rs.sumOf { recipes[it].activeMinutes } > input.minutesPerSession } * cfg.sessionMinutesPenalty
        }

        /** Regenerate: if nothing changed, the pot with the smallest loss takes its next best recipe. */
        fun forceChange(groups: List<Group>, previous: Map<SlotKey, String>, total: Double): Double {
            val changed = groups.any { g -> g.chosenRecipe?.let { r -> g.slots.any { previous[it.key] != recipes[r].id } } == true }
            if (changed) return total
            var best: Pair<Group, Int>? = null
            var bestTotal = Double.NEGATIVE_INFINITY
            for (g in groups) {
                val taken = g.siblings.mapNotNull { it.chosenRecipe }.toSet()
                val alt = g.candidates.indices.firstOrNull { it != g.choice && g.candidates[it] !in taken } ?: continue
                val old = g.choice
                g.choice = alt
                val t = total(groups)
                g.choice = old
                if (t > bestTotal) {
                    bestTotal = t
                    best = g to alt
                }
            }
            val (g, alt) = best ?: return total
            g.choice = alt
            return bestTotal
        }

        fun reasons(perSlot: List<Factors>): List<PlanReason> {
            if (perSlot.isEmpty()) return emptyList()
            val n = perSlot.size
            fun avg(f: (Factors) -> Double) = perSlot.sumOf(f) / n
            val list = listOf(
                PlanReason.RatedUp to avg { it.rated },
                PlanReason.LikedFoods(perSlot.flatMap { it.likedKeys }.distinct()) to avg { it.liked },
                (perSlot.firstNotNullOfOrNull { it.cuisineKey }?.let { PlanReason.Cuisine(it) } ?: PlanReason.Cuisine("")) to avg { it.cuisine },
                PlanReason.GoodProtein to avg { it.protein }.takeIf { it >= cfg.proteinFitMax * STRONG }.orZero(),
                PlanReason.Cheap to avg { it.cost }.takeIf { it >= cfg.costMax * STRONG }.orZero(),
                PlanReason.UsesPantry(perSlot.flatMap { it.pantryFoods }.distinct()) to avg { it.pantry },
                PlanReason.CyclePhase to avg { it.phase },
                PlanReason.Freezes to avg { it.freezes },
            )
            return list.filter { it.second > 0 }.sortedByDescending { it.second }.take(MAX_REASONS).map { it.first }
        }

        fun result(groups: List<Group>, seed: Long, total: Double): PlanResult {
            val choices = ArrayList<PlanChoice>()
            val newSlots = ArrayList<PlanSlot>()
            val used = HashMap<String, MutableSet<Int>>()
            for (bucket in groups.groupBy { it.bucket }.toSortedMap().values) {
                for (g in bucket) {
                    val r = g.chosenRecipe ?: continue
                    val factors = g.slots.map { slotFactors(memberIndex.getValue(it.key.memberId), r, it) }
                    choices += PlanChoice(recipes[r].id, g.kind, g.cookDate, g.slots.map { it.key }, g.base[g.choice] - recency(g, r, used), reasons(factors))
                    g.slots.forEach { slot ->
                        newSlots += PlanSlot(
                            id = "",
                            planId = "",
                            date = slot.key.date,
                            meal = slot.key.meal,
                            memberId = slot.key.memberId,
                            kind = g.kind,
                            recipeId = recipes[r].id,
                            target = slot.target,
                        )
                    }
                }
                for (g in bucket) g.chosenRecipe?.let { r -> g.slots.forEach { used.getOrPut(it.key.memberId) { HashSet() }.add(r) } }
            }
            val planned = newSlots.map { it.key }.toSet()
            val unplanned = openSlots(input).map { it.key }.filter { it !in planned }
            val slots = (input.kept + newSlots).sortedWith(
                compareBy({ it.date }, { CookingSessions.MEAL_ORDER.indexOf(it.meal) }, { memberIndex[it.memberId] ?: Int.MAX_VALUE }),
            )
            return PlanResult(seed, slots, choices, unplanned, total)
        }

        private fun Double?.orZero() = this ?: 0.0
    }

    /** A reason must be at least this share of its factor's maximum to be shown (protein and cost). */
    private const val STRONG = 2.0 / 3.0
    private const val MAX_REASONS = 3

    /** Splits [dates] into [k] runs of days, the first ones longer when they don't divide evenly. */
    internal fun chunks(dates: List<LocalDate>, k: Int): List<List<LocalDate>> {
        if (dates.isEmpty()) return emptyList()
        val parts = k.coerceIn(1, dates.size)
        val size = dates.size / parts
        val extra = dates.size % parts
        var start = 0
        return (0 until parts).map { i ->
            val length = size + if (i < extra) 1 else 0
            dates.subList(start, start + length).also { start += length }
        }
    }
}

/** A liked or disliked entry matches by food key (`generic:…`), food uuid or shop section name. */
fun PlannerIngredient.matches(entry: String): Boolean = entry == key || entry == foodUuid || entry == section.name
