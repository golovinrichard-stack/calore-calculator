package com.calore.tracker.ui.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.household.HouseholdRepository
import com.calore.tracker.data.household.HouseholdState
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.domain.BoxView
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.household.Ownership
import com.calore.tracker.domain.mealAt
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.kitchen.OwnershipLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

data class HouseholdToday(val inHousehold: Boolean = false, val waiting: Int = 0, val upForGrabs: List<Pair<BoxView, Member?>> = emptyList())

/** Today's household card: "Up for grabs" boxes from others (M11.6) and changes waiting for home Wi-Fi (M11.10). */
class HouseholdTodayViewModel(
    household: HouseholdRepository,
    private val cooking: CookingRepository,
    members: MemberRepository,
    private val clock: Clock,
) : ViewModel() {
    private val people = combine(members.me(), members.managed(), members.household()) { me, managed, others -> Triple(me, managed, others) }

    val state: StateFlow<HouseholdToday> = combine(household.state, household.waitingChanges, cooking.activeBoxes(), people) { hh, waiting, boxes, (me, managed, others) ->
        if (hh !is HouseholdState.In) return@combine HouseholdToday()
        val managedIds = managed.mapTo(HashSet()) { it.id }
        val byId = (listOf(me) + managed + others).associateBy { it.id }
        HouseholdToday(
            inHousehold = true,
            waiting = waiting,
            upForGrabs = boxes.filter { Ownership.showsAsUpForGrabs(it.box.ownerMemberId, it.box.visibility, me.id, managedIds) }
                .map { it to it.box.ownerMemberId?.let(byId::get) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseholdToday())

    /** Takes an up-for-grabs box: it goes into my diary. */
    fun take(view: BoxView) {
        viewModelScope.launch {
            val meal = view.box.plannedMeal ?: mealAt(LocalTime.now(clock))
            cooking.eat(view, null, meal, LocalDate.now(clock), ownerIsMe = true)
        }
    }
}

@Composable
fun HouseholdTodayCard(viewModel: HouseholdTodayViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.inHousehold || (state.waiting == 0 && state.upForGrabs.isEmpty())) return
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.upForGrabs.isNotEmpty()) {
                Text(stringResource(R.string.today_up_for_grabs), style = MaterialTheme.typography.titleMedium)
                state.upForGrabs.forEach { (view, owner) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${view.box.code} · ${view.name}")
                            OwnershipLabel(view.box.visibility, owner)
                        }
                        TextButton(onClick = { viewModel.take(view) }) { Text(stringResource(R.string.today_take_it)) }
                    }
                }
            }
            if (state.waiting > 0) {
                Text(pluralStringResource(R.plurals.household_changes_waiting, state.waiting, state.waiting), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
