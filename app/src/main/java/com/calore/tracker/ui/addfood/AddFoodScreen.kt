package com.calore.tracker.ui.addfood

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.remote.RemoteFood
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.MealType
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.labelRes
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    onBack: () -> Unit,
    onFoodChosen: (foodId: Long, LocalDate, MealType) -> Unit,
    onCreateCustom: (LocalDate, MealType, name: String) -> Unit,
    viewModel: AddFoodViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(viewModel) {
        viewModel.chosenFoodIds.collect { id -> onFoodChosen(id, viewModel.date, viewModel.meal) }
    }
    // Ready to type on arrival; not when coming back from a food with a search already entered.
    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) searchFocus.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_food_title, stringResource(viewModel.meal.labelRes()))) },
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
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, stringResource(R.string.clear_search))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.searchNow()
                    keyboard?.hide()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(searchFocus),
            )
            if (state.isSaving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                localSection(state, onFoodClick = { onFoodChosen(it.id, viewModel.date, viewModel.meal) })
                item(key = "create") {
                    ListItem(
                        modifier = Modifier.clickable {
                            onCreateCustom(viewModel.date, viewModel.meal, state.query.trim())
                        },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.create_custom_food)) },
                    )
                }
                onlineSection(
                    state = state,
                    onRemoteClick = { if (!state.isSaving) viewModel.chooseRemote(it) },
                    onRetry = viewModel::searchNow,
                )
            }
        }
    }
}

private fun LazyListScope.localSection(state: AddFoodUiState, onFoodClick: (Food) -> Unit) {
    val isSearching = state.query.isNotBlank()
    if (!isSearching || state.localFoods.isNotEmpty()) {
        item(key = "local_header") {
            SectionHeader(
                text = stringResource(if (isSearching) R.string.section_my_foods else R.string.section_recent),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
    if (!isSearching && state.localFoods.isEmpty()) {
        item(key = "no_recent") { HintText(stringResource(R.string.no_recent)) }
    }
    items(state.localFoods, key = { "local_${it.id}" }) { food ->
        FoodRow(name = food.name, brand = food.brand, kcalPer100g = food.per100g.kcal, onClick = { onFoodClick(food) })
    }
}

private fun LazyListScope.onlineSection(
    state: AddFoodUiState,
    onRemoteClick: (RemoteFood) -> Unit,
    onRetry: () -> Unit,
) {
    item(key = "online_header") {
        SectionHeader(
            text = stringResource(R.string.section_online),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
    }
    when (val online = state.online) {
        OnlineSearchState.Idle -> item(key = "online_idle") { HintText(stringResource(R.string.online_hint)) }
        OnlineSearchState.Loading -> item(key = "online_loading") {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.online_loading), style = MaterialTheme.typography.bodyMedium)
            }
        }
        is OnlineSearchState.Failed -> item(key = "online_failed") {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        when (online.reason) {
                            OnlineFailure.NETWORK -> R.string.online_offline
                            OnlineFailure.SERVICE -> R.string.online_service_error
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
        is OnlineSearchState.Results -> {
            val foods = state.newOnlineFoods
            if (foods.isEmpty() && online.foods.isEmpty()) {
                item(key = "online_empty") { HintText(stringResource(R.string.online_no_results)) }
            }
            items(foods, key = { "remote_${it.barcode}" }) { remote ->
                FoodRow(
                    name = remote.name,
                    brand = remote.brand,
                    kcalPer100g = remote.per100g.kcal,
                    onClick = { onRemoteClick(remote) },
                )
            }
        }
    }
}

@Composable
private fun FoodRow(name: String, brand: String?, kcalPer100g: Double, onClick: () -> Unit) {
    val kcal = formatKcal(kcalPer100g)
    val hideNumbers = LocalHideNumbers.current
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = if (hideNumbers) {
                    brand.orEmpty()
                } else if (brand != null) {
                    stringResource(R.string.food_per_100g_brand, brand, kcal)
                } else {
                    stringResource(R.string.food_per_100g, kcal)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
