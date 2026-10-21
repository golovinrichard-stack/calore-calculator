package com.calore.tracker.ui.addfood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.remote.RemoteFood
import com.calore.tracker.data.remote.RemoteResult
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.MealType
import com.calore.tracker.ui.navigation.dateArg
import com.calore.tracker.ui.navigation.mealArg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class OnlineFailure { NETWORK, SERVICE }

sealed interface OnlineSearchState {
    /** Query too short to send. */
    data object Idle : OnlineSearchState
    data object Loading : OnlineSearchState
    data class Results(val foods: List<RemoteFood>) : OnlineSearchState
    data class Failed(val reason: OnlineFailure) : OnlineSearchState
}

data class AddFoodUiState(
    val query: String = "",
    val localFoods: List<Food> = emptyList(),
    val online: OnlineSearchState = OnlineSearchState.Idle,
    val isSaving: Boolean = false,
) {
    /** Online results that aren't already listed among the user's own foods. */
    val newOnlineFoods: List<RemoteFood>
        get() {
            val results = (online as? OnlineSearchState.Results)?.foods ?: return emptyList()
            val known = localFoods.mapNotNullTo(HashSet()) { it.barcode }
            return results.filterNot { it.barcode in known }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddFoodViewModel(
    savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
    clock: Clock,
) : ViewModel() {

    val date: LocalDate = savedStateHandle.dateArg(clock)
    val meal: MealType = savedStateHandle.mealArg()

    private val query = MutableStateFlow("")
    private val online = MutableStateFlow<OnlineSearchState>(OnlineSearchState.Idle)
    private val isSaving = MutableStateFlow(false)
    private var onlineJob: Job? = null

    private val chosen = Channel<Long>(Channel.BUFFERED)

    /** Ids of foods picked from online results, once they are saved locally. */
    val chosenFoodIds: Flow<Long> = chosen.receiveAsFlow()

    private val localFoods: Flow<List<Food>> = query
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isEmpty()) {
                foods.recentFoods()
            } else {
                flow {
                    delay(LOCAL_DEBOUNCE_MS)
                    emit(foods.searchLocal(q))
                }
            }
        }

    val uiState: StateFlow<AddFoodUiState> = combine(query, localFoods, online, isSaving) { q, local, remote, saving ->
        AddFoodUiState(query = q, localFoods = local, online = remote, isSaving = saving)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddFoodUiState())

    fun onQueryChange(text: String) {
        query.value = text.take(MAX_QUERY_LENGTH)
        scheduleOnlineSearch(ONLINE_DEBOUNCE_MS)
    }

    /** Skips the typing delay, e.g. when the keyboard's search key is pressed or on retry. */
    fun searchNow() = scheduleOnlineSearch(delayMs = 0)

    private fun scheduleOnlineSearch(delayMs: Long) {
        onlineJob?.cancel()
        val q = query.value.trim()
        if (q.length < MIN_ONLINE_QUERY_LENGTH) {
            online.value = OnlineSearchState.Idle
            return
        }
        online.value = OnlineSearchState.Loading
        onlineJob = viewModelScope.launch {
            delay(delayMs)
            online.value = when (val result = foods.searchOnline(q)) {
                is RemoteResult.Success -> OnlineSearchState.Results(result.value)
                RemoteResult.NetworkError -> OnlineSearchState.Failed(OnlineFailure.NETWORK)
                RemoteResult.BadResponse -> OnlineSearchState.Failed(OnlineFailure.SERVICE)
            }
        }
    }

    /** Saves the picked product locally, then reports its id through [chosenFoodIds]. */
    fun chooseRemote(remote: RemoteFood) {
        if (isSaving.value) return
        isSaving.value = true
        viewModelScope.launch {
            try {
                chosen.send(foods.saveRemote(remote))
            } finally {
                isSaving.value = false
            }
        }
    }

    private companion object {
        const val LOCAL_DEBOUNCE_MS = 150L
        const val ONLINE_DEBOUNCE_MS = 600L
        const val MIN_ONLINE_QUERY_LENGTH = 2
        const val MAX_QUERY_LENGTH = 100
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
