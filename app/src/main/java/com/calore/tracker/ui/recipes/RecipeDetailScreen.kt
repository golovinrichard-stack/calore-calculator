package com.calore.tracker.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.RecipeStep
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NutrientTable
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.components.ShowNumbersButton
import com.calore.tracker.ui.cuisineLabelRes
import com.calore.tracker.ui.formatGrams
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.rememberNumbersVisible

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: (id: String) -> Unit,
    onDeleted: () -> Unit,
    bottomBar: @Composable (RecipeDetails) -> Unit = {},
    viewModel: RecipeDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val load by viewModel.uiState.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val loaded = load as? RecipeLoad.Loaded

    LaunchedEffect(viewModel) { viewModel.deleted.collect { onDeleted() } }

    Scaffold(
        topBar = {
            TopAppBar(
                // The name is the page heading below, where long names can wrap.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (loaded != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.recipe_edit)) },
                                    onClick = {
                                        menuOpen = false
                                        onEdit(viewModel.recipeId)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.recipe_delete)) },
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
        bottomBar = { loaded?.let { bottomBar(it.details) } },
    ) { padding ->
        when (val current = load) {
            RecipeLoad.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            RecipeLoad.NotFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.recipe_not_found))
            }
            is RecipeLoad.Loaded -> RecipeContent(current.details, current.myAllergens, Modifier.padding(padding))
        }
    }

    if (confirmDelete && loaded != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.recipe_delete_confirm, loaded.details.recipe.displayName())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RecipeContent(details: RecipeDetails, myAllergens: Set<Allergen>, modifier: Modifier) {
    val recipe = details.recipe
    val nutrition = details.nutrition
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(recipe.displayName(), style = MaterialTheme.typography.headlineSmall)
            val cuisine = recipe.cuisine?.let { cuisineLabelRes(it) }?.let { stringResource(it) }
            val servings = pluralStringResource(R.plurals.recipe_servings, recipe.servings, recipe.servings)
            Text(
                listOfNotNull(servings, cuisine).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (nutrition.missingFoods > 0) {
            Text(
                pluralStringResource(R.plurals.recipe_missing_foods, nutrition.missingFoods, nutrition.missingFoods),
                color = MaterialTheme.colorScheme.error,
            )
        }

        RecipeNutritionCards(details)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(stringResource(R.string.recipe_allergens))
            AllergenText(details.allergens, myAllergens)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.recipe_ingredients))
            details.ingredients.forEach { ingredient ->
                val food = details.foods[ingredient.foodUuid]
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            food?.displayName() ?: stringResource(R.string.recipe_missing_food),
                            color = if (food == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        val extra = listOfNotNull(
                            ingredient.note,
                            if (ingredient.optional) stringResource(R.string.recipe_optional) else null,
                        )
                        if (extra.isNotEmpty()) {
                            Text(
                                extra.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(stringResource(R.string.value_g, formatGrams(ingredient.grams)))
                }
            }
        }

        if (details.steps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(R.string.recipe_steps))
                details.steps.forEachIndexed { index, step ->
                    Row {
                        Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.displayText())
                            stepDetails(step)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(stringResource(R.string.recipe_storage))
            Text(pluralStringResource(R.plurals.recipe_fridge_days, recipe.fridgeDays, recipe.fridgeDays))
            Text(
                if (recipe.freezable) {
                    pluralStringResource(R.plurals.recipe_freezer_months, recipe.freezerMonths, recipe.freezerMonths)
                } else {
                    stringResource(R.string.recipe_not_freezable)
                },
            )
            recipe.reheatNote?.let { Text(stringResource(R.string.recipe_reheat, it)) }
            if (recipe.mealPrepFriendly) Text(stringResource(R.string.recipe_meal_prep))
        }
    }
}

/** Per serving (always), the whole recipe, and per 100 g cooked once a yield is known. */
@Composable
fun RecipeNutritionCards(details: RecipeDetails) {
    val (numbersVisible, showNumbers) = rememberNumbersVisible()
    if (!numbersVisible) {
        ShowNumbersButton(onClick = showNumbers)
        return
    }
    val nutrition = details.nutrition
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(stringResource(R.string.recipe_per_serving))
            NutrientTable(nutrition.perServing)
            Text(
                stringResource(R.string.recipe_whole_kcal, formatKcal(nutrition.total.kcal)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    nutrition.per100gCooked?.let { per100g ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionHeader(stringResource(R.string.recipe_per_100g_cooked))
                NutrientTable(per100g)
            }
        }
    }
}

/** All allergens of the recipe; the user's own are shown in red. */
@Composable
fun AllergenText(allergens: Set<Allergen>, myAllergens: Set<Allergen>) {
    if (allergens.isEmpty()) {
        Text(stringResource(R.string.recipe_no_allergens), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (mine, others) = allergens.sortedBy { it.ordinal }.partition { it in myAllergens }
    if (mine.isNotEmpty()) {
        Text(
            stringResource(R.string.recipe_contains, mine.map { stringResource(it.labelRes()) }.joinToString(", ")),
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
    }
    if (others.isNotEmpty()) Text(others.map { stringResource(it.labelRes()) }.joinToString(", "))
}

@Composable
private fun stepDetails(step: RecipeStep): String? {
    val parts = listOfNotNull(
        step.minutes?.let { stringResource(R.string.recipe_minutes, it) },
        if (step.passive) stringResource(R.string.recipe_passive) else null,
        step.equipment?.let { equipmentLabel(it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** "STOVE" or an [Equipment] name, as stored on a step. */
@Composable
fun equipmentLabel(name: String): String? = when (name) {
    StepDraft.STOVE -> stringResource(R.string.equipment_stove)
    else -> Equipment.entries.firstOrNull { it.name == name }?.let { stringResource(it.labelRes()) }
}
