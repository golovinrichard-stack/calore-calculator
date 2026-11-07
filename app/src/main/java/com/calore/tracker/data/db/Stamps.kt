package com.calore.tracker.data.db

import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Soft deletes (data-model.md, Conventions): a row with `deletedAt` set is hidden from normal
 * queries but kept, so a delete can sync to other phones later. Use the constant in queries:
 *
 * ```
 * @Query("SELECT * FROM recipes WHERE ${SoftDelete.NOT_DELETED} ORDER BY name")
 * ```
 */
object SoftDelete {
    const val NOT_DELETED = "deletedAt IS NULL"

    /** Rows soft-deleted longer ago than this may be purged (not in tables that sync, M11). */
    const val PURGE_AFTER_DAYS = 30L
}

/** The timestamp columns of every new table: epoch millis, `deletedAt` null while the row is live. */
interface Stamped<T : Stamped<T>> {
    val createdAt: Long
    val updatedAt: Long
    val deletedAt: Long?

    /** A copy with these timestamps; implement it with the data class `copy`. */
    fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?): T
}

val Stamped<*>.isDeleted: Boolean get() = deletedAt != null

/** Sets `createdAt`, `updatedAt` and `deletedAt` from the injected [Clock], so tests control time. */
class Stamper(private val clock: Clock) {

    fun now(): Long = clock.millis()

    /** A new row: created and updated now, not deleted. */
    fun <T : Stamped<T>> created(row: T): T {
        val now = now()
        return row.withStamps(createdAt = now, updatedAt = now, deletedAt = null)
    }

    /** A changed row: only `updatedAt` moves. */
    fun <T : Stamped<T>> updated(row: T): T =
        row.withStamps(createdAt = row.createdAt, updatedAt = now(), deletedAt = row.deletedAt)

    /** A soft delete is also a change, so `updatedAt` moves too. */
    fun <T : Stamped<T>> softDeleted(row: T): T {
        val now = now()
        return row.withStamps(createdAt = row.createdAt, updatedAt = now, deletedAt = now)
    }

    /** Undoes [softDeleted]. */
    fun <T : Stamped<T>> restored(row: T): T =
        row.withStamps(createdAt = row.createdAt, updatedAt = now(), deletedAt = null)

    /** Rows with `deletedAt` before this moment may be purged. */
    fun purgeBefore(): Long = now() - TimeUnit.DAYS.toMillis(SoftDelete.PURGE_AFTER_DAYS)
}
