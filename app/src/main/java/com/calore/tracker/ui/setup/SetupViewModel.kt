package com.calore.tracker.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.ui.profile.TargetForm
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class SetupStep { BODY, ACTIVITY, GOAL, RESULT }

data class SetupUiState(
    val step: SetupStep = SetupStep.BODY,
    val form: TargetForm = TargetForm(),
)

/**
 * First run on a new install (M1.2): four short steps, each skippable. Finishing saves the
 * profile and its targets; skipping keeps the default goals.
 */
class SetupViewModel(
    private val members: MemberRepository,
    private val weights: WeightRepository,
    private val appPrefs: AppPrefs,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = state.asStateFlow()

    private val doneChannel = Channel<Unit>(Channel.CONFLATED)
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    val today: LocalDate get() = LocalDate.now(clock)

    fun update(transform: (TargetForm) -> TargetForm) {
        state.update { it.copy(form = transform(it.form)) }
    }

    fun next() {
        state.update { current ->
            val steps = SetupStep.entries
            current.copy(step = steps[(current.step.ordinal + 1).coerceAtMost(steps.lastIndex)])
        }
    }

    /** Returns false on the first step, where back leaves nothing to go back to. */
    fun back(): Boolean {
        val current = state.value.step
        if (current == SetupStep.BODY) return false
        state.update { it.copy(step = SetupStep.entries[current.ordinal - 1]) }
        return true
    }

    fun skip() {
        viewModelScope.launch {
            appPrefs.setFirstRunDone(true)
            doneChannel.send(Unit)
        }
    }

    fun finish() {
        val form = state.value.form
        viewModelScope.launch {
            val result = form.result(today)
            members.updateMe { form.applyTo(it, result) }
            val kg = form.weightValue?.takeIf { form.profile(today) != null }
            if (kg != null) weights.save(today, kg)
            appPrefs.setFirstRunDone(true)
            doneChannel.send(Unit)
        }
    }
}
