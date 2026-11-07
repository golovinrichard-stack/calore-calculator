package com.calore.tracker.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.CheckinService
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.domain.PlanSlot
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
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
    /** M6.6: this week's planned meals, not cooked yet, that could take the new target. */
    val resizable: Int = 0,
    /** M6.6: how many were resized. */
    val resized: Int? = null,
)

/** The weekly check-in (M1.7). Opening it runs this week's check-in if Monday's job hasn't yet. */
class CheckinViewModel(
    private val service: CheckinService,
    private val plans: PlanRepository? = null,
    private val members: MemberRepository? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

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
            val count = resizable().size
            state.update { it.copy(resizable = count) }
        }
    }

    /** M6.6: this week's planned meals that are not cooked yet get the new target. */
    fun resizeThisWeek() {
        val repository = plans ?: return
        val people = members ?: return
        viewModelScope.launch {
            val me = people.me().first()
            val slots = resizable()
            repository.retarget(me, slots)
            state.update { it.copy(resizable = 0, resized = slots.size) }
        }
    }

    fun dontResize() = state.update { it.copy(resizable = 0) }

    private suspend fun resizable(): List<PlanSlot> {
        val repository = plans ?: return emptyList()
        val me = members?.me()?.first() ?: return emptyList()
        return repository.retargetable(me, LocalDate.now(clock))
    }

    fun keepTarget() {
        val checkin = state.value.checkin ?: return
        viewModelScope.launch {
            service.keep(checkin)
            state.update { it.copy(decided = false) }
        }
    }

}
