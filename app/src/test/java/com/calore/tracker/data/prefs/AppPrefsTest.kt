package com.calore.tracker.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppPrefsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun defaultsAreCzechAndFirstRunIsPending() = runTest {
        val prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { file() })
        assertEquals(AppSettings(country = "CZ", currency = "CZK", firstRunDone = false), prefs.settings.first())
    }

    @Test
    fun changesAreStored() = runTest {
        val prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { file() })
        prefs.setFirstRunDone(true)
        prefs.setCountry("SK")
        prefs.setCurrency("EUR")
        assertEquals(AppSettings(country = "SK", currency = "EUR", firstRunDone = true), prefs.settings.first())
    }

    private fun file() = File(folder.root, "app_prefs.preferences_pb")
}
