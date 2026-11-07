package com.calore.tracker.ui.calculator

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.ShowNumbersButton
import com.calore.tracker.ui.rememberNumbersVisible
import com.calore.tracker.ui.profile.ActivitySection
import com.calore.tracker.ui.profile.BodyBasicsSection
import com.calore.tracker.ui.profile.GoalSection
import com.calore.tracker.ui.profile.MacroSection
import com.calore.tracker.ui.profile.ResultCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onBack: () -> Unit,
    onApplied: () -> Unit,
    viewModel: CalculatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val result = form.result(viewModel.today)

    LaunchedEffect(viewModel) {
        viewModel.applied.collect { onApplied() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calculator_title)) },
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
            BodyBasicsSection(
                form = form,
                today = viewModel.today,
                onChange = viewModel::update,
                weightHint = stringResource(R.string.weight_saved_as_weigh_in),
            )
            ActivitySection(form = form, onChange = viewModel::update)
            GoalSection(form = form, onChange = viewModel::update)
            MacroSection(form = form, onChange = viewModel::update)

            val (numbersVisible, showNumbers) = rememberNumbersVisible()
            if (result != null && !numbersVisible) {
                ShowNumbersButton(onClick = showNumbers)
            } else if (result != null) {
                ResultCard(result = result, form = form)
            } else {
                Text(
                    text = stringResource(R.string.calculator_incomplete),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = viewModel::apply,
                enabled = result != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.calculator_apply))
            }
            Text(
                text = stringResource(R.string.calculator_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
