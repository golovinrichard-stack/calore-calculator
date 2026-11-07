package com.calore.tracker.ui.kitchen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import com.calore.tracker.R
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PantryRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.RatingRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.ShopRepository
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.PantryItem
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.mealTarget
import com.calore.tracker.domain.plan.Planner
import com.calore.tracker.domain.plan.PlannerMember
import com.calore.tracker.domain.plan.PlannerRecipe
import com.calore.tracker.domain.plan.UseItUp
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.rememberMediumDateFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.TextStyle

/** One pantry item that goes off soon, with the recipes that use it. */
data class UseItUpEntry(val item: PantryItem, val recipes: List<PlannerRecipe>)

data class UseItUpUiState(val loaded: Boolean = false, val entries: List<UseItUpEntry> = emptyList(), val today: LocalDate = LocalDate.MIN)

/** "Use it up" (M6.4). */
class UseItUpViewModel(
    pantry: PantryRepository,
    private val recipes: RecipeRepository,
    private val plans: PlanRepository,
    private val members: MemberRepository,
    private val ratings: RatingRepository,
    shops: ShopRepository,
    private val clock: Clock,
) : ViewModel() {

    private val plannerRecipes = MutableStateFlow<List<PlannerRecipe>>(emptyList())

    init {
        viewModelScope.launch { plannerRecipes.value = recipes.forPlanner(shops.foodPrices().first()) }
    }

    val uiState: StateFlow<UseItUpUiState> = combine(pantry.available(), plannerRecipes) { entries, all ->
        val today = LocalDate.now(clock)
        UseItUpUiState(
            loaded = true,
            entries = UseItUp.expiring(entries.map { it.item }, today).map { item -> UseItUpEntry(item, UseItUp.recipesUsing(item.foodUuid!!, all)) },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UseItUpUiState())

    private val addedChannel = Channel<Int>(Channel.BUFFERED)

    /** How many people a recipe was planned for. */
    val added: Flow<Int> = addedChannel.receiveAsFlow()

    /**
     * **Add to plan**: the recipe cooked fresh on [date] for [meal], for me and everyone I cook for who
     * eats that meal and may eat the recipe (A15 filters). I always get it: I chose it.
     */
    fun addToPlan(recipe: PlannerRecipe, date: LocalDate, meal: MealType) {
        viewModelScope.launch {
            val me = members.me().first()
            val rated = ratings.all()
            val people = listOf(me) + members.managed().first().filter { person ->
                person.mealTarget(meal) != null && Planner.allowedForPerson(PlannerMember(person, rated[person.id].orEmpty()), recipe)
            }
            val planId = plans.planFor(date)
            people.forEach { person -> plans.setSlot(planId, date, meal, person.id, SlotKind.COOK_FRESH, recipe.id, person.mealTarget(meal)) }
            addedChannel.send(people.size)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UseItUpScreen(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    viewModel: UseItUpViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = rememberMediumDateFormatter()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var adding by remember { mutableStateOf<PlannerRecipe?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.added.collect { snackbarHostState.showSnackbar(resources.getString(R.string.use_it_up_added)) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.use_it_up_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (state.loaded && state.entries.isEmpty()) {
                item(key = "empty") { Text(stringResource(R.string.use_it_up_empty), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            state.entries.forEach { entry ->
                item(key = "h_${entry.item.id}") {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                        Text(entry.item.name, style = MaterialTheme.typography.titleMedium)
                        entry.item.effectiveBestBefore?.let {
                            Text(stringResource(R.string.pantry_best_before, it.format(formatter)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (entry.recipes.isEmpty()) {
                    item(key = "n_${entry.item.id}") {
                        Text(stringResource(R.string.use_it_up_no_recipes), modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(entry.recipes.take(MAX_RECIPES), key = { "${entry.item.id}_${it.id}" }) { recipe ->
                    ListItem(
                        headlineContent = { Text(recipe.recipe.displayName()) },
                        trailingContent = { TextButton(onClick = { adding = recipe }) { Text(stringResource(R.string.use_it_up_add)) } },
                        modifier = Modifier.clickable { onOpenRecipe(recipe.id) },
                    )
                }
            }
        }
    }
    adding?.let { recipe ->
        AddToPlanDialog(
            recipe = recipe,
            today = state.today,
            onAdd = { date, meal ->
                adding = null
                viewModel.addToPlan(recipe, date, meal)
            },
            onDismiss = { adding = null },
        )
    }
}

private const val MAX_RECIPES = 5

/** Choose the day (from today, while the food is still good) and the meal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddToPlanDialog(recipe: PlannerRecipe, today: LocalDate, onAdd: (LocalDate, MealType) -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var day by remember { mutableStateOf(today) }
    var meal by remember { mutableStateOf(recipe.meals.minByOrNull { CookingSessions.MEAL_ORDER.indexOf(it) } ?: MealType.DINNER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.recipe.displayName()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0L..3L).map { today.plusDays(it) }.forEach { date ->
                        FilterChip(selected = day == date, onClick = { day = date }, label = { Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CookingSessions.MEAL_ORDER.forEach { option ->
                        FilterChip(selected = meal == option, onClick = { meal = option }, label = { Text(stringResource(option.labelRes())) })
                    }
                }
                Text(stringResource(R.string.use_it_up_add_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(day, meal) }) { Text(stringResource(R.string.use_it_up_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
