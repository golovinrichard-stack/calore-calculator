package com.calore.tracker.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Cuisines
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.cuisineLabelRes
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.varietyLabelRes
import java.time.DayOfWeek
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { saved ->
            val message = when (saved) {
                ProfileSaved.Saved -> resources.getString(R.string.profile_saved)
                is ProfileSaved.NewTargets -> resources.getString(R.string.profile_saved_targets, formatKcal(saved.kcal.toDouble()))
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.loaded) { Text(stringResource(R.string.save)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loaded) {
            ProfileContent(state = state, viewModel = viewModel, modifier = Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(state: ProfileUiState, viewModel: ProfileViewModel, modifier: Modifier) {
    val member = state.member
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GroupTitle(R.string.profile_group_body)
        BodyBasicsSection(form = state.target, today = viewModel.today, onChange = viewModel::updateTarget, showWeight = false)
        ActivitySection(form = state.target, onChange = viewModel::updateTarget)

        GroupTitle(R.string.profile_group_goal)
        GoalSection(form = state.target, onChange = viewModel::updateTarget)
        MacroSection(form = state.target, onChange = viewModel::updateTarget)
        SectionHeader(stringResource(R.string.meal_split_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { meal ->
                NumberField(
                    value = state.mealSplit[meal].orEmpty(),
                    onValueChange = { text -> viewModel.updateState { it.copy(mealSplit = it.mealSplit + (meal to text)) } },
                    label = stringResource(meal.labelRes()),
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = if (state.attemptedSave && state.mealSplitValues == null) {
                stringResource(R.string.error_meal_split, state.mealSplitTotal)
            } else {
                stringResource(R.string.meal_split_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.attemptedSave && state.mealSplitValues == null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        GroupTitle(R.string.profile_group_food)
        Text(
            stringResource(R.string.profile_later_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionHeader(stringResource(R.string.diet_label))
        Column(modifier = Modifier.selectableGroup()) {
            Diet.entries.forEach { diet ->
                ChoiceRow(
                    title = stringResource(diet.labelRes()),
                    description = null,
                    selected = member.diet == diet,
                    onSelect = { viewModel.updateMember { it.copy(diet = diet) } },
                )
            }
        }
        ChipGroup(
            title = R.string.allergens_label,
            options = Allergen.entries,
            selected = member.allergens,
            label = { stringResource(it.labelRes()) },
            onToggle = { allergen -> viewModel.updateMember { it.copy(allergens = it.allergens.toggle(allergen)) } },
        )
        ChipGroup(
            title = R.string.liked_label,
            options = ShopSection.FOOD_KINDS.map { it.name },
            selected = member.likedFoods.toSet(),
            label = { stringResource(ShopSection.valueOf(it).labelRes()) },
            onToggle = { key ->
                viewModel.updateMember { it.copy(likedFoods = it.likedFoods.toggle(key), dislikedFoods = it.dislikedFoods - key) }
            },
        )
        ChipGroup(
            title = R.string.disliked_label,
            options = ShopSection.FOOD_KINDS.map { it.name },
            selected = member.dislikedFoods.toSet(),
            label = { stringResource(ShopSection.valueOf(it).labelRes()) },
            onToggle = { key ->
                viewModel.updateMember { it.copy(dislikedFoods = it.dislikedFoods.toggle(key), likedFoods = it.likedFoods - key) }
            },
        )
        ChipGroup(
            title = R.string.cuisines_label,
            options = Cuisines.ALL,
            selected = member.cuisines.toSet(),
            label = { key -> cuisineLabelRes(key)?.let { stringResource(it) } ?: key },
            onToggle = { key -> viewModel.updateMember { it.copy(cuisines = it.cuisines.toggle(key)) } },
        )

        GroupTitle(R.string.profile_group_cooking)
        SectionHeader(stringResource(R.string.cook_sessions_label))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..7).forEach { times ->
                FilterChip(
                    selected = member.cookSessionsPerWeek == times,
                    onClick = { viewModel.updateMember { it.copy(cookSessionsPerWeek = times) } },
                    label = { Text(times.toString()) },
                )
            }
        }
        ChipGroup(
            title = R.string.cook_days_label,
            options = DayOfWeek.entries,
            selected = member.cookDays.toSet(),
            label = { it.getDisplayName(TextStyle.SHORT, locale) },
            onToggle = { day ->
                viewModel.updateMember { m -> m.copy(cookDays = m.cookDays.toggle(day).sortedBy { it.value }) }
            },
            emptyHint = R.string.cook_days_hint,
        )
        NumberField(
            value = state.minutes,
            onValueChange = { text -> viewModel.updateState { it.copy(minutes = text) } },
            label = stringResource(R.string.minutes_label),
            decimal = false,
            errorText = if (state.attemptedSave && state.minutesValue == null) stringResource(R.string.error_minutes) else null,
        )
        ChipGroup(
            title = R.string.prepped_meals_label,
            options = MealType.entries,
            selected = member.preppedMeals,
            label = { stringResource(it.labelRes()) },
            onToggle = { meal -> viewModel.updateMember { it.copy(preppedMeals = it.preppedMeals.toggle(meal)) } },
        )
        ChipGroup(
            title = R.string.equipment_label,
            options = Equipment.entries,
            selected = member.equipment,
            label = { stringResource(it.labelRes()) },
            onToggle = { item -> viewModel.updateMember { it.copy(equipment = it.equipment.toggle(item)) } },
        )
        SectionHeader(stringResource(R.string.variety_label))
        Column(modifier = Modifier.selectableGroup()) {
            (1..3).forEach { level ->
                ChoiceRow(
                    title = stringResource(varietyLabelRes(level)),
                    description = null,
                    selected = member.variety == level,
                    onSelect = { viewModel.updateMember { it.copy(variety = level) } },
                )
            }
        }
        NumberField(
            value = state.budget,
            onValueChange = { text -> viewModel.updateState { it.copy(budget = text) } },
            label = stringResource(R.string.budget_label),
            suffix = stringResource(R.string.unit_czk),
            errorText = if (state.attemptedSave && !state.budgetValid) stringResource(R.string.error_budget) else null,
        )
    }
}

@Composable
private fun GroupTitle(title: Int) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Multiple-choice chips. With nothing chosen it shows "Not set", or [emptyHint]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    title: Int,
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    emptyHint: Int = R.string.not_set,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(title))
        if (selected.isEmpty()) {
            Text(
                stringResource(emptyHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option in selected, onClick = { onToggle(option) }, label = { Text(label(option)) })
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

private fun <T> List<T>.toggle(item: T): List<T> = if (item in this) this - item else this + item
