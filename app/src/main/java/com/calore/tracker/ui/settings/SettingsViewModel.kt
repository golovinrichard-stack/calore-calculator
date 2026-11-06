package com.calore.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.repository.ReminderSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hideNumbers: Boolean = false,
    val measureReminderWeeks: Int = 0,
    val checkinReminders: Boolean = false,
)

class SettingsViewModel(
    private val appPrefs: AppPrefs,
    private val reminders: ReminderSettings? = null,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = appPrefs.settings
        .map {
            SettingsUiState(
                hideNumbers = it.hideNumbers,
                measureReminderWeeks = it.measureReminderWeeks,
                checkinReminders = it.checkinReminders,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setHideNumbers(hide: Boolean) {
        viewModelScope.launch { appPrefs.setHideNumbers(hide) }
    }

    fun setMeasureReminderWeeks(weeks: Int) {
        viewModelScope.launch { reminders?.setMeasureReminderWeeks(weeks) }
    }

    fun setCheckinReminders(on: Boolean) {
        viewModelScope.launch { appPrefs.setCheckinReminders(on) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
