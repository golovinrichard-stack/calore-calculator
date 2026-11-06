package com.calore.tracker.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.BodyRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.ReminderSettings
import com.calore.tracker.domain.BodyFatEstimate
import com.calore.tracker.domain.BodyFatMethod
import com.calore.tracker.domain.NavyBodyFat
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.parseDecimal
import com.calore.tracker.ui.formatEditable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class BodyFatEntryMode { TAPE, MANUAL }

data class BodyFatForm(
    val mode: BodyFatEntryMode = BodyFatEntryMode.TAPE,
    val sex: Sex? = null,
    val heightCm: String = "",
    val neckCm: String = "",
    val waistCm: String = "",
    val hipCm: String = "",
    val manualPct: String = "",
    val attemptedSave: Boolean = false,
) {
    /** The tape estimate, or null while a field is still empty. */
    val estimate: BodyFatEstimate?
        get() {
            val sex = sex ?: return null
            val height = parseDecimal(heightCm) ?: return null
            val neck = parseDecimal(neckCm) ?: return null
            val waist = parseDecimal(waistCm) ?: return null
            val hip = parseDecimal(hipCm)
            if (sex == Sex.FEMALE && hip == null) return null
            return NavyBodyFat.estimate(sex, height, neck, waist, hip)
        }

    val manualValue: Double? get() = parseDecimal(manualPct)?.takeIf { it in NavyBodyFat.MANUAL_RANGE_PCT }

    /** The value that would be saved now. */
    val bodyFatPct: Double?
        get() = when (mode) {
            BodyFatEntryMode.TAPE -> (estimate as? BodyFatEstimate.Ok)?.pct
            BodyFatEntryMode.MANUAL -> manualValue
        }
}

/** Saved; [first] is true for the very first measurement, which offers a reminder (M1.4). */
data class BodyFatSaved(val first: Boolean)

class BodyFatViewModel(
    private val members: MemberRepository,
    private val body: BodyRepository,
    private val reminders: ReminderSettings? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val state = MutableStateFlow(BodyFatForm())
    val form: StateFlow<BodyFatForm> = state.asStateFlow()

    private val savedChannel = Channel<BodyFatSaved>(Channel.CONFLATED)
    val saved: Flow<BodyFatSaved> = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val me = members.me().first()
            state.update { it.copy(sex = me.sex, heightCm = formatEditable(me.heightCm)) }
        }
    }

    fun update(transform: (BodyFatForm) -> BodyFatForm) {
        state.update(transform)
    }

    fun save() {
        val current = state.value
        val pct = current.bodyFatPct
        if (pct == null) {
            state.update { it.copy(attemptedSave = true) }
            return
        }
        viewModelScope.launch {
            // Sex and height belong to the profile; fill them in there if they were missing.
            members.updateMe { me ->
                me.copy(sex = me.sex ?: current.sex, heightCm = me.heightCm ?: parseDecimal(current.heightCm))
            }
            val first = when (current.mode) {
                BodyFatEntryMode.TAPE -> body.saveBodyFat(
                    method = BodyFatMethod.NAVY,
                    bodyFatPct = pct,
                    neckCm = parseDecimal(current.neckCm),
                    waistCm = parseDecimal(current.waistCm),
                    hipCm = parseDecimal(current.hipCm).takeIf { current.sex == Sex.FEMALE },
                )
                BodyFatEntryMode.MANUAL -> body.saveBodyFat(method = BodyFatMethod.MANUAL, bodyFatPct = pct)
            }
            reminders?.onMeasured(LocalDate.now(clock))
            savedChannel.send(BodyFatSaved(first))
        }
    }

    /** The answer to the reminder offer after the first measurement; 0 means no reminder. */
    fun chooseReminder(weeks: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            reminders?.setMeasureReminderWeeks(weeks)
            onDone()
        }
    }
}
