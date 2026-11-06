package com.calore.tracker.ui.body

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.calore.tracker.R
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.ui.profile.ChoiceRow

/** Every 2–8 weeks, or off (0). [offering] words it as an offer after the first measurement. */
@Composable
fun MeasureReminderDialog(
    current: Int,
    offering: Boolean,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var weeks by rememberSaveable { mutableIntStateOf(if (current > 0) current else ReminderSchedule.DEFAULT_MEASURE_WEEKS) }
    var off by rememberSaveable { mutableStateOf(!offering && current == 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (offering) R.string.reminder_offer_title else R.string.settings_measure_reminder)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                if (offering) Text(stringResource(R.string.reminder_offer_text))
                ReminderSchedule.MEASURE_WEEKS.forEach { option ->
                    ChoiceRow(
                        title = pluralStringResource(R.plurals.every_weeks, option, option),
                        description = null,
                        selected = !off && weeks == option,
                        onSelect = {
                            weeks = option
                            off = false
                        },
                    )
                }
                if (!offering) {
                    ChoiceRow(title = stringResource(R.string.reminder_off), description = null, selected = off, onSelect = { off = true })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(if (off) 0 else weeks) }) {
                Text(stringResource(if (offering) R.string.remind_me else R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(if (offering) R.string.no_thanks else R.string.cancel)) }
        },
    )
}
