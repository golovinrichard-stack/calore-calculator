package com.calore.tracker.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.calore.tracker.domain.WeightEntry

/**
 * A plain line chart of body weight over time. The x axis is real calendar time, so gaps
 * between weigh-ins show as longer segments. Needs at least two entries, oldest first.
 */
@Composable
fun WeightChart(entries: List<WeightEntry>, description: String, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description },
    ) {
        if (entries.size < 2) return@Canvas

        val inset = 8.dp.toPx()
        val width = size.width - 2 * inset
        val height = size.height - 2 * inset

        val firstDay = entries.first().date.toEpochDay()
        val daySpan = (entries.last().date.toEpochDay() - firstDay).coerceAtLeast(1L).toFloat()
        val minKg = entries.minOf { it.kg }
        val maxKg = entries.maxOf { it.kg }
        // Keep a little headroom, and at least a 1 kg window so a flat line isn't stretched.
        val padding = ((maxKg - minKg) * 0.15).coerceAtLeast(0.5)
        val low = minKg - padding
        val kgSpan = (maxKg + padding - low).toFloat()

        fun point(entry: WeightEntry) = Offset(
            x = inset + (entry.date.toEpochDay() - firstDay) / daySpan * width,
            y = inset + height - ((entry.kg - low).toFloat() / kgSpan) * height,
        )

        for (i in 0..GRID_LINES) {
            val y = inset + height * i / GRID_LINES
            drawLine(gridColor, Offset(inset, y), Offset(inset + width, y), strokeWidth = 1.dp.toPx())
        }

        val path = Path()
        entries.forEachIndexed { index, entry ->
            val p = point(entry)
            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (entries.size <= MAX_DOTS) {
            entries.forEach { drawCircle(lineColor, radius = 4.dp.toPx(), center = point(it)) }
        }
    }
}

private const val GRID_LINES = 3
private const val MAX_DOTS = 60
