package com.calore.tracker.ui.supplements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.DoseUnit
import com.calore.tracker.domain.Supplement
import com.calore.tracker.domain.SupplementKind
import com.calore.tracker.domain.SupplementStatus
import com.calore.tracker.notifications.labelRes
import com.calore.tracker.notifications.rememberNotificationPermission
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatNumber
import com.calore.tracker.ui.rememberMediumDateFormatter
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun doseText(supplement: Supplement): String =
    formatNumber(supplement.doseAmount, decimals = 2) + " " + stringResource(supplement.doseUnit.labelRes())

/** Me → Supplements (M8): the list with how regularly each was taken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementsScreen(
    onBack: () -> Unit,
    onOpen: (String?) -> Unit,
    viewModel: SupplementsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.supplements_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpen(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.supplement_add)) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (state.loaded && state.rows.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.supplements_empty), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.rows, key = { it.supplement.id }) { row ->
                val s = row.supplement
                ListItem(
                    headlineContent = { Text(s.name) },
                    supportingContent = {
                        Column {
                            Text(doseText(s) + " · " + s.times.joinToString(", ") { it.format(timeFormat) })
                            Text(
                                listOfNotNull(
                                    row.week.percent?.let { stringResource(R.string.supplement_regular_7, row.week.taken, row.week.due) },
                                    row.month.percent?.let { stringResource(R.string.supplement_regular_30, row.month.taken, row.month.due) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (row.history.isNotEmpty()) {
                                Text(
                                    row.history.reversed().joinToString(" ") { (_, status) ->
                                        when (status) {
                                            SupplementStatus.TAKEN -> "●"
                                            SupplementStatus.SKIPPED -> "✕"
                                            null -> "○"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    trailingContent = { if (s.remindersOn) Text(stringResource(R.string.supplement_reminders_on), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.clickable { onOpen(s.id) },
                )
            }
            if (state.rows.isNotEmpty()) {
                item(key = "legend") {
                    Text(stringResource(R.string.supplement_history_legend), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Today's supplements (M8.3): the doses due today with Taken and Skip. Nothing when there are none. */
@Composable
fun SupplementsTodayCard(viewModel: SupplementsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.today.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.supplements_today), style = MaterialTheme.typography.titleMedium)
            state.today.forEach { item ->
                val dose = item.dose
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dose.supplement.name)
                        Text(dose.time.format(timeFormat) + " · " + doseText(dose.supplement), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (item.status) {
                        null -> {
                            TextButton(onClick = { viewModel.take(dose) }) { Text(stringResource(R.string.supplement_taken)) }
                            TextButton(onClick = { viewModel.skip(dose) }) { Text(stringResource(R.string.supplement_skip)) }
                        }
                        else -> {
                            Text(
                                stringResource(if (item.status == SupplementStatus.TAKEN) R.string.supplement_was_taken else R.string.supplement_was_skipped),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = { viewModel.undo(dose) }) { Text(stringResource(R.string.undo)) }
                        }
                    }
                }
            }
        }
    }
}

/** Adding or changing a supplement (M8.2). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupplementEditScreen(
    onBack: () -> Unit,
    viewModel: SupplementEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val formatter = rememberMediumDateFormatter()
    var addingTime by rememberSaveable { mutableStateOf(false) }
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val askPermission = rememberNotificationPermission { granted ->
        permissionDenied = !granted
        if (granted) viewModel.update { it.copy(reminders = true) }
    }
    LaunchedEffect(viewModel) { viewModel.done.collect { onBack() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) R.string.supplement_add else R.string.supplement_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { TextButton(onClick = viewModel::save) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { text -> viewModel.update { it.copy(name = text) } },
                label = { Text(stringResource(R.string.supplement_name)) },
                isError = form.attemptedSave && !form.nameValid,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader(stringResource(R.string.supplement_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupplementKind.entries.forEach { kind ->
                    FilterChip(
                        selected = form.kind == kind,
                        onClick = {
                            viewModel.update { f ->
                                // Picking a kind fills in a usual unit and a name, if none was typed.
                                f.copy(kind = kind, unit = kind.usualUnit() ?: f.unit, name = f.name)
                            }
                        },
                        label = { Text(stringResource(kind.labelRes())) },
                    )
                }
            }
            NumberField(
                value = form.dose,
                onValueChange = { text -> viewModel.update { it.copy(dose = text) } },
                label = stringResource(R.string.supplement_dose),
                errorText = if (form.attemptedSave && form.doseValue == null) stringResource(R.string.supplement_dose_error) else null,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DoseUnit.entries.forEach { unit ->
                    FilterChip(selected = form.unit == unit, onClick = { viewModel.update { it.copy(unit = unit) } }, label = { Text(stringResource(unit.labelRes())) })
                }
            }
            SectionHeader(stringResource(R.string.supplement_times))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                form.times.sorted().forEach { time ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(time.format(timeFormat)) },
                        trailingIcon = {
                            if (form.times.size > 1) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.supplement_remove_time),
                                    modifier = Modifier.clickable { viewModel.update { f -> f.copy(times = f.times - time) } },
                                )
                            }
                        },
                    )
                }
                TextButton(onClick = { addingTime = true }) { Text(stringResource(R.string.supplement_add_time)) }
            }
            SectionHeader(stringResource(R.string.supplement_days))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in form.days,
                        onClick = { viewModel.update { f -> f.copy(days = if (day in f.days) f.days - day else f.days + day) } },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                    )
                }
            }
            Text(stringResource(R.string.supplement_days_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingStart = true }) { Text(stringResource(R.string.supplement_from, form.startDate.format(formatter))) }
                OutlinedButton(onClick = { pickingEnd = true }) {
                    Text(form.endDate?.let { stringResource(R.string.supplement_until, it.format(formatter)) } ?: stringResource(R.string.supplement_no_end))
                }
            }
            if (form.attemptedSave && !form.datesValid) Text(stringResource(R.string.supplement_dates_error), color = MaterialTheme.colorScheme.error)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.supplement_reminders))
                    Text(stringResource(R.string.supplement_reminders_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = form.reminders, onCheckedChange = { on -> if (on) askPermission() else viewModel.update { it.copy(reminders = false) } })
            }
            if (permissionDenied) Text(stringResource(R.string.notify_permission_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            SectionHeader(stringResource(R.string.supplement_nutrition))
            Text(stringResource(R.string.supplement_nutrition_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(form.kcal, { t -> viewModel.update { it.copy(kcal = t) } }, stringResource(R.string.goal_kcal), Modifier.weight(1f))
                NumberField(form.protein, { t -> viewModel.update { it.copy(protein = t) } }, stringResource(R.string.goal_protein), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(form.carbs, { t -> viewModel.update { it.copy(carbs = t) } }, stringResource(R.string.goal_carbs), Modifier.weight(1f))
                NumberField(form.fat, { t -> viewModel.update { it.copy(fat = t) } }, stringResource(R.string.goal_fat), Modifier.weight(1f))
            }
            if (form.attemptedSave && !form.nutritionValid) Text(stringResource(R.string.supplement_nutrition_error), color = MaterialTheme.colorScheme.error)

            if (!viewModel.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.supplement_active), modifier = Modifier.weight(1f))
                    Switch(checked = form.active, onCheckedChange = { on -> viewModel.update { it.copy(active = on) } })
                }
                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.supplement_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (addingTime) {
        val pickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { addingTime = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    addingTime = false
                    val time = LocalTime.of(pickerState.hour, pickerState.minute)
                    viewModel.update { f -> f.copy(times = (f.times + time).distinct().sorted()) }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { addingTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pickingStart) {
        DatePickerModal(initial = form.startDate, onDismiss = { pickingStart = false }, onConfirm = { date ->
            pickingStart = false
            viewModel.update { it.copy(startDate = date) }
        })
    }
    if (pickingEnd) {
        DatePickerModal(initial = form.endDate ?: form.startDate, onDismiss = {
            pickingEnd = false
            viewModel.update { it.copy(endDate = null) }
        }, onConfirm = { date ->
            pickingEnd = false
            viewModel.update { it.copy(endDate = date) }
        })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.supplement_delete_confirm, form.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.supplement_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun SupplementKind.usualUnit(): DoseUnit? = when (this) {
    SupplementKind.VITAMIN_D -> DoseUnit.IU
    SupplementKind.IRON, SupplementKind.MAGNESIUM -> DoseUnit.MG
    SupplementKind.CREATINE -> DoseUnit.G
    SupplementKind.PROTEIN_POWDER -> DoseUnit.SCOOP
    SupplementKind.OMEGA_3, SupplementKind.MULTIVITAMIN -> DoseUnit.CAPSULE
    SupplementKind.OTHER -> null
}

private fun SupplementKind.labelRes(): Int = when (this) {
    SupplementKind.VITAMIN_D -> R.string.supplement_kind_vitamin_d
    SupplementKind.IRON -> R.string.supplement_kind_iron
    SupplementKind.MAGNESIUM -> R.string.supplement_kind_magnesium
    SupplementKind.CREATINE -> R.string.supplement_kind_creatine
    SupplementKind.PROTEIN_POWDER -> R.string.supplement_kind_protein
    SupplementKind.OMEGA_3 -> R.string.supplement_kind_omega3
    SupplementKind.MULTIVITAMIN -> R.string.supplement_kind_multivitamin
    SupplementKind.OTHER -> R.string.supplement_kind_other
}
