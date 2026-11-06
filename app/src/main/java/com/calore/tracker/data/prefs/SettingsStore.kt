package com.calore.tracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.MacroSplit
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.WeightGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** What the user last entered in the calculator. Any field can be missing on first use. */
data class CalculatorInputs(
    val sex: Sex? = null,
    val ageYears: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val activity: ActivityLevel? = null,
    val weightGoal: WeightGoal? = null,
    val split: MacroSplit = MacroSplit.DEFAULT,
)

/**
 * What the old settings held, read once when the profile moves to the `members` table (M1.1).
 * [hasAnyValue] is false for a fresh install.
 */
data class LegacyProfile(
    val goals: NutritionGoals?,
    val inputs: CalculatorInputs,
    val hasAnyValue: Boolean,
)

/**
 * The 0.x settings: goals and calculator inputs. Since M1 they are only read once, copied into
 * the ME row of `members` and then cleared.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    /** An unreadable preferences file falls back to defaults instead of crashing the app. */
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val goals: Flow<NutritionGoals> = preferences.map { prefs ->
        val default = NutritionGoals.DEFAULT
        NutritionGoals(
            kcal = prefs[GOAL_KCAL] ?: default.kcal,
            proteinG = prefs[GOAL_PROTEIN] ?: default.proteinG,
            carbsG = prefs[GOAL_CARBS] ?: default.carbsG,
            fatG = prefs[GOAL_FAT] ?: default.fatG,
        )
    }

    suspend fun setGoals(goals: NutritionGoals) {
        dataStore.edit { prefs ->
            prefs[GOAL_KCAL] = goals.kcal
            prefs[GOAL_PROTEIN] = goals.proteinG
            prefs[GOAL_CARBS] = goals.carbsG
            prefs[GOAL_FAT] = goals.fatG
        }
    }

    val calculatorInputs: Flow<CalculatorInputs> = preferences.map { prefs ->
        val split = MacroSplit(
            proteinPercent = prefs[SPLIT_PROTEIN] ?: MacroSplit.DEFAULT.proteinPercent,
            carbsPercent = prefs[SPLIT_CARBS] ?: MacroSplit.DEFAULT.carbsPercent,
            fatPercent = prefs[SPLIT_FAT] ?: MacroSplit.DEFAULT.fatPercent,
        )
        CalculatorInputs(
            sex = prefs[PROFILE_SEX]?.let { name -> Sex.entries.firstOrNull { it.name == name } },
            ageYears = prefs[PROFILE_AGE],
            heightCm = prefs[PROFILE_HEIGHT],
            weightKg = prefs[PROFILE_WEIGHT],
            activity = prefs[PROFILE_ACTIVITY]?.let { name -> ActivityLevel.entries.firstOrNull { it.name == name } },
            weightGoal = prefs[PROFILE_GOAL]?.let { name -> WeightGoal.entries.firstOrNull { it.name == name } },
            split = if (split.isValid) split else MacroSplit.DEFAULT,
        )
    }

    suspend fun setCalculatorInputs(inputs: CalculatorInputs) {
        dataStore.edit { prefs ->
            prefs.setOrRemove(PROFILE_SEX, inputs.sex?.name)
            prefs.setOrRemove(PROFILE_AGE, inputs.ageYears)
            prefs.setOrRemove(PROFILE_HEIGHT, inputs.heightCm)
            prefs.setOrRemove(PROFILE_WEIGHT, inputs.weightKg)
            prefs.setOrRemove(PROFILE_ACTIVITY, inputs.activity?.name)
            prefs.setOrRemove(PROFILE_GOAL, inputs.weightGoal?.name)
            prefs[SPLIT_PROTEIN] = inputs.split.proteinPercent
            prefs[SPLIT_CARBS] = inputs.split.carbsPercent
            prefs[SPLIT_FAT] = inputs.split.fatPercent
        }
    }

    /** Everything stored, for the one-time move to the `members` table. */
    suspend fun legacyProfile(): LegacyProfile {
        val prefs = preferences.first()
        val hasGoals = listOf(GOAL_KCAL, GOAL_PROTEIN, GOAL_CARBS, GOAL_FAT).all { it in prefs }
        return LegacyProfile(
            goals = if (hasGoals) goals.first() else null,
            inputs = calculatorInputs.first(),
            hasAnyValue = prefs.asMap().isNotEmpty(),
        )
    }

    /** Removes every old key; call only after the copy has been saved. */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.setOrRemove(
        key: Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) remove(key) else set(key, value)
    }

    private companion object {
        val GOAL_KCAL = intPreferencesKey("goal_kcal")
        val GOAL_PROTEIN = intPreferencesKey("goal_protein_g")
        val GOAL_CARBS = intPreferencesKey("goal_carbs_g")
        val GOAL_FAT = intPreferencesKey("goal_fat_g")

        val PROFILE_SEX = stringPreferencesKey("profile_sex")
        val PROFILE_AGE = intPreferencesKey("profile_age")
        val PROFILE_HEIGHT = doublePreferencesKey("profile_height_cm")
        val PROFILE_WEIGHT = doublePreferencesKey("profile_weight_kg")
        val PROFILE_ACTIVITY = stringPreferencesKey("profile_activity")
        val PROFILE_GOAL = stringPreferencesKey("profile_weight_goal")

        val SPLIT_PROTEIN = intPreferencesKey("split_protein_pct")
        val SPLIT_CARBS = intPreferencesKey("split_carbs_pct")
        val SPLIT_FAT = intPreferencesKey("split_fat_pct")
    }
}
