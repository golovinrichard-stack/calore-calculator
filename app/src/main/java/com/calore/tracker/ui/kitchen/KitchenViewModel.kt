package com.calore.tracker.ui.kitchen

import com.calore.tracker.data.household.RequestRepository
import com.calore.tracker.domain.Visibility
import com.calore.tracker.domain.household.Ownership
import kotlinx.coroutines.flow.flowOf

import androidx.lifecycle.ViewModel
import com.calore.tracker.data.repository.RatingRepository
import com.calore.tracker.ui.rating.RatingPrompt
import com.calore.tracker.ui.rating.askAbout
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

/**
 * A box in the Kitchen tab, with whose it is. In a household (M11.6): [canTake] is false for
 * someone else's "Mine, don't touch" box (taking it asks first), and only the owner changes the label.
 */
data class KitchenItem(val view: BoxView, val owner: Member?, val canTake: Boolean = true, val canChangeLabel: Boolean = true) {
    val box: Box get() = view.box
}

data class KitchenUiState(
    val loading: Boolean = true,
    val fridge: List<KitchenItem> = emptyList(),
    val freezer: List<KitchenItem> = emptyList(),
    val mineOnly: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    /** Labels show only in a household (M11.6). */
    val inHousehold: Boolean = false,
) {
    val isEmpty: Boolean get() = fridge.isEmpty() && freezer.isEmpty()
}

sealed interface KitchenEvent {
    data class Eaten(val eaten: EatenBox, val code: String) : KitchenEvent
    data object CantFreeze : KitchenEvent

    /** M11.6: "Can I have it?" went to the owner. */
    data class Asked(val owner: String) : KitchenEvent
}

/** The Kitchen tab (M4.6): boxes in the fridge and the freezer, and what can be done with them. */
class KitchenViewModel(
    private val cooking: CookingRepository,
    private val members: MemberRepository,
    private val clock: Clock,
    private val ratings: RatingRepository? = null,
    private val ratingPrompt: RatingPrompt? = null,
    private val requests: RequestRepository? = null,
    inHousehold: Flow<Boolean> = flowOf(false),
) : ViewModel() {

    private val mineOnly = MutableStateFlow(false)

    private val people = combine(members.me(), members.managed(), members.household()) { me, managed, household -> Triple(me, managed, household) }

    val uiState: StateFlow<KitchenUiState> = combine(cooking.activeBoxes(), people, mineOnly, inHousehold) { boxes, (me, managed, household), mine, shared ->
        val byId = (listOf(me) + managed + household).associateBy { it.id }
        val managedIds = managed.mapTo(HashSet()) { it.id }
        // Boxes for the whole household (no owner) count as mine too.
        val shown = boxes.filter { !mine || it.box.ownerMemberId == null || it.box.ownerMemberId == me.id }
            .map { view ->
                val owner = view.box.ownerMemberId
                KitchenItem(
                    view = view,
                    owner = owner?.let(byId::get),
                    canTake = !shared || Ownership.canTake(owner, view.box.visibility, me.id, managedIds),
                    canChangeLabel = Ownership.canChangeLabel(owner, me.id, managedIds),
                )
            }
            .sortedWith(compareBy({ it.box.eatByDate }, { it.box.code }))
        KitchenUiState(
            loading = false,
            fridge = shown.filter { it.box.storage != Storage.FREEZER },
            freezer = shown.filter { it.box.storage == Storage.FREEZER },
            mineOnly = mine,
            today = LocalDate.now(clock),
            inHousehold = shared,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), KitchenUiState())

    /** M11.6: the owner's label. */
    fun setVisibility(box: Box, visibility: Visibility) {
        viewModelScope.launch { cooking.setVisibility(box, visibility) }
    }

    /** M11.6: someone else's "Mine" box: ask its owner instead of taking it. */
    fun askFor(item: KitchenItem) {
        val repo = requests ?: return
        viewModelScope.launch {
            repo.canIHave("boxes", item.box.id, item.box.ownerMemberId, "${item.box.code} · ${item.view.name}")
            eventChannel.send(KitchenEvent.Asked(item.owner?.name.orEmpty()))
        }
    }

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
            // Only the boxes of people I cook for go into their diary; everything else I take is mine (M11.6).
            val ownerIsMe = box.ownerMemberId == null || box.ownerMemberId == me.id || members.managedMember(box.ownerMemberId) == null
            val eaten = cooking.eat(item.view, share, meal, LocalDate.now(clock), ownerIsMe)
            eventChannel.send(KitchenEvent.Eaten(eaten, box.code))
            if (ratings != null) ratingPrompt?.askAbout(item.view, box.ownerMemberId ?: me.id, ratings)
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
