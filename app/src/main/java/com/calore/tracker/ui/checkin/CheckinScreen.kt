package com.calore.tracker.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.Checkin
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.ShowNumbersButton
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.formatKg
import com.calore.tracker.ui.formatSigned
import com.calore.tracker.ui.rememberMediumDateFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinScreen(
    onBack: () -> Unit,
    viewModel: CheckinViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val checkin = state.checkin
            val notEnough = state.notEnough
            when {
                state.loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                checkin != null && !state.numbersVisible -> ShowNumbersButton(onClick = viewModel::showNumbers)
                checkin != null -> CheckinDetails(checkin, state.decided, viewModel::useIt, viewModel::keepTarget)
                notEnough != null -> {
                    Text(stringResource(R.string.checkin_not_enough), style = MaterialTheme.typography.bodyLarge)
                    if (notEnough.daysMissing > 0) {
                        Text(
                            pluralStringResource(R.plurals.checkin_days_missing, notEnough.daysMissing, notEnough.daysMissing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (notEnough.weighInsMissing > 0) {
                        Text(
                            pluralStringResource(R.plurals.checkin_weigh_ins_missing, notEnough.weighInsMissing, notEnough.weighInsMissing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.checkin_how_it_works),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckinDetails(checkin: Checkin, decided: Boolean?, onUse: () -> Unit, onKeep: () -> Unit) {
    val dateFormatter = rememberMediumDateFormatter()
    Text(
        stringResource(R.string.checkin_week_of, checkin.weekStart.format(dateFormatter)),
        style = MaterialTheme.typography.titleMedium,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val start = checkin.trendStartKg
            val end = checkin.trendEndKg
            if (start != null && end != null) {
                val change = end - start
                ValueRow(stringResource(R.string.checkin_trend), stringResource(R.string.checkin_trend_value, formatKg(start), formatKg(end)))
                ValueRow(stringResource(R.string.checkin_trend_change), stringResource(R.string.value_kg, formatSigned(change, formatKg(abs(change)))))
            }
            checkin.avgIntakeKcal?.let {
                ValueRow(stringResource(R.string.checkin_avg_intake), stringResource(R.string.value_kcal, formatKcal(it)))
            }
            ValueRow(stringResource(R.string.checkin_counted_days), checkin.countedDays.toString())
            checkin.estimatedMaintenanceKcal?.let {
                ValueRow(stringResource(R.string.checkin_maintenance), stringResource(R.string.value_kcal, formatKcal(it.toDouble())))
            }
            HorizontalDivider()
            ValueRow(stringResource(R.string.checkin_current_target), stringResource(R.string.value_kcal, formatKcal(checkin.oldTargetKcal.toDouble())))
            ValueRow(
                stringResource(R.string.checkin_proposed_target),
                stringResource(R.string.value_kcal, formatKcal(checkin.proposedTargetKcal.toDouble())),
                emphasized = true,
            )
        }
    }
    when {
        checkin.accepted || decided == true -> Text(
            stringResource(R.string.checkin_using, formatKcal(checkin.proposedTargetKcal.toDouble())),
            style = MaterialTheme.typography.bodyLarge,
        )
        decided == false -> Text(stringResource(R.string.checkin_kept), style = MaterialTheme.typography.bodyLarge)
        checkin.proposedTargetKcal == checkin.oldTargetKcal -> Text(
            stringResource(R.string.checkin_unchanged, formatKcal(checkin.oldTargetKcal.toDouble())),
            style = MaterialTheme.typography.bodyLarge,
        )
        else -> {
            Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.checkin_use_it)) }
            OutlinedButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.checkin_keep)) }
        }
    }
    Text(
        stringResource(R.string.checkin_small_steps),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ValueRow(label: String, value: String, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    val weight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style, fontWeight = weight)
        Text(value, style = style, fontWeight = weight)
    }
}
