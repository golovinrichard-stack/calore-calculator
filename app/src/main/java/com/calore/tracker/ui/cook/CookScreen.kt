package com.calore.tracker.ui.cook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.Batches
import com.calore.tracker.domain.FillMode
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.parseWholeIn
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatGrams
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.recipes.FoodPickerDialog
import com.calore.tracker.ui.rememberMediumDateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenCookMode: (recipeIds: List<String>) -> Unit = {},
    viewModel: CookViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    val title = stringResource(
        when (state.step) {
            CookStep.AMOUNT -> R.string.cook_step_amount
            CookStep.INGREDIENTS -> R.string.recipe_ingredients
            CookStep.COOK -> R.string.cook_step_cook
            CookStep.WEIGH -> R.string.cook_step_weigh
            CookStep.FILL -> R.string.cook_step_fill
        },
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cook_title) + " · " + title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.step != CookStep.AMOUNT && state.step != CookStep.FILL) {
                    OutlinedButton(onClick = viewModel::back) { Text(stringResource(R.string.back)) }
                }
                Button(onClick = viewModel::next, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (state.step == CookStep.FILL) R.string.fill_save else R.string.cook_next))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                CookStep.AMOUNT -> AmountStep(state, viewModel)
                CookStep.INGREDIENTS -> IngredientsStep(state, viewModel)
                CookStep.COOK -> {
                    Text(stringResource(R.string.cook_cooking_hint))
                    val ids = state.items.mapNotNull { it.recipe?.recipe?.id }
                    if (ids.isNotEmpty()) {
                        OutlinedButton(onClick = { onOpenCookMode(ids) }) { Text(stringResource(R.string.cook_open_mode)) }
                    }
                }
                CookStep.WEIGH -> WeighStep(state, viewModel)
                CookStep.FILL -> FillStep(state, viewModel)
            }
        }
    }
}

@Composable
private fun AmountStep(state: CookUiState, viewModel: CookViewModel) {
    var pickingRecipe by rememberSaveable { mutableStateOf(false) }
    var pickingFood by rememberSaveable { mutableStateOf(false) }
    val picker by viewModel.picker.state.collectAsStateWithLifecycle()
    state.items.forEach { item ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeItem(item.id) }) { Icon(Icons.Filled.Close, stringResource(R.string.recipe_remove)) }
                }
                if (item.recipe != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !item.byWeight, onClick = { viewModel.setByWeight(item.id, false) }, label = { Text(stringResource(R.string.recipe_servings_label)) })
                        FilterChip(selected = item.byWeight, onClick = { viewModel.setByWeight(item.id, true) }, label = { Text(stringResource(R.string.cook_by_weight)) })
                    }
                    NumberField(
                        value = item.amountText,
                        onValueChange = { viewModel.setAmount(item.id, it) },
                        label = stringResource(if (item.byWeight) R.string.cook_by_weight else R.string.recipe_servings_label),
                        suffix = if (item.byWeight) stringResource(R.string.unit_g) else null,
                        errorText = if (state.showErrors && item.scale() == null) stringResource(R.string.error_amount) else null,
                    )
                } else {
                    NumberField(
                        value = item.amountText,
                        onValueChange = { viewModel.setAmount(item.id, it) },
                        label = stringResource(R.string.recipe_raw_grams),
                        suffix = stringResource(R.string.unit_g),
                        errorText = if (state.showErrors && item.ingredients().isEmpty()) stringResource(R.string.error_amount) else null,
                    )
                }
            }
        }
    }
    if (state.showErrors && state.items.isEmpty()) Text(stringResource(R.string.error_recipe_ingredients), color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = { pickingRecipe = true }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.cook_add_recipe), modifier = Modifier.padding(start = 8.dp))
    }
    OutlinedButton(onClick = {
        viewModel.picker.onQueryChange("")
        pickingFood = true
    }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.cook_add_food), modifier = Modifier.padding(start = 8.dp))
    }
    if (pickingRecipe) {
        AlertDialog(
            onDismissRequest = { pickingRecipe = false },
            title = { Text(stringResource(R.string.cook_add_recipe)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    state.allRecipes.forEach { details ->
                        ListItem(
                            headlineContent = { Text(details.recipe.displayName()) },
                            modifier = Modifier.clickable {
                                pickingRecipe = false
                                viewModel.addRecipe(details.recipe.id)
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingRecipe = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pickingFood) {
        FoodPickerDialog(
            query = picker.query,
            results = picker.results,
            onQueryChange = viewModel.picker::onQueryChange,
            onPick = {
                pickingFood = false
                viewModel.addFood(it)
            },
            onCreate = null,
            onDismiss = { pickingFood = false },
        )
    }
}

@Composable
private fun IngredientsStep(state: CookUiState, viewModel: CookViewModel) {
    Text(stringResource(R.string.cook_ingredients_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    state.items.forEach { item ->
        SectionHeader(item.name)
        val details = item.recipe
        if (details == null) {
            Text(stringResource(R.string.value_g, item.amountText))
            return@forEach
        }
        details.ingredients.forEachIndexed { index, ingredient ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(details.foods[ingredient.foodUuid]?.displayName() ?: stringResource(R.string.recipe_missing_food), modifier = Modifier.weight(1f))
                NumberField(
                    value = item.gramsTexts.getOrElse(index) { "" },
                    onValueChange = { viewModel.setIngredientGrams(item.id, index, it) },
                    label = stringResource(R.string.recipe_raw_grams),
                    suffix = stringResource(R.string.unit_g),
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

@Composable
private fun WeighStep(state: CookUiState, viewModel: CookViewModel) {
    state.items.forEach { item ->
        var potMenu by remember { mutableStateOf(false) }
        val pot = state.pots.firstOrNull { it.id == item.potId }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.recipe_raw_grams) + ": " + stringResource(R.string.value_g, formatGrams(item.rawGrams)))
                Box {
                    OutlinedButton(onClick = { potMenu = true }) { Text(pot?.name ?: stringResource(R.string.cook_pot_none)) }
                    DropdownMenu(expanded = potMenu, onDismissRequest = { potMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.cook_pot_none)) }, onClick = {
                            potMenu = false
                            viewModel.setPot(item.id, null)
                        })
                        state.pots.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name) }, onClick = {
                                potMenu = false
                                viewModel.setPot(item.id, option.id)
                            })
                        }
                    }
                }
                val cooked = item.cookedGrams(state.pots)
                NumberField(
                    value = item.weightText,
                    onValueChange = { viewModel.setWeight(item.id, it) },
                    label = stringResource(if (pot != null) R.string.cook_full_weight else R.string.cook_net_weight),
                    suffix = stringResource(R.string.unit_g),
                    errorText = if ((state.showErrors || item.weightText.isNotEmpty()) && cooked == null) stringResource(R.string.error_cook_weight) else null,
                )
                if (cooked != null && !LocalHideNumbers.current) {
                    val per100 = Batches.per100gCooked(Batches.potNutrients(item.ingredients()), cooked)
                    Text(stringResource(R.string.value_g, formatGrams(cooked)) + " · " + stringResource(R.string.cook_per_100g, formatKcal(per100.kcal)))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FillStep(state: CookUiState, viewModel: CookViewModel) {
    val fill = state.fill
    val formatter = rememberMediumDateFormatter()
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val mixed = fill.mixed && state.batches.size > 1
    if (state.batches.size > 1) {
        SwitchLine(stringResource(R.string.fill_mixed), fill.mixed) { on -> viewModel.editFill { it.copy(mixed = on) } }
    }
    if (mixed) {
        state.items.forEach { item ->
            val batch = item.batch ?: return@forEach
            NumberField(
                value = fill.fixedTexts[batch.id].orEmpty(),
                onValueChange = { text -> viewModel.editFill { it.copy(fixedTexts = it.fixedTexts + (batch.id to text)) } },
                label = stringResource(R.string.fill_fixed, item.name),
                suffix = stringResource(R.string.unit_g),
            )
        }
        PeopleChooser(state, viewModel)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(FillMode.EQUAL to R.string.fill_mode_equal, FillMode.BY_CALORIES to R.string.fill_mode_calories, FillMode.BY_PERSON to R.string.fill_mode_person)
                .forEach { (mode, label) ->
                    FilterChip(selected = fill.mode == mode, onClick = { viewModel.editFill { it.copy(mode = mode) } }, label = { Text(stringResource(label)) })
                }
        }
        when (fill.mode) {
            FillMode.EQUAL -> NumberField(
                value = fill.countText,
                onValueChange = { text -> viewModel.editFill { it.copy(countText = text) } },
                label = stringResource(R.string.fill_count),
                decimal = false,
            )
            FillMode.BY_CALORIES -> NumberField(
                value = fill.targetKcalText,
                onValueChange = { text -> viewModel.editFill { it.copy(targetKcalText = text) } },
                label = stringResource(R.string.fill_target_kcal),
                suffix = stringResource(R.string.unit_kcal),
            )
            FillMode.BY_PERSON -> PeopleChooser(state, viewModel)
        }
        if (fill.mode != FillMode.EQUAL) {
            SwitchLine(stringResource(R.string.fill_leftover_box), fill.addLeftoverBox) { on -> viewModel.editFill { it.copy(addLeftoverBox = on) } }
        }
    }
    SwitchLine(stringResource(R.string.fill_plan_days), fill.planStart != null) { on ->
        viewModel.editFill { it.copy(planStart = if (on) state.today.plusDays(1) else null, planMeal = if (on) it.planMeal ?: MealType.LUNCH else null) }
    }
    fill.planStart?.let { start ->
        OutlinedButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.fill_plan_from, start.format(formatter))) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { meal ->
                FilterChip(selected = fill.planMeal == meal, onClick = { viewModel.editFill { it.copy(planMeal = meal) } }, label = { Text(stringResource(meal.labelRes())) })
            }
        }
    }
    if (pickingDate) {
        DatePickerModal(
            initial = fill.planStart ?: state.today,
            onDismiss = { pickingDate = false },
            onConfirm = { date ->
                pickingDate = false
                viewModel.editFill { it.copy(planStart = date) }
            },
        )
    }

    // The preview: what will be filled, and where it goes.
    val results = state.fillResults()
    results.forEach { (_, result) ->
        if (result.scaledDown) Text(stringResource(R.string.fill_scaled_down), color = MaterialTheme.colorScheme.error)
        if (result.leftoverGrams > 0) Text(stringResource(R.string.fill_leftover, formatGrams(result.leftoverGrams)))
        if (result.notEnough.isNotEmpty()) {
            val names = state.items.filter { it.batch?.id in result.notEnough }.joinToString(", ") { it.name }
            Text(stringResource(R.string.fill_not_enough, names), color = MaterialTheme.colorScheme.error)
        }
    }
    val hideNumbers = LocalHideNumbers.current
    viewModel.previews(state).forEach { preview ->
        val owner = state.people.firstOrNull { it.id == preview.box.ownerMemberId }?.name
        ListItem(
            leadingContent = { Text(preview.code, fontWeight = FontWeight.Bold) },
            headlineContent = {
                Text(
                    if (hideNumbers) {
                        stringResource(R.string.value_g, formatGrams(preview.box.grams))
                    } else {
                        stringResource(
                            R.string.fill_box_line,
                            formatGrams(preview.box.grams),
                            formatKcal(preview.box.nutrients.kcal),
                            formatGrams(preview.box.nutrients.protein ?: 0.0),
                        )
                    },
                )
            },
            supportingContent = {
                Column {
                    val plan = listOfNotNull(owner, preview.box.plannedDate?.format(formatter), preview.box.plannedMeal?.let { stringResource(it.labelRes()) })
                    if (plan.isNotEmpty()) Text(plan.joinToString(" · "))
                    if (preview.toFreezer) Text(stringResource(R.string.fill_to_freezer), color = MaterialTheme.colorScheme.primary)
                    if (preview.box.missesTarget) Text(stringResource(R.string.fill_misses_target), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    if (state.showErrors && results.all { it.second.boxes.isEmpty() }) {
        Text(stringResource(R.string.recipe_fix_errors), color = MaterialTheme.colorScheme.error)
    }
}

/** Per person: a meal and the number of boxes (0 = none). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleChooser(state: CookUiState, viewModel: CookViewModel) {
    Text(stringResource(R.string.fill_people), style = MaterialTheme.typography.labelLarge)
    state.people.forEach { member ->
        val choice = state.fill.people[member.id]
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(member.name.ifBlank { stringResource(R.string.nav_me) }, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                MealType.entries.forEach { meal ->
                    FilterChip(
                        selected = choice?.meal == meal,
                        onClick = {
                            viewModel.editFill { fill ->
                                fill.copy(people = fill.people + (member.id to PersonChoice(meal, choice?.count?.takeIf { it > 0 } ?: 1)))
                            }
                        },
                        label = { Text(stringResource(meal.labelRes())) },
                    )
                }
                NumberField(
                    value = choice?.count?.toString().orEmpty(),
                    onValueChange = { text ->
                        val count = parseWholeIn(text, 0..50) ?: 0
                        viewModel.editFill { fill ->
                            if (count == 0) fill.copy(people = fill.people - member.id)
                            else fill.copy(people = fill.people + (member.id to PersonChoice(choice?.meal ?: MealType.LUNCH, count)))
                        }
                    },
                    label = stringResource(R.string.fill_count),
                    decimal = false,
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}
