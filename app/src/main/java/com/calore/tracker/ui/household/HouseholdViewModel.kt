package com.calore.tracker.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.household.HouseholdRepository
import com.calore.tracker.data.household.HouseholdState
import com.calore.tracker.data.household.HouseholdSync
import com.calore.tracker.data.household.SyncStatus
import com.calore.tracker.domain.household.HouseholdInvite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HouseholdUiState(
    val loaded: Boolean = false,
    val household: HouseholdState = HouseholdState.None,
    val status: SyncStatus = SyncStatus(),
    val waiting: Int = 0,
    val hubMode: Boolean = false,
    val sharesTargets: Boolean = false,
)

/** The household screen (M11.2, M11.5): create, show the code, confirm and remove phones, hub mode, leave. */
class HouseholdViewModel(
    private val household: HouseholdRepository,
    private val sync: HouseholdSync,
    private val setHubService: (Boolean) -> Unit,
) : ViewModel() {

    private val hubMode = MutableStateFlow(household.hubMode)

    /** The code to show (the host's), or null when hidden. */
    private val shownInvite = MutableStateFlow<HouseholdInvite?>(null)
    val invite: StateFlow<HouseholdInvite?> = shownInvite.asStateFlow()

    val uiState: StateFlow<HouseholdUiState> = combine(household.state, sync.status, household.waitingChanges, hubMode) { state, status, waiting, hub ->
        HouseholdUiState(
            loaded = true,
            household = state,
            status = status,
            waiting = waiting,
            hubMode = hub && state is HouseholdState.In && state.isHub,
            sharesTargets = (state as? HouseholdState.In)?.me?.sharesTargets == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseholdUiState())

    /** Creates the household and starts hub mode; the explanation was shown first (M11.5). */
    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            shownInvite.value = household.create(name)
            setHubMode(true)
        }
    }

    fun setHubMode(on: Boolean) {
        household.hubMode = on
        hubMode.value = on
        setHubService(on)
    }

    fun showCode() {
        viewModelScope.launch { shownInvite.value = household.invite() }
    }

    fun hideCode() {
        shownInvite.value = null
    }

    fun approve(deviceId: String) {
        viewModelScope.launch { household.approve(deviceId) }
    }

    /** Removes a phone and shows the new code for the others. */
    fun remove(deviceId: String) {
        viewModelScope.launch { shownInvite.value = household.remove(deviceId) }
    }

    fun handOver(deviceId: String) {
        viewModelScope.launch { household.handOver(deviceId) }
    }

    fun setSharesTargets(on: Boolean) {
        viewModelScope.launch { household.setSharesTargets(on) }
    }

    fun syncNow() {
        viewModelScope.launch { sync.syncNow() }
    }

    fun leave() {
        viewModelScope.launch {
            setHubMode(false)
            household.leave()
        }
    }
}
