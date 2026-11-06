package com.calore.tracker.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.WeightEntry
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.formatKg
import com.calore.tracker.ui.rememberMediumDateFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(viewModel: WeightViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val dateFormatter = rememberMediumDateFormatter()
    val newestFirst = remember(state.entries) { state.entries.asReversed() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.weight_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "input") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value = state.input.text,
                            onValueChange = viewModel::onTextChange,
                            label = stringResource(R.string.weight_input),
                            suffix = stringResource(R.string.unit_kg),
                            errorText = if (state.input.showError) stringResource(R.string.error_weight_entry) else null,
                            imeAction = ImeAction.Done,
                            onDone = viewModel::save,
                        )
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Text(stringResource(R.string.weight_date, dateLabel(state.input.date, state.today, dateFormatter)))
                        }
                        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.weight_save))
                        }
                    }
                }
            }

            item(key = "chart") {
                ChartCard(entries = state.entries, dateFormatter = dateFormatter)
            }

            items(newestFirst, key = { it.id }) { entry ->
                val dateText = dateLabel(entry.date, state.today, dateFormatter)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.value_kg, formatKg(entry.kg))) },
                    supportingContent = { Text(dateText) },
                    trailingContent = {
                        IconButton(onClick = {
                            viewModel.delete(entry)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = resources.getString(R.string.weight_removed),
                                    actionLabel = resources.getString(R.string.undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
                            }
                        }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.delete_weight, dateText))
                        }
                    },
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initial = state.input.date,
            latest = state.today,
            onDismiss = { showDatePicker = false },
            onConfirm = viewModel::onDateChange,
        )
    }
}

@Composable
private fun ChartCard(entries: List<WeightEntry>, dateFormatter: DateTimeFormatter) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                entries.isEmpty() -> Text(
                    stringResource(R.string.weight_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entries.size == 1 -> Text(
                    stringResource(R.string.weight_chart_need_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val first = entries.first()
                    val last = entries.last()
                    val change = last.kg - first.kg
                    val sign = if (change > 0) "+" else ""
                    Text(
                        text = stringResource(R.string.weight_change, sign + formatKg(change), first.date.format(dateFormatter)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    WeightChart(
                        entries = entries,
                        description = stringResource(
                            R.string.weight_chart_description,
                            formatKg(first.kg),
                            first.date.format(dateFormatter),
                            formatKg(last.kg),
                            last.date.format(dateFormatter),
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.weight_range,
                            formatKg(entries.minOf { it.kg }),
                            formatKg(entries.maxOf { it.kg }),
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
private fun dateLabel(date: LocalDate, today: LocalDate, formatter: DateTimeFormatter): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> date.format(formatter)
}
