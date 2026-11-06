package com.calore.tracker.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.formatKcal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onOpenCalculator: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: GoalsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val form = state.form
    val errors = state.errors

    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            focusManager.clearFocus()
            snackbarHostState.showSnackbar(resources.getString(R.string.goals_saved))
        }
    }

    val macroError = stringResource(R.string.error_goal_macro)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_title)) },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Outlined.Info, stringResource(R.string.goals_about))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NumberField(
                value = form.kcal,
                onValueChange = { text -> viewModel.updateForm { it.copy(kcal = text) } },
                label = stringResource(R.string.goal_kcal),
                suffix = stringResource(R.string.unit_kcal),
                decimal = false,
                errorText = if (GoalField.KCAL in errors) stringResource(R.string.error_goal_kcal) else null,
            )
            NumberField(
                value = form.protein,
                onValueChange = { text -> viewModel.updateForm { it.copy(protein = text) } },
                label = stringResource(R.string.goal_protein),
                suffix = stringResource(R.string.unit_g),
                decimal = false,
                errorText = if (GoalField.PROTEIN in errors) macroError else null,
            )
            NumberField(
                value = form.carbs,
                onValueChange = { text -> viewModel.updateForm { it.copy(carbs = text) } },
                label = stringResource(R.string.goal_carbs),
                suffix = stringResource(R.string.unit_g),
                decimal = false,
                errorText = if (GoalField.CARBS in errors) macroError else null,
            )
            NumberField(
                value = form.fat,
                onValueChange = { text -> viewModel.updateForm { it.copy(fat = text) } },
                label = stringResource(R.string.goal_fat),
                suffix = stringResource(R.string.unit_g),
                decimal = false,
                errorText = if (GoalField.FAT in errors) macroError else null,
                imeAction = ImeAction.Done,
                onDone = viewModel::save,
            )

            state.macroKcal?.let { kcal ->
                Text(
                    text = stringResource(R.string.goals_macro_kcal, formatKcal(kcal.toDouble())),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.macrosMismatch) {
                Text(
                    text = stringResource(R.string.goals_mismatch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.save))
            }
            OutlinedButton(onClick = onOpenCalculator, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.goals_open_calculator))
            }
            OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_title))
            }
        }
    }
}
