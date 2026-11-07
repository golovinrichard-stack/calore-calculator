package com.calore.tracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.calore.tracker.domain.cycle.AppLock
import com.calore.tracker.domain.cycle.Contraception
import com.calore.tracker.domain.cycle.CycleSettings
import com.calore.tracker.domain.cycle.Menopause
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Cycle settings (data-model M7, DataStore `cycle_prefs`). Private: on this phone only. */
class CyclePrefs(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<CycleSettings> = dataStore.data.map { prefs ->
        CycleSettings(
            enabled = prefs[ENABLED] ?: false,
            contraception = prefs[CONTRACEPTION]?.let { name -> Contraception.entries.firstOrNull { it.name == name } } ?: Contraception.NONE,
            pregnant = prefs[PREGNANT] ?: false,
            breastfeeding = prefs[BREASTFEEDING] ?: false,
            menopause = prefs[MENOPAUSE]?.let { name -> Menopause.entries.firstOrNull { it.name == name } } ?: Menopause.NO,
            defaultCycleLength = prefs[CYCLE_LENGTH]?.takeIf { it in CycleSettings.CYCLE_LENGTHS } ?: CycleSettings.DEFAULT_CYCLE_LENGTH,
            defaultPeriodLength = prefs[PERIOD_LENGTH]?.takeIf { it in CycleSettings.PERIOD_LENGTHS } ?: CycleSettings.DEFAULT_PERIOD_LENGTH,
            appLock = prefs[APP_LOCK]?.let { name -> AppLock.entries.firstOrNull { it.name == name } } ?: AppLock.OFF,
        )
    }

    /** Whether the setup card (M7.1) was shown. */
    val asked: Flow<Boolean> = dataStore.data.map { it[ASKED] ?: false }

    suspend fun save(settings: CycleSettings) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = settings.enabled
            prefs[CONTRACEPTION] = settings.contraception.name
            prefs[PREGNANT] = settings.pregnant
            prefs[BREASTFEEDING] = settings.breastfeeding
            prefs[MENOPAUSE] = settings.menopause.name
            prefs[CYCLE_LENGTH] = settings.defaultCycleLength.coerceIn(CycleSettings.CYCLE_LENGTHS)
            prefs[PERIOD_LENGTH] = settings.defaultPeriodLength.coerceIn(CycleSettings.PERIOD_LENGTHS)
            prefs[APP_LOCK] = settings.appLock.name
        }
    }

    suspend fun setAsked() {
        dataStore.edit { it[ASKED] = true }
    }

    /** M7.6: everything but whether tracking is on and the app lock goes back to the defaults. */
    suspend fun clearHealthSettings() {
        dataStore.edit { prefs ->
            listOf(CONTRACEPTION, MENOPAUSE).forEach { prefs.remove(it) }
            listOf(PREGNANT, BREASTFEEDING).forEach { prefs.remove(it) }
            listOf(CYCLE_LENGTH, PERIOD_LENGTH).forEach { prefs.remove(it) }
        }
    }

    companion object {
        const val FILE_NAME = "cycle_prefs"
        private val ENABLED = booleanPreferencesKey("enabled")
        private val CONTRACEPTION = stringPreferencesKey("contraception")
        private val PREGNANT = booleanPreferencesKey("pregnant")
        private val BREASTFEEDING = booleanPreferencesKey("breastfeeding")
        private val MENOPAUSE = stringPreferencesKey("menopause")
        private val CYCLE_LENGTH = intPreferencesKey("default_cycle_length")
        private val PERIOD_LENGTH = intPreferencesKey("default_period_length")
        private val APP_LOCK = stringPreferencesKey("app_lock")
        private val ASKED = booleanPreferencesKey("asked")
    }
}
