package com.calore.tracker.ui.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.prefs.ResearchConsent
import com.calore.tracker.data.repository.ResearchReportView
import com.calore.tracker.data.repository.ResearchRepository
import com.calore.tracker.domain.research.ResearchCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The steps of "Help research" (M10.2). */
enum class ResearchStep { INTRO, AGE, CHOOSE, UNDER_18 }

data class ResearchUiState(
    val loaded: Boolean = false,
    val consent: ResearchConsent = ResearchConsent(),
    val step: ResearchStep = ResearchStep.INTRO,
    /** Nothing is chosen for the user (M10.2 step 3). */
    val chosen: Set<ResearchCategory> = emptySet(),
    val region: String? = null,
    val reports: List<ResearchReportView> = emptyList(),
    val busy: Boolean = false,
) {
    /** Taking part, stopped, or waiting to delete: the status screen. */
    val showStatus: Boolean get() = consent.participantId != null || consent.pendingDeletion
    val canAgree: Boolean get() = chosen.isNotEmpty() && !busy
    val lastSentWeek: String? get() = reports.firstOrNull { it.status == ResearchRepository.STATUS_SENT }?.isoWeek
}

/**
 * "Help research" (M10.2, M10.4, M10.5). [onAgreed] and [onStopped] start and stop the background
 * jobs; [onSend] asks for sending (or deleting) as soon as there is a network.
 */
class ResearchViewModel(
    private val research: ResearchRepository,
    private val onAgreed: () -> Unit = {},
    private val onStopped: () -> Unit = {},
    private val onSend: () -> Unit = {},
) : ViewModel() {

    private val local = MutableStateFlow(ResearchUiState())

    val uiState: StateFlow<ResearchUiState> = combine(research.consent, research.reports(), local) { consent, reports, state ->
        state.copy(loaded = true, consent = consent, reports = reports)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResearchUiState())

    fun next() = local.update { s ->
        s.copy(step = if (s.step == ResearchStep.INTRO) ResearchStep.AGE else s.step)
    }

    /** Under 18, or no answer: the feature is not available (M10.2 step 2). */
    fun answerAge(adult: Boolean) = local.update { it.copy(step = if (adult) ResearchStep.CHOOSE else ResearchStep.UNDER_18) }

    fun back() = local.update { s ->
        s.copy(
            step = when (s.step) {
                ResearchStep.AGE, ResearchStep.UNDER_18 -> ResearchStep.INTRO
                ResearchStep.CHOOSE -> ResearchStep.AGE
                ResearchStep.INTRO -> ResearchStep.INTRO
            },
        )
    }

    fun toggle(category: ResearchCategory) = local.update { s -> s.copy(chosen = if (category in s.chosen) s.chosen - category else s.chosen + category) }

    fun setRegion(region: String?) = local.update { it.copy(region = region) }

    /** "I agree to send the selected data": only with at least one category chosen. */
    fun agree() {
        val s = local.value
        if (s.chosen.isEmpty() || s.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            research.agree(s.chosen, s.region)
            onAgreed()
            onSend()
            local.update { it.copy(busy = false, step = ResearchStep.INTRO, chosen = emptySet()) }
        }
    }

    fun stop() {
        viewModelScope.launch {
            research.stop()
            onStopped()
        }
    }

    fun deleteFromServer() {
        viewModelScope.launch {
            onStopped()
            research.deleteFromServer()
            onSend()
        }
    }

    /** After stopping (and deleting), taking part again starts from the intro with a new participant. */
    fun startAgain() = local.update { ResearchUiState(step = ResearchStep.INTRO) }
}
