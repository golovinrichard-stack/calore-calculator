package com.calore.tracker.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.GoalsRepository
import com.calore.tracker.domain.Limits
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.parseWholeIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class GoalField { KCAL, PROTEIN, CARBS, FAT }

data class GoalsForm(
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
) {
    private val kcalValue get() = parseWholeIn(kcal, Limits.MIN_GOAL_KCAL..Limits.MAX_GOAL_KCAL)
    private val proteinValue get() = parseWholeIn(protein, 0..Limits.MAX_GOAL_MACRO_G)
    private val carbsValue get() = parseWholeIn(carbs, 0..Limits.MAX_GOAL_MACRO_G)
    private val fatValue get() = parseWholeIn(fat, 0..Limits.MAX_GOAL_MACRO_G)

    val invalidFields: Set<GoalField>
        get() = buildSet {
            if (kcalValue == null) add(GoalField.KCAL)
            if (proteinValue == null) add(GoalField.PROTEIN)
            if (carbsValue == null) add(GoalField.CARBS)
            if (fatValue == null) add(GoalField.FAT)
        }

    fun toGoals(): NutritionGoals? {
        return NutritionGoals(
            kcal = kcalValue ?: return null,
            proteinG = proteinValue ?: return null,
            carbsG = carbsValue ?: return null,
            fatG = fatValue ?: return null,
        )
    }

    companion object {
        fun from(goals: NutritionGoals) = GoalsForm(
            kcal = goals.kcal.toString(),
            protein = goals.proteinG.toString(),
            carbs = goals.carbsG.toString(),
            fat = goals.fatG.toString(),
        )
    }
}

data class GoalsUiState(
    val form: GoalsForm = GoalsForm(),
    val attemptedSave: Boolean = false,
) {
    val errors: Set<GoalField> get() = if (attemptedSave) form.invalidFields else emptySet()

    /** Calories implied by the macro fields, when all of them are valid. */
    val macroKcal: Int? get() = form.toGoals()?.kcalFromMacros

    /** True when the macros and the calorie goal disagree by more than 10%. */
    val macrosMismatch: Boolean
        get() {
            val goals = form.toGoals() ?: return false
            return abs(goals.kcalFromMacros - goals.kcal) > goals.kcal * MISMATCH_TOLERANCE
        }

    private companion object {
        const val MISMATCH_TOLERANCE = 0.10
    }
}

class GoalsViewModel(private val repository: GoalsRepository) : ViewModel() {

    private val state = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = state.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    init {
        // Refill the form whenever stored goals change, e.g. after the calculator applies new ones.
        viewModelScope.launch {
            repository.goals.collect { goals ->
                state.update { GoalsUiState(form = GoalsForm.from(goals)) }
            }
        }
    }

    fun updateForm(transform: (GoalsForm) -> GoalsForm) {
        state.update { it.copy(form = transform(it.form)) }
    }

    fun save() {
        val goals = state.value.form.toGoals()
        if (goals == null) {
            state.update { it.copy(attemptedSave = true) }
            return
        }
        viewModelScope.launch {
            repository.setGoals(goals)
            savedChannel.send(Unit)
        }
    }
}
