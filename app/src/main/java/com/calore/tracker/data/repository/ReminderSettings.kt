package com.calore.tracker.data.repository

import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.notifications.ReminderScheduler
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/** Keeps the reminder settings and the scheduled work in step. */
class ReminderSettings(
    private val appPrefs: AppPrefs,
    private val scheduler: ReminderScheduler,
    private val body: BodyRepository,
    private val clock: Clock,
) {
    /** Sets the body-fat reminder to every [weeks] weeks, counted from the last measurement; 0 turns it off. */
    suspend fun setMeasureReminderWeeks(weeks: Int) {
        appPrefs.setMeasureReminderWeeks(weeks)
        if (weeks > 0) {
            val last = body.measurements().first().lastOrNull()?.date ?: LocalDate.now(clock)
            scheduler.scheduleMeasureReminder(last, weeks)
        } else {
            scheduler.cancelMeasureReminder()
        }
    }

    /** A new measurement restarts the countdown. */
    suspend fun onMeasured(date: LocalDate) {
        val weeks = appPrefs.settings.first().measureReminderWeeks
        if (weeks > 0) scheduler.scheduleMeasureReminder(date, weeks)
    }
}
