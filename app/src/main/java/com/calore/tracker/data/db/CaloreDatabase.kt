package com.calore.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FoodEntity::class, DiaryEntryEntity::class, WeightEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CaloreDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun weightDao(): WeightDao

    companion object {
        private const val FILE_NAME = "calore.db"

        fun build(context: Context): CaloreDatabase =
            Room.databaseBuilder(context, CaloreDatabase::class.java, FILE_NAME).build()
    }
}
