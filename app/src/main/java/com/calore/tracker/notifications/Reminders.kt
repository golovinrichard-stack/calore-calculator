package com.calore.tracker.notifications

import com.calore.tracker.domain.Freshness
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
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

    /** The weekly check-in job, every Monday morning in a flexible window (M1.7). */
    fun scheduleWeeklyCheckin()

    /** "Is the food in the fridge?" 2 hours after cook mode ends (M4.4, A12). */
    fun scheduleCoolingReminder()

    /** The daily morning "eat today" job and the 19:00 thaw job, or neither (M4.9). */
    fun setFreshnessJobs(morning: Boolean, evening: Boolean)
}

/** Does nothing; for previews and tests. */
object NoReminders : ReminderScheduler {
    override fun scheduleMeasureReminder(lastMeasured: LocalDate, weeks: Int) = Unit
    override fun cancelMeasureReminder() = Unit
    override fun scheduleWeeklyCheckin() = Unit
    override fun scheduleCoolingReminder() = Unit
    override fun setFreshnessJobs(morning: Boolean, evening: Boolean) = Unit
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

    override fun scheduleWeeklyCheckin() {
        val delay = ReminderSchedule.untilNextCheckin(ZonedDateTime.now(clock))
        val request = PeriodicWorkRequestBuilder<CheckinWorker>(7, TimeUnit.DAYS, CHECKIN_FLEX_HOURS, TimeUnit.HOURS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        // KEEP: scheduling again at every start must not push the next run back.
        workManager.enqueueUniquePeriodicWork(CHECKIN_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun scheduleCoolingReminder() {
        val request = OneTimeWorkRequestBuilder<CoolingReminderWorker>()
            .setInitialDelay(Freshness.COOLING_REMINDER_HOURS, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork(COOLING_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun setFreshnessJobs(morning: Boolean, evening: Boolean) {
        daily<FreshnessWorker>(FRESHNESS_WORK, morning, Freshness.MORNING_REMINDER_TIME)
        daily<ThawWorker>(THAW_WORK, evening, Freshness.THAW_REMINDER_TIME)
    }

    private inline fun <reified W : androidx.work.ListenableWorker> daily(name: String, on: Boolean, time: java.time.LocalTime) {
        if (!on) {
            workManager.cancelUniqueWork(name)
            return
        }
        val delay = Freshness.until(time, ZonedDateTime.now(clock))
        val request = PeriodicWorkRequestBuilder<W>(1, TimeUnit.DAYS, DAILY_FLEX_MINUTES, TimeUnit.MINUTES)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val FRESHNESS_WORK = "freshness_morning"
        const val THAW_WORK = "thaw_evening"
        const val DAILY_FLEX_MINUTES = 45L
        const val COOLING_WORK = "cooling_reminder"
        const val CHECKIN_WORK = "weekly_checkin"
        const val CHECKIN_FLEX_HOURS = 3L
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

/** "Is the food in the fridge?" (M4.4): cooked food belongs in the fridge within 2 hours. */
class CoolingReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Notifications.post(
            applicationContext,
            CaloreChannel.FRESHNESS,
            Notifications.ID_COOLING,
            applicationContext.getString(R.string.notify_cooling_title),
            applicationContext.getString(R.string.notify_cooling_text),
            Routes.KITCHEN,
        )
        return Result.success()
    }
}

/** Morning (M4.9): "2 boxes should be eaten today" (due today, plus red ones). */
class FreshnessWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CaloreApp).container
        if (!container.appPrefs.settings.first().freshnessReminders) return Result.success()
        val due = Freshness.dueToday(container.cookingRepository.activeBoxes().first().map { it.box }, LocalDate.now(container.clock))
        if (due.isNotEmpty()) {
            val resources = applicationContext.resources
            Notifications.post(
                applicationContext,
                CaloreChannel.FRESHNESS,
                Notifications.ID_FRESHNESS,
                resources.getQuantityString(R.plurals.notify_eat_today_title, due.size, due.size),
                due.joinToString(", ") { it.code },
                Routes.KITCHEN,
            )
        }
        return Result.success()
    }
}

/** Evening (A13, M4.9): "Move box C4 from the freezer to the fridge." */
class ThawWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CaloreApp).container
        if (!container.appPrefs.settings.first().thawReminders) return Result.success()
        val thaw = Freshness.toThawTonight(container.cookingRepository.activeBoxes().first().map { it.box }, LocalDate.now(container.clock))
        if (thaw.isNotEmpty()) {
            Notifications.post(
                applicationContext,
                CaloreChannel.FRESHNESS,
                Notifications.ID_THAW,
                applicationContext.getString(R.string.notify_thaw_title),
                applicationContext.getString(R.string.notify_thaw_text, thaw.joinToString(", ") { it.code }),
                Routes.KITCHEN,
            )
        }
        return Result.success()
    }
}
