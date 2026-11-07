package com.calore.tracker.ui.settings

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.R
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.repository.DiaryRepository
import com.calore.tracker.data.repository.GoalsRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.FoodSource
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.newId
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.theme.CaloreTheme
import com.calore.tracker.ui.today.TodayScreen
import com.calore.tracker.ui.today.TodayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** M1.8: switching hide-numbers on leaves no calorie numbers on Today. */
@RunWith(AndroidJUnit4::class)
class HideNumbersTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var appPrefs: AppPrefs
    private lateinit var members: MemberRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb")
        }
        appPrefs = AppPrefs(store("a"))
        members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), appPrefs, clock)
        runBlocking {
            members.ensureMe()
            val porridge = Food(0, "Porridge", null, null, Nutrients(160.0, 6.0, 27.0, 3.0), null, FoodSource.CUSTOM, newId())
            DiaryRepository(db.diaryDao(), clock).log(porridge, 250.0, MealType.BREAKFAST, LocalDate.of(2026, 11, 6))
        }
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun noCalorieNumbersOnTodayWhenHidden() {
        val settingsViewModel = SettingsViewModel(appPrefs)
        val todayViewModel = TodayViewModel(
            SavedStateHandle(),
            DiaryRepository(db.diaryDao(), clock),
            GoalsRepository(members),
            clock,
            appPrefs,
            members,
        )
        val hideFlow = appPrefs.settings.map { it.hideNumbers }
        var showToday by mutableStateOf(false)
        compose.setContent {
            val hide by hideFlow.collectAsState(initial = false)
            CaloreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHideNumbers provides hide) {
                    if (showToday) {
                        TodayScreen(onAddFood = { _, _ -> }, viewModel = todayViewModel)
                    } else {
                        SettingsScreen(onBack = {}, onOpenBackup = {}, viewModel = settingsViewModel)
                    }
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_hide_numbers)).performClick()
        compose.waitUntil(5_000) { runBlocking { appPrefs.settings.first().hideNumbers } }
        showToday = true

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Porridge")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Porridge").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.numbers_hidden_note)).assertIsDisplayed()
        compose.onAllNodes(hasText(context.getString(R.string.unit_kcal), substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("400")).assertCountEquals(0)
        assertTrue(todayViewModel.uiState.value.hideNumbers)
    }
}
