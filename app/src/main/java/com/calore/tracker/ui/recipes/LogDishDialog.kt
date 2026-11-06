package com.calore.tracker.ui.recipes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calore.tracker.R
import com.calore.tracker.domain.Dishes
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.RecipeNutrition
import com.calore.tracker.domain.parsePortionGrams
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.labelRes

/**
 * Log a dish by hand (M3.5): servings (default 1), or the cooked weight once the recipe's yield
 * is known. With [chooseMeal] the meal can be picked too (logging from the recipe itself).
 */
@Composable
fun LogDishDialog(
    name: String,
    nutrition: RecipeNutrition,
    meal: MealType,
    chooseMeal: Boolean,
    onLog: (servings: Double?, grams: Double?, meal: MealType) -> Unit,
    onDismiss: () -> Unit,
) {
    val canUseGrams = nutrition.per100gCooked != null
    var byGrams by rememberSaveable { mutableStateOf(false) }
    var servingsText by rememberSaveable { mutableStateOf("1") }
    var gramsText by rememberSaveable { mutableStateOf("") }
    var chosenMeal by rememberSaveable { mutableStateOf(meal) }
    val servings = Dishes.parseServings(servingsText)
    val grams = parsePortionGrams(gramsText)
    val preview = if (byGrams) grams?.let { Dishes.forGrams(nutrition, it) } else servings?.let { Dishes.forServings(nutrition, it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canUseGrams) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !byGrams, onClick = { byGrams = false }, label = { Text(stringResource(R.string.dish_by_servings)) })
                        FilterChip(selected = byGrams, onClick = { byGrams = true }, label = { Text(stringResource(R.string.dish_by_grams)) })
                    }
                }
                if (byGrams) {
                    NumberField(
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        label = stringResource(R.string.dish_grams_label),
                        suffix = stringResource(R.string.unit_g),
                        errorText = if (grams == null && gramsText.isNotEmpty()) stringResource(R.string.error_amount) else null,
                    )
                } else {
                    NumberField(
                        value = servingsText,
                        onValueChange = { servingsText = it },
                        label = stringResource(R.string.recipe_servings_label),
                        errorText = if (servings == null) stringResource(R.string.error_servings) else null,
                    )
                }
                if (chooseMeal) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MealType.entries.forEach { option ->
                            FilterChip(
                                selected = option == chosenMeal,
                                onClick = { chosenMeal = option },
                                label = { Text(stringResource(option.labelRes())) },
                            )
                        }
                    }
                }
                if (!LocalHideNumbers.current) {
                    preview?.let {
                        Text(stringResource(R.string.value_kcal, formatKcal(it.kcal)), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (byGrams) onLog(null, grams, chosenMeal) else onLog(servings, null, chosenMeal) },
                enabled = preview != null,
            ) { Text(stringResource(R.string.add_to_meal, stringResource(chosenMeal.labelRes()))) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
