package com.calore.tracker.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PantryRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.RatingRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.ShopRepository
import com.calore.tracker.data.repository.ShoppingRepository
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.MealTarget
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.PantryLot
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.PlanTotals
import com.calore.tracker.domain.PlannedCook
import com.calore.tracker.domain.ProductOption
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.domain.Shopping
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.StoreInfo
import com.calore.tracker.domain.parseIsoDate
import com.calore.tracker.domain.plan.PlanAlternative
import com.calore.tracker.domain.plan.PhaseBonusProvider
import com.calore.tracker.domain.cycle.CyclePhaseBonus
import com.calore.tracker.domain.cycle.Cycles
import com.calore.tracker.data.repository.CycleRepository
import com.calore.tracker.domain.plan.PlanEstimates
import com.calore.tracker.domain.plan.PlanReason
import com.calore.tracker.domain.plan.PlanResult
import com.calore.tracker.domain.plan.Planner
import com.calore.tracker.domain.plan.PlannerInput
import com.calore.tracker.domain.plan.PlannerMember
import com.calore.tracker.domain.plan.PlannerPref
import com.calore.tracker.domain.plan.PlannerPrefs
import com.calore.tracker.domain.plan.PlannerRecipe
import com.calore.tracker.domain.plan.ShoppingEstimate
import com.calore.tracker.domain.plan.SlotKey
import com.calore.tracker.domain.plan.key
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate

/** Where "Plan my week" is. */
enum class PlanWeekPhase { LOADING, ASK_REPLAN, GENERATING, PROPOSAL, SAVING, DONE }

data class PlanWeekUiState(
    val phase: PlanWeekPhase = PlanWeekPhase.LOADING,
    val weekStart: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val people: List<Member> = emptyList(),
    /** The people planned for; everyone by default. */
    val chosen: Set<String> = emptySet(),
    val missing: List<PlannerPref> = emptyList(),
    /** M6.1: the checklist of missing preferences, shown once on top of the first proposal. */
    val showChecklist: Boolean = false,
    /** The proposal: kept slots and planned ones. */
    val slots: List<PlanSlot> = emptyList(),
    /** Meals the planner (or a swap) filled. */
    val planned: Set<SlotKey> = emptySet(),
    val unplanned: List<SlotKey> = emptyList(),
    val locked: Set<SlotKey> = emptySet(),
    val reasons: Map<SlotKey, List<PlanReason>> = emptyMap(),
    val recipes: Map<String, PlannerRecipe> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
    val foodNames: Map<String, String> = emptyMap(),
    val cooks: List<PlannedCook> = emptyList(),
    val minutes: Map<LocalDate, Int> = emptyMap(),
    val estimate: ShoppingEstimate? = null,
    val cookingDays: List<DayOfWeek> = emptyList(),
    val prepped: Set<MealType> = emptySet(),
    /** **Swap**: the meal and its alternatives. */
    val swapping: SlotKey? = null,
    val alternatives: List<PlanAlternative> = emptyList(),
) {
    val days: List<LocalDate> get() = (0L..6L).map { weekStart.plusDays(it) }
    val shownPeople: List<Member> get() = people.filter { it.id in chosen }

    fun slot(date: LocalDate, meal: MealType, memberId: String): PlanSlot? =
        slots.firstOrNull { it.date == date && it.meal == meal && it.memberId == memberId }

    fun plannedTotals(date: LocalDate, memberId: String): MealTarget = PlanTotals.planned(slots.filter { it.date == date && it.memberId == memberId })

    /** A box eaten after the eat-by date of its cooking day goes in the freezer (A13): its age in days, else null. */
    fun frozenAge(slot: PlanSlot): Long? {
        if (slot.kind != SlotKind.BOX) return null
        val fridgeDays = slot.recipeId?.let { recipes[it]?.recipe?.fridgeDays } ?: return null
        val cookDay = CookingSessions.coveringDay(slot.date, slot.meal, cookingDays, prepped + slot.meal) ?: return null
        return PlanTotals.boxAgeIfFrozen(cookDay, slot.date, fridgeDays)
    }

    val frozenBoxes: Int get() = slots.count { it.key in planned && frozenAge(it) != null }
}

/** "Plan my week" (M6.3): propose, lock, swap, regenerate and accept. Also "Replan the rest of the week" (M6.5). */
class PlanWeekViewModel(
    savedStateHandle: SavedStateHandle,
    private val plans: PlanRepository,
    private val members: MemberRepository,
    private val recipes: RecipeRepository,
    private val ratings: RatingRepository,
    private val pantry: PantryRepository,
    private val shops: ShopRepository,
    private val shopping: ShoppingRepository,
    private val appPrefs: AppPrefs,
    private val clock: Clock,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val cycle: CycleRepository? = null,
) : ViewModel() {

    private val state = MutableStateFlow(PlanWeekUiState())
    val uiState: StateFlow<PlanWeekUiState> = state.asStateFlow()

    /** What a plan is made from, loaded once per week. */
    private class Base(
        var me: Member,
        var people: List<Member>,
        val recipes: List<PlannerRecipe>,
        var ratings: Map<String, Map<String, Int>>,
        val pantry: List<PantryLot>,
        val products: List<ProductOption>,
        val stores: List<StoreInfo>,
        val prices: Map<String, Double>,
        val existing: List<PlanSlot>,
        val cooked: Set<Pair<LocalDate, String>>,
        val recent: Map<String, Set<String>>,
    )

    private var base: Base? = null
    private var input: PlannerInput? = null
    private var seed: Long = 0
    private var seedCounter = 0L

    init {
        val start = savedStateHandle.get<String>(Routes.ARG_WEEK_START)?.let(::parseIsoDate) ?: LocalDate.now(clock)
        load(ReminderSchedule.weekStart(start))
    }

    private fun load(weekStart: LocalDate) {
        state.update { it.copy(phase = PlanWeekPhase.LOADING, weekStart = weekStart) }
        viewModelScope.launch {
            val me = members.me().first()
            val people = listOf(me) + members.managed().first()
            val prices = shops.foodPrices().first()
            val (planId, existing) = plans.weekNow(weekStart)
            val previousPlan = plans.weekNow(weekStart.minusWeeks(1)).first
            val loaded = Base(
                me = me,
                people = people,
                recipes = recipes.forPlanner(prices),
                ratings = ratings.all(),
                pantry = pantry.lots(),
                products = shops.productsNow().mapNotNull { it.option() },
                stores = shops.storesNow().map { it.info },
                prices = prices,
                existing = existing,
                cooked = plans.cookedNow(planId) + plans.cookedNow(previousPlan),
                recent = plans.recentRecipes(weekStart),
            )
            base = loaded
            val details = recipes.observeAll().first()
            val settings = appPrefs.settings.first()
            val today = LocalDate.now(clock)
            state.update { s ->
                s.copy(
                    today = today,
                    people = people,
                    chosen = s.chosen.ifEmpty { people.map { it.id }.toSet() }.filter { id -> people.any { it.id == id } }.toSet(),
                    recipes = loaded.recipes.associateBy { it.id },
                    names = details.associate { it.recipe.id to it.recipe.displayName() },
                    foodNames = details.flatMap { it.foods.values }.associate { it.uuid to it.displayName() },
                    cookingDays = CookingSessions.days(me.cookSessionsPerWeek, me.cookDays),
                    prepped = me.preppedMeals,
                    missing = PlannerPrefs.missing(me),
                    showChecklist = !settings.plannerPrefsAsked && PlannerPrefs.missing(me).isNotEmpty(),
                    locked = emptySet(),
                )
            }
            if (existing.any { it.key.date >= today && !isFixed(it) && it.recipeId != null }) {
                state.update { it.copy(phase = PlanWeekPhase.ASK_REPLAN) }
            } else {
                generate(previous = emptyMap())
            }
        }
    }

    /** M6.1: the checklist was seen (filled in or not now); it is not shown again. */
    fun checklistDone() {
        state.update { it.copy(showChecklist = false) }
        viewModelScope.launch { appPrefs.setPlannerPrefsAsked() }
    }

    /** M6.3: keep the cooked boxes and eaten meals and plan the rest. */
    fun replanTheRest() = generate(previous = emptyMap())

    fun showWeek(weekStart: LocalDate) {
        if (weekStart != state.value.weekStart) load(weekStart)
    }

    fun togglePerson(id: String) {
        val s = state.value
        if (id == base?.me?.id) return // The cook is always planned for.
        state.update { it.copy(chosen = if (id in s.chosen) s.chosen - id else s.chosen + id, locked = emptySet()) }
        generate(previous = emptyMap())
    }

    /** **Regenerate** everything not locked, with a new seed. */
    fun regenerate() {
        val s = state.value
        generate(previous = s.slots.filter { it.key in s.planned && it.key !in s.locked }.mapNotNull { slot -> slot.recipeId?.let { slot.key to it } }.toMap())
    }

    fun toggleLock(key: SlotKey) = state.update { s -> s.copy(locked = if (key in s.locked) s.locked - key else s.locked + key) }

    /** **Swap**: the top 5 other recipes for one meal. */
    fun swap(key: SlotKey) {
        val current = input ?: return
        val s = state.value
        val result = PlanResult(seed, s.slots, emptyList(), s.unplanned, 0.0)
        val options = Planner.alternatives(current, result, key)
        state.update { it.copy(swapping = key, alternatives = options) }
    }

    fun dismissSwap() = state.update { it.copy(swapping = null, alternatives = emptyList()) }

    /** Puts [choice] in the meal and locks it, so **Regenerate** keeps it. */
    fun applySwap(choice: PlanAlternative) {
        val key = state.value.swapping ?: return
        state.update { s ->
            val slots = s.slots.map { if (it.key == key) it.copy(recipeId = choice.recipeId) else it }
            s.copy(slots = slots, planned = s.planned + key, locked = s.locked + key, reasons = s.reasons + (key to choice.reasons), swapping = null, alternatives = emptyList())
        }
        refreshEstimates()
    }

    /** **Accept**: saves the plan, its cooks and the shopping list. */
    fun accept() {
        val b = base ?: return
        val s = state.value
        if (s.phase != PlanWeekPhase.PROPOSAL) return
        state.update { it.copy(phase = PlanWeekPhase.SAVING) }
        viewModelScope.launch {
            val slots = s.slots.map { if (it.key in s.locked) it.copy(locked = true) else it }
            plans.accept(s.weekStart, slots, s.cooks, seed)
            shopping.regenerate(b.me, s.today)
            state.update { it.copy(phase = PlanWeekPhase.DONE) }
        }
    }

    private fun isFixed(slot: PlanSlot): Boolean {
        val s = state.value
        val b = base ?: return true
        if (slot.date.isBefore(s.today) || slot.done || slot.boxId != null || slot.locked) return true
        if (slot.kind == SlotKind.EAT_OUT || slot.kind == SlotKind.FREE) return true
        if (s.chosen.isNotEmpty() && slot.memberId !in s.chosen) return true
        val recipe = slot.recipeId ?: return false
        val cookDay = if (slot.kind == SlotKind.BOX) {
            CookingSessions.coveringDay(slot.date, slot.meal, s.cookingDays, s.prepped + slot.meal)
        } else {
            slot.date
        }
        return cookDay != null && (cookDay to recipe) in b.cooked
    }

    private fun generate(previous: Map<SlotKey, String>) {
        val b = base ?: return
        val s = state.value
        state.update { it.copy(phase = PlanWeekPhase.GENERATING, swapping = null, alternatives = emptyList()) }
        seed = clock.millis() xor (++seedCounter shl 32)
        val runSeed = seed
        viewModelScope.launch {
            // The profile may have changed since (the checklist sends people there): read it again.
            b.me = members.me().first()
            b.people = listOf(b.me) + members.managed().first()
            b.ratings = ratings.all()
            // M7.4 (A20): the cycle bonus for me, only when tracking is on and not paused.
            val phase = cycle?.let { repository ->
                val settings = repository.settings.first()
                val periods = repository.periodsNow()
                if (settings.enabled && !settings.paused && periods.isNotEmpty()) {
                    CyclePhaseBonus(b.me.id) { date -> Cycles.phaseOn(date, periods, settings) }
                } else {
                    null
                }
            } ?: PhaseBonusProvider.NONE
            plan(b, s, previous, runSeed, phase)
        }
    }

    private suspend fun plan(b: Base, s: PlanWeekUiState, previous: Map<SlotKey, String>, runSeed: Long, phase: PhaseBonusProvider) {
        val lockedSlots = s.slots.filter { it.key in s.locked }.map { it.copy(locked = true) }
        val lockedKeys = lockedSlots.map { it.key }.toSet()
        val kept = b.existing.filter { isFixed(it) && it.key !in lockedKeys } + lockedSlots
        val me = b.me
        val chosen = b.people.filter { it.id in s.chosen || it.id == me.id }
        val plannerInput = PlannerInput(
            weekStart = s.weekStart,
            today = s.today,
            members = chosen.map { PlannerMember(it, b.ratings[it.id].orEmpty()) },
            recipes = b.recipes,
            cookingDays = s.cookingDays,
            prepped = me.preppedMeals,
            minutesPerSession = me.minutesPerSession,
            variety = me.variety,
            weeklyBudgetHalers = me.weeklyBudget?.halers,
            pantry = b.pantry,
            kept = kept,
            recent = b.recent,
            phase = phase,
        )
        input = plannerInput
        val result = withContext(compute) { Planner.plan(plannerInput, runSeed, previous) }
        val reasons = result.choices.flatMap { c -> c.slots.map { it to c.reasons } }.toMap()
        state.update {
            it.copy(
                phase = PlanWeekPhase.PROPOSAL,
                people = b.people,
                cookingDays = CookingSessions.days(me.cookSessionsPerWeek, me.cookDays),
                prepped = me.preppedMeals,
                missing = PlannerPrefs.missing(me),
                slots = result.slots,
                planned = result.newKeys,
                unplanned = result.unplanned,
                reasons = reasons,
                locked = it.locked.filter { k -> k in lockedKeys }.toSet(),
            )
        }
        refreshEstimates()
    }

    /** Steps 4 and 5 for what is shown: the cooks, the active minutes and the shopping estimate. */
    private fun refreshEstimates() {
        val b = base ?: return
        val current = input ?: return
        val s = state.value
        val cooks = PlanEstimates.cooks(current, s.slots, s.today, b.cooked)
        val byId = b.recipes.associateBy { it.id }
        val lines = Shopping.lines(PlanEstimates.needs(cooks, byId), b.pantry, b.products, b.stores)
        state.update {
            it.copy(
                cooks = cooks,
                minutes = PlanEstimates.sessionMinutes(cooks, byId),
                estimate = PlanEstimates.cost(lines, b.prices),
            )
        }
    }
}
