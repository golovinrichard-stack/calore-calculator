package com.calore.tracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
    /** Set once the first-run setup has been completed or skipped (M1.2). */
    val firstRunDone: Boolean = false,
    /** Show the one-time "What's new" card to people who updated from 0.x (M1.2). */
    val whatsNewPending: Boolean = false,
    /** The birth date was guessed from the old age field and should be confirmed (M1.1). */
    val birthDateToConfirm: Boolean = false,
    /** Log food without seeing calories and macros (M1.8). */
    val hideNumbers: Boolean = false,
    /** Body-fat measuring reminder every this many weeks; 0 is off (M1.4). */
    val measureReminderWeeks: Int = 0,
    /** Notify when the weekly check-in is ready (M1.7). */
    val checkinReminders: Boolean = false,
    /** The bundled basic-foods dataset already imported (M2.2); 0 before the first import. */
    val genericFoodsVersion: Int = 0,
    /** The bundled starter recipes version last imported (M3.4); 0 = never. */
    val starterRecipesVersion: Int = 0,
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
                whatsNewPending = prefs[WHATS_NEW_PENDING] ?: false,
                birthDateToConfirm = prefs[BIRTH_DATE_TO_CONFIRM] ?: false,
                hideNumbers = prefs[HIDE_NUMBERS] ?: false,
                measureReminderWeeks = prefs[MEASURE_REMINDER_WEEKS] ?: 0,
                checkinReminders = prefs[CHECKIN_REMINDERS] ?: false,
                genericFoodsVersion = prefs[GENERIC_FOODS_VERSION] ?: 0,
                starterRecipesVersion = prefs[STARTER_RECIPES_VERSION] ?: 0,
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

    suspend fun setWhatsNewPending(pending: Boolean) {
        dataStore.edit { it[WHATS_NEW_PENDING] = pending }
    }

    suspend fun setBirthDateToConfirm(toConfirm: Boolean) {
        dataStore.edit { it[BIRTH_DATE_TO_CONFIRM] = toConfirm }
    }

    suspend fun setHideNumbers(hide: Boolean) {
        dataStore.edit { it[HIDE_NUMBERS] = hide }
    }

    suspend fun setMeasureReminderWeeks(weeks: Int) {
        dataStore.edit { it[MEASURE_REMINDER_WEEKS] = weeks }
    }

    suspend fun setCheckinReminders(on: Boolean) {
        dataStore.edit { it[CHECKIN_REMINDERS] = on }
    }

    suspend fun setGenericFoodsVersion(version: Int) {
        dataStore.edit { it[GENERIC_FOODS_VERSION] = version }
    }

    suspend fun setStarterRecipesVersion(version: Int) {
        dataStore.edit { it[STARTER_RECIPES_VERSION] = version }
    }

    /** The flags set once when the profile moves out of the old settings (M1.1), in one write. */
    suspend fun markProfileMoved(existingUser: Boolean, birthDateToConfirm: Boolean) {
        dataStore.edit {
            it[FIRST_RUN_DONE] = existingUser
            it[WHATS_NEW_PENDING] = existingUser
            it[BIRTH_DATE_TO_CONFIRM] = birthDateToConfirm
        }
    }

    companion object {
        const val FILE_NAME = "app_prefs"

        private val COUNTRY = stringPreferencesKey("country")
        private val CURRENCY = stringPreferencesKey("currency")
        private val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
        private val WHATS_NEW_PENDING = booleanPreferencesKey("whats_new_pending")
        private val BIRTH_DATE_TO_CONFIRM = booleanPreferencesKey("birth_date_to_confirm")
        private val HIDE_NUMBERS = booleanPreferencesKey("hide_numbers")
        private val MEASURE_REMINDER_WEEKS = intPreferencesKey("measure_reminder_weeks")
        private val CHECKIN_REMINDERS = booleanPreferencesKey("checkin_reminders")
        private val GENERIC_FOODS_VERSION = intPreferencesKey("generic_foods_version")
        private val STARTER_RECIPES_VERSION = intPreferencesKey("starter_recipes_version")
    }
}
