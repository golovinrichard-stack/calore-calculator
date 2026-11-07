package com.calore.tracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.calore.tracker.MainActivity
import com.calore.tracker.R

/**
 * Notification channels (M1.6). Each one is created only when its feature is first switched on,
 * so Android's settings never list channels for features the user doesn't use.
 */
enum class CaloreChannel(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
) {
    /** Weekly check-ins and body-fat measuring reminders (M1). */
    PROGRESS("progress", R.string.channel_progress, R.string.channel_progress_desc),

    /** Eat-by and thaw reminders (M4). */
    FRESHNESS("freshness", R.string.channel_freshness, R.string.channel_freshness_desc),

    /** Cook-mode timers (M4). */
    COOKING_TIMERS("cooking_timers", R.string.channel_cooking_timers, R.string.channel_cooking_timers_desc),

    /** Supplement reminders (M8). */
    SUPPLEMENTS("supplements", R.string.channel_supplements, R.string.channel_supplements_desc),

    /** Household requests and activity (M11). */
    HOUSEHOLD("household", R.string.channel_household, R.string.channel_household_desc),

    /** The quiet, ongoing notification while hub mode is on (M11.5). */
    HUB("household_hub", R.string.channel_hub, R.string.channel_hub_desc, NotificationManager.IMPORTANCE_LOW),
}

object Notifications {
    const val EXTRA_OPEN = "com.calore.tracker.OPEN"
    const val ID_CHECKIN = 1001
    const val ID_MEASURE = 1002
    const val ID_COOLING = 1003
    const val ID_FRESHNESS = 1004
    const val ID_THAW = 1005

    /** Cook-mode timers use ids from here up, one per step. */
    const val ID_TIMER_BASE = 2000

    /** Supplement reminders (M8.4) use ids from here up, one per supplement. */
    const val ID_SUPPLEMENT_BASE = 3000

    /** Household (M11). */
    const val ID_HUB = 4000
    const val ID_HOUSEHOLD_PHONE = 4001
    const val ID_HOUSEHOLD_HUB_NOW = 4002
    const val ID_HOUSEHOLD_TAKEN = 4003

    /** Household requests and answers use ids from here up. */
    const val ID_REQUEST_BASE = 5000

    fun ensureChannel(context: Context, channel: CaloreChannel) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(channel.id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(channel.id, context.getString(channel.title), channel.importance).apply {
                description = context.getString(channel.description)
            },
        )
    }

    /** True when Android lets the app post notifications (Android 13+ needs the permission). */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Updates an ongoing notification (hub mode); nothing without permission. */
    fun notifyOngoing(context: Context, id: Int, notification: android.app.Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // The permission was withdrawn between the check and the post.
        }
    }

    /** Posts a notification that opens the app at [openRoute] when tapped. Does nothing without permission. */
    fun post(context: Context, channel: CaloreChannel, id: Int, title: String, text: String, openRoute: String) {
        if (!canPost(context)) return
        ensureChannel(context, channel)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, openRoute)
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // The permission was withdrawn between the check and the post.
        }
    }
}
