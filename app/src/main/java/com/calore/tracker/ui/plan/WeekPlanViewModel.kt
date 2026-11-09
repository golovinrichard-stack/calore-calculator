package com.calore.tracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.PlanTemplate
import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.ShopRepository
import com.calore.tracker.domain.Batches
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.MealTarget
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.PlanCooks
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.PlannedCook
import com.calore.tracker.domain.PlanTotals
import com.calore.tracker.domain.RecipeCosts
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.mealTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate

data class WeekPlanUiState(
    val loaded: Boolean = false,
    val weekStart: LocalDate = LocalDate.MIN,
    val planId: String = "",
    val people: List<Member> = emptyList(),
    /** People shown and planned for; all by default. */
    val hidden: Set<String> = emptySet(),
    val slots: List<PlanSlot> = emptyList(),
    val recipes: Map<String, RecipeDetails> = emptyMap(),
    /** The phone's user's cooking days (A14). */
    val cookingDays: List<DayOfWeek> = emptyList(),
    val prepped: Set<MealType> = emptySet(),
    /** Ask how often to cook (M5.2), the first time. */
    val askCooking: Boolean = false,
    val templates: List<PlanTemplate> = emptyList(),
    /** Cooks already done: (date, recipe). */
    val cooked: Set<Pair<LocalDate, String>> = emptySet(),
    /** Price per gram by food (A18). */
    val prices: Map<String, Double> = emptyMap(),
) {
    /** A planned meal's cost (A18): its grams × the recipe's cost per cooked gram; null when a price is missing. */
    fun slotCost(slot: PlanSlot): Double? {
        if (slot.kind != SlotKind.BOX && slot.kind != SlotKind.COOK_FRESH) return null
        val details = slot.recipeId?.let { recipes[it] } ?: return null
        val cost = RecipeCosts.cost(details.ingredients, details.nutrition, details.recipe.servings, prices).takeIf { it.complete } ?: return null
        val potGrams = Batches.estimatedCookedGrams(details.nutrition.rawGrams, details.recipe.learnedYield).takeIf { it > 0 } ?: return null
        return cost.totalHalers / potGrams * PlanCooks.slotGrams(slot, details.nutrition, details.recipe.servings, details.recipe.learnedYield)
    }

    /** The week's planned meals that have a known cost, in haléře; null when none has. */
    val weekCost: Double?
        get() = slots.filter { it.memberId !in hidden }.mapNotNull { slotCost(it) }.takeIf { it.isNotEmpty() }?.sum()

    /** What to cook for this plan, per cooking day (M5.4). */
    val cooks: List<PlannedCook>
        get() = PlanCooks.compute(slots, recipes.mapValues { (_, d) -> d.recipe to d.nutrition }, cookingDays, prepped)

    val days: List<LocalDate> get() = (0L..6L).map { weekStart.plusDays(it) }
    val shownPeople: List<Member> get() = people.filter { it.id !in hidden }

    fun slot(date: LocalDate, meal: MealType, memberId: String): PlanSlot? =
        slots.firstOrNull { it.date == date && it.meal == meal && it.memberId == memberId }

    fun planned(date: LocalDate, memberId: String): MealTarget = PlanTotals.planned(slots.filter { it.date == date && it.memberId == memberId })

    /** A box planned after the eat-by date of its cooking day: its age in days (A13), else null. */
    fun frozenAge(slot: PlanSlot): Long? {
        if (slot.kind != SlotKind.BOX) return null
        val fridgeDays = slot.recipeId?.let { recipes[it]?.recipe?.fridgeDays } ?: return null
        val cookDay = CookingSessions.coveringDay(slot.date, slot.meal, cookingDays, prepped) ?: return null
        return PlanTotals.boxAgeIfFrozen(cookDay, slot.date, fridgeDays)
    }
}

sealed interface WeekPlanEvent {
    data object NothingToRepeat : WeekPlanEvent
}

/** The week plan by hand (M5.3), and how often the user cooks (M5.2). */
@OptIn(ExperimentalCoroutinesApi::class)
class WeekPlanViewModel(
    private val plans: PlanRepository,
    private val members: MemberRepository,
    recipes: RecipeRepository,
    private val appPrefs: AppPrefs,
    private val clock: Clock,
    shops: ShopRepository? = null,
) : ViewModel() {

    private val weekStart = MutableStateFlow(ReminderSchedule.weekStart(LocalDate.now(clock)))
    private val hidden = MutableStateFlow(emptySet<String>())

    private val planWithSlots: Flow<Triple<String, List<PlanSlot>, Set<Pair<LocalDate, String>>>> = weekStart.flatMapLatest { start ->
        flow { emit(plans.planFor(start)) }.flatMapLatest { id -> combine(plans.slots(id), plans.cooked(id)) { slots, cooked -> Triple(id, slots, cooked) } }
    }

    private val people: Flow<List<Member>> = combine(members.me(), members.cookFor()) { me, managed -> listOf(me) + managed }

    val uiState: StateFlow<WeekPlanUiState> = combine(
        combine(weekStart, planWithSlots, ::Pair),
        people,
        hidden,
        recipes.observeAll(),
        combine(appPrefs.settings, plans.templates(), shops?.foodPrices() ?: flowOf(emptyMap()), ::Triple),
    ) { (start, plan), people, hidden, all, (settings, templates, prices) ->
        val me = people.first()
        WeekPlanUiState(
            loaded = true,
            weekStart = start,
            planId = plan.first,
            people = people,
            hidden = hidden,
            slots = plan.second,
            cooked = plan.third,
            recipes = all.associateBy { it.recipe.id },
            cookingDays = CookingSessions.days(me.cookSessionsPerWeek, me.cookDays),
            prepped = me.preppedMeals,
            askCooking = !settings.cookingAsked,
            templates = templates,
            prices = prices,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekPlanUiState())

    private val eventChannel = Channel<WeekPlanEvent>(Channel.BUFFERED)
    val events: Flow<WeekPlanEvent> = eventChannel.receiveAsFlow()

    fun previousWeek() = weekStart.update { it.minusWeeks(1) }
    fun nextWeek() = weekStart.update { it.plusWeeks(1) }
    fun thisWeek() {
        weekStart.value = ReminderSchedule.weekStart(LocalDate.now(clock))
    }

    fun togglePerson(id: String) = hidden.update { if (id in it) it - id else it + id }

    /** Plans a meal; the slot keeps the person's meal target (A8) as it is now. */
    fun setSlot(date: LocalDate, meal: MealType, member: Member, kind: SlotKind, recipeId: String?) {
        val planId = uiState.value.planId.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch { plans.setSlot(planId, date, meal, member.id, kind, recipeId, member.mealTarget(meal)) }
    }

    fun clearSlot(slot: PlanSlot) {
        viewModelScope.launch { plans.clearSlot(slot.id) }
    }

    fun repeatLastWeek() {
        viewModelScope.launch { if (plans.repeatLastWeek(weekStart.value) == 0) eventChannel.send(WeekPlanEvent.NothingToRepeat) }
    }

    fun saveAsTemplate(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { plans.saveAsTemplate(weekStart.value, name) }
    }

    fun useTemplate(id: String) {
        viewModelScope.launch { plans.useTemplate(id, weekStart.value) }
    }

    /** M5.2: how often and when the user cooks, and which meals come from boxes. */
    fun saveCooking(sessions: Int, days: List<DayOfWeek>, minutes: Int, prepped: Set<MealType>) {
        viewModelScope.launch {
            members.updateMe { it.copy(cookSessionsPerWeek = sessions, cookDays = days, minutesPerSession = minutes, preppedMeals = prepped) }
            appPrefs.setCookingAsked()
        }
    }

    fun dismissCookingQuestion() {
        viewModelScope.launch { appPrefs.setCookingAsked() }
    }

    suspend fun me(): Member = members.me().first()
}
