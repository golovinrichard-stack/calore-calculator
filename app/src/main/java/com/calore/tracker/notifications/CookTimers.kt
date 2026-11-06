package com.calore.tracker.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.calore.tracker.ui.navigation.Routes

/**
 * Cook-mode timers that ring with the app closed (M4.4): an exact alarm when the user allows exact
 * alarms, otherwise an inexact one that Android may delay a little.
 */
object CookTimers {

    /** True when timers can ring on the minute. */
    fun canBeExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /** Rings at [atMillis] with [title] and [text]; a timer with the same [id] is replaced. */
    fun start(context: Context, id: Int, atMillis: Long, title: String, text: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, id, title, text)
        if (canBeExact(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, id, "", ""))
    }

    private fun pendingIntent(context: Context, id: Int, title: String, text: String): PendingIntent {
        val intent = Intent(context, CookTimerReceiver::class.java)
            .putExtra(CookTimerReceiver.EXTRA_ID, id)
            .putExtra(CookTimerReceiver.EXTRA_TITLE, title)
            .putExtra(CookTimerReceiver.EXTRA_TEXT, text)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** Posts the "time's up" notification on the Cooking timers channel. */
class CookTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.post(
            context,
            CaloreChannel.COOKING_TIMERS,
            intent.getIntExtra(EXTRA_ID, Notifications.ID_TIMER_BASE),
            intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            Routes.KITCHEN,
        )
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}
