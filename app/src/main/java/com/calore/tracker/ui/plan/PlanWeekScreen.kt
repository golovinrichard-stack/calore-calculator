package com.calore.tracker.ui.plan

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.plan.PlanReason
import com.calore.tracker.domain.plan.PlannerPref
import com.calore.tracker.domain.plan.SlotKey
import com.calore.tracker.domain.plan.key
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.cuisineLabelRes
import com.calore.tracker.ui.formatCount
import com.calore.tracker.ui.formatNumber
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.rememberMediumDateFormatter
import java.time.LocalDate
import java.time.format.TextStyle

/** "Plan my week" (M6.3) and "Replan the rest of the week" (M6.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanWeekScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: PlanWeekViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.phase) { if (state.phase == PlanWeekPhase.DONE) onDone() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_week_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            if (state.phase == PlanWeekPhase.PROPOSAL) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::regenerate, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.plan_regenerate)) }
                        Button(onClick = viewModel::accept, modifier = Modifier.weight(1f), enabled = state.planned.isNotEmpty()) {
                            Text(stringResource(R.string.plan_accept))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.phase) {
                PlanWeekPhase.LOADING, PlanWeekPhase.GENERATING, PlanWeekPhase.DONE -> Busy(stringResource(R.string.plan_week_planning))
                PlanWeekPhase.SAVING -> Busy(stringResource(R.string.plan_week_saving))
                PlanWeekPhase.ASK_REPLAN -> AskReplan(onReplan = viewModel::replanTheRest, onCancel = onBack)
                PlanWeekPhase.PROPOSAL -> Proposal(state, viewModel, onOpenProfile)
            }
        }
    }
    val swapping = state.swapping
    if (swapping != null) {
        SwapDialog(state, swapping, viewModel)
    }
}

@Composable
private fun Busy(text: String) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text(text, modifier = Modifier.padding(top = 16.dp))
    }
}

/** M6.1: the preferences still missing, once, on top of the first proposal; all optional. */
@Composable
private fun Checklist(missing: List<PlannerPref>, onFillIn: () -> Unit, onSkip: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.plan_checklist_title), style = MaterialTheme.typography.titleMedium)
            missing.forEach { pref -> Text("•  " + stringResource(pref.labelRes()), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.plan_checklist_optional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onFillIn) { Text(stringResource(R.string.plan_checklist_fill)) }
                OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.rate_not_now)) }
            }
        }
    }
}

private fun PlannerPref.labelRes(): Int = when (this) {
    PlannerPref.ALLERGENS -> R.string.plan_pref_allergens
    PlannerPref.LIKED_FOODS -> R.string.plan_pref_liked
    PlannerPref.DISLIKED_FOODS -> R.string.plan_pref_disliked
    PlannerPref.CUISINES -> R.string.plan_pref_cuisines
    PlannerPref.EQUIPMENT -> R.string.plan_pref_equipment
    PlannerPref.BUDGET -> R.string.plan_pref_budget
    PlannerPref.COOK_DAYS -> R.string.plan_pref_cook_days
}

@Composable
private fun AskReplan(onReplan: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.plan_replan_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.plan_replan_text))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onReplan) { Text(stringResource(R.string.plan_replan_go)) }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun Proposal(state: PlanWeekUiState, viewModel: PlanWeekViewModel, onOpenProfile: () -> Unit) {
    val formatter = rememberMediumDateFormatter()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.showChecklist) {
            item(key = "checklist") {
                Checklist(
                    missing = state.missing,
                    onFillIn = {
                        viewModel.checklistDone()
                        onOpenProfile()
                    },
                    onSkip = viewModel::checklistDone,
                )
            }
        }
        item(key = "header") { ProposalHeader(state, viewModel) }
        item(key = "summary") { SummaryCard(state) }
        if (state.cooks.isNotEmpty()) {
            item(key = "sessions") { SessionsCard(state) }
        }
        items(state.days.filter { !it.isBefore(state.today) }, key = { it.toString() }) { date ->
            ProposalDay(date, state, formatter, onOpen = viewModel::swap, onLock = viewModel::toggleLock)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalHeader(state: PlanWeekUiState, viewModel: PlanWeekViewModel) {
    val formatter = rememberMediumDateFormatter()
    val thisWeek = ReminderSchedule.weekStart(state.today)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.week_range, state.weekStart.format(formatter), state.weekStart.plusDays(6).format(formatter)),
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.weekStart == thisWeek, onClick = { viewModel.showWeek(thisWeek) }, label = { Text(stringResource(R.string.week_this)) })
            FilterChip(selected = state.weekStart == thisWeek.plusWeeks(1), onClick = { viewModel.showWeek(thisWeek.plusWeeks(1)) }, label = { Text(stringResource(R.string.plan_week_next)) })
        }
        if (state.people.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.people.forEachIndexed { index, person ->
                    FilterChip(
                        selected = person.id in state.chosen,
                        enabled = index > 0,
                        onClick = { viewModel.togglePerson(person.id) },
                        label = { Text(person.name.ifBlank { stringResource(R.string.nav_me) }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: PlanWeekUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.estimate?.let { estimate ->
                if (estimate.halers > 0) {
                    Text(stringResource(R.string.plan_shopping_estimate, formatNumber(estimate.halers / 100, decimals = 0)), style = MaterialTheme.typography.titleMedium)
                }
                if (estimate.unpriced > 0) {
                    Text(
                        pluralStringResource(R.plurals.plan_unpriced, estimate.unpriced, estimate.unpriced),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.frozenBoxes > 0) {
                Text(pluralStringResource(R.plurals.plan_frozen, state.frozenBoxes, state.frozenBoxes))
            }
            if (state.unplanned.isNotEmpty()) {
                Text(pluralStringResource(R.plurals.plan_unplanned, state.unplanned.size, state.unplanned.size), color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.plan_lock_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.missing.isNotEmpty() && !state.showChecklist) {
                Text(stringResource(R.string.plan_regenerate_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The cooking sessions with recipes, amounts and the estimated active time. */
@Composable
private fun SessionsCard(state: PlanWeekUiState) {
    val formatter = rememberMediumDateFormatter()
    val hideNumbers = LocalHideNumbers.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.week_cooks_title), style = MaterialTheme.typography.titleMedium)
            state.cooks.groupBy { it.date }.forEach { (date, onDay) ->
                Text(
                    stringResource(R.string.plan_session_minutes, date.format(formatter), state.minutes[date] ?: 0),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                onDay.forEach { cook ->
                    val name = state.names[cook.recipeId].orEmpty()
                    Text(
                        if (hideNumbers) {
                            "$name × ${cook.portions}"
                        } else {
                            stringResource(R.string.plan_cook_line, name, cook.portions, formatCount(Math.round(cook.targetCookedGrams).toInt()))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProposalDay(
    date: LocalDate,
    state: PlanWeekUiState,
    formatter: java.time.format.DateTimeFormatter,
    onOpen: (SlotKey) -> Unit,
    onLock: (SlotKey) -> Unit,
) {
    val hideNumbers = LocalHideNumbers.current
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " · " + date.format(formatter),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (date.dayOfWeek in state.cookingDays) {
                    Text(stringResource(R.string.week_cooking_day), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            state.shownPeople.forEach { person ->
                if (state.shownPeople.size > 1) {
                    Text(person.name.ifBlank { stringResource(R.string.nav_me) }, style = MaterialTheme.typography.labelLarge, color = Color(person.colour))
                }
                CookingSessions.MEAL_ORDER.forEach { meal ->
                    val slot = state.slot(date, meal, person.id)
                    if (slot != null || SlotKey(date, meal, person.id) in state.unplanned) {
                        ProposalMeal(meal, slot, SlotKey(date, meal, person.id), state, onOpen, onLock)
                    }
                }
                if (!hideNumbers) {
                    val planned = state.plannedTotals(date, person.id)
                    val targets = person.effectiveTargets
                    Text(
                        stringResource(
                            R.string.week_planned,
                            formatCount(Math.round(planned.kcal).toInt()),
                            formatCount(targets.kcal),
                            formatCount(Math.round(planned.proteinG).toInt()),
                            formatCount(targets.proteinG),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProposalMeal(meal: MealType, slot: PlanSlot?, key: SlotKey, state: PlanWeekUiState, onOpen: (SlotKey) -> Unit, onLock: (SlotKey) -> Unit) {
    val planned = key in state.planned
    val locked = key in state.locked
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = planned) { onOpen(key) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(meal.labelRes()), modifier = Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Column(modifier = Modifier.weight(1f)) {
            val name = slot?.recipeId?.let { state.names[it] }
            Text(
                when {
                    slot == null -> stringResource(R.string.plan_no_recipe)
                    slot.kind == SlotKind.BOX && name != null -> stringResource(R.string.slot_box_recipe, name)
                    slot.kind == SlotKind.COOK_FRESH && name != null -> stringResource(R.string.slot_fresh_recipe, name)
                    else -> stringResource(slot.kind.labelRes())
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (planned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                slot == null -> Unit
                !planned -> Text(stringResource(R.string.plan_kept), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> state.reasons[key]?.firstOrNull()?.let { reason ->
                    Text(reasonText(reason, state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            slot?.takeIf { planned }?.let { state.frozenAge(it) }?.let { age ->
                Text(pluralStringResource(R.plurals.week_box_frozen, age.toInt(), age.toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (planned) {
            IconButton(onClick = { onLock(key) }) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(if (locked) R.string.plan_unlock else R.string.plan_lock),
                    tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun reasonText(reason: PlanReason, state: PlanWeekUiState): String = when (reason) {
    PlanReason.RatedUp -> stringResource(R.string.plan_reason_rated)
    is PlanReason.LikedFoods -> stringResource(R.string.plan_reason_liked)
    is PlanReason.Cuisine -> cuisineLabelRes(reason.key)?.let { stringResource(R.string.plan_reason_cuisine_named, stringResource(it)) }
        ?: stringResource(R.string.plan_reason_cuisine)
    PlanReason.GoodProtein -> stringResource(R.string.plan_reason_protein)
    PlanReason.Cheap -> stringResource(R.string.plan_reason_cheap)
    is PlanReason.UsesPantry -> stringResource(R.string.plan_reason_pantry, reason.foodUuids.mapNotNull { state.foodNames[it] }.take(2).joinToString(", "))
    PlanReason.CyclePhase -> stringResource(R.string.plan_reason_phase)
    PlanReason.Freezes -> stringResource(R.string.plan_reason_freezes)
}

/** Tap a planned meal: its reasons, lock, and **Swap** with the top 5 other recipes. */
@Composable
private fun SwapDialog(state: PlanWeekUiState, key: SlotKey, viewModel: PlanWeekViewModel) {
    val slot = state.slots.firstOrNull { it.key == key }
    val locked = key in state.locked
    AlertDialog(
        onDismissRequest = viewModel::dismissSwap,
        title = { Text(slot?.recipeId?.let { state.names[it] } ?: stringResource(R.string.plan_no_recipe)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.reasons[key].orEmpty().forEach { Text("•  " + reasonText(it, state), style = MaterialTheme.typography.bodyMedium) }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.plan_swap_title), style = MaterialTheme.typography.titleSmall)
                if (state.alternatives.isEmpty()) {
                    Text(stringResource(R.string.plan_swap_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.alternatives.forEach { alt ->
                    ListItem(
                        headlineContent = { Text(state.names[alt.recipeId].orEmpty()) },
                        supportingContent = alt.reasons.takeIf { it.isNotEmpty() }?.let { reasons ->
                            { Text(reasons.map { reasonText(it, state) }.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
                        },
                        modifier = Modifier.clickable { viewModel.applySwap(alt) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.toggleLock(key)
                viewModel.dismissSwap()
            }) { Text(stringResource(if (locked) R.string.plan_unlock else R.string.plan_lock)) }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissSwap) { Text(stringResource(R.string.close)) } },
    )
}
