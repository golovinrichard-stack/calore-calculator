package com.calore.tracker.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.CycleRepository
import com.calore.tracker.domain.cycle.CycleDay
import com.calore.tracker.domain.cycle.CyclePeriod
import com.calore.tracker.domain.cycle.CyclePhase
import com.calore.tracker.domain.cycle.CycleSettings
import com.calore.tracker.domain.cycle.CycleTip
import com.calore.tracker.domain.cycle.CycleTips
import com.calore.tracker.domain.cycle.CycleToday
import com.calore.tracker.domain.cycle.Cycles
import com.calore.tracker.domain.cycle.Flow
import com.calore.tracker.domain.cycle.Symptom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** How a calendar day looks (M7.3). */
enum class DayMark { LOGGED_PERIOD, PREDICTED_PERIOD, FOLLICULAR, OVULATION, LUTEAL, NONE }

data class CycleUiState(
    val loaded: Boolean = false,
    val settings: CycleSettings = CycleSettings(),
    val periods: List<CyclePeriod> = emptyList(),
    val days: Map<LocalDate, CycleDay> = emptyMap(),
    val today: LocalDate = LocalDate.MIN,
    val month: YearMonth = YearMonth.of(2000, 1),
    val selected: LocalDate = LocalDate.MIN,
) {
    val todayInfo: CycleToday? get() = Cycles.today(today, periods, settings)

    /** The period running today, if it has no end yet. */
    val openPeriod: CyclePeriod? get() = periods.filter { !it.start.isAfter(today) }.maxByOrNull { it.start }?.takeIf { it.end == null }

    private val periodLength: Int get() = Cycles.periodLength(periods, settings.defaultPeriodLength)

    /** Days with logged bleeding: each period's days (until today for a running one) and days with a flow. */
    val bleedingDays: Set<LocalDate>
        get() = buildSet {
            val latest = periods.maxOfOrNull { it.start }
            for (p in periods) {
                val planned = p.start.plusDays(periodLength - 1L)
                val end = p.end ?: if (p.start == latest && planned.isAfter(today)) today else planned
                var d = p.start
                while (!d.isAfter(end)) {
                    add(d)
                    d = d.plusDays(1)
                }
            }
            days.values.filter { it.flow != null && it.flow != Flow.NONE }.forEach { add(it.date) }
        }

    /** Symptoms logged since the current period started (cramps bring the "warm meals" tip). */
    val periodSymptoms: Set<Symptom>
        get() {
            val start = periods.filter { !it.start.isAfter(today) }.maxOfOrNull { it.start } ?: return emptySet()
            return days.values.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }.flatMap { it.symptoms }.toSet()
        }

    val tips: List<CycleTip> get() = CycleTips.tips(todayInfo?.phase, periodSymptoms)

    fun mark(date: LocalDate, bleeding: Set<LocalDate>, predicted: ClosedRange<LocalDate>?): DayMark {
        if (date in bleeding) return DayMark.LOGGED_PERIOD
        if (settings.paused) return DayMark.NONE
        if (predicted != null && date in predicted && !date.isBefore(today)) return DayMark.PREDICTED_PERIOD
        return when (Cycles.phaseOn(date, periods, settings)) {
            CyclePhase.PERIOD -> if (date.isAfter(today)) DayMark.PREDICTED_PERIOD else DayMark.NONE
            CyclePhase.FOLLICULAR -> DayMark.FOLLICULAR
            CyclePhase.OVULATION -> DayMark.OVULATION
            CyclePhase.LUTEAL -> DayMark.LUTEAL
            null -> DayMark.NONE
        }
    }
}

/** Cycle tracking (M7.1, M7.3, M7.6): switching on, the calendar, logging and settings. */
class CycleViewModel(private val cycle: CycleRepository, private val clock: Clock) : ViewModel() {

    private val view = MutableStateFlow(LocalDate.now(clock).let { YearMonth.from(it) to it })

    val uiState: StateFlow<CycleUiState> = combine(cycle.settings, cycle.periods(), cycle.days(), view) { settings, periods, days, (month, selected) ->
        CycleUiState(
            loaded = true,
            settings = settings,
            periods = periods,
            days = days.associateBy { it.date },
            today = LocalDate.now(clock),
            month = month,
            selected = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleUiState())

    fun showMonth(month: YearMonth) {
        view.value = month to view.value.second
    }

    fun select(date: LocalDate) {
        view.value = view.value.first to date
    }

    fun setEnabled(on: Boolean) {
        viewModelScope.launch { cycle.setEnabled(on) }
    }

    fun saveSettings(settings: CycleSettings) {
        viewModelScope.launch { cycle.saveSettings(settings) }
    }

    fun periodStarted(date: LocalDate) {
        viewModelScope.launch { cycle.startPeriod(date) }
    }

    fun periodEnded(date: LocalDate) {
        viewModelScope.launch { cycle.endPeriod(date) }
    }

    fun deletePeriod(period: CyclePeriod) {
        viewModelScope.launch { cycle.deletePeriod(period.id) }
    }

    fun logDay(date: LocalDate, flow: Flow?, symptoms: Set<Symptom>, note: String?) {
        viewModelScope.launch { cycle.logDay(date, flow, symptoms, note) }
    }

    fun deleteAll() {
        viewModelScope.launch { cycle.deleteAll() }
    }
}
