package com.calore.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        FoodEntity::class,
        DiaryEntryEntity::class,
        WeightEntryEntity::class,
        MemberEntity::class,
        BodyMeasurementEntity::class,
        CheckinEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeStepEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CaloreDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun weightDao(): WeightDao
    abstract fun backupDao(): BackupDao
    abstract fun memberDao(): MemberDao
    abstract fun bodyDao(): BodyDao
    abstract fun recipeDao(): RecipeDao

    companion object {
        private const val FILE_NAME = "calore.db"

        fun build(context: Context): CaloreDatabase =
            Room.databaseBuilder(context, CaloreDatabase::class.java, FILE_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
