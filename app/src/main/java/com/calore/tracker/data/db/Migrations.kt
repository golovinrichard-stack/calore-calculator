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

/**
 * Version 3 (M1.1): people and body. New tables `members`, `body_measurements` and `checkins`,
 * and `diary_entries.memberId` (NULL = the phone's user). The ME row itself is created by the
 * app on first start, because the old profile lives in DataStore, not in SQLite.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `members` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`colour` INTEGER NOT NULL, `sex` TEXT, `birthDate` TEXT, `heightCm` REAL, `weightKg` REAL, " +
                "`activity` TEXT, `goalType` TEXT, `goalRatePct` REAL, `macroMode` TEXT NOT NULL DEFAULT 'AUTO', " +
                "`proteinPerKg` REAL, `fatPct` INTEGER, `splitProteinPct` INTEGER, `splitCarbsPct` INTEGER, " +
                "`splitFatPct` INTEGER, `targetKcal` INTEGER, `targetProteinG` INTEGER, `targetCarbsG` INTEGER, " +
                "`targetFatG` INTEGER, `mealSplit` TEXT NOT NULL, `diet` TEXT NOT NULL DEFAULT 'OMNIVORE', " +
                "`allergens` TEXT NOT NULL, `likedFoods` TEXT NOT NULL, `dislikedFoods` TEXT NOT NULL, " +
                "`cuisines` TEXT NOT NULL, `equipment` TEXT NOT NULL, `variety` INTEGER NOT NULL DEFAULT 2, " +
                "`weeklyBudgetHalers` INTEGER, `cookSessionsPerWeek` INTEGER NOT NULL DEFAULT 2, " +
                "`cookDays` TEXT NOT NULL, `minutesPerSession` INTEGER NOT NULL DEFAULT 120, " +
                "`preppedMeals` TEXT NOT NULL, `sharesTargets` INTEGER NOT NULL DEFAULT 0, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_measurements` (`id` TEXT NOT NULL, `memberId` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, `neckCm` REAL, `waistCm` REAL, `hipCm` REAL, `bodyFatPct` REAL, " +
                "`method` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_body_measurements_memberId_date` " +
                "ON `body_measurements` (`memberId`, `date`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `checkins` (`id` TEXT NOT NULL, `memberId` TEXT NOT NULL, " +
                "`weekStart` TEXT NOT NULL, `trendStartKg` REAL, `trendEndKg` REAL, `avgIntakeKcal` REAL, " +
                "`countedDays` INTEGER NOT NULL, `estimatedMaintenanceKcal` INTEGER, `oldTargetKcal` INTEGER NOT NULL, " +
                "`proposedTargetKcal` INTEGER NOT NULL, `accepted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checkins_memberId_weekStart` ON `checkins` (`memberId`, `weekStart`)",
        )
        db.execSQL("ALTER TABLE `diary_entries` ADD COLUMN `memberId` TEXT")
    }
}

/**
 * Version 4 (M2.1): basic foods and micronutrients. New `foods` columns for bundled basic foods
 * (key, names per language, shop section, allergens …) and iron, magnesium and vitamin C; diary
 * entries get an entry type (quick add) and the same micronutrients.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "`key` TEXT",
            "`nameCs` TEXT",
            "`nameEn` TEXT",
            "`section` TEXT NOT NULL DEFAULT 'OTHER'",
            "`isGeneric` INTEGER NOT NULL DEFAULT 0",
            "`sourceRef` TEXT",
            "`allergens` TEXT NOT NULL DEFAULT '[]'",
            "`animalFlags` TEXT NOT NULL DEFAULT '[]'",
            "`defaultPackGrams` REAL",
            "`storage` TEXT",
            "`shelfLifeDays` INTEGER",
            "`gramsPerPiece` REAL",
            "`ironMgPer100g` REAL",
            "`magnesiumMgPer100g` REAL",
            "`vitaminCMgPer100g` REAL",
            "`datasetVersion` INTEGER",
        ).forEach { column -> db.execSQL("ALTER TABLE `foods` ADD COLUMN $column") }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_foods_key` ON `foods` (`key`)")

        listOf(
            "`entryType` TEXT NOT NULL DEFAULT 'FOOD'",
            "`ironMg` REAL",
            "`magnesiumMg` REAL",
            "`vitaminCMg` REAL",
        ).forEach { column -> db.execSQL("ALTER TABLE `diary_entries` ADD COLUMN $column") }
    }
}

/**
 * Version 5 (M3.1): recipes. Three new tables (recipes, their ingredients and steps); diary entries
 * get `recipeId` for logged dishes (M3.5).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipes` (`id` TEXT NOT NULL, `key` TEXT, `name` TEXT NOT NULL, " +
                "`nameCs` TEXT, `nameEn` TEXT, `userEdited` INTEGER NOT NULL DEFAULT 0, `shortCode` TEXT NOT NULL, " +
                "`servings` INTEGER NOT NULL, `mealTypes` TEXT NOT NULL, `cuisine` TEXT, `tags` TEXT NOT NULL, " +
                "`equipment` TEXT NOT NULL, `fridgeDays` INTEGER NOT NULL DEFAULT 3, " +
                "`freezable` INTEGER NOT NULL DEFAULT 0, `freezerMonths` INTEGER NOT NULL DEFAULT 3, " +
                "`reheatNote` TEXT, `mealPrepFriendly` INTEGER NOT NULL DEFAULT 0, `learnedYield` REAL, " +
                "`learnedYieldCount` INTEGER NOT NULL DEFAULT 0, `ownerMemberId` TEXT, " +
                "`visibility` TEXT NOT NULL DEFAULT 'SHARED', `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recipes_key` ON `recipes` (`key`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipe_ingredients` (`id` TEXT NOT NULL, `recipeId` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `foodUuid` TEXT NOT NULL, `grams` REAL NOT NULL, `note` TEXT, " +
                "`optional` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_recipeId` ON `recipe_ingredients` (`recipeId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipe_steps` (`id` TEXT NOT NULL, `recipeId` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `text` TEXT NOT NULL, `textCs` TEXT, `textEn` TEXT, `minutes` INTEGER, " +
                "`passive` INTEGER NOT NULL DEFAULT 0, `equipment` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_steps_recipeId` ON `recipe_steps` (`recipeId`)")
        db.execSQL("ALTER TABLE `diary_entries` ADD COLUMN `recipeId` TEXT")
    }
}

/** Every migration, oldest first. Pass these to the database builder and to migration tests. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
