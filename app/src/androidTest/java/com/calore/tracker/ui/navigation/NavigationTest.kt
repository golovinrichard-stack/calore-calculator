package com.calore.tracker.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.calore.tracker.CaloreApp
import com.calore.tracker.MainActivity
import com.calore.tracker.R
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** M1.9: every existing feature can still be reached through Today and Me. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Before
    fun skipFirstRunSetup() = runBlocking {
        (context.applicationContext as CaloreApp).container.appPrefs.setFirstRunDone(true)
    }

    private fun openMe() {
        compose.onNodeWithText(text(R.string.nav_me)).performClick()
        compose.onNodeWithText(text(R.string.me_goals)).assertIsDisplayed()
    }

    private fun back() {
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
    }

    @Test
    fun todayIsTheStartScreen() {
        compose.onNodeWithText(text(R.string.nav_today)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.meal_breakfast)).assertIsDisplayed()
    }

    @Test
    fun goalsAndCalculatorAreInMe() {
        openMe()
        compose.onNodeWithText(text(R.string.me_goals)).performClick()
        compose.onNodeWithText(text(R.string.goals_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.goals_open_calculator)).performClick()
        compose.onNodeWithText(text(R.string.calculator_title)).assertIsDisplayed()
        back()
        back()
        compose.onNodeWithText(text(R.string.me_body)).assertIsDisplayed()
    }

    @Test
    fun bodySettingsBackupAndAboutAreInMe() {
        openMe()
        compose.onNodeWithText(text(R.string.me_body)).performClick()
        compose.onNodeWithText(text(R.string.weight_save)).assertIsDisplayed()
        back()

        compose.onNodeWithText(text(R.string.me_settings)).performClick()
        compose.onNodeWithText(text(R.string.settings_hide_numbers)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.backup_title)).performClick()
        compose.onNodeWithText(text(R.string.backup_export_button)).assertIsDisplayed()
        back()
        back()

        compose.onNodeWithText(text(R.string.me_about)).performClick()
        compose.onNodeWithText(text(R.string.about_license_title)).assertIsDisplayed()
    }
}
