package com.calore.tracker.ui.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calore.tracker.R
import com.calore.tracker.domain.cycle.CyclePeriod
import com.calore.tracker.domain.cycle.CycleSettings
import com.calore.tracker.domain.cycle.Cycles
import com.calore.tracker.domain.cycle.PeriodProblem
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.rememberMediumDateFormatter
import java.time.LocalDate

/**
 * "Adjust my cycle" (0.14): every body is different, so the user can set their own cycle and
 * period length, choose to always use them, and fix or add logged periods by hand.
 */
@Composable
fun AdjustCycleDialog(state: CycleUiState, viewModel: CycleViewModel, onDismiss: () -> Unit) {
    val settings = state.settings
    var cycleText by remember { mutableStateOf(settings.defaultCycleLength.toString()) }
    var periodText by remember { mutableStateOf(settings.defaultPeriodLength.toString()) }
    var own by remember { mutableStateOf(settings.ownLengths) }
    var editing by remember { mutableStateOf<PeriodDraft?>(null) }
    var deleting by remember { mutableStateOf<CyclePeriod?>(null) }
    val cycleLength = cycleText.toIntOrNull()?.takeIf { it in CycleSettings.CYCLE_LENGTHS }
    val periodLength = periodText.toIntOrNull()?.takeIf { it in CycleSettings.PERIOD_LENGTHS }
    val learned = Cycles.stats(state.periods, settings.defaultCycleLength)
    val learnedPeriod = Cycles.periodLength(state.periods, settings.defaultPeriodLength)
    val formatter = rememberMediumDateFormatter()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cycle_adjust)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cycle_adjust_intro), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (learned.lengths.size >= 2) {
                        stringResource(R.string.cycle_adjust_learned, days(learned.averageDays), days(learnedPeriod))
                    } else {
                        stringResource(R.string.cycle_adjust_not_learned)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumberField(
                    value = cycleText,
                    onValueChange = { cycleText = it },
                    label = stringResource(R.string.cycle_usual_length),
                    decimal = false,
                    errorText = if (cycleLength == null) stringResource(R.string.cycle_adjust_cycle_range) else null,
                )
                NumberField(
                    value = periodText,
                    onValueChange = { periodText = it },
                    label = stringResource(R.string.cycle_usual_period),
                    decimal = false,
                    errorText = if (periodLength == null) stringResource(R.string.cycle_adjust_period_range) else null,
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cycle_adjust_own))
                        Text(
                            stringResource(if (own) R.string.cycle_adjust_own_on else R.string.cycle_lengths_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = own, onCheckedChange = { own = it })
                }
                HorizontalDivider()
                SectionHeader(stringResource(R.string.cycle_adjust_periods))
                if (state.periods.isEmpty()) {
                    Text(stringResource(R.string.cycle_adjust_no_periods), style = MaterialTheme.typography.bodySmall)
                }
                state.periods.sortedByDescending { it.start }.forEach { period ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            period.start.format(formatter) + " – " + (period.end?.format(formatter) ?: stringResource(R.string.cycle_adjust_still_going)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { editing = PeriodDraft(period.id, period.start, period.end) }) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.cycle_adjust_edit_period))
                        }
                        IconButton(onClick = { deleting = period }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.cycle_adjust_delete_period))
                        }
                    }
                }
                OutlinedButton(onClick = { editing = PeriodDraft(null, state.today, state.today) }) {
                    Text(stringResource(R.string.cycle_adjust_add_period))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (cycleLength != null && periodLength != null) {
                        viewModel.setLengths(cycleLength, periodLength, own)
                        onDismiss()
                    }
                },
                enabled = cycleLength != null && periodLength != null,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    editing?.let { draft ->
        PeriodEditDialog(draft, today = state.today, onSave = { start, end, onProblem ->
            viewModel.savePeriod(draft.id, start, end) { problem -> if (problem == null) editing = null else onProblem(problem) }
        }, onDismiss = { editing = null })
    }
    deleting?.let { period ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.cycle_adjust_delete_confirm, period.start.format(formatter))) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePeriod(period)
                    deleting = null
                }) { Text(stringResource(R.string.cycle_adjust_delete_period), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A period being changed (id) or added (id null). */
data class PeriodDraft(val id: String?, val start: LocalDate, val end: LocalDate?)

@Composable
private fun PeriodEditDialog(
    draft: PeriodDraft,
    today: LocalDate,
    onSave: (LocalDate, LocalDate?, (PeriodProblem) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = rememberMediumDateFormatter()
    var start by remember { mutableStateOf(draft.start) }
    var end by remember { mutableStateOf(draft.end) }
    var picking by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<PeriodProblem?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.id == null) R.string.cycle_adjust_add_period else R.string.cycle_adjust_edit_period)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cycle_adjust_first_day))
                OutlinedButton(onClick = { picking = START }) { Text(start.format(formatter)) }
                Text(stringResource(R.string.cycle_adjust_last_day))
                OutlinedButton(onClick = { picking = END }, enabled = end != null) {
                    Text(end?.format(formatter) ?: stringResource(R.string.cycle_adjust_still_going))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = end == null, onCheckedChange = { going -> end = if (going) null else maxOf(start, minOf(today, start.plusDays(4))) })
                    Text(stringResource(R.string.cycle_adjust_still_going))
                }
                problem?.let { Text(stringResource(it.labelRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(start, end) { problem = it } }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    when (picking) {
        START -> DatePickerModal(initial = start, latest = today, onDismiss = { picking = null }, onConfirm = {
            start = it
            problem = null
        })
        END -> DatePickerModal(initial = end ?: start, latest = today, onDismiss = { picking = null }, onConfirm = {
            end = it
            problem = null
        })
    }
}

private const val START = "start"
private const val END = "end"

private fun PeriodProblem.labelRes(): Int = when (this) {
    PeriodProblem.END_BEFORE_START -> R.string.cycle_adjust_end_before_start
    PeriodProblem.IN_THE_FUTURE -> R.string.cycle_adjust_in_future
    PeriodProblem.OVERLAPS -> R.string.cycle_adjust_overlaps
}

/** "28 days" with the right plural form. */
@Composable
fun days(count: Int): String = pluralStringResource(R.plurals.cycle_days_n, count, count)
