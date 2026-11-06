package com.calore.tracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A random lowercase version-4 UUID, computed by SQLite for each row. Used only to backfill
 * ids in migrations; the app itself makes ids with [com.calore.tracker.domain.newId].
 */
private const val SQL_RANDOM_UUID =
    "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || " +
        "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))"

/** Adds a `uuid` column to [table], gives every existing row a random one, then makes it unique. */
private fun SupportSQLiteDatabase.addUuidColumn(table: String) {
    execSQL("ALTER TABLE `$table` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
    execSQL("UPDATE `$table` SET `uuid` = $SQL_RANDOM_UUID")
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uuid` ON `$table` (`uuid`)")
}

/** Version 2 (M0.2): sync-ready ids on the existing tables, and `foods.updatedAt`. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addUuidColumn("foods")
        db.addUuidColumn("diary_entries")
        db.addUuidColumn("weight_entries")
        db.execSQL("ALTER TABLE `foods` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `foods` SET `updatedAt` = `lastUsedAt`")
    }
}

/** Every migration, oldest first. Pass these to the database builder and to migration tests. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
