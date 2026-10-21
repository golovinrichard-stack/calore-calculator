package com.calore.tracker.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.prefs.CalculatorInputs
import com.calore.tracker.data.repository.GoalsRepository
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.BodyProfile
import com.calore.tracker.domain.CalculatorResult
import com.calore.tracker.domain.CalorieCalculator
import com.calore.tracker.domain.MacroSplit
import com.calore.tracker.domain.ProfileField
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.WeightGoal
import com.calore.tracker.domain.parseDecimal
import com.calore.tracker.domain.parseWholeIn
import com.calore.tracker.ui.formatEditable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalculatorForm(
    val sex: Sex? = null,
    val age: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val goal: WeightGoal = WeightGoal.MAINTAIN,
    val proteinPercent: String = MacroSplit.DEFAULT.proteinPercent.toString(),
    val carbsPercent: String = MacroSplit.DEFAULT.carbsPercent.toString(),
    val fatPercent: String = MacroSplit.DEFAULT.fatPercent.toString(),
) {
    private val ageValue get() = age.trim().toIntOrNull()
    private val heightValue get() = parseDecimal(heightCm)
    private val weightValue get() = parseDecimal(weightKg)

    /** The profile, once every field holds a number. Range checks happen separately. */
    val profile: BodyProfile?
        get() {
            return BodyProfile(
                sex = sex ?: return null,
                ageYears = ageValue ?: return null,
                heightCm = heightValue ?: return null,
                weightKg = weightValue ?: return null,
                activity = activity,
            )
        }

    /** Fields the user filled in with an out-of-range value. Blank fields are not errors yet. */
    val invalidFields: Set<ProfileField>
        get() = buildSet {
            if (age.isNotBlank() && ageValue?.let { it in CalorieCalculator.AGE_RANGE } != true) add(ProfileField.AGE)
            if (heightCm.isNotBlank() && heightValue?.let { it in CalorieCalculator.HEIGHT_RANGE_CM } != true) {
                add(ProfileField.HEIGHT)
            }
            if (weightKg.isNotBlank() && weightValue?.let { it in CalorieCalculator.WEIGHT_RANGE_KG } != true) {
                add(ProfileField.WEIGHT)
            }
        }

    val split: MacroSplit?
        get() {
            return MacroSplit(
                proteinPercent = parseWholeIn(proteinPercent, 0..100) ?: return null,
                carbsPercent = parseWholeIn(carbsPercent, 0..100) ?: return null,
                fatPercent = parseWholeIn(fatPercent, 0..100) ?: return null,
            )
        }

    /** Sum of the percentages as typed, for the "must add up to 100" hint. */
    val splitTotal: Int
        get() = listOf(proteinPercent, carbsPercent, fatPercent).sumOf { it.trim().toIntOrNull()?.coerceIn(0, 100) ?: 0 }

    val result: CalculatorResult?
        get() {
            val body = profile ?: return null
            val macroSplit = split ?: return null
            return CalorieCalculator.calculate(body, goal, macroSplit)
        }

    fun toInputs(): CalculatorInputs = CalculatorInputs(
        sex = sex,
        ageYears = ageValue,
        heightCm = heightValue,
        weightKg = weightValue,
        activity = activity,
        weightGoal = goal,
        split = split?.takeIf { it.isValid } ?: MacroSplit.DEFAULT,
    )
}

class CalculatorViewModel(
    private val goals: GoalsRepository,
    private val weights: WeightRepository,
) : ViewModel() {

    private val state = MutableStateFlow(CalculatorForm())
    val form: StateFlow<CalculatorForm> = state.asStateFlow()

    private val appliedChannel = Channel<Unit>(Channel.CONFLATED)
    val applied: Flow<Unit> = appliedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val saved = goals.calculatorInputs.first()
            // Prefer the most recent logged weight over the one typed here last time.
            val latestKg = weights.latest()?.kg ?: saved.weightKg
            state.value = CalculatorForm(
                sex = saved.sex,
                age = saved.ageYears?.toString().orEmpty(),
                heightCm = formatEditable(saved.heightCm),
                weightKg = formatEditable(latestKg),
                activity = saved.activity ?: ActivityLevel.LIGHT,
                goal = saved.weightGoal ?: WeightGoal.MAINTAIN,
                proteinPercent = saved.split.proteinPercent.toString(),
                carbsPercent = saved.split.carbsPercent.toString(),
                fatPercent = saved.split.fatPercent.toString(),
            )
        }
    }

    fun update(transform: (CalculatorForm) -> CalculatorForm) {
        state.update(transform)
    }

    fun apply() {
        val current = state.value
        val result = current.result ?: return
        viewModelScope.launch {
            goals.setCalculatorInputs(current.toInputs())
            goals.setGoals(result.goals)
            appliedChannel.send(Unit)
        }
    }
}
