package com.calore.tracker.ui.kitchen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.data.repository.EatenBox
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.domain.Box
import com.calore.tracker.domain.BoxEating
import com.calore.tracker.domain.BoxView
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.Storage
import com.calore.tracker.domain.WasteReason
import com.calore.tracker.domain.mealAt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/** A box in the Kitchen tab, with whose it is. */
data class KitchenItem(val view: BoxView, val owner: Member?) {
    val box: Box get() = view.box
}

data class KitchenUiState(
    val loading: Boolean = true,
    val fridge: List<KitchenItem> = emptyList(),
    val freezer: List<KitchenItem> = emptyList(),
    val mineOnly: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
) {
    val isEmpty: Boolean get() = fridge.isEmpty() && freezer.isEmpty()
}

sealed interface KitchenEvent {
    data class Eaten(val eaten: EatenBox, val code: String) : KitchenEvent
    data object CantFreeze : KitchenEvent
}

/** The Kitchen tab (M4.6): boxes in the fridge and the freezer, and what can be done with them. */
class KitchenViewModel(
    private val cooking: CookingRepository,
    private val members: MemberRepository,
    private val clock: Clock,
) : ViewModel() {

    private val mineOnly = MutableStateFlow(false)

    val uiState: StateFlow<KitchenUiState> = combine(cooking.activeBoxes(), members.me(), members.managed(), mineOnly) { boxes, me, managed, mine ->
        val people = (listOf(me) + managed).associateBy { it.id }
        // Boxes for the whole household (no owner) count as mine too.
        val shown = boxes.filter { !mine || it.box.ownerMemberId == null || it.box.ownerMemberId == me.id }
            .map { KitchenItem(it, it.box.ownerMemberId?.let(people::get)) }
            .sortedWith(compareBy({ it.box.eatByDate }, { it.box.code }))
        KitchenUiState(
            loading = false,
            fridge = shown.filter { it.box.storage != Storage.FREEZER },
            freezer = shown.filter { it.box.storage == Storage.FREEZER },
            mineOnly = mine,
            today = LocalDate.now(clock),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), KitchenUiState())

    private val eventChannel = Channel<KitchenEvent>(Channel.BUFFERED)
    val events: Flow<KitchenEvent> = eventChannel.receiveAsFlow()

    fun setMineOnly(on: Boolean) {
        mineOnly.value = on
    }

    /** The day a batch was cooked. */
    fun cookedDay(millis: Long): LocalDate = cooking.dayOf(millis)

    /**
     * "Ate it" (M4.7): everything left, or [gramsEaten] of it. The entry goes into the planned
     * meal, or the meal matching the time of day, in the owner's diary.
     */
    fun eat(item: KitchenItem, gramsEaten: Double? = null) {
        viewModelScope.launch {
            val me = members.me().first()
            val box = item.box
            val share = gramsEaten?.let { BoxEating.shareForGrams(it, item.view.fullGrams) }
            val meal = box.plannedMeal ?: mealAt(LocalTime.now(clock))
            val ownerIsMe = box.ownerMemberId == null || box.ownerMemberId == me.id
            val eaten = cooking.eat(item.view, share, meal, LocalDate.now(clock), ownerIsMe)
            eventChannel.send(KitchenEvent.Eaten(eaten, box.code))
        }
    }

    fun undo(eaten: EatenBox) {
        viewModelScope.launch { cooking.undoEat(eaten) }
    }

    fun freeze(box: Box) {
        viewModelScope.launch { if (!cooking.freeze(box)) eventChannel.send(KitchenEvent.CantFreeze) }
    }

    fun thaw(box: Box) {
        viewModelScope.launch { cooking.thaw(box) }
    }

    fun throwAway(box: Box, reason: WasteReason) {
        viewModelScope.launch { cooking.throwAway(box, reason) }
    }

    fun move(box: Box, date: LocalDate?, meal: MealType?) {
        viewModelScope.launch { cooking.move(box, date, meal) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
