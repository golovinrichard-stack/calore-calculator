package com.calore.tracker.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.CalculatorResult
import com.calore.tracker.domain.ProfileField
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.WeightGoal
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.descriptionRes
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onBack: () -> Unit,
    onApplied: () -> Unit,
    viewModel: CalculatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val invalid = form.invalidFields
    val result = form.result

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
            SectionHeader(stringResource(R.string.sex_label))
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
                value = form.age,
                onValueChange = { text -> viewModel.update { it.copy(age = text) } },
                label = stringResource(R.string.age_label),
                decimal = false,
                errorText = if (ProfileField.AGE in invalid) stringResource(R.string.error_age) else null,
            )
            NumberField(
                value = form.heightCm,
                onValueChange = { text -> viewModel.update { it.copy(heightCm = text) } },
                label = stringResource(R.string.height_label),
                errorText = if (ProfileField.HEIGHT in invalid) stringResource(R.string.error_height) else null,
            )
            NumberField(
                value = form.weightKg,
                onValueChange = { text -> viewModel.update { it.copy(weightKg = text) } },
                label = stringResource(R.string.weight_label),
                errorText = if (ProfileField.WEIGHT in invalid) stringResource(R.string.error_weight) else null,
            )

            SectionHeader(stringResource(R.string.activity_label))
            Column(modifier = Modifier.selectableGroup()) {
                ActivityLevel.entries.forEach { level ->
                    ActivityOption(
                        level = level,
                        selected = form.activity == level,
                        onSelect = { viewModel.update { it.copy(activity = level) } },
                    )
                }
            }

            SectionHeader(stringResource(R.string.weight_goal_label))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WeightGoal.entries.forEachIndexed { index, goal ->
                    SegmentedButton(
                        selected = form.goal == goal,
                        onClick = { viewModel.update { it.copy(goal = goal) } },
                        shape = SegmentedButtonDefaults.itemShape(index, WeightGoal.entries.size),
                    ) { Text(stringResource(goal.labelRes())) }
                }
            }

            SectionHeader(stringResource(R.string.split_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = form.proteinPercent,
                    onValueChange = { text -> viewModel.update { it.copy(proteinPercent = text) } },
                    label = stringResource(R.string.split_protein),
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = form.carbsPercent,
                    onValueChange = { text -> viewModel.update { it.copy(carbsPercent = text) } },
                    label = stringResource(R.string.split_carbs),
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = form.fatPercent,
                    onValueChange = { text -> viewModel.update { it.copy(fatPercent = text) } },
                    label = stringResource(R.string.split_fat),
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
            }
            if (form.split?.isValid != true) {
                Text(
                    text = stringResource(R.string.split_total, form.splitTotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (result != null) {
                ResultCard(result)
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

@Composable
private fun ActivityOption(level: ActivityLevel, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(stringResource(level.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(level.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultCard(result: CalculatorResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ResultRow(stringResource(R.string.result_bmr), stringResource(R.string.value_kcal, formatKcal(result.bmr.toDouble())))
            ResultRow(stringResource(R.string.result_tdee), stringResource(R.string.value_kcal, formatKcal(result.tdee.toDouble())))
            HorizontalDivider()
            ResultRow(
                stringResource(R.string.result_target),
                stringResource(R.string.value_kcal, formatKcal(result.goals.kcal.toDouble())),
                emphasized = true,
            )
            ResultRow(stringResource(R.string.nutrient_protein), stringResource(R.string.value_g, result.goals.proteinG.toString()))
            ResultRow(stringResource(R.string.nutrient_carbs), stringResource(R.string.value_g, result.goals.carbsG.toString()))
            ResultRow(stringResource(R.string.nutrient_fat), stringResource(R.string.value_g, result.goals.fatG.toString()))
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    val weight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style, fontWeight = weight)
        Text(value, style = style, fontWeight = weight)
    }
}
