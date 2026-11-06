package com.calore.tracker.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.domain.BodyMeasurement
import com.calore.tracker.domain.Checkin
import com.calore.tracker.domain.Limits
import com.calore.tracker.domain.TrendPoint
import com.calore.tracker.domain.TrendWeight
import com.calore.tracker.domain.WeightEntry
import com.calore.tracker.domain.parseDecimalIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class WeightInput(
    val text: String = "",
    val date: LocalDate,
    val attemptedSave: Boolean = false,
) {
    val kg: Double? get() = parseDecimalIn(text, Limits.MIN_WEIGHT_KG..Limits.MAX_WEIGHT_KG)
    val showError: Boolean get() = attemptedSave && kg == null
}

data class WeightUiState(
    /** Oldest first. */
    val entries: List<WeightEntry>,
    val input: WeightInput,
    val today: LocalDate,
    /** Oldest first. */
    val measurements: List<BodyMeasurement> = emptyList(),
    val hideNumbers: Boolean = false,
    /** Newest first. */
    val checkins: List<Checkin> = emptyList(),
) {
    /** Weigh-ins with the trend (A6), oldest first. */
    val trend: List<TrendPoint> = TrendWeight.of(entries)
}

class WeightViewModel(
    private val repository: WeightRepository,
    private val clock: Clock,
    measurements: Flow<List<BodyMeasurement>> = flowOf(emptyList()),
    hideNumbers: Flow<Boolean> = flowOf(false),
    checkins: Flow<List<Checkin>> = flowOf(emptyList()),
) : ViewModel() {

    private val input = MutableStateFlow(WeightInput(date = LocalDate.now(clock)))

    val uiState: StateFlow<WeightUiState> = combine(
        repository.entries(),
        input,
        measurements,
        hideNumbers,
        checkins,
    ) { entries, currentInput, bodyMeasurements, hide, pastCheckins ->
        WeightUiState(
            entries = entries,
            input = currentInput,
            today = LocalDate.now(clock),
            measurements = bodyMeasurements,
            hideNumbers = hide,
            checkins = pastCheckins,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = WeightUiState(emptyList(), input.value, LocalDate.now(clock)),
    )

    fun onTextChange(text: String) {
        input.update { it.copy(text = text) }
    }

    fun onDateChange(date: LocalDate) {
        val today = LocalDate.now(clock)
        input.update { it.copy(date = if (date.isAfter(today)) today else date) }
    }

    fun save() {
        val current = input.value
        val kg = current.kg
        if (kg == null) {
            input.update { it.copy(attemptedSave = true) }
            return
        }
        viewModelScope.launch {
            repository.save(current.date, kg)
            input.value = WeightInput(date = LocalDate.now(clock))
        }
    }

    fun delete(entry: WeightEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    fun restore(entry: WeightEntry) {
        viewModelScope.launch { repository.restore(entry) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
