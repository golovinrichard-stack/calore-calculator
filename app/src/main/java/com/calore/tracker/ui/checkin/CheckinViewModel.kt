package com.calore.tracker.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.CheckinService
import com.calore.tracker.domain.Checkin
import com.calore.tracker.domain.CheckinOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

/** The weekly check-in (M1.7). Opening it runs this week's check-in if Monday's job hasn't yet. */
class CheckinViewModel(private val service: CheckinService) : ViewModel() {

    private val state = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = state.asStateFlow()

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

}
