package com.calore.tracker.ui.customfood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.FoodField
import com.calore.tracker.domain.Limits
import com.calore.tracker.domain.MealType
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NumberField
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFoodScreen(
    onBack: () -> Unit,
    onCreated: (foodId: Long, LocalDate, MealType) -> Unit,
    onUpdated: () -> Unit,
    viewModel: CustomFoodViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft
    val errors = state.errors

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomFoodEvent.Created -> onCreated(event.foodId, viewModel.date, viewModel.meal)
                CustomFoodEvent.Updated -> onUpdated()
            }
        }
    }

    val per100Error = stringResource(R.string.error_per_100g)
    fun errorFor(field: FoodField, message: String): String? = if (field in errors) message else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEditing) R.string.custom_food_edit else R.string.custom_food_new))
                },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.custom_food_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { text -> viewModel.updateDraft { it.copy(name = text.take(Limits.MAX_NAME_LENGTH)) } },
                label = { Text(stringResource(R.string.field_name)) },
                isError = FoodField.NAME in errors,
                supportingText = errorFor(FoodField.NAME, stringResource(R.string.error_name))?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.brand,
                onValueChange = { text -> viewModel.updateDraft { it.copy(brand = text.take(Limits.MAX_NAME_LENGTH)) } },
                label = { Text(stringResource(R.string.field_brand)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                value = draft.kcal,
                onValueChange = { text -> viewModel.updateDraft { it.copy(kcal = text) } },
                label = stringResource(R.string.field_kcal),
                suffix = stringResource(R.string.unit_kcal),
                errorText = errorFor(FoodField.KCAL, stringResource(R.string.error_kcal)),
            )
            NumberField(
                value = draft.protein,
                onValueChange = { text -> viewModel.updateDraft { it.copy(protein = text) } },
                label = stringResource(R.string.field_protein),
                suffix = stringResource(R.string.unit_g),
                errorText = errorFor(FoodField.PROTEIN, per100Error),
            )
            NumberField(
                value = draft.carbs,
                onValueChange = { text -> viewModel.updateDraft { it.copy(carbs = text) } },
                label = stringResource(R.string.field_carbs),
                suffix = stringResource(R.string.unit_g),
                errorText = errorFor(FoodField.CARBS, per100Error),
            )
            NumberField(
                value = draft.sugar,
                onValueChange = { text -> viewModel.updateDraft { it.copy(sugar = text) } },
                label = stringResource(R.string.field_sugar),
                suffix = stringResource(R.string.unit_g),
                errorText = errorFor(FoodField.SUGAR, per100Error),
            )
            NumberField(
                value = draft.fat,
                onValueChange = { text -> viewModel.updateDraft { it.copy(fat = text) } },
                label = stringResource(R.string.field_fat),
                suffix = stringResource(R.string.unit_g),
                errorText = errorFor(FoodField.FAT, per100Error),
            )
            NumberField(
                value = draft.fiber,
                onValueChange = { text -> viewModel.updateDraft { it.copy(fiber = text) } },
                label = stringResource(R.string.field_fiber),
                suffix = stringResource(R.string.unit_g),
                errorText = errorFor(FoodField.FIBER, per100Error),
            )
            NumberField(
                value = draft.servingGrams,
                onValueChange = { text -> viewModel.updateDraft { it.copy(servingGrams = text) } },
                label = stringResource(R.string.field_serving),
                suffix = stringResource(R.string.unit_g),
                errorText = errorFor(FoodField.SERVING, stringResource(R.string.error_serving)),
                imeAction = ImeAction.Done,
                onDone = viewModel::save,
            )
            Button(
                onClick = viewModel::save,
                enabled = state.isLoaded && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
