package com.calore.tracker.ui.fooddetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.MealType
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.NutrientTable
import com.calore.tracker.ui.components.ShowNumbersButton
import com.calore.tracker.ui.rememberNumbersVisible
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.ui.formatGrams
import com.calore.tracker.ui.labelRes
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
    onBack: () -> Unit,
    onLogged: () -> Unit,
    onEdit: (foodId: Long, LocalDate, MealType) -> Unit,
    onDeleted: () -> Unit,
    viewModel: FoodDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val food = (state.load as? FoodLoad.Loaded)?.food

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FoodDetailEvent.LOGGED -> onLogged()
                FoodDetailEvent.DELETED -> onDeleted()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // The name is the page heading below, where long names can wrap.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (food != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_food)) },
                                    onClick = {
                                        menuOpen = false
                                        onEdit(food.id, viewModel.date, state.meal)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_food)) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (food != null) {
                Button(
                    onClick = viewModel::addToDiary,
                    enabled = state.canAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.add_to_meal, stringResource(state.meal.labelRes())))
                }
            }
        },
    ) { padding ->
        when (val load = state.load) {
            FoodLoad.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            FoodLoad.NotFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.food_not_found))
            }
            is FoodLoad.Loaded -> FoodDetailContent(
                food = load.food,
                state = state,
                modifier = Modifier.padding(padding),
                onGramsChange = viewModel::onGramsChange,
                onMealChange = viewModel::onMealChange,
            )
        }
    }

    if (confirmDelete && food != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.delete_food_confirm, food.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteFood()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun FoodDetailContent(
    food: Food,
    state: FoodDetailUiState,
    modifier: Modifier,
    onGramsChange: (String) -> Unit,
    onMealChange: (MealType) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(food.name, style = MaterialTheme.typography.headlineSmall)
            food.brand?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text(
                text = stringResource(
                    if (food.source == FoodSource.OPEN_FOOD_FACTS) R.string.source_off else R.string.source_custom,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberField(
                value = state.gramsText,
                onValueChange = onGramsChange,
                label = stringResource(R.string.amount_label),
                suffix = stringResource(R.string.unit_g),
                errorText = if (state.grams == null) stringResource(R.string.error_amount) else null,
                imeAction = ImeAction.Done,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                food.servingGrams?.let { serving ->
                    AssistChip(
                        onClick = { onGramsChange(formatEditable(serving)) },
                        label = { Text(stringResource(R.string.serving_chip, formatGrams(serving))) },
                    )
                }
                AssistChip(
                    onClick = { onGramsChange("100") },
                    label = { Text(stringResource(R.string.hundred_g_chip)) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(stringResource(R.string.meal_label))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealType.entries.forEach { meal ->
                    FilterChip(
                        selected = meal == state.meal,
                        onClick = { onMealChange(meal) },
                        label = { Text(stringResource(meal.labelRes())) },
                    )
                }
            }
        }

        val (numbersVisible, showNumbers) = rememberNumbersVisible()
        if (!numbersVisible) {
            ShowNumbersButton(onClick = showNumbers)
        } else {
            state.portion?.let { portion ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(stringResource(R.string.this_portion))
                        NutrientTable(portion)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(stringResource(R.string.per_100g))
                    NutrientTable(food.per100g)
                }
            }
        }
    }
}
