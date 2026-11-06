package com.calore.tracker.ui.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Where the tape goes in the tutorial (M1.4). */
enum class TapeSpot { NECK, WAIST_MEN, WAIST_WOMEN, HIPS }

/**
 * A simple front-view figure with the tape at [spot]. Drawn in a 100 × 120 grid; the women's
 * figure has a narrower waist and wider hips so the two waist positions are easy to tell apart.
 */
@Composable
fun TapeDrawing(spot: TapeSpot, contentDescription: String, modifier: Modifier = Modifier) {
    val body = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val tape = MaterialTheme.colorScheme.primary
    val female = spot == TapeSpot.WAIST_WOMEN || spot == TapeSpot.HIPS
    Canvas(
        modifier = modifier
            .size(width = 200.dp, height = 240.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val unit = minOf(size.width / 100f, size.height / 120f)
        val left = (size.width - 100f * unit) / 2f
        fun p(x: Float, y: Float) = Offset(left + x * unit, y * unit)

        val shape = figurePath(female, ::p)
        drawPath(shape, color = body)
        drawPath(shape, color = outline, style = Stroke(width = 1.2f * unit))
        drawCircle(body, radius = 10f * unit, center = p(50f, 13f))
        drawCircle(outline, radius = 10f * unit, center = p(50f, 13f), style = Stroke(width = 1.2f * unit))

        val (y, from, to) = when (spot) {
            TapeSpot.NECK -> Triple(25.5f, 43f, 57f)
            TapeSpot.WAIST_MEN -> Triple(72f, 29f, 71f)
            TapeSpot.WAIST_WOMEN -> Triple(61f, 33f, 67f)
            TapeSpot.HIPS -> Triple(84f, 21f, 79f)
        }
        drawTape(p(from, y), p(to, y), unit, tape)
        if (spot == TapeSpot.WAIST_MEN) {
            // The belly button the tape goes over.
            drawCircle(outline, radius = 1.2f * unit, center = p(50f, 72f + 3.5f))
        }
    }
}

private fun figurePath(female: Boolean, p: (Float, Float) -> Offset): Path {
    val shoulder = if (female) 27f else 23f
    val waist = if (female) 35f else 31f
    val waistY = if (female) 61f else 70f
    val hip = if (female) 22f else 29f
    return Path().apply {
        fun move(o: Offset) = moveTo(o.x, o.y)
        fun line(o: Offset) = lineTo(o.x, o.y)
        fun curve(c: Offset, o: Offset) = quadraticTo(c.x, c.y, o.x, o.y)
        move(p(45f, 22f))
        line(p(45f, 29f))
        curve(p(shoulder, 30f), p(shoulder, 38f))
        curve(p(waist - 1f, waistY - 12f), p(waist, waistY))
        curve(p(hip, 80f), p(hip + 1f, 92f))
        line(p(hip + 3f, 118f))
        line(p(48f, 118f))
        line(p(50f, 98f))
        line(p(52f, 118f))
        line(p(100f - hip - 3f, 118f))
        line(p(100f - hip - 1f, 92f))
        curve(p(100f - hip, 80f), p(100f - waist, waistY))
        curve(p(100f - waist + 1f, waistY - 12f), p(100f - shoulder, 38f))
        curve(p(100f - shoulder, 30f), p(55f, 29f))
        line(p(55f, 22f))
        close()
    }
}

private fun DrawScope.drawTape(from: Offset, to: Offset, unit: Float, color: androidx.compose.ui.graphics.Color) {
    drawLine(color, from, to, strokeWidth = 3f * unit, cap = StrokeCap.Round)
    // Centimetre marks on the tape.
    val marks = 8
    for (i in 1 until marks) {
        val x = from.x + (to.x - from.x) * i / marks
        drawLine(
            color.copy(alpha = 0.6f),
            Offset(x, from.y - 2.5f * unit),
            Offset(x, from.y - 1.5f * unit),
            strokeWidth = 0.6f * unit,
        )
    }
    drawRect(color, topLeft = Offset(to.x, to.y - 2.5f * unit), size = Size(3f * unit, 5f * unit))
}
