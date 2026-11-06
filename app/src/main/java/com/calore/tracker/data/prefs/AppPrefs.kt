package com.calore.tracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Settings that belong to the app on this phone, not to a person (M0.6). Personal values
 * (goals, body, preferences) live in [SettingsStore] until M1 moves them to the `members` table.
 */
data class AppSettings(
    /** ISO 3166 country code; picks country data such as `assets/seed/cz/`. */
    val country: String = DEFAULT_COUNTRY,
    /** ISO 4217 currency code for prices. */
    val currency: String = DEFAULT_CURRENCY,
    /** Set once the first-run setup has been completed (M1). */
    val firstRunDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_COUNTRY = "CZ"
        const val DEFAULT_CURRENCY = "CZK"
    }
}

/** DataStore file `app_prefs`. */
class AppPrefs(private val dataStore: DataStore<Preferences>) {

    /** An unreadable file falls back to defaults instead of crashing the app. */
    val settings: Flow<AppSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            AppSettings(
                country = prefs[COUNTRY] ?: AppSettings.DEFAULT_COUNTRY,
                currency = prefs[CURRENCY] ?: AppSettings.DEFAULT_CURRENCY,
                firstRunDone = prefs[FIRST_RUN_DONE] ?: false,
            )
        }

    suspend fun setCountry(code: String) {
        dataStore.edit { it[COUNTRY] = code }
    }

    suspend fun setCurrency(code: String) {
        dataStore.edit { it[CURRENCY] = code }
    }

    suspend fun setFirstRunDone(done: Boolean) {
        dataStore.edit { it[FIRST_RUN_DONE] = done }
    }

    companion object {
        const val FILE_NAME = "app_prefs"

        private val COUNTRY = stringPreferencesKey("country")
        private val CURRENCY = stringPreferencesKey("currency")
        private val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    }
}
