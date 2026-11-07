package com.calore.tracker.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.domain.BodyFatReading
import com.calore.tracker.ui.profile.TargetForm
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * The calorie calculator (M1.3): body, goal type and speed, macro mode. Applying saves the
 * profile and the new targets; a changed weight becomes today's weigh-in.
 */
class CalculatorViewModel(
    private val members: MemberRepository,
    private val weights: WeightRepository,
    private val clock: Clock,
    private val latestBodyFat: Flow<BodyFatReading?> = flowOf(null),
) : ViewModel() {

    private val state = MutableStateFlow(TargetForm())
    val form: StateFlow<TargetForm> = state.asStateFlow()

    private var loadedWeightKg: Double? = null

    private val appliedChannel = Channel<Unit>(Channel.CONFLATED)
    val applied: Flow<Unit> = appliedChannel.receiveAsFlow()

    val today: LocalDate get() = LocalDate.now(clock)

    init {
        viewModelScope.launch {
            val me = members.me().first()
            loadedWeightKg = weights.latest()?.kg
            state.value = TargetForm.from(me, loadedWeightKg, latestBodyFat.first())
        }
    }

    fun update(transform: (TargetForm) -> TargetForm) {
        state.update(transform)
    }

    fun apply() {
        val current = state.value
        val result = current.result(today) ?: return
        viewModelScope.launch {
            members.updateMe { current.applyTo(it, result) }
            val typedKg = current.weightValue
            if (typedKg != null && typedKg != loadedWeightKg) weights.save(today, typedKg)
            appliedChannel.send(Unit)
        }
    }
}
