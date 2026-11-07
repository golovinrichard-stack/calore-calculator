package com.calore.tracker.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import androidx.compose.ui.Alignment
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import com.calore.tracker.domain.Sex
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.profile.ActivitySection
import com.calore.tracker.ui.profile.BodyBasicsSection
import com.calore.tracker.ui.profile.GoalSection
import com.calore.tracker.ui.profile.ResultCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onDone: () -> Unit,
    viewModel: SetupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val step = state.step
    val form = state.form
    val today = viewModel.today

    LaunchedEffect(viewModel) {
        viewModel.done.collect { onDone() }
    }
    BackHandler(enabled = step != SetupStep.BODY) { viewModel.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                actions = {
                    TextButton(onClick = viewModel::skip) { Text(stringResource(R.string.setup_skip)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.setup_step, step.ordinal + 1, SetupStep.entries.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (step.ordinal + 1f) / SetupStep.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag("setup_step_${step.name}"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    SetupStep.BODY -> {
                        Text(stringResource(R.string.setup_body_intro), style = MaterialTheme.typography.bodyLarge)
                        BodyBasicsSection(form = form, today = today, onChange = viewModel::update)
                    }
                    SetupStep.ACTIVITY -> ActivitySection(form = form, onChange = viewModel::update)
                    SetupStep.GOAL -> GoalSection(form = form, onChange = viewModel::update)
                    SetupStep.RESULT -> {
                        val result = form.result(today)
                        if (result != null) {
                            Text(stringResource(R.string.setup_result_intro), style = MaterialTheme.typography.bodyLarge)
                            ResultCard(result = result, form = form)
                        } else {
                            Text(stringResource(R.string.setup_result_missing), style = MaterialTheme.typography.bodyLarge)
                        }
                        // M7.1: one optional card for people who chose female.
                        if (form.sex == Sex.FEMALE) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.cycle_setup_title), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.cycle_setup_text), style = MaterialTheme.typography.bodyMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.cycle_setup_yes), modifier = Modifier.weight(1f))
                                        Switch(checked = state.trackCycle, onCheckedChange = viewModel::setTrackCycle)
                                    }
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.setup_change_later),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (step != SetupStep.BODY) {
                    OutlinedButton(onClick = { viewModel.back() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.back))
                    }
                }
                if (step == SetupStep.RESULT) {
                    Button(onClick = viewModel::finish, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.setup_finish))
                    }
                } else {
                    Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.setup_next))
                    }
                }
            }
        }
    }
}
