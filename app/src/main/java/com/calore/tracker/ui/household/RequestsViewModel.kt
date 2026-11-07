package com.calore.tracker.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.db.HouseholdDao
import com.calore.tracker.data.db.RequestEntity
import com.calore.tracker.data.household.RequestRepository
import com.calore.tracker.data.household.RequestStatus
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.household.ActivityFeed
import com.calore.tracker.domain.household.ActivityLine
import com.calore.tracker.domain.household.ActivityRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** A request with the names it needs on screen. */
data class RequestItem(val request: RequestEntity, val from: Member?, val to: Member?, val itemName: String?, val mine: Boolean, val forMe: Boolean)

data class RequestsUiState(
    val loaded: Boolean = false,
    val meId: String = "",
    val incoming: List<RequestItem> = emptyList(),
    val sent: List<RequestItem> = emptyList(),
    val others: List<Member> = emptyList(),
    val recipes: List<Pair<String, String>> = emptyList(),
    val feed: List<Pair<ActivityLine, Member?>> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
)

/** Requests (M11.7) and the activity feed (M11.8). */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModel(
    private val requests: RequestRepository,
    members: MemberRepository,
    recipes: RecipeRepository,
    dao: HouseholdDao,
    private val itemName: suspend (String?, String?) -> String?,
    private val clock: Clock,
) : ViewModel() {

    private val people = combine(members.me(), members.managed(), members.household()) { me, managed, household -> Triple(me, managed, household) }

    val uiState: StateFlow<RequestsUiState> = combine(
        requests.requests,
        people,
        recipes.observeAll(),
        dao.observeActivity(clock.millis() - ActivityFeed.DAYS * 24 * 60 * 60 * 1000),
    ) { rows, (me, managed, household), recipeList, activity ->
        Snapshot(rows, me, managed, household, recipeList.map { it.recipe.id to it.recipe.name }.sortedBy { it.second }, activity)
    }.mapLatest { snap ->
        val byId = (listOf(snap.me) + snap.managed + snap.household).associateBy { it.id }
        val items = snap.rows.map { r ->
            RequestItem(
                request = r,
                from = byId[r.fromMemberId],
                to = r.toMemberId?.let(byId::get),
                itemName = itemName(r.refTable, r.refId),
                mine = r.fromMemberId == snap.me.id,
                forMe = r.fromMemberId != snap.me.id && (r.toMemberId == null || r.toMemberId == snap.me.id),
            )
        }
        RequestsUiState(
            loaded = true,
            meId = snap.me.id,
            incoming = items.filter { it.forMe && it.request.status != RequestStatus.CANCELLED.name },
            sent = items.filter { it.mine },
            others = snap.household,
            recipes = snap.recipes,
            feed = ActivityFeed.build(snap.activity.map { ActivityRow(it.memberId, it.type, it.summary, it.at) }, clock.millis())
                .map { it to byId[it.memberId] },
            today = LocalDate.now(clock),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestsUiState())

    private data class Snapshot(
        val rows: List<RequestEntity>,
        val me: Member,
        val managed: List<Member>,
        val household: List<Member>,
        val recipes: List<Pair<String, String>>,
        val activity: List<com.calore.tracker.data.db.ActivityEntity>,
    )

    fun cookForMe(to: String?, date: LocalDate, meal: MealType, recipe: Pair<String, String>?, portions: Int, message: String) {
        viewModelScope.launch { requests.cookForMe(to, date, meal, recipe?.first, recipe?.second, portions, message) }
    }

    fun buyForMe(name: String, message: String) {
        if (name.isBlank()) return
        viewModelScope.launch { requests.buyForMe(name, message) }
    }

    fun answer(request: RequestEntity, status: RequestStatus) {
        viewModelScope.launch { requests.answer(request, status) }
    }

    fun suggest(request: RequestEntity, date: LocalDate, meal: MealType) {
        viewModelScope.launch { requests.suggest(request, date, meal, null) }
    }

    fun cancel(request: RequestEntity) {
        viewModelScope.launch { requests.cancel(request) }
    }
}
