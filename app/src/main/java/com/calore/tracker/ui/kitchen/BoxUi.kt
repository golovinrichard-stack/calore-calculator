package com.calore.tracker.ui.kitchen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.Visibility

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calore.tracker.R
import com.calore.tracker.domain.FreshnessColour
import com.calore.tracker.domain.WasteReason

/** Green, yellow or red (A12), never the only signal: [freshnessLabel] says it in words. */
@Composable
fun freshnessColor(colour: FreshnessColour): Color = when (colour) {
    FreshnessColour.GREEN -> Color(0xFF2E7D32)
    FreshnessColour.YELLOW -> Color(0xFFF9A825)
    FreshnessColour.RED -> MaterialTheme.colorScheme.error
}

@Composable
fun freshnessLabel(colour: FreshnessColour): String = stringResource(
    when (colour) {
        FreshnessColour.GREEN -> R.string.fresh_green
        FreshnessColour.YELLOW -> R.string.fresh_yellow
        FreshnessColour.RED -> R.string.fresh_red
    },
)

@Composable
fun FreshnessDot(colour: FreshnessColour, modifier: Modifier = Modifier) {
    Box(modifier.size(12.dp).clip(CircleShape).background(freshnessColor(colour)))
}

fun WasteReason.labelRes(): Int = when (this) {
    WasteReason.EXPIRED -> R.string.waste_expired
    WasteReason.SPOILED -> R.string.waste_spoiled
    WasteReason.DIDNT_LIKE -> R.string.waste_didnt_like
    WasteReason.OTHER -> R.string.waste_other
}

/** M11.6: "🔒 Anna's, don't touch" in Anna's colour, "Shared" or "Up for grabs". */
@Composable
fun OwnershipLabel(visibility: Visibility, owner: Member?) {
    when (visibility) {
        Visibility.MINE -> Text(
            if (owner != null) stringResource(R.string.label_mine_of, owner.name) else stringResource(R.string.label_mine),
            color = owner?.let { Color(it.colour) } ?: MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Visibility.SHARED -> Text(stringResource(R.string.label_shared), style = MaterialTheme.typography.labelLarge)
        Visibility.UP_FOR_GRABS -> Text(stringResource(R.string.label_up_for_grabs), color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelLarge)
    }
}

/** The owner changes the label (M11.6). */
@Composable
fun LabelPicker(current: Visibility, onPick: (Visibility) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Visibility.entries.forEach { v ->
            FilterChip(
                selected = current == v,
                onClick = { onPick(v) },
                label = {
                    Text(
                        stringResource(
                            when (v) {
                                Visibility.MINE -> R.string.label_pick_mine
                                Visibility.SHARED -> R.string.label_shared
                                Visibility.UP_FOR_GRABS -> R.string.label_up_for_grabs
                            },
                        ),
                    )
                },
            )
        }
    }
}
