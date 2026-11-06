package com.calore.tracker.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.calore.tracker.CaloreApp
import com.calore.tracker.R
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.flow.first

/** Monday morning (M1.7): creates this week's check-in and, if reminders are on, says it is ready. */
class CheckinWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CaloreApp).container
        val checkin = container.checkinService.checkInThisWeek() ?: return Result.success()
        if (container.appPrefs.settings.first().checkinReminders && !checkin.accepted) {
            Notifications.post(
                applicationContext,
                CaloreChannel.PROGRESS,
                Notifications.ID_CHECKIN,
                applicationContext.getString(R.string.notify_checkin_title),
                applicationContext.getString(R.string.notify_checkin_text),
                Routes.CHECKIN,
            )
        }
        return Result.success()
    }
}
