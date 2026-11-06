package com.calore.tracker.data.repository

import com.calore.tracker.data.db.BodyDao
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.domain.BodyFatMethod
import com.calore.tracker.domain.BodyFatReading
import com.calore.tracker.domain.BodyMeasurement
import com.calore.tracker.domain.Checkin
import com.calore.tracker.domain.newId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** The phone's user's body measurements and weekly check-ins. Private data: never shared. */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyRepository(
    private val bodyDao: BodyDao,
    private val members: MemberRepository,
    private val clock: Clock,
) {
    private val meId: Flow<String> = members.me().map { it.id }

    /** Oldest first. */
    fun measurements(): Flow<List<BodyMeasurement>> = meId.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList()) else bodyDao.observeMeasurements(id).map { rows -> rows.mapNotNull { it.toDomain() } }
    }

    /** The latest body-fat value, which decides the BMR formula (A2). */
    fun latestBodyFat(): Flow<BodyFatReading?> = meId.flatMapLatest { id ->
        if (id.isEmpty()) {
            flowOf(null)
        } else {
            bodyDao.observeLatestBodyFat(id).map { row ->
                val measurement = row?.toDomain()
                val pct = measurement?.bodyFatPct
                if (measurement != null && pct != null) BodyFatReading(pct, measurement.date) else null
            }
        }
    }

    /** Saves a measurement for today and returns true if it was the first one ever. */
    suspend fun saveBodyFat(
        method: BodyFatMethod,
        bodyFatPct: Double,
        neckCm: Double? = null,
        waistCm: Double? = null,
        hipCm: Double? = null,
        date: LocalDate = LocalDate.now(clock),
    ): Boolean {
        val me = members.ensureMe()
        val first = measurements().first().isEmpty()
        val now = clock.millis()
        bodyDao.upsertMeasurement(
            BodyMeasurement(
                id = newId(),
                memberId = me.id,
                date = date,
                neckCm = neckCm,
                waistCm = waistCm,
                hipCm = hipCm,
                bodyFatPct = bodyFatPct,
                method = method,
                createdAt = now,
                updatedAt = now,
            ).toEntity(),
        )
        return first
    }

    /** Newest first. */
    fun checkins(): Flow<List<Checkin>> = meId.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList()) else bodyDao.observeCheckins(id).map { rows -> rows.mapNotNull { it.toDomain() } }
    }

    suspend fun checkinFor(weekStart: LocalDate): Checkin? {
        val me = members.ensureMe()
        return bodyDao.checkinFor(me.id, weekStart.toString())?.toDomain()
    }

    suspend fun saveCheckin(checkin: Checkin) {
        bodyDao.upsertCheckin(checkin.toEntity())
    }
}
