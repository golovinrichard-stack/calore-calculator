package com.calore.tracker.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.DiaryEntry
import com.calore.tracker.ui.quickadd.QuickAddDialog
import com.calore.tracker.domain.QuickAddDraft
import com.calore.tracker.domain.EntryType
import com.calore.tracker.domain.MealSummary
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.parsePortionGrams
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.components.GoalBar
import com.calore.tracker.ui.components.GoalProgress
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.ui.formatGrams
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.rememberMediumDateFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onAddFood: (LocalDate, MealType) -> Unit,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var changingBirthDate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = viewModel::previousDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_day))
                    }
                },
                title = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(dateLabel(state.date, state.today), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (state.date != state.today) {
                        TextButton(onClick = viewModel::goToToday) { Text(stringResource(R.string.date_today)) }
                    }
                    IconButton(onClick = viewModel::nextDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        TodayContent(
            state = state,
            contentPadding = padding,
            onAddFood = { meal -> onAddFood(state.date, meal) },
            onEntryClick = { editing = it },
            onDismissWhatsNew = viewModel::dismissWhatsNew,
            onConfirmBirthDate = { state.birthDateToConfirm?.let(viewModel::confirmBirthDate) },
            onChangeBirthDate = { changingBirthDate = true },
        )
    }

    val guessedBirthDate = state.birthDateToConfirm
    if (changingBirthDate && guessedBirthDate != null) {
        DatePickerModal(
            initial = guessedBirthDate,
            latest = state.today,
            onDismiss = { changingBirthDate = false },
            onConfirm = { date ->
                changingBirthDate = false
                viewModel.confirmBirthDate(date)
            },
        )
    }

    editing?.takeIf { it.entryType == EntryType.QUICK_ADD }?.let { entry ->
        QuickAddDialog(
            initial = QuickAddDraft.from(entry),
            onSave = { name, nutrients ->
                viewModel.updateQuickAdd(entry, name, nutrients)
                editing = null
            },
            onDismiss = { editing = null },
            onDelete = {
                editing = null
                viewModel.delete(entry)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.entry_removed, entry.displayName(resources)),
                        actionLabel = resources.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
                }
            },
        )
    }
    editing?.takeIf { it.entryType != EntryType.QUICK_ADD }?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { grams ->
                viewModel.changeGrams(entry, grams)
                editing = null
            },
            onDelete = {
                editing = null
                viewModel.delete(entry)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.entry_removed, entry.name),
                        actionLabel = resources.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
                }
            },
        )
    }

    if (showDatePicker) {
        DatePickerModal(
            initial = state.date,
            onDismiss = { showDatePicker = false },
            onConfirm = viewModel::selectDate,
        )
    }
}

/** Stateless body of the diary, separate so it can be previewed and UI-tested without a ViewModel. */
@Composable
fun TodayContent(
    state: TodayUiState,
    contentPadding: PaddingValues,
    onAddFood: (MealType) -> Unit,
    onEntryClick: (DiaryEntry) -> Unit,
    onDismissWhatsNew: () -> Unit = {},
    onConfirmBirthDate: () -> Unit = {},
    onChangeBirthDate: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.whatsNew) {
            item(key = "whats_new") { WhatsNewCard(onDismiss = onDismissWhatsNew) }
        }
        state.birthDateToConfirm?.let { birthDate ->
            item(key = "birth_date") {
                BirthDateCard(birthDate = birthDate, onConfirm = onConfirmBirthDate, onChange = onChangeBirthDate)
            }
        }
        item(key = "summary") {
            if (state.hideNumbers) {
                HiddenNumbersNote()
            } else {
                SummaryCard(totals = state.summary.totals, goals = state.goals)
            }
        }
        items(state.summary.meals, key = { it.meal.name }) { meal ->
            MealCard(
                meal = meal,
                onAdd = { onAddFood(meal.meal) },
                onEntryClick = onEntryClick,
                hideNumbers = state.hideNumbers,
            )
        }
    }
}

@Composable
private fun SummaryCard(totals: Nutrients, goals: NutritionGoals) {
    val remaining = goals.kcal - totals.kcal
    val isOver = remaining < 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatKcal(abs(remaining)),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(if (isOver) R.string.summary_over else R.string.summary_left),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.summary_eaten, formatKcal(totals.kcal)), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.summary_goal, formatKcal(goals.kcal.toDouble())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            GoalBar(value = totals.kcal, goal = goals.kcal, color = MaterialTheme.colorScheme.primary, height = 10.dp)
            MacroProgress(R.string.nutrient_protein, totals.protein, goals.proteinG, MaterialTheme.colorScheme.tertiary)
            MacroProgress(R.string.nutrient_carbs, totals.carbs, goals.carbsG, MaterialTheme.colorScheme.secondary)
            MacroProgress(R.string.nutrient_fat, totals.fat, goals.fatG, MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.summary_fiber_sugar, formatGrams(totals.fiber), formatGrams(totals.sugar)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroProgress(labelRes: Int, grams: Double?, goal: Int, color: Color) {
    val eaten = grams ?: 0.0
    GoalProgress(
        label = stringResource(labelRes),
        valueText = stringResource(R.string.summary_macro, formatGrams(eaten), goal.toString()),
        value = eaten,
        goal = goal,
        color = color,
    )
}

@Composable
private fun MealCard(meal: MealSummary, onAdd: () -> Unit, onEntryClick: (DiaryEntry) -> Unit, hideNumbers: Boolean) {
    val mealName = stringResource(meal.meal.labelRes())
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = mealName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (!hideNumbers) {
                Text(
                    text = stringResource(R.string.value_kcal, formatKcal(meal.totals.kcal)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.meal_add, mealName))
            }
        }
        if (meal.entries.isEmpty()) {
            Text(
                text = stringResource(R.string.meal_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        } else {
            meal.entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                EntryRow(entry = entry, onClick = { onEntryClick(entry) }, hideNumbers = hideNumbers)
            }
        }
    }
}

@Composable
private fun EntryRow(entry: DiaryEntry, onClick: () -> Unit, hideNumbers: Boolean) {
    if (hideNumbers) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(entry.displayName(LocalResources.current), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        )
        return
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(entry.displayName(LocalResources.current), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (entry.entryType == EntryType.QUICK_ADD) {
                Text(stringResource(R.string.entry_subtitle_quick, formatKcal(entry.nutrients.kcal)))
            } else {
                Text(stringResource(R.string.entry_subtitle, formatGrams(entry.grams), formatKcal(entry.nutrients.kcal)))
            }
        },
        trailingContent = {
            Text(
                text = stringResource(
                    R.string.entry_macros,
                    formatGrams(entry.nutrients.protein),
                    formatGrams(entry.nutrients.carbs),
                    formatGrams(entry.nutrients.fat),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}

@Composable
private fun EditEntryDialog(
    entry: DiaryEntry,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    var gramsText by rememberSaveable(entry.id) { mutableStateOf(formatEditable(entry.grams)) }
    val grams = parsePortionGrams(gramsText)
    val previewKcal = grams?.let { entry.nutrients.kcal * it / entry.grams }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                NumberField(
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    label = stringResource(R.string.amount_label),
                    suffix = stringResource(R.string.unit_g),
                    errorText = if (grams == null) stringResource(R.string.error_amount) else null,
                )
                if (!LocalHideNumbers.current) {
                    previewKcal?.let {
                        Text(stringResource(R.string.value_kcal, formatKcal(it)), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { grams?.let(onSave) }, enabled = grams != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    else -> date.format(rememberMediumDateFormatter())
}

@Composable
private fun WhatsNewCard(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.whats_new_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.whats_new_body), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.got_it)) }
            }
        }
    }
}

@Composable
private fun BirthDateCard(birthDate: LocalDate, onConfirm: () -> Unit, onChange: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.birth_date_confirm, birthDate.format(rememberMediumDateFormatter())),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onChange) { Text(stringResource(R.string.change)) }
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.birth_date_correct)) }
            }
        }
    }
}

/** Hide-numbers mode: no totals and no progress bars, just a short note (M1.8). */
@Composable
private fun HiddenNumbersNote() {
    Text(
        stringResource(R.string.numbers_hidden_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** A quick add without a name shows as "Quick add". */
private fun DiaryEntry.displayName(resources: android.content.res.Resources): String =
    name.ifBlank { if (entryType == EntryType.QUICK_ADD) resources.getString(R.string.quick_add_title) else name }
