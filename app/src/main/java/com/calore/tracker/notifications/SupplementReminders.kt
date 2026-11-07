package com.calore.tracker.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.calore.tracker.CaloreApp
import com.calore.tracker.MainActivity
import com.calore.tracker.R
import com.calore.tracker.data.repository.SupplementRepository
import com.calore.tracker.domain.SupplementSchedule
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Supplement reminders (M8.4), only for supplements whose **Reminders** switch is on. Each one has
 * one alarm, for its next dose, with a window of about 10 minutes: supplements don't need exact
 * alarms. The alarm posts a notification with **Taken** and **Skip** and sets the next one.
 */
class SupplementReminders(private val context: Context, private val supplements: SupplementRepository, private val clock: Clock) {

    /** Sets (or cancels) every supplement's next reminder: after a change, a reboot or a time change. */
    suspend fun rescheduleAll() {
        supplements.supplementsNow().forEach { reschedule(it.id) }
    }

    /** Sets the next reminder of one supplement, or cancels it when reminders are off or nothing is left. */
    suspend fun reschedule(supplementId: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val supplement = supplements.supplement(supplementId)
        manager.cancel(alarmIntent(context, supplementId, 0L))
        if (supplement == null || !supplement.remindersOn || !supplement.active) return
        val next = SupplementSchedule.nextDose(supplement, clock.millis(), clock.zone) ?: return
        manager.setWindow(AlarmManager.RTC_WAKEUP, next.scheduledAt, WINDOW_MS, alarmIntent(context, supplementId, next.scheduledAt))
    }

    companion object {
        /** About 10 minutes (M8.4). */
        const val WINDOW_MS = 10 * 60 * 1000L

        internal const val ACTION_ALARM = "com.calore.tracker.SUPPLEMENT_ALARM"
        internal const val ACTION_TAKEN = "com.calore.tracker.SUPPLEMENT_TAKEN"
        internal const val ACTION_SKIP = "com.calore.tracker.SUPPLEMENT_SKIP"
        internal const val EXTRA_ID = "supplementId"
        internal const val EXTRA_AT = "scheduledAt"

        /** One notification per supplement. */
        fun notificationId(supplementId: String): Int = Notifications.ID_SUPPLEMENT_BASE + (supplementId.hashCode() and 0xFFF)

        internal fun alarmIntent(context: Context, supplementId: String, scheduledAt: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                supplementId.hashCode(),
                Intent(context, SupplementReceiver::class.java).setAction(ACTION_ALARM).putExtra(EXTRA_ID, supplementId).putExtra(EXTRA_AT, scheduledAt),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun actionIntent(context: Context, action: String, supplementId: String, scheduledAt: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                (action + supplementId).hashCode(),
                Intent(context, SupplementReceiver::class.java).setAction(action).putExtra(EXTRA_ID, supplementId).putExtra(EXTRA_AT, scheduledAt),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** The reminder: the supplement and dose, with Taken and Skip that log without opening the app. */
        internal fun post(context: Context, supplementId: String, scheduledAt: Long, title: String, text: String) {
            if (!Notifications.canPost(context)) return
            Notifications.ensureChannel(context, CaloreChannel.SUPPLEMENTS)
            val open = PendingIntent.getActivity(
                context,
                notificationId(supplementId),
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(Notifications.EXTRA_OPEN, Routes.DIARY),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CaloreChannel.SUPPLEMENTS.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.supplement_taken), actionIntent(context, ACTION_TAKEN, supplementId, scheduledAt))
                .addAction(0, context.getString(R.string.supplement_skip), actionIntent(context, ACTION_SKIP, supplementId, scheduledAt))
                .build()
            try {
                NotificationManagerCompat.from(context).notify(notificationId(supplementId), notification)
            } catch (e: SecurityException) {
                // The permission was withdrawn between the check and the post.
            }
        }
    }
}

/** The alarm, and Taken / Skip on the notification (M8.4). */
class SupplementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supplementId = intent.getStringExtra(SupplementReminders.EXTRA_ID) ?: return
        val scheduledAt = intent.getLongExtra(SupplementReminders.EXTRA_AT, 0L)
        val container = (context.applicationContext as CaloreApp).container
        val pending = goAsync()
        scope.launch {
            try {
                val repository = container.supplementRepository
                val dose = repository.dose(supplementId, scheduledAt)
                when (intent.action) {
                    SupplementReminders.ACTION_ALARM -> {
                        val logged = dose != null && repository.logged(dose)
                        if (dose != null && !logged) {
                            val amount = com.calore.tracker.ui.formatNumber(dose.supplement.doseAmount, decimals = 1)
                            SupplementReminders.post(
                                context,
                                supplementId,
                                scheduledAt,
                                dose.supplement.name,
                                context.getString(R.string.supplement_reminder_text, amount, context.getString(dose.supplement.doseUnit.labelRes())),
                            )
                        }
                        container.supplementReminders.reschedule(supplementId)
                    }
                    SupplementReminders.ACTION_TAKEN, SupplementReminders.ACTION_SKIP -> {
                        if (dose != null) {
                            if (intent.action == SupplementReminders.ACTION_TAKEN) repository.take(dose) else repository.skip(dose)
                        }
                        NotificationManagerCompat.from(context).cancel(SupplementReminders.notificationId(supplementId))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** After a reboot, an app update or a change of time or time zone, the reminders are set again (M8.4). */
class SupplementBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val container = (context.applicationContext as CaloreApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                container.supplementReminders.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

/** For texts: the unit of a dose. */
fun com.calore.tracker.domain.DoseUnit.labelRes(): Int = when (this) {
    com.calore.tracker.domain.DoseUnit.MG -> R.string.dose_mg
    com.calore.tracker.domain.DoseUnit.UG -> R.string.dose_ug
    com.calore.tracker.domain.DoseUnit.IU -> R.string.dose_iu
    com.calore.tracker.domain.DoseUnit.G -> R.string.dose_g
    com.calore.tracker.domain.DoseUnit.ML -> R.string.dose_ml
    com.calore.tracker.domain.DoseUnit.CAPSULE -> R.string.dose_capsule
    com.calore.tracker.domain.DoseUnit.TABLET -> R.string.dose_tablet
    com.calore.tracker.domain.DoseUnit.SCOOP -> R.string.dose_scoop
}
