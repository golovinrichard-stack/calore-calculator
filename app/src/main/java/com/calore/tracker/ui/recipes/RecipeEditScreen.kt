package com.calore.tracker.ui.recipes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Cuisines
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.MealType
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.NutrientTable
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.components.ShowNumbersButton
import com.calore.tracker.ui.cuisineLabelRes
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.rememberNumbersVisible
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    onBack: () -> Unit,
    onSaved: (id: String, isNew: Boolean) -> Unit,
    onScanIngredient: () -> Unit,
    onCreateFood: (name: String) -> Unit,
    /** A food chosen by the scanner or just created, waiting to be added (by its row id). */
    pickedFoodId: Long? = null,
    onPickedFoodUsed: () -> Unit = {},
    viewModel: RecipeEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val fixErrors = stringResource(R.string.recipe_fix_errors)

    LaunchedEffect(viewModel) { viewModel.saved.collect { id -> onSaved(id, state.isNew) } }
    LaunchedEffect(pickedFoodId) {
        if (pickedFoodId != null) {
            viewModel.addFoodById(pickedFoodId)
            onPickedFoodUsed()
        }
    }
    val leave = { if (state.changed) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.changed) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.recipe_new_title else R.string.recipe_edit)) },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save()
                            if (state.form.invalidFields().isNotEmpty()) scope.launch { snackbarHostState.showSnackbar(fixErrors) }
                        },
                        enabled = !state.loading && !state.notFound && !state.saving,
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.notFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.recipe_not_found))
            }
            else -> RecipeEditForm(
                state = state,
                viewModel = viewModel,
                onAddIngredient = {
                    viewModel.onPickerQueryChange("")
                    picking = true
                },
                onScanIngredient = onScanIngredient,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (picking) {
        FoodPickerDialog(
            query = state.pickerQuery,
            results = state.pickerResults,
            onQueryChange = viewModel::onPickerQueryChange,
            onPick = { food ->
                picking = false
                viewModel.addFood(food)
            },
            onCreate = {
                picking = false
                onCreateFood(state.pickerQuery.trim())
            },
            onDismiss = { picking = false },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            text = { Text(stringResource(R.string.recipe_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.recipe_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.recipe_keep_editing)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeEditForm(
    state: RecipeEditUiState,
    viewModel: RecipeEditViewModel,
    onAddIngredient: () -> Unit,
    onScanIngredient: () -> Unit,
    modifier: Modifier,
) {
    val form = state.form
    val invalid = if (state.showErrors) form.invalidFields() else emptySet()
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.recipe_name)) },
            isError = RecipeField.NAME in invalid,
            supportingText = if (RecipeField.NAME in invalid) {
                { Text(stringResource(R.string.error_recipe_name)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                value = form.servingsText,
                onValueChange = viewModel::onServingsChange,
                label = stringResource(R.string.recipe_servings_label),
                decimal = false,
                errorText = if (RecipeField.SERVINGS in invalid) stringResource(R.string.error_recipe_servings) else null,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.shortCode,
                onValueChange = viewModel::onShortCodeChange,
                label = { Text(stringResource(R.string.recipe_short_code)) },
                isError = RecipeField.SHORT_CODE in invalid,
                supportingText = if (RecipeField.SHORT_CODE in invalid) {
                    { Text(stringResource(R.string.error_recipe_short_code)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.recipe_short_code_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NutritionPreview(form)

        SectionHeader(stringResource(R.string.recipe_ingredients))
        if (RecipeField.INGREDIENTS in invalid && form.ingredients.isEmpty()) {
            Text(stringResource(R.string.error_recipe_ingredients), color = MaterialTheme.colorScheme.error)
        }
        form.ingredients.forEachIndexed { index, row ->
            IngredientRow(
                row = row,
                index = index,
                count = form.ingredients.size,
                showGramsError = state.showErrors && index in form.badGramsRows,
                viewModel = viewModel,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onAddIngredient) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.recipe_add_ingredient), modifier = Modifier.padding(start = 8.dp))
            }
            IconButton(onClick = onScanIngredient) {
                Icon(painterResource(R.drawable.ic_barcode), stringResource(R.string.scanner_title))
            }
        }

        HorizontalDivider()
        SectionHeader(stringResource(R.string.recipe_steps))
        form.steps.forEachIndexed { index, step -> StepRow(step, index, form.steps.size, viewModel) }
        OutlinedButton(onClick = viewModel::addStep) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.recipe_add_step), modifier = Modifier.padding(start = 8.dp))
        }

        HorizontalDivider()
        SectionHeader(stringResource(R.string.recipe_storage))
        NumberField(
            value = form.fridgeDaysText,
            onValueChange = viewModel::onFridgeDaysChange,
            label = stringResource(R.string.recipe_fridge_days_label),
            decimal = false,
            errorText = if (RecipeField.FRIDGE_DAYS in invalid) stringResource(R.string.error_recipe_days) else null,
        )
        SwitchRow(stringResource(R.string.recipe_freezable_label), form.freezable, viewModel::onFreezableChange)
        if (form.freezable) {
            NumberField(
                value = form.freezerMonthsText,
                onValueChange = viewModel::onFreezerMonthsChange,
                label = stringResource(R.string.recipe_freezer_months_label),
                decimal = false,
                errorText = if (RecipeField.FREEZER_MONTHS in invalid) stringResource(R.string.error_recipe_months) else null,
            )
        }
        OutlinedTextField(
            value = form.reheatNote,
            onValueChange = viewModel::onReheatNoteChange,
            label = { Text(stringResource(R.string.recipe_reheat_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        SectionHeader(stringResource(R.string.recipe_details))
        Text(stringResource(R.string.recipe_meals), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { meal ->
                FilterChip(
                    selected = meal in form.mealTypes,
                    onClick = { viewModel.onMealTypeToggle(meal) },
                    label = { Text(stringResource(meal.labelRes())) },
                )
            }
        }
        CuisinePicker(form.cuisine, viewModel::onCuisineChange)
        OutlinedTextField(
            value = form.tagsText,
            onValueChange = viewModel::onTagsChange,
            label = { Text(stringResource(R.string.recipe_tags)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.recipe_equipment), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.entries.forEach { equipment ->
                FilterChip(
                    selected = equipment in form.equipment,
                    onClick = { viewModel.onEquipmentToggle(equipment) },
                    label = { Text(stringResource(equipment.labelRes())) },
                )
            }
        }
        SwitchRow(stringResource(R.string.recipe_meal_prep), form.mealPrepFriendly, viewModel::onMealPrepChange)
    }
}

/** Updates while typing: per serving, the whole recipe, and allergens (M3.2). */
@Composable
private fun NutritionPreview(form: RecipeForm) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.recipe_nutrition))
            val (numbersVisible, showNumbers) = rememberNumbersVisible()
            if (!numbersVisible) {
                ShowNumbersButton(onClick = showNumbers)
            } else {
                val nutrition = form.nutrition()
                Text(stringResource(R.string.recipe_per_serving), style = MaterialTheme.typography.labelLarge)
                NutrientTable(nutrition.perServing)
                Text(
                    stringResource(R.string.recipe_whole_kcal, formatKcal(nutrition.total.kcal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.recipe_allergens), style = MaterialTheme.typography.labelLarge)
            AllergenText(form.allergens(), myAllergens = emptySet<Allergen>())
        }
    }
}

@Composable
private fun IngredientRow(row: IngredientDraft, index: Int, count: Int, showGramsError: Boolean, viewModel: RecipeEditViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.food?.displayName() ?: stringResource(R.string.recipe_missing_food),
                color = if (row.food == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val extra = listOfNotNull(row.note.takeIf { it.isNotBlank() }, if (row.optional) stringResource(R.string.recipe_optional) else null)
            if (extra.isNotEmpty()) {
                Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        NumberField(
            value = row.gramsText,
            onValueChange = { viewModel.onGramsChange(index, it) },
            label = stringResource(R.string.recipe_raw_grams),
            suffix = stringResource(R.string.unit_g),
            errorText = if (showGramsError) stringResource(R.string.error_recipe_grams) else null,
            modifier = Modifier.width(140.dp),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.recipe_note)) },
                    onClick = {
                        menuOpen = false
                        editingNote = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.recipe_optional_label)) },
                    leadingIcon = { Checkbox(checked = row.optional, onCheckedChange = null) },
                    onClick = {
                        menuOpen = false
                        viewModel.onOptionalChange(index, !row.optional)
                    },
                )
                if (index > 0) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.recipe_move_up)) }, onClick = {
                        menuOpen = false
                        viewModel.moveIngredient(index, -1)
                    })
                }
                if (index < count - 1) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.recipe_move_down)) }, onClick = {
                        menuOpen = false
                        viewModel.moveIngredient(index, 1)
                    })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.recipe_remove)) }, onClick = {
                    menuOpen = false
                    viewModel.removeIngredient(index)
                })
            }
        }
    }
    if (editingNote) {
        var note by remember { mutableStateOf(row.note) }
        AlertDialog(
            onDismissRequest = { editingNote = false },
            title = { Text(stringResource(R.string.recipe_note)) },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.recipe_note_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editingNote = false
                    viewModel.onNoteChange(index, note)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editingNote = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun StepRow(step: StepDraft, index: Int, count: Int, viewModel: RecipeEditViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var equipmentOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = step.text,
                onValueChange = { viewModel.onStepTextChange(index, it) },
                label = { Text(stringResource(R.string.recipe_step_number, index + 1)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (index > 0) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.recipe_move_up)) }, onClick = {
                            menuOpen = false
                            viewModel.moveStep(index, -1)
                        })
                    }
                    if (index < count - 1) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.recipe_move_down)) }, onClick = {
                            menuOpen = false
                            viewModel.moveStep(index, 1)
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.recipe_remove)) }, onClick = {
                        menuOpen = false
                        viewModel.removeStep(index)
                    })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                value = step.minutesText,
                onValueChange = { viewModel.onStepMinutesChange(index, it) },
                label = stringResource(R.string.recipe_step_minutes),
                decimal = false,
                modifier = Modifier.width(120.dp),
            )
            Box {
                OutlinedButton(onClick = { equipmentOpen = true }) {
                    Text(step.equipment?.let { equipmentLabel(it) } ?: stringResource(R.string.recipe_step_equipment))
                }
                DropdownMenu(expanded = equipmentOpen, onDismissRequest = { equipmentOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.recipe_equipment_none)) }, onClick = {
                        equipmentOpen = false
                        viewModel.onStepEquipmentChange(index, null)
                    })
                    (listOf(StepDraft.STOVE) + Equipment.entries.map { it.name }).forEach { name ->
                        DropdownMenuItem(text = { Text(equipmentLabel(name).orEmpty()) }, onClick = {
                            equipmentOpen = false
                            viewModel.onStepEquipmentChange(index, name)
                        })
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { viewModel.onStepPassiveChange(index, !step.passive) },
        ) {
            Checkbox(checked = step.passive, onCheckedChange = null)
            Text(stringResource(R.string.recipe_step_passive), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun CuisinePicker(cuisine: String?, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.recipe_cuisine), style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(cuisine?.let { cuisineLabelRes(it) }?.let { stringResource(it) } ?: stringResource(R.string.recipe_cuisine_none))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.recipe_cuisine_none)) }, onClick = {
                    open = false
                    onChange(null)
                })
                Cuisines.ALL.forEach { key ->
                    val label = cuisineLabelRes(key) ?: return@forEach
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                        open = false
                        onChange(key)
                    })
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

/** Recent foods, or a search with basic foods first (M3.2). */
@Composable
private fun FoodPickerDialog(
    query: String,
    results: List<Food>,
    onQueryChange: (String) -> Unit,
    onPick: (Food) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hideNumbers = LocalHideNumbers.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_pick_food)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.id }) { food ->
                        ListItem(
                            headlineContent = { Text(food.displayName(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = if (hideNumbers) {
                                food.brand?.let { brand -> { Text(brand) } }
                            } else {
                                {
                                    val kcal = formatKcal(food.per100g.kcal)
                                    Text(
                                        food.brand?.let { stringResource(R.string.food_per_100g_brand, it, kcal) }
                                            ?: stringResource(R.string.food_per_100g, kcal),
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onPick(food) },
                        )
                    }
                    item(key = "create") {
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                            headlineContent = { Text(stringResource(R.string.create_custom_food)) },
                            modifier = Modifier.clickable(onClick = onCreate),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
