package com.calore.tracker.ui.research

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.R
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.CyclePrefs
import com.calore.tracker.data.prefs.ResearchPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.remote.ResearchClient
import com.calore.tracker.data.repository.BodyRepository
import com.calore.tracker.data.repository.CycleRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.ResearchRepository
import com.calore.tracker.data.repository.SupplementRepository
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.domain.research.ResearchCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.util.UUID

/** M10.2 "done when": "agree" can't be reached without a category, and the stored consent matches the choice. */
@RunWith(AndroidJUnit4::class)
class ResearchConsentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = Clock.systemDefaultZone()
    private lateinit var db: CaloreDatabase
    private lateinit var research: ResearchRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb") }
        val members = MemberRepository(db.memberDao(), db.weightDao(), SettingsStore(store("s")), AppPrefs(store("a")), clock)
        members.ensureMe()
        research = ResearchRepository(
            ResearchPrefs(store("r")), db.researchDao(), ResearchClient("", "test"), db.diaryDao(), db.foodDao(), db.cookingDao(), members,
            WeightRepository(db.weightDao()), BodyRepository(db.bodyDao(), members, clock), PlanRepository(db.planDao(), clock),
            RecipeRepository(db.recipeDao(), db.foodDao(), clock), CycleRepository(db.cycleDao(), CyclePrefs(store("c")), clock),
            SupplementRepository(db.supplementDao(), db.diaryDao(), clock), "test", clock,
        )
        Unit
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun text(id: Int) = context.getString(id)

    @Test
    fun agreeNeedsACategoryAndStoresExactlyTheChoice() {
        val viewModel = ResearchViewModel(research)
        compose.setContent { ResearchScreen(onBack = {}, onOpenPolicy = {}, onOpenReports = {}, viewModel = viewModel) }
        compose.onNodeWithText(text(R.string.research_continue)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.research_yes)).performClick()

        // Nothing is chosen for the user: agreeing is not possible yet.
        compose.onNodeWithTag("research_agree").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("research_NUTRITION").performScrollTo().performClick()
        compose.onNodeWithTag("research_CYCLE").performScrollTo().performClick()
        compose.onNodeWithTag("research_agree").performScrollTo().assertIsEnabled().performClick()

        compose.waitUntil(5_000) { runBlocking { research.consent.first().participantId != null } }
        val consent = runBlocking { research.consent.first() }
        assertEquals(setOf(ResearchCategory.NUTRITION, ResearchCategory.CYCLE), consent.categories)
        assertEquals(1, consent.consentVersion)
    }

    @Test
    fun under18CantTakePart() {
        val viewModel = ResearchViewModel(research)
        compose.setContent { ResearchScreen(onBack = {}, onOpenPolicy = {}, onOpenReports = {}, viewModel = viewModel) }
        compose.onNodeWithText(text(R.string.research_continue)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.research_no)).performClick()
        compose.onNodeWithText(text(R.string.research_under_18)).assertExists()
    }
}
