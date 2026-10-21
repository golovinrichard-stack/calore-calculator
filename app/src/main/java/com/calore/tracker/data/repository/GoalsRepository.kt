package com.calore.tracker.data.repository

import com.calore.tracker.data.prefs.CalculatorInputs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.domain.NutritionGoals
import kotlinx.coroutines.flow.Flow

class GoalsRepository(private val settings: SettingsStore) {

    val goals: Flow<NutritionGoals> = settings.goals

    val calculatorInputs: Flow<CalculatorInputs> = settings.calculatorInputs

    suspend fun setGoals(goals: NutritionGoals) = settings.setGoals(goals)

    suspend fun setCalculatorInputs(inputs: CalculatorInputs) = settings.setCalculatorInputs(inputs)
}
