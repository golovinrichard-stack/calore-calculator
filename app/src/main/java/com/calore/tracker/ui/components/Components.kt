package com.calore.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calore.tracker.R
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.ui.UNKNOWN_VALUE
import com.calore.tracker.ui.formatGrams
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.progressFraction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A single-line text field for numbers, with the error message shown under it. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    suffix: String? = null,
    decimal: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_NUMBER_LENGTH)) },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

private const val MAX_NUMBER_LENGTH = 9

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

/** Label on the left, progress text on the right, and a thin bar underneath. */
@Composable
fun GoalProgress(
    label: String,
    valueText: String,
    value: Double,
    goal: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val over = goal > 0 && value > goal
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        GoalBar(value = value, goal = goal, color = color)
    }
}

/** A rounded progress bar that turns the error color once [value] passes [goal]. */
@Composable
fun GoalBar(value: Double, goal: Int, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val over = goal > 0 && value > goal
    LinearProgressIndicator(
        progress = { progressFraction(value, goal) },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2)),
        color = if (over) MaterialTheme.colorScheme.error else color,
        // Cards sit on surfaceContainerHighest, so the plain surface color gives the track contrast.
        trackColor = MaterialTheme.colorScheme.surface,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Calories and macros as a simple two-column table, nutrition-label style. */
@Composable
fun NutrientTable(nutrients: Nutrients, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        NutrientRow(stringResource(R.string.nutrient_calories), stringResource(R.string.value_kcal, formatKcal(nutrients.kcal)), bold = true)
        HorizontalDivider()
        NutrientRow(stringResource(R.string.nutrient_protein), gramsText(nutrients.protein))
        NutrientRow(stringResource(R.string.nutrient_carbs), gramsText(nutrients.carbs))
        NutrientRow(stringResource(R.string.nutrient_sugar), gramsText(nutrients.sugar), indent = true)
        NutrientRow(stringResource(R.string.nutrient_fat), gramsText(nutrients.fat))
        NutrientRow(stringResource(R.string.nutrient_fiber), gramsText(nutrients.fiber))
    }
}

@Composable
private fun gramsText(grams: Double?): String =
    if (grams == null) UNKNOWN_VALUE else stringResource(R.string.value_g, formatGrams(grams))

@Composable
private fun NutrientRow(label: String, value: String, bold: Boolean = false, indent: Boolean = false) {
    val weight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            modifier = if (indent) Modifier.padding(start = 16.dp) else Modifier,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
    }
}

/**
 * Material date picker that works in [LocalDate]. The picker itself counts in UTC
 * midnights, so conversion happens in UTC to avoid off-by-one days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    latest: LocalDate? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                latest == null || !utcMillisToDate(utcTimeMillis).isAfter(latest)

            override fun isSelectableYear(year: Int): Boolean = latest == null || year <= latest.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(utcMillisToDate(it)) }
                onDismiss()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun utcMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
