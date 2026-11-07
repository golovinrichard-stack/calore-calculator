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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.domain.CookingSessions
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.PlannedCook
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.matchesSearch
import com.calore.tracker.domain.parseWholeIn
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatCount
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.rememberMediumDateFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** Which slot the user tapped. */
private data class SlotRef(val date: LocalDate, val meal: MealType, val memberId: String)

/** The week plan (M5.3): days × meals for the chosen people, with daily totals against targets. */
@Composable
fun WeekPlanContent(
    snackbarHostState: SnackbarHostState,
    onCook: (LocalDate) -> Unit = {},
    viewModel: WeekPlanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    var editing by remember { mutableStateOf<SlotRef?>(null) }
    var editingCooking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WeekPlanEvent.NothingToRepeat -> snackbarHostState.showSnackbar(resources.getString(R.string.week_repeat_none))
            }
        }
    }
    if (!state.loaded) return
    val formatter = rememberMediumDateFormatter()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") {
            WeekHeader(state, viewModel, onEditCooking = { editingCooking = true })
        }
        val cooks = state.cooks
        if (cooks.isNotEmpty()) {
            item(key = "cooks") { CooksCard(cooks, state, onCook) }
        }
        items(state.days, key = { it.toString() }) { date ->
            DayCard(date, state, onSlotClick = { meal, member -> editing = SlotRef(date, meal, member.id) }, formatter = formatter)
        }
    }

    editing?.let { ref ->
        val member = state.people.firstOrNull { it.id == ref.memberId }
        if (member != null) {
            SlotDialog(
                current = state.slot(ref.date, ref.meal, ref.memberId),
                recipes = state.recipes.values.toList(),
                onSave = { kind, recipeId ->
                    editing = null
                    viewModel.setSlot(ref.date, ref.meal, member, kind, recipeId)
                },
                onClear = { slot ->
                    editing = null
                    viewModel.clearSlot(slot)
                },
                onDismiss = { editing = null },
            )
        }
    }
    if (state.askCooking || editingCooking) {
        val me = state.people.firstOrNull()
        if (me != null) {
            CookingDialog(
                me = me,
                onSave = { sessions, days, minutes, prepped ->
                    editingCooking = false
                    viewModel.saveCooking(sessions, days, minutes, prepped)
                },
                onDismiss = {
                    editingCooking = false
                    viewModel.dismissCookingQuestion()
                },
            )
        }
    }
}

@Composable
private fun WeekHeader(state: WeekPlanUiState, viewModel: WeekPlanViewModel, onEditCooking: () -> Unit) {
    val formatter = rememberMediumDateFormatter()
    var menuOpen by remember { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var choosingTemplate by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previousWeek) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.week_previous)) }
            TextButton(onClick = viewModel::thisWeek, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.week_range, state.weekStart.format(formatter), state.weekStart.plusDays(6).format(formatter)))
            }
            IconButton(onClick = viewModel::nextWeek) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.week_next)) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.week_this)) }, onClick = {
                        menuOpen = false
                        viewModel.thisWeek()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.week_repeat)) }, onClick = {
                        menuOpen = false
                        viewModel.repeatLastWeek()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.week_save_template)) }, onClick = {
                        menuOpen = false
                        naming = true
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.week_use_template)) }, onClick = {
                        menuOpen = false
                        choosingTemplate = true
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.week_cooking_days)) }, onClick = {
                        menuOpen = false
                        onEditCooking()
                    })
                }
            }
        }
        if (state.people.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.people.forEach { person ->
                    FilterChip(
                        selected = person.id !in state.hidden,
                        onClick = { viewModel.togglePerson(person.id) },
                        label = { Text(person.name.ifBlank { stringResource(R.string.nav_me) }) },
                    )
                }
            }
        }
    }
    if (naming) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.week_save_template)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.week_template_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    naming = false
                    viewModel.saveAsTemplate(name)
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (choosingTemplate) {
        AlertDialog(
            onDismissRequest = { choosingTemplate = false },
            title = { Text(stringResource(R.string.week_use_template)) },
            text = {
                Column {
                    if (state.templates.isEmpty()) Text(stringResource(R.string.week_no_templates))
                    state.templates.forEach { template ->
                        ListItem(headlineContent = { Text(template.name) }, modifier = Modifier.clickable {
                            choosingTemplate = false
                            viewModel.useTemplate(template.id)
                        })
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingTemplate = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DayCard(date: LocalDate, state: WeekPlanUiState, onSlotClick: (MealType, Member) -> Unit, formatter: java.time.format.DateTimeFormatter) {
    val hideNumbers = LocalHideNumbers.current
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSlotClick(meal, person) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(stringResource(meal.labelRes()), modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                slotLabel(slot, state),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (slot == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            slot?.let { state.frozenAge(it) }?.let { age ->
                                Text(pluralStringResource(R.plurals.week_box_frozen, age.toInt(), age.toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (!hideNumbers) {
                    val planned = state.planned(date, person.id)
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

/** What to cook on each cooking day for this plan, with Cook (M5.4). */
@Composable
private fun CooksCard(cooks: List<PlannedCook>, state: WeekPlanUiState, onCook: (LocalDate) -> Unit) {
    val formatter = rememberMediumDateFormatter()
    val hideNumbers = LocalHideNumbers.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.week_cooks_title), style = MaterialTheme.typography.titleMedium)
            cooks.groupBy { it.date }.forEach { (date, onDay) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        onDay.forEach { cook ->
                            val name = state.recipes[cook.recipeId]?.recipe?.displayName().orEmpty()
                            Text(
                                if (hideNumbers) "${date.format(formatter)} · $name × ${cook.portions}" else stringResource(
                                    R.string.week_cook_line,
                                    date.format(formatter),
                                    name,
                                    cook.portions,
                                    formatCount(Math.round(cook.targetCookedGrams).toInt()),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (onDay.all { (it.date to it.recipeId) in state.cooked }) {
                        Text(stringResource(R.string.week_cooked), color = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = { onCook(date) }) { Text(stringResource(R.string.cook_button)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun slotLabel(slot: PlanSlot?, state: WeekPlanUiState): String {
    if (slot == null) return stringResource(R.string.slot_empty)
    val recipe = slot.recipeId?.let { state.recipes[it]?.recipe?.displayName() }
    return when (slot.kind) {
        SlotKind.BOX -> recipe?.let { stringResource(R.string.slot_box_recipe, it) } ?: stringResource(R.string.slot_box)
        SlotKind.COOK_FRESH -> recipe?.let { stringResource(R.string.slot_fresh_recipe, it) } ?: stringResource(R.string.slot_cook_fresh)
        SlotKind.EAT_OUT -> stringResource(R.string.slot_eat_out)
        SlotKind.FREE -> stringResource(R.string.slot_free)
    }
}

fun SlotKind.labelRes(): Int = when (this) {
    SlotKind.BOX -> R.string.slot_box
    SlotKind.COOK_FRESH -> R.string.slot_cook_fresh
    SlotKind.EAT_OUT -> R.string.slot_eat_out
    SlotKind.FREE -> R.string.slot_free
}

/** Tap a meal: prep box or cook fresh (with a recipe), eat out, or free (M5.3). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotDialog(
    current: PlanSlot?,
    recipes: List<RecipeDetails>,
    onSave: (SlotKind, String?) -> Unit,
    onClear: (PlanSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(current?.kind ?: SlotKind.BOX) }
    var recipeId by rememberSaveable { mutableStateOf(current?.recipeId) }
    var query by rememberSaveable { mutableStateOf("") }
    val needsRecipe = kind == SlotKind.BOX || kind == SlotKind.COOK_FRESH
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlotKind.entries.forEach { option ->
                        FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(stringResource(option.labelRes())) })
                    }
                }
                if (needsRecipe) {
                    SectionHeader(stringResource(R.string.slot_choose_recipe))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val shown = recipes.filter { matchesSearch(query, it.recipe.displayName(), it.recipe.nameCs, it.recipe.nameEn) }
                        .sortedBy { it.recipe.displayName() }
                    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        shown.forEach { details ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        details.recipe.displayName(),
                                        fontWeight = if (details.recipe.id == recipeId) FontWeight.Bold else FontWeight.Normal,
                                        color = if (details.recipe.id == recipeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                modifier = Modifier.clickable { recipeId = details.recipe.id },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(kind, if (needsRecipe) recipeId else null) }, enabled = !needsRecipe || recipeId != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onClear(current) }) { Text(stringResource(R.string.slot_clear), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** M5.2: how often, which days, how long, and which meals come from boxes, with what each cooking day covers (A14). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookingDialog(me: Member, onSave: (Int, List<DayOfWeek>, Int, Set<MealType>) -> Unit, onDismiss: () -> Unit) {
    var sessions by rememberSaveable { mutableIntStateOf(me.cookSessionsPerWeek) }
    var days by rememberSaveable { mutableStateOf(me.cookDays) }
    var minutesText by rememberSaveable { mutableStateOf(me.minutesPerSession.toString()) }
    var prepped by rememberSaveable { mutableStateOf(me.preppedMeals) }
    val minutes = parseWholeIn(minutesText, 15..600)
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cooking_setup_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cook_sessions_label), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..7).forEach { n -> FilterChip(selected = sessions == n, onClick = { sessions = n }, label = { Text(n.toString()) }) }
                }
                Text(stringResource(R.string.cook_days_label), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.cook_days_hint), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)) },
                        )
                    }
                }
                NumberField(
                    value = minutesText,
                    onValueChange = { minutesText = it },
                    label = stringResource(R.string.minutes_label),
                    decimal = false,
                    errorText = if (minutes == null) stringResource(R.string.error_minutes) else null,
                )
                Text(stringResource(R.string.prepped_meals_label), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { meal ->
                        FilterChip(selected = meal in prepped, onClick = { prepped = if (meal in prepped) prepped - meal else prepped + meal }, label = { Text(stringResource(meal.labelRes())) })
                    }
                }
                // What it means (A14): which meals each cooking day covers.
                CookingSessions.coverage(CookingSessions.days(sessions, days), prepped).forEach { coverage ->
                    val first = coverage.meals.firstOrNull() ?: return@forEach
                    val last = coverage.meals.last()
                    fun label(day: DayOfWeek, meal: MealType) = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    Text(
                        stringResource(
                            R.string.cooking_covers,
                            label(coverage.day, first.meal).replaceFirstChar { it.titlecase(locale) },
                            stringResource(R.string.cooking_day_meal, label(first.day, first.meal), stringResource(first.meal.labelRes()).lowercase(locale)),
                            stringResource(R.string.cooking_day_meal, label(last.day, last.meal), stringResource(last.meal.labelRes()).lowercase(locale)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (CookingSessions.freezerWarning(sessions)) {
                    Text(stringResource(R.string.cooking_freezer_warning), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let { onSave(sessions, days, it, prepped) } }, enabled = minutes != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
