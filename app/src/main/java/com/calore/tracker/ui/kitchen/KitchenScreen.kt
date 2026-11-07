package com.calore.tracker.ui.kitchen

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import com.calore.tracker.ui.rating.RatePromptCard
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.FreshnessColour
import com.calore.tracker.domain.Freshness
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.Storage
import com.calore.tracker.domain.WasteReason
import com.calore.tracker.domain.parsePortionGrams
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.NutrientTable
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatGrams
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.formatNumber
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.rememberMediumDateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(
    onOpenWaste: () -> Unit,
    onScanForPantry: () -> Unit = {},
    onUseItUp: () -> Unit = {},
    scannedFoodId: Long? = null,
    onScannedFoodUsed: () -> Unit = {},
    viewModel: KitchenViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPantry by rememberSaveable { mutableStateOf(false) }
    var addingToPantry by rememberSaveable { mutableStateOf(false) }
    // A scanned food goes to the pantry tab.
    LaunchedEffect(scannedFoodId) { if (scannedFoodId != null) showPantry = true }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is KitchenEvent.Eaten -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.box_logged, event.code),
                        actionLabel = resources.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.eaten)
                }
                KitchenEvent.CantFreeze -> snackbarHostState.showSnackbar(resources.getString(R.string.box_cant_freeze))
                is KitchenEvent.Asked -> snackbarHostState.showSnackbar(resources.getString(R.string.request_sent_to, event.owner))
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_kitchen)) },
                    actions = {
                        if (showPantry) {
                            IconButton(onClick = onScanForPantry) { Icon(painterResource(R.drawable.ic_barcode), stringResource(R.string.scanner_title)) }
                        } else {
                            TextButton(onClick = onOpenWaste) { Text(stringResource(R.string.kitchen_thrown_away)) }
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = if (showPantry) 1 else 0) {
                    Tab(selected = !showPantry, onClick = { showPantry = false }, text = { Text(stringResource(R.string.kitchen_tab_boxes)) })
                    Tab(selected = showPantry, onClick = { showPantry = true }, text = { Text(stringResource(R.string.kitchen_tab_pantry)) })
                }
            }
        },
        floatingActionButton = {
            if (showPantry) {
                ExtendedFloatingActionButton(
                    onClick = { addingToPantry = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.pantry_add)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (showPantry) {
            Box(modifier = Modifier.padding(padding)) {
                PantryContent(
                    adding = addingToPantry,
                    onAddingDone = { addingToPantry = false },
                    scannedFoodId = scannedFoodId,
                    onScannedFoodUsed = onScannedFoodUsed,
                    onUseItUp = onUseItUp,
                )
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "filters") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = state.mineOnly, onClick = { viewModel.setMineOnly(true) }, label = { Text(stringResource(R.string.kitchen_filter_mine)) })
                    FilterChip(selected = !state.mineOnly, onClick = { viewModel.setMineOnly(false) }, label = { Text(stringResource(R.string.kitchen_filter_all)) })
                }
            }
            item(key = "rate") { RatePromptCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            if (!state.loading && state.isEmpty) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.kitchen_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            boxSection("fridge", R.string.kitchen_fridge, state.fridge, state.today, state.inHousehold) { openId = it.box.id }
            boxSection("freezer", R.string.kitchen_freezer, state.freezer, state.today, state.inHousehold) { openId = it.box.id }
        }
    }

    val open = (state.fridge + state.freezer).firstOrNull { it.box.id == openId }
    if (open != null) {
        BoxSheet(item = open, today = state.today, inHousehold = state.inHousehold, viewModel = viewModel, onDismiss = { openId = null })
    }
}

private fun LazyListScope.boxSection(key: String, title: Int, items: List<KitchenItem>, today: LocalDate, inHousehold: Boolean, onClick: (KitchenItem) -> Unit) {
    if (items.isEmpty()) return
    item(key = "${key}_header") {
        SectionHeader(stringResource(title), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
    }
    items(items, key = { it.box.id }) { item -> BoxRow(item, today, inHousehold, onClick = { onClick(item) }) }
}

@Composable
private fun BoxRow(item: KitchenItem, today: LocalDate, inHousehold: Boolean, onClick: () -> Unit) {
    val box = item.box
    val frozen = box.storage == Storage.FREEZER
    val colour = item.view.colour(today)
    val formatter = rememberMediumDateFormatter()
    val hideNumbers = LocalHideNumbers.current
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Text(
                box.code,
                fontWeight = FontWeight.Bold,
                color = item.owner?.let { Color(it.colour) } ?: MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        headlineContent = { Text(item.view.name) },
        supportingContent = {
            Column {
                if (!frozen) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FreshnessDot(colour)
                        Text(freshnessLabel(colour) + " · " + stringResource(R.string.box_eat_by, box.eatByDate.format(formatter)))
                    }
                }
                val owner = item.owner?.name
                val planned = box.plannedDate?.let { date ->
                    stringResource(R.string.box_planned, date.format(formatter), box.plannedMeal?.let { stringResource(it.labelRes()) } ?: "")
                }
                listOfNotNull(owner, planned).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) }
                if (inHousehold) OwnershipLabel(box.visibility, item.owner)
            }
        },
        trailingContent = if (hideNumbers) {
            null
        } else {
            { Text(stringResource(R.string.value_kcal, formatKcal(item.view.remainingNutrients.kcal))) }
        },
    )
}

/** Box detail and actions (M4.6, M4.7). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BoxSheet(item: KitchenItem, today: LocalDate, inHousehold: Boolean, viewModel: KitchenViewModel, onDismiss: () -> Unit) {
    val box = item.box
    val frozen = box.storage == Storage.FREEZER
    val colour = item.view.colour(today)
    val formatter = rememberMediumDateFormatter()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val eat = { grams: Double? ->
        viewModel.eat(item, grams)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${box.code} · ${item.view.name}", style = MaterialTheme.typography.titleLarge)
            Text(item.owner?.name ?: stringResource(R.string.box_owner_everyone), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (inHousehold) {
                OwnershipLabel(box.visibility, item.owner)
                if (item.canChangeLabel) LabelPicker(box.visibility) { viewModel.setVisibility(box, it) }
            }
            if (frozen) {
                val frozenDay = box.frozenAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                if (frozenDay != null && Freshness.freezerQualityOver(frozenDay, Recipe.DEFAULT_FREEZER_MONTHS, today)) {
                    Text(stringResource(R.string.box_freezer_quality), color = MaterialTheme.colorScheme.error)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FreshnessDot(colour)
                    Text(freshnessLabel(colour) + " · " + stringResource(R.string.box_eat_by, box.eatByDate.format(formatter)))
                }
            }
            if (box.remainingFraction < 1.0) Text(stringResource(R.string.box_left, (box.remainingFraction * 100).toInt()))

            SectionHeader(stringResource(R.string.box_contents))
            item.view.parts.forEach { (part, batch) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(batch?.name ?: "—")
                        batch?.let {
                            Text(
                                viewModel.cookedDay(it.cookedAt).format(formatter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(stringResource(R.string.value_g, formatGrams(part.grams)))
                }
            }
            if (!LocalHideNumbers.current) NutrientTable(item.view.remainingNutrients)
            item.view.costHalers?.let { cost ->
                Text(stringResource(R.string.box_cost, formatNumber(cost * box.remainingFraction / 100, decimals = 0)))
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.canTake) {
                    Button(onClick = { if (colour == FreshnessColour.RED && !frozen) dialog = "red" else eat(null) }) {
                        Text(stringResource(R.string.box_ate_it))
                    }
                    OutlinedButton(onClick = { dialog = "part" }) { Text(stringResource(R.string.box_ate_part)) }
                } else {
                    // M11.6: someone else's "Mine, don't touch" box.
                    Button(onClick = {
                        viewModel.askFor(item)
                        onDismiss()
                    }) { Text(stringResource(R.string.request_can_i_have)) }
                }
                if (frozen) {
                    OutlinedButton(onClick = {
                        viewModel.thaw(box)
                        onDismiss()
                    }) { Text(stringResource(R.string.box_thawed)) }
                } else {
                    OutlinedButton(onClick = {
                        viewModel.freeze(box)
                        onDismiss()
                    }) { Text(stringResource(R.string.box_freeze)) }
                }
                OutlinedButton(onClick = { dialog = "move" }) { Text(stringResource(R.string.box_move)) }
                OutlinedButton(onClick = { dialog = "waste" }) { Text(stringResource(R.string.box_throw_away)) }
            }
        }
    }

    when (dialog) {
        "red" -> AlertDialog(
            onDismissRequest = { dialog = null },
            text = { Text(stringResource(R.string.box_red_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    eat(null)
                }) { Text(stringResource(R.string.box_eat_anyway)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        "part" -> AtePartDialog(
            leftGrams = item.view.fullGrams * box.remainingFraction,
            onSave = { grams ->
                dialog = null
                eat(grams)
            },
            onDismiss = { dialog = null },
        )
        "waste" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.box_throw_away_title)) },
            text = {
                Column {
                    WasteReason.entries.forEach { reason ->
                        ListItem(
                            headlineContent = { Text(stringResource(reason.labelRes())) },
                            modifier = Modifier.clickable {
                                dialog = null
                                viewModel.throwAway(box, reason)
                                onDismiss()
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        "move" -> MoveDialog(
            date = box.plannedDate,
            meal = box.plannedMeal,
            today = today,
            onSave = { date, meal ->
                dialog = null
                viewModel.move(box, date, meal)
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun AtePartDialog(leftGrams: Double, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val grams = parsePortionGrams(text)?.takeIf { it <= leftGrams + 0.5 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.box_ate_part_title)) },
        text = {
            NumberField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.box_part_grams),
                suffix = stringResource(R.string.unit_g),
                errorText = if (text.isNotEmpty() && grams == null) stringResource(R.string.error_amount) else null,
            )
        },
        confirmButton = { TextButton(onClick = { grams?.let(onSave) }, enabled = grams != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveDialog(date: LocalDate?, meal: MealType?, today: LocalDate, onSave: (LocalDate?, MealType?) -> Unit, onDismiss: () -> Unit) {
    var chosenDate by rememberSaveable { mutableStateOf(date) }
    var chosenMeal by rememberSaveable { mutableStateOf(meal) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val formatter = rememberMediumDateFormatter()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.box_move_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = true }) {
                        Text(chosenDate?.format(formatter) ?: stringResource(R.string.box_move_none))
                    }
                    if (chosenDate != null) TextButton(onClick = { chosenDate = null }) { Text(stringResource(R.string.box_move_none)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { option ->
                        FilterChip(
                            selected = option == chosenMeal,
                            onClick = { chosenMeal = if (chosenMeal == option) null else option },
                            label = { Text(stringResource(option.labelRes())) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosenDate, chosenMeal) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) {
        DatePickerModal(
            initial = chosenDate ?: today,
            onDismiss = { picking = false },
            onConfirm = {
                chosenDate = it
                picking = false
            },
        )
    }
}
