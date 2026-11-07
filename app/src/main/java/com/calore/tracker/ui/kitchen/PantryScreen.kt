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
import androidx.compose.material3.Button
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
import com.calore.tracker.domain.plan.UseItUp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
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
import com.calore.tracker.data.household.RequestRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.Visibility
import com.calore.tracker.domain.household.Ownership
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** A planned cook that pantry food can be reserved for (M11.6). */
data class CookChoice(val id: String, val recipeName: String, val date: LocalDate)

/** A pantry line in a household (M11.6): whose it is, what the owner may do, and what it is reserved for. */
data class PantryRow(val entry: PantryEntry, val owner: Member?, val canTake: Boolean, val canChangeLabel: Boolean, val reservedFor: CookChoice?)

data class PantryHousehold(val inHousehold: Boolean = false, val rows: Map<String, PantryRow> = emptyMap(), val cooks: List<CookChoice> = emptyList())

/** The pantry (M5.5): what is at home, grouped by fridge, freezer and cupboard. */
class PantryViewModel(
    private val pantry: PantryRepository,
    private val foods: FoodRepository,
    private val clock: Clock,
    private val members: MemberRepository? = null,
    private val requests: RequestRepository? = null,
    inHousehold: Flow<Boolean> = flowOf(false),
    cooks: Flow<List<CookChoice>> = flowOf(emptyList()),
) : ViewModel() {
    val entries: StateFlow<List<PantryEntry>> = pantry.available().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val people: Flow<Triple<Member?, List<Member>, List<Member>>> = members?.let { m ->
        combine(m.me(), m.managed(), m.household()) { me, managed, household -> Triple<Member?, List<Member>, List<Member>>(me, managed, household) }
    } ?: flowOf(Triple(null, emptyList(), emptyList()))

    /** M11.6: labels, owners and reservations; empty outside a household. */
    val household: StateFlow<PantryHousehold> = combine(pantry.available(), people, inHousehold, cooks) { list, (me, managed, others), shared, cookList ->
        if (!shared || me == null) return@combine PantryHousehold(cooks = cookList)
        val byId = (listOf(me) + managed + others).associateBy { it.id }
        val managedIds = managed.mapTo(HashSet()) { it.id }
        val cookById = cookList.associateBy { it.id }
        PantryHousehold(
            inHousehold = true,
            rows = list.associate { entry ->
                val item = entry.item
                item.id to PantryRow(
                    entry = entry,
                    owner = item.ownerMemberId?.let(byId::get),
                    canTake = Ownership.canTake(item.ownerMemberId, item.visibility, me.id, managedIds),
                    canChangeLabel = Ownership.canChangeLabel(item.ownerMemberId, me.id, managedIds),
                    reservedFor = item.reservedForCookId?.let(cookById::get),
                )
            },
            cooks = cookList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryHousehold())

    fun setLabel(item: PantryItem, visibility: Visibility) {
        viewModelScope.launch {
            // Marking something "Mine" makes the person who marks it its owner, if it had none.
            val me = members?.me()?.first()?.id
            val owner = if (visibility == Visibility.MINE && item.ownerMemberId == null) me else item.ownerMemberId
            pantry.setOwnership(item, owner, visibility)
        }
    }

    fun reserve(item: PantryItem, cookId: String?) {
        viewModelScope.launch { pantry.reserve(item, cookId) }
    }

    fun askFor(row: PantryRow) {
        val repo = requests ?: return
        viewModelScope.launch { repo.canIHave("pantry_items", row.entry.item.id, row.entry.item.ownerMemberId, row.entry.item.name) }
    }
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
    onUseItUp: () -> Unit = {},
    viewModel: PantryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val household by viewModel.household.collectAsStateWithLifecycle()
    var reserving by remember { mutableStateOf<PantryItem?>(null) }
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
        // M6.4: food that goes off within 4 days, and recipes to use it up.
        val expiring = UseItUp.expiring(entries.map { it.item }, today).size
        if (expiring > 0) {
            item(key = "use_it_up") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(pluralStringResource(R.plurals.use_it_up_card, expiring, expiring), modifier = Modifier.weight(1f))
                        TextButton(onClick = onUseItUp) { Text(stringResource(R.string.use_it_up_title)) }
                    }
                }
            }
        }
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
                val row = household.rows[item.id]
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (date != null && storage != Storage.FREEZER) FreshnessDot(Freshness.colour(date, today))
                                Text(date?.let { stringResource(R.string.pantry_best_before, it.format(formatter)) } ?: stringResource(R.string.pantry_no_date))
                            }
                            if (row != null) {
                                OwnershipLabel(item.visibility, row.owner)
                                row.reservedFor?.let { ReservedText(it, row.owner) }
                            }
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
        val row = household.rows[item.id]
        EditPantryDialog(
            item = item,
            today = today,
            row = row,
            onLabel = {
                editing = null
                viewModel.setLabel(item, it)
            },
            onReserve = {
                editing = null
                reserving = item
            },
            onAsk = {
                editing = null
                row?.let(viewModel::askFor)
            },
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
    reserving?.let { item ->
        AlertDialog(
            onDismissRequest = { reserving = null },
            title = { Text(stringResource(R.string.pantry_reserve_title, item.name)) },
            text = {
                Column {
                    ListItem(headlineContent = { Text(stringResource(R.string.pantry_not_reserved)) }, modifier = Modifier.clickable {
                        viewModel.reserve(item, null)
                        reserving = null
                    })
                    if (household.cooks.isEmpty()) Text(stringResource(R.string.pantry_no_cooks), style = MaterialTheme.typography.bodySmall)
                    household.cooks.forEach { cook ->
                        ListItem(
                            headlineContent = { Text(cook.recipeName) },
                            supportingContent = { Text(cook.date.format(formatter)) },
                            modifier = Modifier.clickable {
                                viewModel.reserve(item, cook.id)
                                reserving = null
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { reserving = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ReservedText(cook: CookChoice, owner: Member?) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val day = cook.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
    Text(
        if (owner != null) stringResource(R.string.pantry_reserved_of, owner.name, cook.recipeName, day)
        else stringResource(R.string.pantry_reserved, cook.recipeName, day),
        style = MaterialTheme.typography.bodySmall,
    )
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
    row: PantryRow?,
    onLabel: (Visibility) -> Unit,
    onReserve: () -> Unit,
    onAsk: () -> Unit,
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
                if (row != null) {
                    // M11.6, in a household: the label (the owner changes it), reserving, and asking first.
                    OwnershipLabel(item.visibility, row.owner)
                    if (row.canChangeLabel) LabelPicker(item.visibility, onLabel)
                    TextButton(onClick = onReserve) { Text(stringResource(R.string.pantry_reserve)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row == null || row.canTake) {
                        OutlinedButton(onClick = onUsedUp) { Text(stringResource(R.string.pantry_used_up)) }
                        OutlinedButton(onClick = onThrowAway) { Text(stringResource(R.string.box_throw_away)) }
                    } else {
                        Button(onClick = onAsk) { Text(stringResource(R.string.request_can_i_have)) }
                    }
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

