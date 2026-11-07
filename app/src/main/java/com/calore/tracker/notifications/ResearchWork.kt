package com.calore.tracker.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.calore.tracker.CaloreApp
import com.calore.tracker.data.repository.ResearchOutcome
import com.calore.tracker.domain.ReminderSchedule
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Research jobs (M10.4, M10.5): a weekly one on Monday that builds last week's report, and a
 * one-time one that sends (or deletes) with a network, retrying with exponential backoff.
 * Neither runs unless the user agreed; neither runs when the battery is low.
 */
object ResearchWork {
    private const val WEEKLY = "research-weekly"
    private const val SEND = "research-send"
    private val MONDAY_TIME: LocalTime = LocalTime.of(6, 0)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    fun scheduleWeekly(context: Context, clock: Clock) {
        val now = ZonedDateTime.now(clock)
        var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).with(MONDAY_TIME)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val request = PeriodicWorkRequestBuilder<ResearchWeeklyWorker>(7, TimeUnit.DAYS, FLEX_HOURS, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WEEKLY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Sends pending reports, the registration or a deletion as soon as there is a network. */
    fun sendSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<ResearchSendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SEND, ExistingWorkPolicy.REPLACE, request)
    }

    /** "Stop sharing": no more weekly reports. A pending deletion still gets sent. */
    fun cancelWeekly(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WEEKLY)
    }

    private const val FLEX_HOURS = 6L
    private const val BACKOFF_MINUTES = 30L
}

/** Monday: last week's report, then send it (A21). */
class ResearchWeeklyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CaloreApp).container
        val repository = container.researchRepository
        if (!repository.consent.first().active) return Result.success()
        repository.retryFailed()
        repository.buildReport(ReminderSchedule.weekStart(LocalDate.now(container.clock)).minusWeeks(1))
        ResearchWork.sendSoon(applicationContext)
        return Result.success()
    }
}

/** Sends the registration and pending reports, or a pending deletion; retries while offline. */
class ResearchSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as CaloreApp).container.researchRepository
        val deletion = repository.processPendingDeletion()
        val sending = repository.sendPending()
        return if (deletion == ResearchOutcome.WAITING || sending == ResearchOutcome.WAITING) Result.retry() else Result.success()
    }
}
