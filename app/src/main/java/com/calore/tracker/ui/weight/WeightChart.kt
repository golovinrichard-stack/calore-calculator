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
import com.calore.tracker.domain.TrendPoint

/**
 * Body weight over time (M1.5): each weigh-in is a dot, the smoothed trend (A6) is a line.
 * The x axis is real calendar time, so gaps between weigh-ins show as longer segments.
 * Needs at least two points, oldest first.
 */
@Composable
fun WeightChart(points: List<TrendPoint>, description: String, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description },
    ) {
        if (points.size < 2) return@Canvas

        val inset = 8.dp.toPx()
        val width = size.width - 2 * inset
        val height = size.height - 2 * inset

        val firstDay = points.first().date.toEpochDay()
        val daySpan = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1L).toFloat()
        val minKg = points.minOf { minOf(it.kg, it.trendKg) }
        val maxKg = points.maxOf { maxOf(it.kg, it.trendKg) }
        // Keep a little headroom, and at least a 1 kg window so a flat line isn't stretched.
        val padding = ((maxKg - minKg) * 0.15).coerceAtLeast(0.5)
        val low = minKg - padding
        val kgSpan = (maxKg + padding - low).toFloat()

        fun offset(date: Long, kg: Double) = Offset(
            x = inset + (date - firstDay) / daySpan * width,
            y = inset + height - ((kg - low).toFloat() / kgSpan) * height,
        )

        for (i in 0..GRID_LINES) {
            val y = inset + height * i / GRID_LINES
            drawLine(gridColor, Offset(inset, y), Offset(inset + width, y), strokeWidth = 1.dp.toPx())
        }

        val dotRadius = if (points.size <= MANY_POINTS) 4.dp.toPx() else 2.5.dp.toPx()
        points.forEach { point ->
            drawCircle(dotColor.copy(alpha = 0.7f), radius = dotRadius, center = offset(point.date.toEpochDay(), point.kg))
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val p = offset(point.date.toEpochDay(), point.trendKg)
            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private const val GRID_LINES = 3
private const val MANY_POINTS = 60
