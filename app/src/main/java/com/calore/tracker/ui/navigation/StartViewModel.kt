package com.calore.tracker.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.repository.MemberRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Decides the first screen: the setup on a new install, Today otherwise. Waits for the profile
 * to exist first, because an update from 0.x marks the setup as done while moving the profile.
 */
class StartViewModel(members: MemberRepository, appPrefs: AppPrefs) : ViewModel() {

    private val route = MutableStateFlow<String?>(null)

    /** Null while loading, which takes a few milliseconds. */
    val startRoute: StateFlow<String?> = route.asStateFlow()

    init {
        viewModelScope.launch {
            members.ensureMe()
            route.value = if (appPrefs.settings.first().firstRunDone) Routes.DIARY else Routes.SETUP
        }
    }
}
