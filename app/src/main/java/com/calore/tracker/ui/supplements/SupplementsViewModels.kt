package com.calore.tracker.ui.supplements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.SupplementRepository
import com.calore.tracker.domain.DoseUnit
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Regularity
import com.calore.tracker.domain.Supplement
import com.calore.tracker.domain.SupplementDose
import com.calore.tracker.domain.SupplementKind
import com.calore.tracker.domain.SupplementLog
import com.calore.tracker.domain.SupplementSchedule
import com.calore.tracker.domain.SupplementStatus
import com.calore.tracker.domain.parseDecimal
import com.calore.tracker.notifications.SupplementReminders
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** One dose due today, with what was logged for it. */
data class TodayDose(val dose: SupplementDose, val status: SupplementStatus?)

/** A supplement in the list, with how regularly it was taken over 7 and 30 days (M8.3). */
data class SupplementRow(val supplement: Supplement, val week: Regularity, val month: Regularity, val history: List<Pair<LocalDate, SupplementStatus?>>)

data class SupplementsUiState(
    val loaded: Boolean = false,
    val rows: List<SupplementRow> = emptyList(),
    val today: List<TodayDose> = emptyList(),
)

/** Me → Supplements and the Today checklist (M8.3). */
class SupplementsViewModel(private val supplements: SupplementRepository, private val clock: Clock) : ViewModel() {

    private val since = LocalDate.now(clock).minusDays(HISTORY_DAYS.toLong())

    val uiState: StateFlow<SupplementsUiState> = combine(supplements.supplements(), supplements.logsSince(since)) { list, logs ->
        val today = LocalDate.now(clock)
        val zone = clock.zone
        val byDose = logs.associateBy { it.supplementId to it.scheduledAt }
        SupplementsUiState(
            loaded = true,
            rows = list.map { s ->
                SupplementRow(
                    supplement = s,
                    week = SupplementSchedule.regularity(s, logs, today, 7, zone),
                    month = SupplementSchedule.regularity(s, logs, today, HISTORY_DAYS, zone),
                    history = (0 until 14).map { today.minusDays(it.toLong()) }.filter { SupplementSchedule.isDue(s, it) }.map { day ->
                        val dayLogs = SupplementSchedule.doses(s, day, zone).mapNotNull { byDose[s.id to it.scheduledAt] }
                        day to dayStatus(dayLogs)
                    },
                )
            },
            today = SupplementSchedule.dosesOn(list, today, zone).map { TodayDose(it, byDose[it.supplement.id to it.scheduledAt]?.status) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupplementsUiState())

    fun take(dose: SupplementDose) {
        viewModelScope.launch { supplements.take(dose) }
    }

    fun skip(dose: SupplementDose) {
        viewModelScope.launch { supplements.skip(dose) }
    }

    fun undo(dose: SupplementDose) {
        viewModelScope.launch { supplements.undo(dose) }
    }

    private fun dayStatus(logs: List<SupplementLog>): SupplementStatus? = when {
        logs.isEmpty() -> null
        logs.all { it.status == SupplementStatus.TAKEN } -> SupplementStatus.TAKEN
        logs.any { it.status == SupplementStatus.SKIPPED } -> SupplementStatus.SKIPPED
        else -> null
    }

    private companion object {
        const val HISTORY_DAYS = 30
    }
}

/** The supplement form (M8.2). Numbers are kept as typed until saved. */
data class SupplementForm(
    val id: String = "",
    val name: String = "",
    val kind: SupplementKind = SupplementKind.OTHER,
    val dose: String = "1",
    val unit: DoseUnit = DoseUnit.TABLET,
    val times: List<LocalTime> = listOf(Supplement.DEFAULT_TIME),
    val days: Set<DayOfWeek> = emptySet(),
    val startDate: LocalDate = LocalDate.MIN,
    val endDate: LocalDate? = null,
    val reminders: Boolean = false,
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0,
    val attemptedSave: Boolean = false,
) {
    val doseValue: Double? get() = parseDecimal(dose)?.takeIf { it > 0 }
    val nameValid: Boolean get() = name.isNotBlank()
    val datesValid: Boolean get() = endDate == null || !endDate.isBefore(startDate)
    private fun optional(text: String): Double? = text.takeIf { it.isNotBlank() }?.let { parseDecimal(it) }
    val nutritionValid: Boolean
        get() = listOf(kcal, protein, carbs, fat).all { it.isBlank() || parseDecimal(it)?.let { v -> v >= 0 } == true } &&
            (kcal.isNotBlank() || listOf(protein, carbs, fat).all { it.isBlank() })
    val valid: Boolean get() = nameValid && doseValue != null && times.isNotEmpty() && datesValid && nutritionValid

    fun toSupplement(): Supplement = Supplement(
        id = id,
        name = name.trim(),
        kind = kind,
        doseAmount = doseValue ?: 1.0,
        doseUnit = unit,
        times = times.distinct().sorted(),
        daysOfWeek = days,
        startDate = startDate,
        endDate = endDate,
        remindersOn = reminders,
        nutrients = optional(kcal)?.let { Nutrients(kcal = it, protein = optional(protein), carbs = optional(carbs), fat = optional(fat)) },
        active = active,
        createdAt = createdAt,
    )

    companion object {
        fun from(s: Supplement) = SupplementForm(
            id = s.id,
            name = s.name,
            kind = s.kind,
            dose = com.calore.tracker.ui.formatNumber(s.doseAmount, decimals = 2),
            unit = s.doseUnit,
            times = s.times,
            days = s.daysOfWeek,
            startDate = s.startDate,
            endDate = s.endDate,
            reminders = s.remindersOn,
            kcal = s.nutrients?.kcal?.let { com.calore.tracker.ui.formatNumber(it, decimals = 1) }.orEmpty(),
            protein = s.nutrients?.protein?.let { com.calore.tracker.ui.formatNumber(it, decimals = 1) }.orEmpty(),
            carbs = s.nutrients?.carbs?.let { com.calore.tracker.ui.formatNumber(it, decimals = 1) }.orEmpty(),
            fat = s.nutrients?.fat?.let { com.calore.tracker.ui.formatNumber(it, decimals = 1) }.orEmpty(),
            active = s.active,
            createdAt = s.createdAt,
        )
    }
}

/** Adding or changing a supplement (M8.2). */
class SupplementEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val supplements: SupplementRepository,
    private val reminders: SupplementReminders?,
    private val clock: Clock,
) : ViewModel() {

    private val id: String = savedStateHandle.get<String>(Routes.ARG_SUPPLEMENT_ID).orEmpty()
    private val form = MutableStateFlow(SupplementForm(startDate = LocalDate.now(clock)))
    val uiState: StateFlow<SupplementForm> = form.asStateFlow()
    val isNew: Boolean get() = id.isEmpty()

    private val doneChannel = Channel<Unit>(Channel.CONFLATED)
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    init {
        if (id.isNotEmpty()) viewModelScope.launch { supplements.supplement(id)?.let { form.value = SupplementForm.from(it) } }
    }

    fun update(transform: (SupplementForm) -> SupplementForm) = form.update(transform)

    fun save() {
        val current = form.value
        if (!current.valid) {
            form.update { it.copy(attemptedSave = true) }
            return
        }
        viewModelScope.launch {
            val saved = supplements.save(current.toSupplement())
            reminders?.reschedule(saved)
            doneChannel.send(Unit)
        }
    }

    fun delete() {
        if (id.isEmpty()) return
        viewModelScope.launch {
            supplements.delete(id)
            reminders?.reschedule(id)
            doneChannel.send(Unit)
        }
    }
}
