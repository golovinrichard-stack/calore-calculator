package com.calore.tracker.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.calore.tracker.CaloreApp
import com.calore.tracker.R
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Schedules background work. An interface so screens and tests don't depend on WorkManager. */
interface ReminderScheduler {
    /** Reminds [weeks] after [lastMeasured] to measure body fat again (M1.4). */
    fun scheduleMeasureReminder(lastMeasured: LocalDate, weeks: Int)

    fun cancelMeasureReminder()
}

/** Does nothing; for previews and tests. */
object NoReminders : ReminderScheduler {
    override fun scheduleMeasureReminder(lastMeasured: LocalDate, weeks: Int) = Unit
    override fun cancelMeasureReminder() = Unit
}

/**
 * WorkManager jobs. No polling: each job runs once at its time, with a flexible window so Android
 * can batch it with other work (implementation rule 8).
 */
class WorkManagerReminders(private val context: Context, private val clock: Clock) : ReminderScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override fun scheduleMeasureReminder(lastMeasured: LocalDate, weeks: Int) {
        val delay = ReminderSchedule.untilMeasureReminder(lastMeasured, weeks, ZonedDateTime.now(clock))
        val request = OneTimeWorkRequestBuilder<MeasureReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(MEASURE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelMeasureReminder() {
        workManager.cancelUniqueWork(MEASURE_WORK)
    }

    private companion object {
        const val MEASURE_WORK = "measure_reminder"
    }
}

/** "Time to measure your body fat", then the next reminder [weeks] later. */
class MeasureReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CaloreApp).container
        val weeks = container.appPrefs.settings.first().measureReminderWeeks
        if (weeks <= 0) return Result.success()
        Notifications.post(
            applicationContext,
            CaloreChannel.PROGRESS,
            Notifications.ID_MEASURE,
            applicationContext.getString(R.string.notify_measure_title),
            applicationContext.getString(R.string.notify_measure_text),
            Routes.BODY_FAT,
        )
        container.reminders.scheduleMeasureReminder(LocalDate.now(container.clock), weeks)
        return Result.success()
    }
}
