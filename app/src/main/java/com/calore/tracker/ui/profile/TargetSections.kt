package com.calore.tracker.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calore.tracker.R
import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.BmrFormula
import com.calore.tracker.domain.CalculatorResult
import com.calore.tracker.domain.CalorieCalculator
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MacroMode
import com.calore.tracker.domain.Macros
import com.calore.tracker.domain.ProfileField
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.TargetWarning
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.descriptionRes
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.formatSigned
import com.calore.tracker.ui.formatNumber
import com.calore.tracker.ui.labelRes
import com.calore.tracker.ui.rememberMediumDateFormatter
import java.time.LocalDate
import kotlin.math.roundToInt

/** Sex, birth date, height and weight. */
@Composable
fun BodyBasicsSection(
    form: TargetForm,
    today: LocalDate,
    onChange: ((TargetForm) -> TargetForm) -> Unit,
    weightHint: String? = null,
    showWeight: Boolean = true,
) {
    val invalid = form.invalidFields(today)
    var pickingBirthDate by rememberSaveable { mutableStateOf(false) }
    val dateFormatter = rememberMediumDateFormatter()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.sex_label))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { index, sex ->
                SegmentedButton(
                    selected = form.sex == sex,
                    onClick = { onChange { it.copy(sex = sex) } },
                    shape = SegmentedButtonDefaults.itemShape(index, Sex.entries.size),
                ) { Text(stringResource(sex.labelRes())) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.birth_date_label), style = MaterialTheme.typography.bodyMedium)
                val birthDate = form.birthDate
                Text(
                    text = if (birthDate == null) {
                        stringResource(R.string.not_set)
                    } else {
                        val age = form.ageYears(today) ?: 0
                        stringResource(
                            R.string.birth_date_with_age,
                            birthDate.format(dateFormatter),
                            pluralStringResource(R.plurals.age_years, age, age),
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (ProfileField.AGE in invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (ProfileField.AGE in invalid) {
                    Text(
                        stringResource(R.string.error_age),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            OutlinedButton(onClick = { pickingBirthDate = true }) {
                Text(stringResource(R.string.choose))
            }
        }

        NumberField(
            value = form.heightCm,
            onValueChange = { text -> onChange { it.copy(heightCm = text) } },
            label = stringResource(R.string.height_label),
            errorText = if (ProfileField.HEIGHT in invalid) stringResource(R.string.error_height) else null,
        )
        if (showWeight) {
            NumberField(
                value = form.weightKg,
                onValueChange = { text -> onChange { it.copy(weightKg = text) } },
                label = stringResource(R.string.weight_label),
                errorText = if (ProfileField.WEIGHT in invalid) stringResource(R.string.error_weight) else weightHint,
            )
        }
    }

    if (pickingBirthDate) {
        DatePickerModal(
            initial = form.birthDate ?: today.minusYears(DEFAULT_PICKER_AGE),
            latest = today,
            onDismiss = { pickingBirthDate = false },
            onConfirm = { date ->
                pickingBirthDate = false
                onChange { it.copy(birthDate = date) }
            },
        )
    }
}

private const val DEFAULT_PICKER_AGE = 30L

@Composable
fun ActivitySection(form: TargetForm, onChange: ((TargetForm) -> TargetForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(stringResource(R.string.activity_label))
        Column(modifier = Modifier.selectableGroup()) {
            ActivityLevel.entries.forEach { level ->
                ChoiceRow(
                    title = stringResource(level.labelRes()),
                    description = stringResource(level.descriptionRes()),
                    selected = form.activity == level,
                    onSelect = { onChange { it.copy(activity = level) } },
                )
            }
        }
    }
}

/** Goal type, and for a cut or build the speed with its effect in kg per week and kcal per day. */
@Composable
fun GoalSection(form: TargetForm, onChange: ((TargetForm) -> TargetForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.weight_goal_label))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            GoalType.entries.forEachIndexed { index, goal ->
                SegmentedButton(
                    selected = form.goalType == goal,
                    onClick = { onChange { it.copy(goalType = goal, goalRatePct = goal.defaultSpeedPct) } },
                    shape = SegmentedButtonDefaults.itemShape(index, GoalType.entries.size),
                ) { Text(stringResource(goal.labelRes()), maxLines = 1) }
            }
        }
        Text(
            stringResource(form.goalType.descriptionRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (form.goalType.hasSpeed) {
            SectionHeader(stringResource(R.string.speed_label), modifier = Modifier.padding(top = 4.dp))
            Column(modifier = Modifier.selectableGroup()) {
                form.goalType.speedsPct.forEach { pct ->
                    ChoiceRow(
                        title = stringResource(R.string.speed_option, formatNumber(pct, 2)),
                        description = speedEffect(form, pct),
                        selected = form.effectiveRatePct == pct,
                        onSelect = { onChange { it.copy(goalRatePct = pct) } },
                    )
                }
            }
        }
    }
}

/** "−0.4 kg per week ≈ −440 kcal per day" for [pct] at the weight in the form. */
@Composable
fun speedEffect(form: TargetForm, pct: Double): String {
    val weight = form.weightValue?.takeIf { it in CalorieCalculator.WEIGHT_RANGE_KG }
        ?: return stringResource(R.string.speed_effect_unknown)
    val kgPerWeek = CalorieCalculator.weeklyChangeKg(weight, form.goalType, pct)
    val kcalPerDay = CalorieCalculator.dailyDeltaKcal(weight, form.goalType, pct)
    return stringResource(
        R.string.speed_effect,
        formatSigned(kgPerWeek, formatNumber(kotlin.math.abs(kgPerWeek), 2)),
        formatSigned(kcalPerDay, formatKcal(kotlin.math.abs(kcalPerDay))),
    )
}

/** Automatic macros (protein per kg, fat %) or the manual percentage split. */
@Composable
fun MacroSection(form: TargetForm, onChange: ((TargetForm) -> TargetForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.macro_mode_label))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MacroMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = form.macroMode == mode,
                    onClick = { onChange { it.copy(macroMode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index, MacroMode.entries.size),
                ) { Text(stringResource(mode.labelRes())) }
            }
        }
        when (form.macroMode) {
            MacroMode.AUTO -> {
                Text(
                    stringResource(R.string.macro_mode_auto_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val perKg = form.effectiveProteinPerKg
                Text(stringResource(R.string.protein_per_kg_label, formatNumber(perKg, 1, 1)))
                val range = Macros.PROTEIN_PER_KG_RANGE
                Slider(
                    value = perKg.toFloat(),
                    onValueChange = { value ->
                        val rounded = (value * 10).roundToInt() / 10.0
                        onChange { it.copy(proteinPerKg = rounded) }
                    },
                    valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
                    steps = ((range.endInclusive - range.start) * 10).roundToInt() - 1,
                )
                Text(stringResource(R.string.fat_pct_label, form.fatPct))
                Slider(
                    value = form.fatPct.toFloat(),
                    onValueChange = { value -> onChange { it.copy(fatPct = value.roundToInt()) } },
                    valueRange = Macros.MIN_FAT_PCT.toFloat()..MAX_FAT_PCT.toFloat(),
                    steps = MAX_FAT_PCT - Macros.MIN_FAT_PCT - 1,
                )
            }
            MacroMode.SPLIT -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = form.proteinPercent,
                        onValueChange = { text -> onChange { it.copy(proteinPercent = text) } },
                        label = stringResource(R.string.split_protein),
                        decimal = false,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = form.carbsPercent,
                        onValueChange = { text -> onChange { it.copy(carbsPercent = text) } },
                        label = stringResource(R.string.split_carbs),
                        decimal = false,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = form.fatPercent,
                        onValueChange = { text -> onChange { it.copy(fatPercent = text) } },
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
            }
        }
    }
}

private const val MAX_FAT_PCT = 40

/** Resting energy, maintenance, the target and its macros, the formula used and any warnings. */
@Composable
fun ResultCard(result: CalculatorResult, form: TargetForm, hideNumbers: Boolean = false) {
    val dateFormatter = rememberMediumDateFormatter()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!hideNumbers) {
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
                HorizontalDivider()
            }
            val bodyFat = form.bodyFat
            Text(
                text = when (result.formula) {
                    BmrFormula.KATCH_MCARDLE -> stringResource(
                        R.string.formula_katch,
                        formatNumber(bodyFat?.pct ?: 0.0, 1, 1),
                        bodyFat?.date?.format(dateFormatter).orEmpty(),
                    )
                    BmrFormula.MIFFLIN_ST_JEOR -> stringResource(R.string.formula_mifflin)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.warnings.sortedBy { it.ordinal }.forEach { warning ->
                Text(
                    text = stringResource(warning.textRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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

/** A radio-button row with a title and a smaller description. */
@Composable
fun ChoiceRow(title: String, description: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun TargetWarning.textRes(): Int = when (this) {
    TargetWarning.FAST_CUT -> R.string.warning_fast_cut
    TargetWarning.LOW_BODY_FAT_CUT -> R.string.warning_low_body_fat
    TargetWarning.PROTEIN_TOO_HIGH -> R.string.warning_protein_too_high
}
