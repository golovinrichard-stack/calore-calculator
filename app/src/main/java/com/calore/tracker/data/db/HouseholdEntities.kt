package com.calore.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The household this phone belongs to (data-model M11). Local: the hub is the authority. The key is never here. */
@Entity(tableName = "household")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hubDeviceId: String,
    /** Goes up when the host changes the key (after removing a member). */
    @ColumnInfo(defaultValue = "1") val keyVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * A phone in the household (data-model M11). Shared: each phone writes its own row (its person's
 * name and colour, and meal targets only if they allow it). [lastSeenAt] and [seenVector] stay on
 * this phone (they are not synced).
 */
@Entity(tableName = "household_devices", indices = [Index(value = ["memberId"])])
data class HouseholdDeviceEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    /** The phone's name, e.g. "Anna's phone". */
    val name: String,
    val memberName: String,
    @ColumnInfo(defaultValue = "0") val colour: Int,
    @ColumnInfo(defaultValue = "0") val isHub: Boolean,
    /** The host confirmed this phone (M11.2). */
    @ColumnInfo(defaultValue = "0") val approved: Boolean,
    /** M11.9: may other phones see this person's meal targets? */
    @ColumnInfo(defaultValue = "0") val sharesTargets: Boolean,
    /** Daily targets, meal split, diet and allergens as JSON, only when [sharesTargets]. */
    val targets: String?,
    val lastSeenAt: Long?,
    /** The phone's version vector at its last sync (for compaction, A22). */
    val seenVector: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A request between members (data-model M11). Shared. */
@Entity(tableName = "requests", indices = [Index(value = ["status"])])
data class RequestEntity(
    @PrimaryKey val id: String,
    /** `COOK`, `BUY`, `CAN_I_HAVE`, `OTHER`. */
    val type: String,
    val fromMemberId: String,
    /** Null: anyone. */
    val toMemberId: String?,
    val refTable: String?,
    val refId: String?,
    val message: String?,
    val dueDate: String?,
    /** "Cook for me": the meal and portions. */
    val meal: String?,
    val portions: Int?,
    /** `OPEN`, `ACCEPTED`, `DECLINED`, `DONE`, `CANCELLED`. */
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One line of the activity feed (data-model M11). Shared. */
@Entity(tableName = "activity", indices = [Index(value = ["at"])])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    /** `BOUGHT`, `COOKED`, `TOOK`, `THREW_AWAY`, `REQUESTED`, `ANSWERED`. */
    val type: String,
    val refTable: String?,
    val refId: String?,
    /** Item names (shared data anyway), joined; the sentence is made on each phone in its language. */
    val summary: String,
    val at: Long,
)

/** Every change this phone made or received (A22). Local. */
@Entity(tableName = "sync_ops", indices = [Index(value = ["deviceId", "seq"], unique = true)])
data class SyncOpEntity(
    @PrimaryKey val opId: String,
    val deviceId: String,
    val seq: Long,
    val hlc: String,
    val tableName: String,
    val rowId: String,
    /** The changed columns as a JSON object. */
    val payload: String,
    val receivedAt: Long,
    /** False while the row it changes isn't here yet; tried again after each sync. */
    @ColumnInfo(defaultValue = "1") val applied: Boolean,
)

/** The clock of the last change of each column of each shared row (A22). Local. */
@Entity(tableName = "sync_field_clock", primaryKeys = ["tableName", "rowId", "column"])
data class SyncFieldClockEntity(val tableName: String, val rowId: String, val column: String, val hlc: String)

/** This phone's version vector: the highest seq seen from each device (A22). Local. */
@Entity(tableName = "sync_peers")
data class SyncPeerEntity(@PrimaryKey val deviceId: String, val maxSeqSeen: Long)

/** Columns changed by local writes, recorded by triggers in the same transaction, waiting to become ops. Local. */
@Entity(tableName = "sync_changes")
data class SyncChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val tableName: String,
    val rowId: String,
    /** A column name, `*` for a new row, or `__deleted` for a removed row. */
    val column: String,
)

/** One row: 0 = not in a household, 1 = record local changes, 2 = applying incoming ops. Local. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val id: Int, val mode: Int)
