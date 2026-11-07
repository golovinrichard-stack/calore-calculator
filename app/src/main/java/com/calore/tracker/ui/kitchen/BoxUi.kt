package com.calore.tracker.ui.kitchen

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
