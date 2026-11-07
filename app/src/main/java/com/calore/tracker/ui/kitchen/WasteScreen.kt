package com.calore.tracker.ui.kitchen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.domain.BoxView
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.rememberMediumDateFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

data class WasteItem(val view: BoxView, val day: LocalDate)

data class WasteUiState(val items: List<WasteItem> = emptyList(), val monthCount: Int = 0, val monthKcal: Double = 0.0)

/** The waste log (M4.10): thrown-away boxes and this month's count and calories. Money comes in M5. */
class WasteViewModel(private val cooking: CookingRepository, private val clock: Clock) : ViewModel() {
    val uiState: StateFlow<WasteUiState> = cooking.wastedBoxes().map { boxes -> buildWaste(boxes, YearMonth.now(clock)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WasteUiState())

    internal fun buildWaste(boxes: List<BoxView>, month: YearMonth): WasteUiState {
        val items = boxes.map { WasteItem(it, cooking.dayOf(it.box.updatedAt)) }
        val thisMonth = items.filter { YearMonth.from(it.day) == month }
        return WasteUiState(items, thisMonth.size, thisMonth.sumOf { it.view.remainingNutrients.kcal })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteScreen(onBack: () -> Unit, viewModel: WasteViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = rememberMediumDateFormatter()
    val hideNumbers = LocalHideNumbers.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kitchen_thrown_away)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "summary") {
                Text(
                    if (state.items.isEmpty()) {
                        stringResource(R.string.waste_none)
                    } else {
                        pluralStringResource(R.plurals.waste_month, state.monthCount, state.monthCount, formatKcal(state.monthKcal))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(state.items, key = { it.view.box.id }) { item ->
                ListItem(
                    headlineContent = { Text("${item.view.box.code} · ${item.view.name}") },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                item.day.format(formatter),
                                item.view.box.wastedReason?.let { stringResource(it.labelRes()) },
                            ).joinToString(" · "),
                        )
                    },
                    trailingContent = if (hideNumbers) null else {
                        { Text(stringResource(R.string.value_kcal, formatKcal(item.view.remainingNutrients.kcal))) }
                    },
                )
            }
        }
    }
}
