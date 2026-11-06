package com.calore.tracker.ui.recipes

import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.domain.Food
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodSearchState(val query: String = "", val results: List<Food> = emptyList())

/** The food picker's search: recent foods, or local foods with basic foods first (M3.2, M3.6). */
class FoodSearch(private val foods: FoodRepository, private val scope: CoroutineScope) {

    private val mutableState = MutableStateFlow(FoodSearchState())
    val state: StateFlow<FoodSearchState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun onQueryChange(query: String) {
        mutableState.update { it.copy(query = query) }
        job?.cancel()
        job = scope.launch {
            val results = if (query.isBlank()) {
                foods.recentFoods().first()
            } else {
                delay(DEBOUNCE_MS)
                foods.searchLocal(query)
            }
            mutableState.update { it.copy(results = results) }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 200L
    }
}
