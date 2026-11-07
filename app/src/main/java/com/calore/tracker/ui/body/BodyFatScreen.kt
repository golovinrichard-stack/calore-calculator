package com.calore.tracker.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.BodyFatEstimate
import com.calore.tracker.domain.BodyFatProblem
import com.calore.tracker.domain.Sex
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.formatNumber
import com.calore.tracker.ui.labelRes
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.notifications.rememberNotificationPermission
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Body fat from tape measurements (US Navy) or a known number (M1.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyFatScreen(
    onBack: () -> Unit,
    onOpenTutorial: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BodyFatViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var offerReminder by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { saved -> if (saved.first) offerReminder = true else onSaved() }
    }

    // After the very first measurement: offer a reminder to measure again (M1.4).
    var chosenWeeks by rememberSaveable { mutableIntStateOf(ReminderSchedule.DEFAULT_MEASURE_WEEKS) }
    val requestPermission = rememberNotificationPermission { granted ->
        viewModel.chooseReminder(if (granted) chosenWeeks else 0, onSaved)
    }
    if (offerReminder) {
        MeasureReminderDialog(
            current = 0,
            offering = true,
            onChoose = { weeks ->
                offerReminder = false
                chosenWeeks = weeks
                requestPermission()
            },
            onDismiss = {
                offerReminder = false
                viewModel.chooseReminder(0, onSaved)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.body_fat_title)) },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BodyFatEntryMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = form.mode == mode,
                        onClick = { viewModel.update { it.copy(mode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, BodyFatEntryMode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                if (mode == BodyFatEntryMode.TAPE) R.string.body_fat_mode_tape else R.string.body_fat_mode_manual,
                            ),
                        )
                    }
                }
            }
            when (form.mode) {
                BodyFatEntryMode.TAPE -> TapeEntry(form, viewModel, onOpenTutorial)
                BodyFatEntryMode.MANUAL -> {
                    Text(stringResource(R.string.body_fat_manual_hint), style = MaterialTheme.typography.bodyMedium)
                    NumberField(
                        value = form.manualPct,
                        onValueChange = { text -> viewModel.update { it.copy(manualPct = text) } },
                        label = stringResource(R.string.body_fat_label),
                        suffix = "%",
                        errorText = if (form.attemptedSave && form.manualValue == null) {
                            stringResource(R.string.error_body_fat_manual)
                        } else {
                            null
                        },
                    )
                }
            }
            Button(onClick = viewModel::save, enabled = form.bodyFatPct != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save))
            }
            Text(
                stringResource(R.string.body_fat_estimate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TapeEntry(form: BodyFatForm, viewModel: BodyFatViewModel, onOpenTutorial: () -> Unit) {
    OutlinedButton(onClick = onOpenTutorial, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.body_fat_how_to))
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Sex.entries.forEachIndexed { index, sex ->
            SegmentedButton(
                selected = form.sex == sex,
                onClick = { viewModel.update { it.copy(sex = sex) } },
                shape = SegmentedButtonDefaults.itemShape(index, Sex.entries.size),
            ) { Text(stringResource(sex.labelRes())) }
        }
    }
    NumberField(
        value = form.heightCm,
        onValueChange = { text -> viewModel.update { it.copy(heightCm = text) } },
        label = stringResource(R.string.height_label),
    )
    NumberField(
        value = form.neckCm,
        onValueChange = { text -> viewModel.update { it.copy(neckCm = text) } },
        label = stringResource(R.string.body_fat_neck),
        suffix = stringResource(R.string.unit_cm),
    )
    NumberField(
        value = form.waistCm,
        onValueChange = { text -> viewModel.update { it.copy(waistCm = text) } },
        label = stringResource(if (form.sex == Sex.FEMALE) R.string.body_fat_waist_women else R.string.body_fat_waist_men),
        suffix = stringResource(R.string.unit_cm),
    )
    if (form.sex == Sex.FEMALE) {
        NumberField(
            value = form.hipCm,
            onValueChange = { text -> viewModel.update { it.copy(hipCm = text) } },
            label = stringResource(R.string.body_fat_hips),
            suffix = stringResource(R.string.unit_cm),
        )
    }
    Text(
        stringResource(R.string.body_fat_average_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (val estimate = form.estimate) {
        is BodyFatEstimate.Ok -> Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.body_fat_result, formatNumber(estimate.pct, 1, 1)),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
        is BodyFatEstimate.Invalid -> Text(
            stringResource(
                if (estimate.problem == BodyFatProblem.WAIST_NOT_ABOVE_NECK || estimate.problem == BodyFatProblem.IMPLAUSIBLE_RESULT) {
                    R.string.body_fat_check
                } else {
                    R.string.body_fat_out_of_range
                },
            ),
            color = MaterialTheme.colorScheme.error,
        )
        null -> Unit
    }
}
