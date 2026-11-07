package com.calore.tracker.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.AppSettings
import com.calore.tracker.data.repository.DiaryRepository
import com.calore.tracker.data.repository.GoalsRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.domain.DaySummary
import com.calore.tracker.domain.DiaryEntry
import com.calore.tracker.domain.DiaryTotals
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.parseIsoDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class TodayUiState(
    val date: LocalDate,
    val today: LocalDate,
    val summary: DaySummary,
    val goals: NutritionGoals,
    val isLoading: Boolean,
    /** The one-time "What's new" card for people who updated from 0.x (M1.2). */
    val whatsNew: Boolean = false,
    /** A birth date guessed from the old age setting, waiting for the user to confirm it. */
    val birthDateToConfirm: LocalDate? = null,
    /** Hide-numbers mode (M1.8). */
    val hideNumbers: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val diary: DiaryRepository,
    goals: GoalsRepository,
    private val clock: Clock,
    private val appPrefs: AppPrefs,
    private val members: MemberRepository,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(
        parseIsoDate(savedStateHandle.get<String>(KEY_DATE)) ?: LocalDate.now(clock),
    )

    val uiState: StateFlow<TodayUiState> = combine(
        selectedDate.flatMapLatest { date -> diary.entriesFor(date).map { entries -> date to entries } },
        goals.goals,
        appPrefs.settings,
        members.me(),
    ) { (date, entries), currentGoals, settings: AppSettings, me ->
        TodayUiState(
            date = date,
            today = LocalDate.now(clock),
            summary = DiaryTotals.summarize(entries),
            goals = currentGoals,
            isLoading = false,
            whatsNew = settings.whatsNewPending,
            birthDateToConfirm = me.birthDate.takeIf { settings.birthDateToConfirm },
            hideNumbers = settings.hideNumbers,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TodayUiState(
            date = selectedDate.value,
            today = LocalDate.now(clock),
            summary = DiaryTotals.summarize(emptyList()),
            goals = NutritionGoals.DEFAULT,
            isLoading = true,
        ),
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        savedStateHandle[KEY_DATE] = date.toString()
    }

    fun previousDay() = selectDate(selectedDate.value.minusDays(1))

    fun nextDay() = selectDate(selectedDate.value.plusDays(1))

    fun goToToday() = selectDate(LocalDate.now(clock))

    fun changeServings(entry: DiaryEntry, servings: Double) {
        viewModelScope.launch { diary.changeServings(entry, servings) }
    }

    fun changeGrams(entry: DiaryEntry, grams: Double) {
        viewModelScope.launch { diary.changeGrams(entry, grams) }
    }

    fun updateQuickAdd(entry: DiaryEntry, name: String, nutrients: com.calore.tracker.domain.Nutrients) {
        viewModelScope.launch { diary.updateQuickAdd(entry, name, nutrients) }
    }

    fun delete(entry: DiaryEntry) {
        viewModelScope.launch { diary.delete(entry) }
    }

    fun restore(entry: DiaryEntry) {
        viewModelScope.launch { diary.restore(entry) }
    }

    fun dismissWhatsNew() {
        viewModelScope.launch { appPrefs.setWhatsNewPending(false) }
    }

    fun confirmBirthDate(date: LocalDate) {
        viewModelScope.launch {
            members.updateMe { it.copy(birthDate = date) }
            appPrefs.setBirthDateToConfirm(false)
        }
    }

    private companion object {
        const val KEY_DATE = "selected_date"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
