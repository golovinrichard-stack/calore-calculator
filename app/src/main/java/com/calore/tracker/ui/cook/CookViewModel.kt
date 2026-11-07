package com.calore.tracker.ui.cook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.BoxSpec
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.domain.Batch
import com.calore.tracker.domain.Batches
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.BoxFilling
import com.calore.tracker.domain.BoxSplitter
import com.calore.tracker.domain.FillMode
import com.calore.tracker.domain.FillResult
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.Freshness
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.MealTarget
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.PersonMeal
import com.calore.tracker.domain.PlanCooks
import com.calore.tracker.domain.PlannedCook
import com.calore.tracker.domain.Pot
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.Recipes
import com.calore.tracker.domain.mealTarget
import com.calore.tracker.domain.newId
import com.calore.tracker.domain.parseIsoDate
import com.calore.tracker.domain.parseDecimalIn
import com.calore.tracker.domain.parsePortionGrams
import com.calore.tracker.domain.parseWholeIn
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.ui.navigation.Routes
import com.calore.tracker.ui.recipes.FoodSearch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class CookStep { AMOUNT, INGREDIENTS, COOK, WEIGH, FILL }

/** One recipe (or a single food) being cooked (M4.3). */
data class CookItem(
    val id: String,
    val recipe: RecipeDetails? = null,
    val food: Food? = null,
    /** Servings; the target cooked grams with [byWeight]; the raw grams of a single food. */
    val amountText: String,
    val byWeight: Boolean = false,
    /** Per recipe ingredient, what really goes in (M4.3 step 3). */
    val gramsTexts: List<String> = emptyList(),
    val potId: String? = null,
    /** The full pot with [potId], otherwise the food alone. */
    val weightText: String = "",
    val batch: Batch? = null,
    /** Cooked for the week plan (M5.4): the slots its boxes are for. */
    val planned: PlannedCook? = null,
) {
    val name: String get() = recipe?.recipe?.displayName() ?: food?.displayName().orEmpty()
    val fridgeDays: Int get() = recipe?.recipe?.fridgeDays ?: Recipe.DEFAULT_FRIDGE_DAYS

    /** How much the saved recipe is multiplied by; null while the amount is invalid. */
    fun scale(): Double? {
        val details = recipe ?: return null
        return if (byWeight) {
            val raw = details.ingredients.sumOf { it.grams }
            parsePortionGrams(amountText)?.let { it / Batches.estimatedCookedGrams(raw, details.recipe.learnedYield) }
        } else {
            parseDecimalIn(amountText, 0.1..200.0)?.let { Recipes.scaleFactor(details.recipe.servings, it) }
        }
    }

    /** What went in, with valid grams only. */
    fun ingredients(): List<Pair<Food, Double>> {
        val details = recipe
        if (details == null) {
            val grams = parsePortionGrams(amountText) ?: return emptyList()
            return listOfNotNull(food?.let { it to grams })
        }
        return details.ingredients.mapIndexedNotNull { i, ingredient ->
            val food = details.foods[ingredient.foodUuid] ?: return@mapIndexedNotNull null
            val grams = gramsTexts.getOrNull(i)?.let { com.calore.tracker.domain.parseDecimal(it) }?.takeIf { it > 0 } ?: return@mapIndexedNotNull null
            food to grams
        }
    }

    val rawGrams: Double get() = ingredients().sumOf { it.second }

    /** The weighed cooked grams (A9), or null while the weight is invalid. */
    fun cookedGrams(pots: List<Pot>): Double? {
        val typed = com.calore.tracker.domain.parseDecimal(weightText) ?: return null
        val pot = pots.firstOrNull { it.id == potId }
        return if (pot != null) Batches.netWeight(typed, pot.emptyGrams, rawGrams) else Batches.checkCookedGrams(typed, rawGrams)
    }
}

/** Who gets boxes in "by person" and mixed filling: a meal and how many boxes. */
data class PersonChoice(val meal: MealType, val count: Int)

data class FillSettings(
    val mode: FillMode = FillMode.BY_CALORIES,
    val countText: String = "4",
    val targetKcalText: String = "",
    val people: Map<String, PersonChoice> = emptyMap(),
    val addLeftoverBox: Boolean = true,
    val mixed: Boolean = false,
    val fixedTexts: Map<String, String> = emptyMap(),
    val planStart: LocalDate? = null,
    val planMeal: MealType? = null,
    /** Recipes cooked for the plan fill one box per planned slot (M5.4). */
    val usePlan: Boolean = true,
)

/** A box in the preview, with its future code and where it goes. */
data class BoxPreview(val code: String, val box: com.calore.tracker.domain.PlannedBox, val toFreezer: Boolean)

data class CookUiState(
    val step: CookStep = CookStep.AMOUNT,
    val items: List<CookItem> = emptyList(),
    val pots: List<Pot> = emptyList(),
    val people: List<Member> = emptyList(),
    val allRecipes: List<RecipeDetails> = emptyList(),
    val fill: FillSettings = FillSettings(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val activeCodes: Set<String> = emptySet(),
    /** Prefixes of active boxes and their recipes, for two-letter codes (A10). */
    val prefixOwners: Map<String, String?> = emptyMap(),
    val today: LocalDate = LocalDate.MIN,
) {
    val batches: List<Batch> get() = items.mapNotNull { it.batch }

    /** One fill result per batch, or one for all batches when mixed. */
    fun fillResults(): List<Pair<CookItem?, FillResult>> {
        val targets = people.flatMap { member ->
            val choice = fill.people[member.id] ?: return@flatMap emptyList()
            val target = member.mealTarget(choice.meal) ?: return@flatMap emptyList()
            List(choice.count) { PersonMeal(member.id, choice.meal, target) }
        }
        if (fill.mixed && batches.size > 1) {
            val fixed = fill.fixedTexts.mapNotNull { (id, text) -> parsePortionGrams(text)?.let { id to it } }.toMap()
            return listOf(null to BoxFilling.mixed(batches, fixed, targets))
        }
        val me = people.firstOrNull()?.id
        return items.mapNotNull { item ->
            val batch = item.batch ?: return@mapNotNull null
            val planned = item.planned
            if (fill.usePlan && planned != null) return@mapNotNull item to fromPlan(batch, planned, me)
            val result = when (fill.mode) {
                FillMode.EQUAL -> parseWholeIn(fill.countText, 1..50)?.let { BoxFilling.singleBatch(batch, FillMode.EQUAL, count = it, owner = me) }
                FillMode.BY_CALORIES -> parseDecimalIn(fill.targetKcalText, 50.0..3000.0)?.let {
                    BoxFilling.singleBatch(batch, FillMode.BY_CALORIES, targetKcal = it, owner = me, addLeftoverBox = fill.addLeftoverBox)
                }
                FillMode.BY_PERSON -> targets.takeIf { it.isNotEmpty() }?.let {
                    BoxFilling.singleBatch(batch, FillMode.BY_PERSON, people = it, owner = me, addLeftoverBox = fill.addLeftoverBox)
                }
            } ?: return@mapNotNull null
            item to result
        }
    }
}

/** One box per planned slot, sized to the slot's target with the weighed density (M5.4). */
private fun fromPlan(batch: Batch, planned: PlannedCook, me: String?): FillResult {
    val perServing = batch.per100g.kcal * batch.cookedGrams / 100 / planned.portions.coerceAtLeast(1)
    val people = planned.slots.map { slot -> PersonMeal(slot.memberId, slot.meal, slot.target ?: MealTarget(perServing, 0.0, 0.0, 0.0)) }
    val result = BoxFilling.singleBatch(batch, FillMode.BY_PERSON, people = people, owner = me)
    return result.copy(
        boxes = result.boxes.mapIndexed { i, box ->
            planned.slots.getOrNull(i)?.let { slot -> box.copy(plannedDate = slot.date, plannedMeal = slot.meal, planSlotId = slot.id) } ?: box
        },
    )
}

/** The cook flow (M4.3) and filling boxes (M4.5), also straight from the week plan (M5.4). */
class CookViewModel(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val cooking: CookingRepository,
    private val members: MemberRepository,
    foods: FoodRepository,
    private val clock: Clock,
    private val plans: PlanRepository? = null,
) : ViewModel() {

    private val state = MutableStateFlow(CookUiState(today = LocalDate.now(clock)))

    val uiState: StateFlow<CookUiState> = combine(state, cooking.pots(), members.me(), members.managed(), recipes.observeAll()) { s, pots, me, managed, all ->
        s.copy(pots = pots, people = listOf(me) + managed, allRecipes = all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    val picker = FoodSearch(foods, viewModelScope)

    private val doneChannel = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    init {
        savedStateHandle.get<String>(Routes.ARG_RECIPE_ID)?.takeIf { it.isNotBlank() }?.let(::addRecipe)
        parseIsoDate(savedStateHandle.get<String>(Routes.ARG_PLAN_DATE))?.let(::addPlannedCooks)
        viewModelScope.launch {
            val me = members.me().first()
            val lunch = me.mealTarget(MealType.LUNCH)
            state.update { s ->
                s.copy(
                    fill = s.fill.copy(
                        targetKcalText = lunch?.kcal?.let { formatEditable(Math.round(it).toDouble()) }.orEmpty(),
                        people = if (lunch != null) mapOf(me.id to PersonChoice(MealType.LUNCH, 4)) else emptyMap(),
                    ),
                )
            }
        }
    }

    fun addRecipe(id: String) {
        viewModelScope.launch {
            val details = recipes.details(id) ?: return@launch
            state.update { it.copy(items = it.items + CookItem(id = newId(), recipe = details, amountText = details.recipe.servings.toString())) }
        }
    }

    /** M5.4: everything the plan cooks on [date], by target cooked weight, linked to its slots. */
    private fun addPlannedCooks(date: LocalDate) {
        val plans = plans ?: return
        viewModelScope.launch {
            val me = members.me().first()
            // A cooking day can cover meals of the next week too (Sunday for Monday to Wednesday).
            val slots = plans.slots(plans.planFor(date)).first() + plans.slots(plans.planFor(date.plusWeeks(1))).first()
            val all = recipes.observeAll().first().associateBy { it.recipe.id }
            val cooks = PlanCooks.compute(
                slots,
                all.mapValues { (_, d) -> d.recipe to d.nutrition },
                CookingSessions.days(me.cookSessionsPerWeek, me.cookDays),
                me.preppedMeals,
            ).filter { it.date == date }
            val items = cooks.mapNotNull { cook ->
                val details = recipes.details(cook.recipeId) ?: return@mapNotNull null
                CookItem(
                    id = newId(),
                    recipe = details,
                    amountText = formatEditable(Math.round(cook.targetCookedGrams).toDouble()),
                    byWeight = true,
                    planned = cook,
                )
            }
            state.update { it.copy(items = it.items + items) }
        }
    }

    fun addFood(food: Food) = state.update { it.copy(items = it.items + CookItem(id = newId(), food = food, amountText = "")) }

    fun removeItem(id: String) = state.update { it.copy(items = it.items.filterNot { item -> item.id == id }) }

    private fun editItem(id: String, transform: (CookItem) -> CookItem) =
        state.update { it.copy(items = it.items.map { item -> if (item.id == id) transform(item) else item }) }

    fun setAmount(id: String, text: String) = editItem(id) { it.copy(amountText = text) }
    fun setByWeight(id: String, byWeight: Boolean) = editItem(id) { it.copy(byWeight = byWeight, amountText = "") }
    fun setIngredientGrams(id: String, index: Int, text: String) =
        editItem(id) { it.copy(gramsTexts = it.gramsTexts.mapIndexed { i, old -> if (i == index) text else old }) }
    fun setPot(id: String, potId: String?) = editItem(id) { it.copy(potId = potId) }
    fun setWeight(id: String, text: String) = editItem(id) { it.copy(weightText = text) }

    fun editFill(transform: (FillSettings) -> FillSettings) = state.update { it.copy(fill = transform(it.fill)) }

    /** Moves on when the current step is complete; otherwise shows what is missing. */
    fun next() {
        val s = uiState.value
        when (s.step) {
            CookStep.AMOUNT -> {
                if (s.items.isEmpty() || s.items.any { it.recipe != null && it.scale() == null || it.recipe == null && it.ingredients().isEmpty() }) {
                    return state.update { it.copy(showErrors = true) }
                }
                // Scale every recipe; the cook can then change the amounts to what was really used.
                state.update { current ->
                    current.copy(
                        step = CookStep.INGREDIENTS,
                        showErrors = false,
                        items = current.items.map { item ->
                            val factor = item.scale() ?: return@map item
                            item.copy(gramsTexts = item.recipe!!.ingredients.map { formatEditable(Math.round(it.grams * factor).toDouble()) })
                        },
                    )
                }
            }
            CookStep.INGREDIENTS -> state.update { it.copy(step = CookStep.COOK) }
            CookStep.COOK -> state.update { it.copy(step = CookStep.WEIGH) }
            CookStep.WEIGH -> saveBatches(s)
            CookStep.FILL -> fillBoxes(s)
        }
    }

    fun back() = state.update { s ->
        val previous = when (s.step) {
            CookStep.AMOUNT, CookStep.FILL -> s.step // batches are saved once filling starts
            CookStep.INGREDIENTS -> CookStep.AMOUNT
            CookStep.COOK -> CookStep.INGREDIENTS
            CookStep.WEIGH -> CookStep.COOK
        }
        s.copy(step = previous, showErrors = false)
    }

    private fun saveBatches(s: CookUiState) {
        if (s.saving) return
        if (s.items.any { it.cookedGrams(s.pots) == null }) return state.update { it.copy(showErrors = true) }
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val me = members.me().first()
            val saved = s.items.map { item ->
                val batch = item.batch ?: cooking.saveBatch(
                    recipeId = item.recipe?.recipe?.id,
                    recipeLearnedYield = item.recipe?.recipe?.learnedYield,
                    name = item.name,
                    ingredients = item.ingredients(),
                    cookedGrams = item.cookedGrams(s.pots)!!,
                    cookedByMemberId = me.id.takeIf { it.isNotEmpty() },
                )
                item.copy(batch = batch)
            }
            val codes = cooking.activeCodes()
            val owners = cooking.activePrefixOwners()
            state.update { it.copy(items = saved, step = CookStep.FILL, saving = false, showErrors = false, activeCodes = codes, prefixOwners = owners) }
        }
    }

    /** The boxes as they will be saved, with codes and fridge or freezer (A10, A13). */
    fun previews(s: CookUiState): List<BoxPreview> {
        var taken = s.activeCodes
        return s.fillResults().flatMap { (item, result) ->
            val prefix = prefixOf(item, s)
            val fromThePlan = item?.planned != null && s.fill.usePlan
            val boxes = if (fromThePlan) result.boxes else s.fill.planStart?.let { BoxFilling.planDays(result.boxes, it, s.fill.planMeal) } ?: result.boxes
            val codes = BoxSplitter.nextCodes(prefix, boxes.size, taken)
            taken = taken + codes
            boxes.mapIndexed { i, box ->
                val fridgeDays = box.parts.minOf { (batchId, _) -> s.items.firstOrNull { it.batch?.id == batchId }?.fridgeDays ?: Recipe.DEFAULT_FRIDGE_DAYS }
                BoxPreview(codes[i], box, Freshness.goesToFreezer(box.plannedDate, Freshness.eatBy(s.today, fridgeDays)))
            }
        }
    }

    /** The recipe's short code, or the food's first letter; two letters when another recipe uses it (A10). */
    private fun prefixOf(item: CookItem?, s: CookUiState): String {
        val source = item ?: s.items.firstOrNull() ?: return "B"
        val short = source.recipe?.recipe?.shortCode ?: source.name.firstOrNull { it.isLetter() }?.uppercase() ?: "B"
        return BoxSplitter.prefixFor(source.recipe?.recipe?.id, short, source.name, s.prefixOwners)
    }

    private fun fillBoxes(s: CookUiState) {
        if (s.saving) return
        val results = s.fillResults()
        if (results.isEmpty() || results.all { it.second.boxes.isEmpty() }) return state.update { it.copy(showErrors = true) }
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val fridgeDays = s.items.mapNotNull { item -> item.batch?.let { it.id to item.fridgeDays } }.toMap()
            for ((item, result) in results) {
                val prefix = prefixOf(item, s)
                val planned = item?.planned?.takeIf { s.fill.usePlan }
                val boxes = if (planned == null) s.fill.planStart?.let { BoxFilling.planDays(result.boxes, it, s.fill.planMeal) } ?: result.boxes else result.boxes
                val saved = cooking.fillBoxes(prefix, boxes.map { BoxSpec(it.parts, it.ownerMemberId, it.plannedDate, it.plannedMeal, it.planSlotId) }, fridgeDays)
                val batch = item?.batch
                if (planned != null && batch != null) {
                    plans?.linkBoxes(saved.mapNotNull { box -> box.planSlotId?.let { it to box.id } })
                    planned.slots.groupBy { it.planId }.forEach { (planId, own) -> plans?.saveCooked(planId, planned.copy(slots = own), batch.id) }
                }
            }
            state.update { it.copy(saving = false) }
            doneChannel.send(Unit)
        }
    }
}
