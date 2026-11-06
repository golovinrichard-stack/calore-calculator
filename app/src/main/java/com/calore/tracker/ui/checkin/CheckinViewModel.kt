package com.calore.tracker.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.CheckinService
import com.calore.tracker.domain.Checkin
import com.calore.tracker.domain.CheckinOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckinUiState(
    val loading: Boolean = true,
    /** This week's saved check-in, once there is enough data. */
    val checkin: Checkin? = null,
    /** Why there is no check-in yet. */
    val notEnough: CheckinOutcome.NotEnoughData? = null,
    /** Set after the user chose: true for "Use it", false for "Keep my target". */
    val decided: Boolean? = null,
    val hideNumbers: Boolean = false,
    val showNumbersAnyway: Boolean = false,
) {
    val numbersVisible: Boolean get() = !hideNumbers || showNumbersAnyway
}

/** The weekly check-in (M1.7). Opening it runs this week's check-in if Monday's job hasn't yet. */
class CheckinViewModel(
    private val service: CheckinService,
    hideNumbers: Flow<Boolean> = flowOf(false),
) : ViewModel() {

    private val state = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = combine(state, hideNumbers) { current, hide -> current.copy(hideNumbers = hide) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CheckinUiState())

    init {
        viewModelScope.launch {
            val checkin = service.checkInThisWeek()
            val notEnough = if (checkin == null) service.evaluate() as? CheckinOutcome.NotEnoughData else null
            state.update { it.copy(loading = false, checkin = checkin, notEnough = notEnough) }
        }
    }

    fun useIt() {
        val checkin = state.value.checkin ?: return
        viewModelScope.launch {
            service.accept(checkin)
            state.update { it.copy(checkin = checkin.copy(accepted = true), decided = true) }
        }
    }

    fun keepTarget() {
        val checkin = state.value.checkin ?: return
        viewModelScope.launch {
            service.keep(checkin)
            state.update { it.copy(decided = false) }
        }
    }

    fun showNumbers() {
        state.update { it.copy(showNumbersAnyway = true) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
