package com.calore.tracker.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.calore.tracker.domain.Limits
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.QuickAddDraft
import com.calore.tracker.domain.QuickAddResult
import com.calore.tracker.ui.components.NumberField

/**
 * Quick add (M2.5): calories without a food, for a meal out or a dish whose ingredients you
 * don't know. With [onDelete] it edits an existing entry.
 */
@Composable
fun QuickAddDialog(
    initial: QuickAddDraft,
    onSave: (name: String, nutrients: Nutrients) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var draft by rememberSaveable(stateSaver = draftSaver) { mutableStateOf(initial) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val invalid = (draft.validate() as? QuickAddResult.Invalid)?.fields.orEmpty().takeIf { attempted }.orEmpty()
    val macroError = stringResource(R.string.error_goal_macro)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (onDelete == null) R.string.quick_add_title else R.string.quick_add_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.quick_add_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(Limits.MAX_NAME_LENGTH + 1)) },
                    label = { Text(stringResource(R.string.quick_add_name)) },
                    singleLine = true,
                    isError = QuickAddDraft.Field.NAME in invalid,
                )
                NumberField(
                    value = draft.kcal,
                    onValueChange = { draft = draft.copy(kcal = it) },
                    label = stringResource(R.string.nutrient_calories),
                    suffix = stringResource(R.string.unit_kcal),
                    errorText = if (QuickAddDraft.Field.KCAL in invalid) stringResource(R.string.error_quick_kcal) else null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = draft.protein,
                        onValueChange = { draft = draft.copy(protein = it) },
                        label = stringResource(R.string.nutrient_protein),
                        errorText = if (QuickAddDraft.Field.PROTEIN in invalid) macroError else null,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = draft.carbs,
                        onValueChange = { draft = draft.copy(carbs = it) },
                        label = stringResource(R.string.nutrient_carbs),
                        errorText = if (QuickAddDraft.Field.CARBS in invalid) macroError else null,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = draft.fat,
                        onValueChange = { draft = draft.copy(fat = it) },
                        label = stringResource(R.string.nutrient_fat),
                        errorText = if (QuickAddDraft.Field.FAT in invalid) macroError else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(stringResource(R.string.quick_add_macros_optional), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val result = draft.validate()) {
                    is QuickAddResult.Valid -> onSave(result.name, result.nutrients)
                    is QuickAddResult.Invalid -> attempted = true
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private val draftSaver = androidx.compose.runtime.saveable.listSaver<QuickAddDraft, String>(
    save = { listOf(it.name, it.kcal, it.protein, it.carbs, it.fat) },
    restore = { QuickAddDraft(it[0], it[1], it[2], it[3], it[4]) },
)
