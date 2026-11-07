package com.calore.tracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.WeightRepository
import com.calore.tracker.domain.BodyFatReading
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.Money
import com.calore.tracker.domain.parseDecimal
import com.calore.tracker.domain.parseWholeIn
import com.calore.tracker.ui.formatEditable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

/** The profile as edited. Body, goal and macros reuse [TargetForm]; food and cooking edit [member]. */
data class ProfileUiState(
    val loaded: Boolean = false,
    val target: TargetForm = TargetForm(),
    val member: Member = Member.newMe(id = "", name = "", now = 0),
    val mealSplit: Map<MealType, String> = emptyMap(),
    val minutes: String = "",
    val budget: String = "",
    val attemptedSave: Boolean = false,
) {
    val mealSplitValues: Map<MealType, Int>?
        get() {
            val values = MealType.entries.associateWith { parseWholeIn(mealSplit[it].orEmpty(), 0..100) ?: return null }
            return values.takeIf { it.values.sum() == 100 }
        }

    val mealSplitTotal: Int get() = MealType.entries.sumOf { mealSplit[it]?.trim()?.toIntOrNull()?.coerceIn(0, 100) ?: 0 }

    val minutesValue: Int? get() = parseWholeIn(minutes, MINUTES_RANGE)

    /** Null budget text means "not set", which is valid. */
    val budgetValid: Boolean get() = budget.isBlank() || budgetValue != null

    val budgetValue: Money?
        get() {
            val crowns = parseDecimal(budget)?.takeIf { it >= 0 && it <= MAX_BUDGET_CZK } ?: return null
            return Money(BigDecimal.valueOf(crowns).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong())
        }

    val isValid: Boolean get() = mealSplitValues != null && minutesValue != null && budgetValid

    companion object {
        val MINUTES_RANGE = 15..600
        const val MAX_BUDGET_CZK = 1_000_000.0
    }
}

sealed interface ProfileSaved {
    data object Saved : ProfileSaved
    data class NewTargets(val kcal: Int) : ProfileSaved
}

/** Me → Profile (M1.2): body, goal, food and cooking, saved together. */
class ProfileViewModel(
    private val members: MemberRepository,
    private val weights: WeightRepository,
    private val clock: Clock,
    private val latestBodyFat: Flow<BodyFatReading?> = flowOf(null),
) : ViewModel() {

    private val state = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = state.asStateFlow()

    private var loadedTarget: TargetForm? = null

    private val savedChannel = Channel<ProfileSaved>(Channel.CONFLATED)
    val saved: Flow<ProfileSaved> = savedChannel.receiveAsFlow()

    val today: LocalDate get() = LocalDate.now(clock)

    init {
        viewModelScope.launch {
            val me = members.me().first()
            val target = TargetForm.from(me, weights.latest()?.kg, latestBodyFat.first())
            loadedTarget = target
            state.value = ProfileUiState(
                loaded = true,
                target = target,
                member = me,
                mealSplit = MealType.entries.associateWith { (me.mealSplit[it] ?: 0).toString() },
                minutes = me.minutesPerSession.toString(),
                budget = me.weeklyBudget?.let { formatEditable(it.halers / 100.0) }.orEmpty(),
            )
        }
    }

    fun updateTarget(transform: (TargetForm) -> TargetForm) {
        state.update { it.copy(target = transform(it.target)) }
    }

    fun updateMember(transform: (Member) -> Member) {
        state.update { it.copy(member = transform(it.member)) }
    }

    fun updateState(transform: (ProfileUiState) -> ProfileUiState) {
        state.update(transform)
    }

    fun save() {
        val current = state.value
        if (!current.loaded) return
        if (!current.isValid) {
            state.update { it.copy(attemptedSave = true) }
            return
        }
        // New targets only when something that decides them changed, so editing allergies
        // never overwrites goals the user typed in by hand.
        val result = current.target.result(today).takeIf { current.target != loadedTarget }
        viewModelScope.launch {
            members.updateMe { me ->
                val edited = current.member
                current.target.applyTo(
                    me.copy(
                        mealSplit = current.mealSplitValues ?: me.mealSplit,
                        diet = edited.diet,
                        allergens = edited.allergens,
                        likedFoods = edited.likedFoods,
                        dislikedFoods = edited.dislikedFoods,
                        cuisines = edited.cuisines,
                        equipment = edited.equipment,
                        variety = edited.variety,
                        weeklyBudget = current.budgetValue,
                        cookSessionsPerWeek = edited.cookSessionsPerWeek,
                        cookDays = edited.cookDays,
                        minutesPerSession = current.minutesValue ?: me.minutesPerSession,
                        preppedMeals = edited.preppedMeals,
                    ),
                    result,
                )
            }
            loadedTarget = current.target
            savedChannel.send(if (result != null) ProfileSaved.NewTargets(result.goals.kcal) else ProfileSaved.Saved)
        }
    }
}
