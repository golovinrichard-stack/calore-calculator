package com.calore.tracker.ui.setup

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.theme.CaloreTheme
import com.calore.tracker.ui.today.TodayScreen
import com.calore.tracker.ui.today.TodayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

/** M1.2: completing the setup gives new targets on Today; skipping keeps the defaults. */
@RunWith(AndroidJUnit4::class)
class SetupFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var appPrefs: AppPrefs
    private lateinit var members: MemberRepository
    private lateinit var weights: WeightRepository

    private fun text(id: Int) = context.getString(id)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb")
        }
        appPrefs = AppPrefs(store("app_prefs"))
        weights = WeightRepository(db.weightDao())
        members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("settings")), appPrefs, clock)
        runBlocking { members.ensureMe() }
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun completingTheSetupShowsTheNewTargetsOnToday() {
        val viewModel = SetupViewModel(members, weights, appPrefs, clock)
        val todayViewModel = TodayViewModel(
            SavedStateHandle(),
            DiaryRepository(db.diaryDao(), clock),
            GoalsRepository(members),
            clock,
            appPrefs,
            members,
        )
        var done = false
        var showToday = false
        compose.setContent {
            CaloreTheme(dynamicColor = false) {
                if (!showToday) {
                    SetupScreen(onDone = { done = true; showToday = true }, viewModel = viewModel)
                } else {
                    TodayScreen(onAddFood = { _, _ -> }, viewModel = todayViewModel)
                }
            }
        }

        // Step 1: body. The birth date comes from a date picker, so it is set directly.
        compose.onNodeWithText(text(R.string.sex_male)).performClick()
        viewModel.update { it.copy(birthDate = LocalDate.of(1996, 1, 15)) }
        compose.onNodeWithText(text(R.string.height_label)).performTextInput("180")
        compose.onNodeWithText(text(R.string.weight_label)).performTextInput("80")
        compose.onNodeWithText(text(R.string.setup_next)).performClick()
        // Step 2: activity (light is preselected).
        compose.onNodeWithText(text(R.string.activity_light)).performClick()
        compose.onNodeWithText(text(R.string.setup_next)).performClick()
        // Step 3: goal.
        compose.onNodeWithText(text(R.string.goal_type_cut)).performClick()
        compose.onNodeWithText(text(R.string.setup_next)).performClick()
        // Step 4: result.
        compose.onNodeWithText(text(R.string.setup_change_later)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_finish)).performClick()
        compose.waitUntil(5_000) { done }

        // Mifflin-St Jeor 1,780 × 1.375 − 440 = 2,007.5 → 2,010 kcal; protein 2.0 g/kg × 80 kg.
        val expected = NutritionGoals(kcal = 2010, proteinG = 160, carbsG = 217, fatG = 56)
        runBlocking {
            compose.waitUntil(5_000) { runBlocking { members.me().first().targets } == expected }
            assertTrue(appPrefs.settings.first().firstRunDone)
            assertEquals(80.0, weights.latest()!!.kg, 0.0)
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextExists(context.getString(R.string.summary_goal, formatKcal(2010.0)))
        }
    }

    @Test
    fun skippingKeepsTheDefaultGoals() {
        val viewModel = SetupViewModel(members, weights, appPrefs, clock)
        var done = false
        compose.setContent {
            CaloreTheme(dynamicColor = false) { SetupScreen(onDone = { done = true }, viewModel = viewModel) }
        }

        compose.onNodeWithText(text(R.string.setup_skip)).performClick()
        compose.waitUntil(5_000) { done }

        runBlocking {
            val me = members.me().first()
            assertNull(me.targets)
            assertEquals(NutritionGoals.DEFAULT, me.effectiveTargets)
            assertTrue(appPrefs.settings.first().firstRunDone)
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextExists(value: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(value)).fetchSemanticsNodes().isNotEmpty()
}
