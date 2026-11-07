package com.calore.tracker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.room.InvalidationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * Keeps the widget up to date (M9.2): after diary, box and plan changes (Room tells us which
 * tables changed; no polling), at midnight, and every 30 minutes otherwise (the widget's
 * `updatePeriodMillis`). Nothing runs when there is no widget.
 */
object WidgetUpdates {

    /** The tables the widget shows. */
    private val TABLES = arrayOf("diary_entries", "boxes", "plan_slots", "members")

    @OptIn(FlowPreview::class)
    fun observe(context: Context, tracker: InvalidationTracker, scope: CoroutineScope) {
        scope.launch {
            tracker.createFlow(*TABLES, emitInitialState = false)
                .debounce(DEBOUNCE_MS)
                .collect { if (hasWidgets(context)) TodayWidget.refresh(context) }
        }
    }

    suspend fun hasWidgets(context: Context): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).isNotEmpty()

    private const val DEBOUNCE_MS = 1_000L
}

/** An inexact alarm just after midnight, so the widget starts the new day fresh (M9.2). */
object WidgetMidnight {

    fun schedule(context: Context, clock: Clock = Clock.systemDefaultZone()) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val midnight = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli() + ONE_MINUTE
        manager.setWindow(AlarmManager.RTC, midnight, WINDOW_MS, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WidgetMidnightReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val ONE_MINUTE = 60_000L
    private const val WINDOW_MS = 10 * ONE_MINUTE
    private const val REQUEST_CODE = 9001
}

class WidgetMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (WidgetUpdates.hasWidgets(context)) {
                    TodayWidget.refresh(context)
                    WidgetMidnight.schedule(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
