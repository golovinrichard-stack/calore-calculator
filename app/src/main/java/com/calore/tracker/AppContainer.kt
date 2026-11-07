package com.calore.tracker

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.calore.tracker.data.backup.BackupRepository
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.foods.GenericFoodImporter
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.recipes.StarterRecipeImporter
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.remote.OpenFoodFactsClient
import com.calore.tracker.data.repository.BodyRepository
import com.calore.tracker.data.repository.CheckinService
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.data.repository.DiaryRepository
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.data.repository.GoalsRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PantryRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.ReminderSettings
import com.calore.tracker.data.repository.ShopRepository
import com.calore.tracker.data.repository.ShoppingRepository
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.notifications.ReminderScheduler
import com.calore.tracker.notifications.WorkManagerReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock

/** Wires the app's dependencies by hand. One instance lives for the whole process. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()

    /** Work that must finish even when the screen that started it closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = CaloreDatabase.build(context)

    /** The 0.x goals and calculator inputs, read once and moved into `members` (M1.1). */
    private val legacySettings = SettingsStore(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
    )

    /** App-wide settings that are not about a person: country, currency, first run (M0.6). */
    val appPrefs = AppPrefs(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(AppPrefs.FILE_NAME) },
    )

    private val openFoodFacts = OpenFoodFactsClient(
        userAgent = "Calore/${BuildConfig.VERSION_NAME} (Android; open-source calorie tracker)",
    )

    val foodRepository = FoodRepository(database.foodDao(), openFoodFacts, clock) { appPrefs.settings.first().country }
    val diaryRepository = DiaryRepository(database.diaryDao(), clock)
    val weightRepository = WeightRepository(database.weightDao())
    val memberRepository = MemberRepository(
        memberDao = database.memberDao(),
        weightDao = database.weightDao(),
        legacySettings = legacySettings,
        appPrefs = appPrefs,
        clock = clock,
    )
    val goalsRepository = GoalsRepository(memberRepository)
    val bodyRepository = BodyRepository(database.bodyDao(), memberRepository, clock)
    val recipeRepository = RecipeRepository(database.recipeDao(), database.foodDao(), clock)
    val cookingRepository = CookingRepository(database.cookingDao(), clock)
    val planRepository = PlanRepository(database.planDao(), clock)
    val pantryRepository = PantryRepository(database.pantryDao(), database.foodDao(), clock)
    val shopRepository = ShopRepository(database.shopDao(), database.foodDao(), appPrefs, clock)
    val shoppingRepository = ShoppingRepository(
        database.shoppingDao(), database.foodDao(), planRepository, recipeRepository, pantryRepository, shopRepository, clock,
    )

    /** Background reminders; WorkManager initialises itself on first use. */
    val reminders: ReminderScheduler = WorkManagerReminders(context, clock)
    val reminderSettings = ReminderSettings(appPrefs, reminders, bodyRepository, clock)
    private val genericFoods = GenericFoodImporter(database.foodDao(), appPrefs, clock) {
        context.assets.open(GenericFoodImporter.ASSET_PATH)
    }
    private val starterRecipes = StarterRecipeImporter(database.recipeDao(), database.foodDao(), appPrefs, clock) {
        context.assets.open(StarterRecipeImporter.ASSET_PATH)
    }
    val checkinService = CheckinService(database.diaryDao(), weightRepository, memberRepository, bodyRepository, clock)
    val backupRepository = BackupRepository(
        backupDao = database.backupDao(),
        contentResolver = context.contentResolver,
        clock = clock,
        appVersion = BuildConfig.VERSION_NAME,
    )

    init {
        // Create the phone's user's profile (and move the 0.x settings into it) before it is needed.
        applicationScope.launch { memberRepository.ensureMe() }
        // Bundled basic foods, then the starter recipes that use them: once per version, in the background.
        applicationScope.launch {
            genericFoods.importIfNewer()
            starterRecipes.importIfNewer()
            // Shops (M5.6), then the owner's products for them, once that file exists (input #1).
            context.assets.open(ShopRepository.STORES_ASSET).use { shopRepository.importStores(it) }
            val products = runCatching { context.assets.open(ShopRepository.PRODUCTS_ASSET) }.getOrNull()
            products?.use { shopRepository.importProducts(it) }
        }
        // The weekly check-in job; KEEP means this costs nothing once it is scheduled.
        reminders.scheduleWeeklyCheckin()
    }
}
