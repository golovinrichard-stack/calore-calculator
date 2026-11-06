package com.calore.tracker

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.calore.tracker.data.backup.BackupRepository
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.data.repository.DiaryRepository
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.data.repository.GoalsRepository
import com.calore.tracker.data.repository.WeightRepository
import java.time.Clock

/** Wires the app's dependencies by hand. One instance lives for the whole process. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()

    private val database = CaloreDatabase.build(context)

    private val settings = SettingsStore(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
    )

    private val openFoodFacts = OpenFoodFactsClient(
        userAgent = "Calore/${BuildConfig.VERSION_NAME} (Android; open-source calorie tracker)",
    )

    val foodRepository = FoodRepository(database.foodDao(), openFoodFacts, clock)
    val diaryRepository = DiaryRepository(database.diaryDao(), clock)
    val weightRepository = WeightRepository(database.weightDao())
    val goalsRepository = GoalsRepository(settings)
    val backupRepository = BackupRepository(
        backupDao = database.backupDao(),
        contentResolver = context.contentResolver,
        clock = clock,
        appVersion = BuildConfig.VERSION_NAME,
    )
}
