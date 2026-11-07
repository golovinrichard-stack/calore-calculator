package com.calore.tracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.calore.tracker.data.repository.RatingRepository
import com.calore.tracker.ui.rating.RatingPrompt
import com.calore.tracker.ui.rating.askAbout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.data.repository.EatenBox
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.domain.BoxView
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.Freshness
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.Storage
import com.calore.tracker.domain.mealTarget
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.labelRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** A planned meal of the day, with its box if one was filled. */
data class PlannedMeal(val slot: PlanSlot, val recipeName: String?, val box: BoxView?)

data class TodayPlanState(val meals: List<PlannedMeal> = emptyList(), val others: List<Member> = emptyList(), val today: LocalDate = LocalDate.MIN)

/** Today's planned meals (M5.11), and skipping or moving a planned box. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayPlanViewModel(
    private val plans: PlanRepository,
    private val cooking: CookingRepository,
    private val members: MemberRepository,
    recipes: RecipeRepository,
    private val clock: Clock,
    private val ratings: RatingRepository? = null,
    private val ratingPrompt: RatingPrompt? = null,
) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now(clock))

    val state: StateFlow<TodayPlanState> = combine(
        date.flatMapLatest { plans.slotsBetween(it, it) },
        cooking.activeBoxes(),
        members.me(),
        members.managed(),
        recipes.observeAll(),
    ) { slots, boxes, me, managed, all ->
        val names = all.associate { it.recipe.id to it.recipe.displayName() }
        val boxById = boxes.associateBy { it.box.id }
        TodayPlanState(
            meals = slots.filter { it.memberId == me.id && it.kind != SlotKind.FREE }
                .sortedBy { CookingSessions.MEAL_ORDER.indexOf(it.meal) }
                .map { PlannedMeal(it, it.recipeId?.let(names::get), it.boxId?.let(boxById::get)) },
            others = managed,
            today = LocalDate.now(clock),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayPlanState())

    private val eatenChannel = Channel<Pair<EatenBox, String>>(Channel.BUFFERED)
    val eaten = eatenChannel.receiveAsFlow()

    fun show(day: LocalDate) {
        date.value = day
    }

    fun eat(meal: PlannedMeal) {
        val box = meal.box ?: return
        viewModelScope.launch {
            val me = members.me().first()
            val ownerIsMe = box.box.ownerMemberId == null || box.box.ownerMemberId == me.id
            eatenChannel.send(cooking.eat(box, null, meal.slot.meal, meal.slot.date, ownerIsMe) to box.box.code)
            if (ratings != null) ratingPrompt?.askAbout(box, box.box.ownerMemberId ?: me.id, ratings)
        }
    }

    fun undo(eaten: EatenBox) {
        viewModelScope.launch { cooking.undoEat(eaten) }
    }

    /** True when the box can wait in the fridge until [day] (A12); frozen boxes can always move. */
    fun canMoveTo(meal: PlannedMeal, day: LocalDate): Boolean {
        val box = meal.box?.box ?: return false
        return box.storage == Storage.FREEZER || !day.isAfter(box.eatByDate)
    }

    /** [eatOut]: M6.5, the meal becomes "eat out" after its box is taken care of. */
    fun move(meal: PlannedMeal, day: LocalDate, eatOut: Boolean = false) {
        val box = meal.box ?: return
        viewModelScope.launch {
            cooking.move(box.box, day, meal.slot.meal)
            freeOrEatOut(meal, eatOut)
            plans.planBox(day, meal.slot.meal, meal.slot.memberId, meal.slot.recipeId, meal.slot.target, box.box.id)
        }
    }

    fun freeze(meal: PlannedMeal, eatOut: Boolean = false) {
        val box = meal.box ?: return
        viewModelScope.launch {
            if (cooking.freeze(box.box)) freeOrEatOut(meal, eatOut)
        }
    }

    fun give(meal: PlannedMeal, person: Member, eatOut: Boolean = false) {
        val box = meal.box ?: return
        viewModelScope.launch {
            cooking.giveTo(box.box, person.id)
            freeOrEatOut(meal, eatOut)
            plans.planBox(meal.slot.date, meal.slot.meal, person.id, meal.slot.recipeId, person.mealTarget(meal.slot.meal), box.box.id)
        }
    }

    /** M6.5 "I'm eating out": the meal is eaten out; a box stays in the fridge until moved, frozen or given. */
    fun eatOut(meal: PlannedMeal) {
        viewModelScope.launch { plans.eatOut(meal.slot.id) }
    }

    private suspend fun freeOrEatOut(meal: PlannedMeal, eatOut: Boolean) {
        if (eatOut) plans.eatOut(meal.slot.id) else plans.freeSlot(meal.slot.id)
    }
}

/** The day's planned meals: boxes with Ate it, cook-fresh with the recipe, eat out with Quick add (M5.11). */
@Composable
fun PlannedTodayCard(
    date: LocalDate,
    snackbarHostState: SnackbarHostState,
    onOpenRecipe: (String) -> Unit,
    onQuickAdd: (com.calore.tracker.domain.MealType) -> Unit,
    viewModel: TodayPlanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    LaunchedEffect(date) { viewModel.show(date) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    var skipping by remember { mutableStateOf<PlannedMeal?>(null) }
    var eatingOut by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.eaten.collect { (eaten, code) ->
            val result = snackbarHostState.showSnackbar(
                resources.getString(R.string.box_logged, code),
                actionLabel = resources.getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(eaten)
        }
    }
    if (state.meals.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.today_planned), style = MaterialTheme.typography.titleMedium)
            state.meals.forEach { meal ->
                PlannedRow(
                    meal,
                    onEat = { viewModel.eat(meal) },
                    onSkip = {
                        eatingOut = false
                        skipping = meal
                    },
                    onEatOut = {
                        // A filled box needs a new plan (M6.5); otherwise the meal just becomes "eat out".
                        if (meal.box != null) {
                            eatingOut = true
                            skipping = meal
                        } else {
                            viewModel.eatOut(meal)
                        }
                    },
                    onOpenRecipe = onOpenRecipe,
                    onQuickAdd = onQuickAdd,
                )
            }
        }
    }
    skipping?.let { meal -> SkipDialog(meal, state, viewModel, eatOut = eatingOut, onDone = { skipping = null }) }
}

@Composable
private fun PlannedRow(
    meal: PlannedMeal,
    onEat: () -> Unit,
    onSkip: () -> Unit,
    onEatOut: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onQuickAdd: (com.calore.tracker.domain.MealType) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val mealName = stringResource(meal.slot.meal.labelRes())
    val name = meal.recipeName.orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(mealName, style = MaterialTheme.typography.labelLarge)
            Text(
                when (meal.slot.kind) {
                    SlotKind.BOX -> meal.box?.let { "${it.box.code} · ${it.name}" } ?: stringResource(R.string.today_box_not_filled, name)
                    SlotKind.COOK_FRESH -> stringResource(R.string.today_cook, name)
                    SlotKind.EAT_OUT -> stringResource(R.string.slot_eat_out)
                    SlotKind.FREE -> stringResource(R.string.slot_free)
                },
            )
        }
        when {
            meal.slot.done -> Text(stringResource(R.string.box_ate_it), color = MaterialTheme.colorScheme.primary)
            meal.slot.kind == SlotKind.BOX && meal.box != null -> TextButton(onClick = onEat) { Text(stringResource(R.string.box_ate_it)) }
            meal.slot.kind == SlotKind.COOK_FRESH && meal.slot.recipeId != null -> TextButton(onClick = { onOpenRecipe(meal.slot.recipeId) }) { Text(stringResource(R.string.today_recipe)) }
            meal.slot.kind == SlotKind.EAT_OUT -> TextButton(onClick = { onQuickAdd(meal.slot.meal) }) { Text(stringResource(R.string.quick_add_title)) }
        }
        if (!meal.slot.done && (meal.slot.kind == SlotKind.BOX || meal.slot.kind == SlotKind.COOK_FRESH)) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.today_eat_out)) }, onClick = {
                        menu = false
                        onEatOut()
                    })
                    if (meal.box != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.today_skip)) }, onClick = {
                            menu = false
                            onSkip()
                        })
                    }
                }
            }
        }
    }
}

/** Skip or move a planned box (M5.11): another day if the eat-by date allows, otherwise freeze it or give it away. */
@Composable
private fun SkipDialog(meal: PlannedMeal, state: TodayPlanState, viewModel: TodayPlanViewModel, eatOut: Boolean, onDone: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var tooLate by remember { mutableStateOf(false) }
    val box = meal.box?.box
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(if (eatOut) R.string.today_eat_out_box else R.string.today_skip)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.skip_move)) }
                if (tooLate) Text(stringResource(R.string.skip_too_late), color = MaterialTheme.colorScheme.error)
                if (box != null && box.storage == Storage.FRIDGE && Freshness.canFreeze(box.eatByDate, state.today)) {
                    OutlinedButton(onClick = {
                        viewModel.freeze(meal, eatOut)
                        onDone()
                    }) { Text(stringResource(R.string.box_freeze)) }
                }
                state.others.forEach { person ->
                    OutlinedButton(onClick = {
                        viewModel.give(meal, person, eatOut)
                        onDone()
                    }) { Text(stringResource(R.string.skip_give, person.name)) }
                }
                if (eatOut) {
                    OutlinedButton(onClick = {
                        viewModel.eatOut(meal)
                        onDone()
                    }) { Text(stringResource(R.string.today_eat_out_keep)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) {
        DatePickerModal(
            initial = meal.slot.date.plusDays(1),
            onDismiss = { picking = false },
            onConfirm = { day ->
                picking = false
                if (viewModel.canMoveTo(meal, day)) {
                    viewModel.move(meal, day, eatOut)
                    onDone()
                } else {
                    tooLate = true
                }
            },
        )
    }
}
