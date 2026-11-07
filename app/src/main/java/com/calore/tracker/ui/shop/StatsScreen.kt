package com.calore.tracker.ui.shop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.data.repository.PantryRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.ShopRepository
import com.calore.tracker.data.repository.ShoppingRepository
import com.calore.tracker.domain.Costs
import com.calore.tracker.domain.MonthStats
import com.calore.tracker.domain.ProteinSource
import com.calore.tracker.domain.Stats
import com.calore.tracker.domain.WastedBox
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatKcal
import com.calore.tracker.ui.formatNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

data class StatsUiState(
    val month: MonthStats? = null,
    val lastMonths: List<MonthStats> = emptyList(),
    val cheapest: List<ProteinSource> = emptyList(),
)

/** Statistics (M5.9): waste and money per month, and the cheapest protein bought. */
class StatsViewModel(
    cooking: CookingRepository,
    private val pantry: PantryRepository,
    shopping: ShoppingRepository,
    shops: ShopRepository,
    private val recipes: RecipeRepository,
    private val clock: Clock,
) : ViewModel() {

    private val selected = MutableStateFlow(YearMonth.now(clock))

    val uiState: StateFlow<StatsUiState> = combine(cooking.wastedBoxes(), pantry.wasted(), shopping.bought(), shops.products(), selected) { boxes, pantryWaste, bought, products, month ->
        val today = LocalDate.now(clock)
        val wasted = boxes.map { WastedBox(cooking.dayOf(it.box.updatedAt), it.remainingNutrients.kcal, it.costHalers?.times(it.box.remainingFraction)) }
        val pantryMoney = pantryWaste.mapNotNull { item -> item.costHalers?.let { pantry.dayOf(item) to it } }
        val spent = bought.map { it.first to it.second }
        fun stats(m: YearMonth) = Stats.month(m, wasted, pantryMoney, spent, today)
        val boughtProducts = bought.mapNotNull { it.third }.toSet()
        val protein = products.filter { it.id in boughtProducts && it.priceHalers != null && it.foodUuid != null }.mapNotNull { product ->
            val food = recipes.food(product.foodUuid!!) ?: return@mapNotNull null
            val proteinPer100g = food.per100g.protein ?: return@mapNotNull null
            Triple(product.name, Costs.pricePerGram(product.priceHalers!!, product.packAmount), proteinPer100g)
        }
        StatsUiState(
            month = stats(month),
            lastMonths = (5 downTo 0).map { stats(YearMonth.from(today).minusMonths(it.toLong())) },
            cheapest = Stats.cheapestProtein(protein),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun previous() = selected.update { it.minusMonths(1) }
    fun next() = selected.update { it.plusMonths(1) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val month = state.month ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previous) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.stats_previous)) }
                Text(
                    month.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.month.year,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::next) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.stats_next)) }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pluralStringResource(R.plurals.stats_wasted, month.wastedBoxes, month.wastedBoxes, formatKcal(month.wastedKcal)))
                    Text(stringResource(R.string.stats_wasted_money, formatNumber(month.wastedHalers / 100, decimals = 0)))
                    Text(stringResource(R.string.stats_spent, formatNumber(month.spentHalers / 100, decimals = 0)))
                    Text(stringResource(R.string.stats_per_day, formatNumber(month.perDayHalers / 100, decimals = 0)))
                }
            }
            SectionHeader(stringResource(R.string.stats_chart))
            MoneyChart(state.lastMonths)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(MaterialTheme.colorScheme.primary, stringResource(R.string.stats_legend_spent))
                Legend(MaterialTheme.colorScheme.error, stringResource(R.string.stats_legend_wasted))
            }
            if (state.cheapest.isNotEmpty()) {
                SectionHeader(stringResource(R.string.stats_cheapest_protein))
                state.cheapest.forEach { source ->
                    Row {
                        Text(source.name, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.recipe_cost_protein, formatNumber(source.per100gProteinHalers / 100, decimals = 1)))
                    }
                }
            }
        }
    }
}

/** Two bars per month: spent and thrown away, on one scale. */
@Composable
private fun MoneyChart(months: List<MonthStats>) {
    val spentColor = MaterialTheme.colorScheme.primary
    val wastedColor = MaterialTheme.colorScheme.error
    val max = months.maxOfOrNull { maxOf(it.spentHalers, it.wastedHalers) }?.takeIf { it > 0 } ?: 1.0
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / months.size.coerceAtLeast(1)
            val bar = slot / 3
            months.forEachIndexed { i, m ->
                val x = i * slot + bar / 2
                val spentHeight = (m.spentHalers / max * size.height).toFloat()
                val wastedHeight = (m.wastedHalers / max * size.height).toFloat()
                drawRect(spentColor, Offset(x, size.height - spentHeight), Size(bar, spentHeight))
                drawRect(wastedColor, Offset(x + bar, size.height - wastedHeight), Size(bar, wastedHeight))
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            months.forEach { m ->
                Text(
                    m.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp)) { Canvas(Modifier.size(12.dp)) { drawRect(color) } }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
