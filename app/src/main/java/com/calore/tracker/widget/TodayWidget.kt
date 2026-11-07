package com.calore.tracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.calore.tracker.CaloreApp
import com.calore.tracker.MainActivity
import com.calore.tracker.R
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.TodayWidgetData
import com.calore.tracker.domain.TodayWidgetModel
import com.calore.tracker.ui.formatCount
import com.calore.tracker.ui.labelRes
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/**
 * The "Today" widget (M9.1): calories against the target as a ring, protein, the next planned
 * meal, the next box with **Ate it**, and how many boxes to eat today. Hide-numbers mode shows only
 * the meal and the boxes. Tapping it opens Today.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent {
            GlanceTheme {
                Content(context, data)
            }
        }
    }

    private class Loaded(val data: TodayWidgetData, val hideNumbers: Boolean)

    private suspend fun load(context: Context): Loaded {
        val c = (context.applicationContext as CaloreApp).container
        val today = LocalDate.now(c.clock)
        val me = c.memberRepository.ensureMe()
        val entries = c.diaryRepository.entriesFor(today).first()
        val totals = entries.fold(Nutrients(kcal = 0.0, protein = 0.0)) { sum, e -> sum + e.nutrients }
        val data = TodayWidgetModel.build(
            today = today,
            now = LocalTime.now(c.clock),
            meId = me.id,
            totals = totals,
            targets = me.effectiveTargets,
            slots = c.planRepository.slotsBetween(today, today).first(),
            boxes = c.cookingRepository.activeBoxes().first(),
            recipeNames = c.recipeRepository.observeAll().first().associate { it.recipe.id to it.recipe.displayName() },
        )
        return Loaded(data, c.appPrefs.settings.first().hideNumbers)
    }

    @Composable
    private fun Content(context: Context, loaded: Loaded) {
        val data = loaded.data
        val wide = LocalSize.current.width >= WIDE.width
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!loaded.hideNumbers) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(ImageProvider(ring(data.kcalProgress)), contentDescription = null, modifier = GlanceModifier.size(RING_DP.dp))
                        Text(
                            formatCount(Math.round(data.kcal).toInt()),
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
                        )
                    }
                    Spacer(GlanceModifier.width(10.dp))
                }
                Column {
                    if (!loaded.hideNumbers) {
                        Text(
                            context.getString(R.string.widget_kcal, formatCount(Math.round(data.kcal).toInt()), formatCount(data.kcalTarget)),
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                        )
                        if (wide) {
                            Text(
                                context.getString(R.string.widget_protein, formatCount(Math.round(data.protein).toInt()), formatCount(data.proteinTarget)),
                                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            )
                        }
                    }
                    Text(
                        data.next?.let { context.getString(R.string.widget_next, context.getString(it.slot.meal.labelRes()), it.label) }
                            ?: context.getString(R.string.widget_nothing_planned),
                        style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
                        maxLines = 2,
                    )
                }
            }
            if (wide) {
                Spacer(GlanceModifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (data.boxesToEat > 0) {
                        Text(
                            context.resources.getQuantityString(R.plurals.widget_boxes_today, data.boxesToEat, data.boxesToEat),
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    } else {
                        Spacer(GlanceModifier.defaultWeight())
                    }
                    val next = data.nextBox
                    val box = next?.box
                    if (next != null && box != null) {
                        Button(
                            text = context.getString(R.string.widget_ate_it, box.box.code),
                            onClick = actionRunCallback<AteItAction>(
                                actionParametersOf(AteItAction.BOX to box.box.id, AteItAction.MEAL to next.slot.meal.name, AteItAction.DATE to next.slot.date.toString()),
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        private const val RING_DP = 56

        /** The calories ring, drawn as a small picture (Glance can't draw arcs). */
        private fun ring(progress: Float): Bitmap {
            val size = 168
            val bitmap = createBitmap(size, size)
            val canvas = Canvas(bitmap)
            val stroke = size * 0.12f
            val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            paint.color = 0x332E7D32
            canvas.drawArc(rect, 0f, 360f, false, paint)
            paint.color = 0xFF2E7D32.toInt()
            if (progress > 0f) canvas.drawArc(rect, -90f, 360f * progress, false, paint)
            return bitmap
        }

        /** Updates every "Today" widget, if there are any. */
        suspend fun refresh(context: Context) = TodayWidget().updateAll(context)
    }
}

/** **Ate it** on the widget: the same action as in the app (M4.7). */
class AteItAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = (context.applicationContext as CaloreApp).container
        val boxId = parameters[BOX] ?: return
        val meal = parameters[MEAL]?.let { name -> MealType.entries.firstOrNull { it.name == name } } ?: return
        val date = parameters[DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val view = c.cookingRepository.activeBoxes().first().firstOrNull { it.box.id == boxId } ?: return
        val me = c.memberRepository.ensureMe()
        c.cookingRepository.eat(view, null, meal, date, ownerIsMe = view.box.ownerMemberId == null || view.box.ownerMemberId == me.id)
        TodayWidget().update(context, glanceId)
    }

    companion object {
        val BOX = ActionParameters.Key<String>("boxId")
        val MEAL = ActionParameters.Key<String>("meal")
        val DATE = ActionParameters.Key<String>("date")
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetMidnight.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetMidnight.cancel(context)
    }
}
