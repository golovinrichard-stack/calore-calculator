package com.calore.tracker.ui.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.MemberKind
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.newId
import com.calore.tracker.domain.parseWholeIn
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatCount
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.navigation.Routes
import com.calore.tracker.ui.profile.ActivitySection
import com.calore.tracker.ui.profile.BodyBasicsSection
import com.calore.tracker.ui.profile.GoalSection
import com.calore.tracker.ui.profile.MacroSection
import com.calore.tracker.ui.profile.TargetForm
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Colours to tell people's boxes apart. */
val PERSON_COLOURS: List<Int> = listOf(0xFF2E7D32, 0xFF1565C0, 0xFFC62828, 0xFF6A1B9A, 0xFFEF6C00, 0xFF00838F).map { it.toInt() }

class PeopleViewModel(members: MemberRepository) : ViewModel() {
    val people: StateFlow<List<Member>> = members.managed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class PersonUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val member: Member = Member(id = "", kind = MemberKind.MANAGED, name = "", colour = PERSON_COLOURS[1]),
    val target: TargetForm = TargetForm(),
    val calculate: Boolean = true,
    val typed: List<String> = listOf("", "", "", ""),
    val mealSplit: Map<MealType, String> = emptyMap(),
    val showErrors: Boolean = false,
)

/** A person I cook for (M4.8): name, colour, targets (calculated or typed), meal split and prepped meals. */
class PersonViewModel(savedStateHandle: SavedStateHandle, private val members: MemberRepository, private val clock: Clock) : ViewModel() {
    private val id: String? = savedStateHandle.get<String>(Routes.ARG_MEMBER_ID)?.takeIf { it.isNotBlank() }
    private val state = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = state.asStateFlow()
    private val doneChannel = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = doneChannel.receiveAsFlow()
    val today: LocalDate get() = LocalDate.now(clock)

    init {
        viewModelScope.launch {
            val existing = id?.let { members.managedMember(it) }
            val member = existing ?: state.value.member.copy(id = newId(), createdAt = clock.millis())
            val goals = member.targets
            state.value = PersonUiState(
                loaded = true,
                isNew = existing == null,
                member = member,
                target = TargetForm.from(member, member.weightKg, null),
                calculate = existing == null || member.sex != null,
                typed = goals?.let { listOf(it.kcal, it.proteinG, it.carbsG, it.fatG).map(Int::toString) } ?: listOf("", "", "", ""),
                mealSplit = MealType.entries.associateWith { (member.mealSplit[it] ?: 0).toString() },
            )
        }
    }

    fun update(transform: (PersonUiState) -> PersonUiState) = state.update(transform)

    private fun typedGoals(s: PersonUiState): NutritionGoals? {
        val (kcal, p, c, f) = s.typed.map { parseWholeIn(it, 0..10_000) }
        return if (kcal != null && kcal >= 500 && p != null && c != null && f != null) NutritionGoals(kcal, p, c, f) else null
    }

    private fun split(s: PersonUiState): Map<MealType, Int>? {
        val values = s.mealSplit.mapValues { parseWholeIn(it.value, 0..100) ?: return null }
        return values.takeIf { it.values.sum() == 100 }
    }

    /** What is wrong, or nothing: a name, targets, and a meal split of 100 %. */
    fun problems(s: PersonUiState): Set<String> = buildSet {
        if (s.member.name.isBlank()) add("name")
        if (if (s.calculate) s.target.result(today) == null else typedGoals(s) == null) add("targets")
        if (split(s) == null) add("split")
    }

    fun save() {
        val s = state.value
        if (problems(s).isNotEmpty()) return state.update { it.copy(showErrors = true) }
        viewModelScope.launch {
            val withTargets = if (s.calculate) {
                s.target.applyTo(s.member, s.target.result(today)).copy(weightKg = s.target.weightValue)
            } else {
                s.member.copy(targets = typedGoals(s))
            }
            members.save(withTargets.copy(name = s.member.name.trim(), mealSplit = split(s)!!))
            doneChannel.send(Unit)
        }
    }

    fun remove() {
        viewModelScope.launch {
            members.removeManaged(state.value.member)
            doneChannel.send(Unit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(onBack: () -> Unit, onOpen: (id: String?) -> Unit, viewModel: PeopleViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onOpen(null) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.people_add)) })
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (people.isEmpty()) {
                item { Text(stringResource(R.string.people_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(people, key = { it.id }) { person ->
                ListItem(
                    leadingContent = { Box(Modifier.size(24.dp).clip(CircleShape).background(Color(person.colour))) },
                    headlineContent = { Text(person.name) },
                    supportingContent = { Text(stringResource(R.string.person_kcal_per_day, formatCount(person.effectiveTargets.kcal))) },
                    modifier = Modifier.clickable { onOpen(person.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonScreen(onBack: () -> Unit, viewModel: PersonViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.done.collect { onBack() } }
    val problems = if (state.showErrors) viewModel.problems(state) else emptySet()
    val onTarget: ((TargetForm) -> TargetForm) -> Unit = { change -> viewModel.update { it.copy(target = change(it.target)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.person_new else R.string.person_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { TextButton(onClick = viewModel::save, enabled = state.loaded) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.member.name,
                onValueChange = { name -> viewModel.update { it.copy(member = it.member.copy(name = name)) } },
                label = { Text(stringResource(R.string.person_name)) },
                isError = "name" in problems,
                supportingText = if ("name" in problems) ({ Text(stringResource(R.string.error_person_name)) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader(stringResource(R.string.person_colour))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PERSON_COLOURS.forEach { colour ->
                    val selected = state.member.colour == colour
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(colour))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { viewModel.update { it.copy(member = it.member.copy(colour = colour)) } },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.calculate, onClick = { viewModel.update { it.copy(calculate = true) } }, label = { Text(stringResource(R.string.person_targets_calc)) })
                FilterChip(selected = !state.calculate, onClick = { viewModel.update { it.copy(calculate = false) } }, label = { Text(stringResource(R.string.person_targets_typed)) })
            }
            if ("targets" in problems) Text(stringResource(R.string.error_person_targets), color = MaterialTheme.colorScheme.error)
            if (state.calculate) {
                BodyBasicsSection(state.target, viewModel.today, onTarget)
                ActivitySection(state.target, onTarget)
                GoalSection(state.target, onTarget)
                MacroSection(state.target, onTarget)
            } else {
                listOf(R.string.goal_kcal, R.string.nutrient_protein, R.string.nutrient_carbs, R.string.nutrient_fat).forEachIndexed { i, label ->
                    NumberField(
                        value = state.typed[i],
                        onValueChange = { text -> viewModel.update { it.copy(typed = it.typed.mapIndexed { j, old -> if (j == i) text else old }) } },
                        label = stringResource(label),
                        suffix = stringResource(if (i == 0) R.string.unit_kcal else R.string.unit_g),
                        decimal = false,
                    )
                }
            }
            SectionHeader(stringResource(R.string.meal_split_label))
            MealType.entries.forEach { meal ->
                NumberField(
                    value = state.mealSplit[meal].orEmpty(),
                    onValueChange = { text -> viewModel.update { it.copy(mealSplit = it.mealSplit + (meal to text)) } },
                    label = stringResource(meal.labelRes()),
                    suffix = "%",
                    decimal = false,
                )
            }
            if ("split" in problems) {
                val total = state.mealSplit.values.sumOf { parseWholeIn(it, 0..100) ?: 0 }
                Text(stringResource(R.string.error_meal_split, total), color = MaterialTheme.colorScheme.error)
            }
            SectionHeader(stringResource(R.string.prepped_meals_label))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { meal ->
                    val on = meal in state.member.preppedMeals
                    FilterChip(
                        selected = on,
                        onClick = { viewModel.update { it.copy(member = it.member.copy(preppedMeals = if (on) it.member.preppedMeals - meal else it.member.preppedMeals + meal)) } },
                        label = { Text(stringResource(meal.labelRes())) },
                    )
                }
            }
            if (!state.isNew) {
                TextButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.person_remove), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            text = { Text(stringResource(R.string.person_remove_confirm, state.member.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.remove()
                }) { Text(stringResource(R.string.recipe_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
