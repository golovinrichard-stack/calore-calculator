package com.calore.tracker.ui.recipes

import com.calore.tracker.ui.plan.WeekPlanContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.FeatureFlags
import com.calore.tracker.R
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.MealType
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.formatNumber
import com.calore.tracker.ui.labelRes

/** The Plan tab: the week plan (M5.3) and the recipe book (M3.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    onOpenRecipe: (id: String) -> Unit,
    onNewRecipe: () -> Unit,
    viewModel: RecipeListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    var showRecipes by rememberSaveable { mutableStateOf(!FeatureFlags.weekPlan) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(if (FeatureFlags.weekPlan) R.string.nav_plan else R.string.recipes_title)) })
                if (FeatureFlags.weekPlan) {
                    PrimaryTabRow(selectedTabIndex = if (showRecipes) 1 else 0) {
                        Tab(selected = !showRecipes, onClick = { showRecipes = false }, text = { Text(stringResource(R.string.plan_tab_week)) })
                        Tab(selected = showRecipes, onClick = { showRecipes = true }, text = { Text(stringResource(R.string.recipes_title)) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (showRecipes) {
                ExtendedFloatingActionButton(
                    onClick = onNewRecipe,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.recipes_new)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (showRecipes) RecipeList(viewModel, onOpenRecipe) else WeekPlanContent(snackbarHostState)
        }
    }
}

@Composable
private fun RecipeList(viewModel: RecipeListViewModel, onOpenRecipe: (id: String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filters = state.filters
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = filters.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filters.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, stringResource(R.string.clear_search))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "filters") {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filters.meal == null,
                    onClick = { viewModel.onMealChange(null) },
                    label = { Text(stringResource(R.string.recipes_all_meals)) },
                )
                MealType.entries.forEach { meal ->
                    FilterChip(
                        selected = filters.meal == meal,
                        onClick = { viewModel.onMealChange(meal) },
                        label = { Text(stringResource(meal.labelRes())) },
                    )
                }
                // Everyone fits "omnivore", so the filter only shows for a real diet.
                if (state.diet != Diet.OMNIVORE) {
                    FilterChip(
                        selected = filters.fitsDietOnly,
                        onClick = { viewModel.onFitsDietChange(!filters.fitsDietOnly) },
                        label = { Text(stringResource(R.string.recipes_fits_diet)) },
                    )
                }
            }
        }
        if (state.hiddenForAllergens > 0 || filters.showAll) {
            item(key = "allergens") {
                ListItem(
                    headlineContent = {
                        Text(
                            if (filters.showAll) {
                                stringResource(R.string.recipes_show_all)
                            } else {
                                pluralStringResource(R.plurals.recipes_hidden_allergens, state.hiddenForAllergens, state.hiddenForAllergens)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    trailingContent = { Switch(checked = filters.showAll, onCheckedChange = null) },
                    modifier = Modifier.clickable { viewModel.onShowAllChange(!filters.showAll) },
                )
            }
        }
        if (!state.loading && state.items.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(if (state.hasAnyRecipe) R.string.recipes_no_match else R.string.recipes_none_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(state.items, key = { it.id }) { item -> RecipeRow(item, onClick = { onOpenRecipe(item.id) }) }
    }
}

@Composable
private fun RecipeRow(item: RecipeListItem, onClick: () -> Unit) {
    val hideNumbers = LocalHideNumbers.current
    val kcal = formatKcal(item.perServing.kcal)
    val summary = when {
        hideNumbers -> null
        item.perServing.protein != null ->
            stringResource(R.string.recipe_kcal_protein_per_serving, kcal, formatNumber(item.perServing.protein, decimals = 0))
        else -> stringResource(R.string.recipe_kcal_per_serving, kcal)
    }
    val allergens = item.myAllergens.map { stringResource(it.labelRes()) }.sorted()
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (summary != null || allergens.isNotEmpty()) {
            {
                Column(horizontalAlignment = Alignment.Start) {
                    summary?.let { Text(it) }
                    if (allergens.isNotEmpty()) {
                        Text(
                            stringResource(R.string.recipe_contains, allergens.joinToString(", ")),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        } else {
            null
        },
    )
}
