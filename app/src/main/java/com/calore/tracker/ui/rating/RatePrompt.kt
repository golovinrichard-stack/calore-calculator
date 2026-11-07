package com.calore.tracker.ui.rating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.RatingRepository
import com.calore.tracker.ui.AppViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A recipe someone just ate, waiting for an optional 👍 / 👎 (M6.1). */
data class RateAsk(val memberId: String, val recipeId: String, val name: String)

/**
 * The recipes waiting for a rating after "Ate it", app-wide and in memory only: the question
 * disappears when answered, dismissed or when the app closes. Easy to ignore.
 */
class RatingPrompt {
    private val pending = MutableStateFlow<List<RateAsk>>(emptyList())
    val asks: StateFlow<List<RateAsk>> = pending.asStateFlow()

    /** Asks about these recipes; recipes the person already rated are left out by the caller. */
    fun ask(items: List<RateAsk>) = pending.update { old -> (old + items).distinctBy { it.memberId to it.recipeId }.takeLast(MAX) }

    fun done(item: RateAsk) = pending.update { old -> old - item }

    private companion object {
        const val MAX = 3
    }
}

class RatePromptViewModel(private val prompt: RatingPrompt, private val ratings: RatingRepository) : ViewModel() {
    val asks: StateFlow<List<RateAsk>> = prompt.asks

    fun rate(item: RateAsk, rating: Int) {
        prompt.done(item)
        viewModelScope.launch { ratings.rate(item.memberId, item.recipeId, rating) }
    }

    fun dismiss(item: RateAsk) = prompt.done(item)
}

/** "How was …? 👍 👎 ✕" for each recipe just eaten. Shows nothing when there is nothing to ask. */
@Composable
fun RatePromptCard(modifier: Modifier = Modifier, viewModel: RatePromptViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val asks by viewModel.asks.collectAsStateWithLifecycle()
    if (asks.isEmpty()) return
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            asks.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.rate_how_was, item.name), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    ThumbButtons(current = null, onRate = { viewModel.rate(item, it) })
                    IconButton(onClick = { viewModel.dismiss(item) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.rate_not_now))
                    }
                }
            }
        }
    }
}

/** 👍 and 👎; tapping the chosen one again takes the rating back ([onRate] with null). */
@Composable
fun ThumbButtons(current: Int?, onRate: (Int) -> Unit, onClear: () -> Unit = {}) {
    val like = stringResource(R.string.rate_like)
    val dislike = stringResource(R.string.rate_dislike)
    FilledTonalIconToggleButton(
        checked = current == 1,
        onCheckedChange = { if (it) onRate(1) else onClear() },
        modifier = Modifier.semantics { contentDescription = like },
    ) { Text("👍") }
    FilledTonalIconToggleButton(
        checked = current == -1,
        onCheckedChange = { if (it) onRate(-1) else onClear() },
        modifier = Modifier.semantics { contentDescription = dislike },
    ) { Text("👎") }
}

/** After "Ate it": asks about the box's recipes that [memberId] has not rated yet. */
suspend fun RatingPrompt.askAbout(view: com.calore.tracker.domain.BoxView, memberId: String, ratings: RatingRepository) {
    val rated = ratings.all()[memberId].orEmpty()
    ask(
        view.parts.mapNotNull { it.second }
            .mapNotNull { batch -> batch.recipeId?.takeIf { it !in rated }?.let { RateAsk(memberId, it, batch.name) } }
            .distinctBy { it.recipeId },
    )
}
