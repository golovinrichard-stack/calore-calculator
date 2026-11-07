package com.calore.tracker.ui.kitchen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.data.repository.PantryEntry
import com.calore.tracker.data.repository.PantryRepository
import com.calore.tracker.domain.AmountUnit
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.Freshness
import com.calore.tracker.domain.Pantry
import com.calore.tracker.domain.PantryItem
import com.calore.tracker.domain.Storage
import com.calore.tracker.domain.parseDecimal
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.ui.formatGrams
import com.calore.tracker.ui.recipes.FoodPickerDialog
import com.calore.tracker.ui.recipes.FoodSearch
import com.calore.tracker.ui.rememberMediumDateFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** The pantry (M5.5): what is at home, grouped by fridge, freezer and cupboard. */
class PantryViewModel(
    private val pantry: PantryRepository,
    private val foods: FoodRepository,
    private val clock: Clock,
) : ViewModel() {
    val entries: StateFlow<List<PantryEntry>> = pantry.available().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val picker = FoodSearch(foods, viewModelScope)
    fun today(): LocalDate = LocalDate.now(clock)

    fun add(food: Food, amount: Double, unit: AmountUnit, storage: Storage, bestBefore: LocalDate?) {
        viewModelScope.launch { pantry.add(food, food.displayName(), amount, unit, storage, bestBefore) }
    }

    suspend fun food(id: Long): Food? = foods.getFood(id)

    fun save(item: PantryItem) {
        viewModelScope.launch { pantry.update(item) }
    }

    fun usedUp(item: PantryItem) {
        viewModelScope.launch { pantry.usedUp(item) }
    }

    fun throwAway(item: PantryItem) {
        viewModelScope.launch { pantry.throwAway(item) }
    }
}

fun Storage.labelRes(): Int = when (this) {
    Storage.FRIDGE -> R.string.kitchen_fridge
    Storage.FREEZER -> R.string.kitchen_freezer
    Storage.CUPBOARD -> R.string.storage_cupboard
}

fun AmountUnit.labelRes(): Int = when (this) {
    AmountUnit.G -> R.string.unit_g
    AmountUnit.ML -> R.string.unit_ml
    AmountUnit.PIECE -> R.string.unit_pieces
}

/**
 * The Pantry tab. [adding] opens the food picker (from the screen's Add button); [scannedFoodId]
 * is a food picked with the scanner, waiting for its amount.
 */
@Composable
fun PantryContent(
    adding: Boolean,
    onAddingDone: () -> Unit,
    scannedFoodId: Long?,
    onScannedFoodUsed: () -> Unit,
    viewModel: PantryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val picker by viewModel.picker.state.collectAsStateWithLifecycle()
    var chosenFood by remember { mutableStateOf<Food?>(null) }
    var editing by remember { mutableStateOf<PantryItem?>(null) }
    val today = viewModel.today()
    val formatter = rememberMediumDateFormatter()

    LaunchedEffect(scannedFoodId) {
        if (scannedFoodId != null) {
            chosenFood = viewModel.food(scannedFoodId)
            onScannedFoodUsed()
        }
    }
    LaunchedEffect(adding) { if (adding) viewModel.picker.onQueryChange("") }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (entries.isEmpty()) {
            item(key = "empty") { Text(stringResource(R.string.pantry_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        Storage.entries.forEach { storage ->
            val here = entries.filter { it.item.storage == storage }
            if (here.isEmpty()) return@forEach
            item(key = "h_$storage") { SectionHeader(stringResource(storage.labelRes()), modifier = Modifier.padding(start = 16.dp, top = 16.dp)) }
            items(here, key = { it.item.id }) { entry ->
                val item = entry.item
                val date = item.effectiveBestBefore
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (date != null && storage != Storage.FREEZER) FreshnessDot(Freshness.colour(date, today))
                            Text(date?.let { stringResource(R.string.pantry_best_before, it.format(formatter)) } ?: stringResource(R.string.pantry_no_date))
                        }
                    },
                    trailingContent = { Text(stringResource(R.string.pantry_amount_value, formatGrams(item.amount), stringResource(item.unit.labelRes()))) },
                    modifier = Modifier.clickable { editing = item },
                )
            }
        }
    }

    if (adding && chosenFood == null) {
        FoodPickerDialog(
            query = picker.query,
            results = picker.results,
            onQueryChange = viewModel.picker::onQueryChange,
            onPick = {
                chosenFood = it
                onAddingDone()
            },
            onCreate = null,
            onDismiss = onAddingDone,
        )
    }
    chosenFood?.let { food ->
        AddPantryDialog(
            food = food,
            today = today,
            onSave = { amount, unit, storage, date ->
                chosenFood = null
                viewModel.add(food, amount, unit, storage, date)
            },
            onDismiss = { chosenFood = null },
        )
    }
    editing?.let { item ->
        EditPantryDialog(
            item = item,
            today = today,
            onSave = {
                editing = null
                viewModel.save(it)
            },
            onUsedUp = {
                editing = null
                viewModel.usedUp(item)
            },
            onThrowAway = {
                editing = null
                viewModel.throwAway(item)
            },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPantryDialog(food: Food, today: LocalDate, onSave: (Double, AmountUnit, Storage, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val (defaultDays, defaultStorage) = Pantry.defaults(food.section)
    var amountText by rememberSaveable { mutableStateOf(food.defaultPackGrams?.let { formatEditable(it) }.orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(AmountUnit.G) }
    var storage by rememberSaveable { mutableStateOf(food.storage ?: defaultStorage) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val amount = parseDecimal(amountText)?.takeIf { it > 0 }
    val formatter = rememberMediumDateFormatter()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.displayName()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = stringResource(R.string.pantry_amount),
                    suffix = stringResource(unit.labelRes()),
                    errorText = if (amountText.isNotEmpty() && amount == null) stringResource(R.string.error_amount) else null,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountUnit.entries.filter { it != AmountUnit.PIECE || food.gramsPerPiece != null }.forEach { option ->
                        FilterChip(selected = unit == option, onClick = { unit = option }, label = { Text(stringResource(option.labelRes())) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Storage.entries.forEach { option ->
                        FilterChip(selected = storage == option, onClick = { storage = option }, label = { Text(stringResource(option.labelRes())) })
                    }
                }
                OutlinedButton(onClick = { picking = true }) {
                    // Without a date from the pack, the default for the shop section applies (A17).
                    val shown = date ?: today.plusDays(defaultDays)
                    Text(stringResource(R.string.pantry_best_before, shown.format(formatter)))
                }
            }
        },
        confirmButton = { TextButton(onClick = { amount?.let { onSave(it, unit, storage, date) } }, enabled = amount != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) {
        DatePickerModal(initial = date ?: today.plusDays(defaultDays), onDismiss = { picking = false }, onConfirm = {
            date = it
            picking = false
        })
    }
}

@Composable
private fun EditPantryDialog(
    item: PantryItem,
    today: LocalDate,
    onSave: (PantryItem) -> Unit,
    onUsedUp: () -> Unit,
    onThrowAway: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(formatEditable(item.amount)) }
    var date by rememberSaveable { mutableStateOf(item.bestBeforeDate) }
    var opened by rememberSaveable { mutableStateOf(item.openedDate) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val amount = parseDecimal(amountText)?.takeIf { it >= 0 }
    val formatter = rememberMediumDateFormatter()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = stringResource(R.string.pantry_amount),
                    suffix = stringResource(item.unit.labelRes()),
                    errorText = if (amount == null) stringResource(R.string.error_amount) else null,
                )
                OutlinedButton(onClick = { picking = true }) {
                    Text(date?.let { stringResource(R.string.pantry_best_before, it.format(formatter)) } ?: stringResource(R.string.pantry_set_date))
                }
                if (opened == null) {
                    OutlinedButton(onClick = { opened = today }) { Text(stringResource(R.string.pantry_open_today)) }
                } else {
                    Text(stringResource(R.string.pantry_opened, opened!!.format(formatter)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onUsedUp) { Text(stringResource(R.string.pantry_used_up)) }
                    OutlinedButton(onClick = onThrowAway) { Text(stringResource(R.string.box_throw_away)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let { onSave(item.copy(amount = it, bestBeforeDate = date, openedDate = opened)) } }, enabled = amount != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) {
        DatePickerModal(initial = date ?: today, onDismiss = { picking = false }, onConfirm = {
            date = it
            picking = false
        })
    }
}

