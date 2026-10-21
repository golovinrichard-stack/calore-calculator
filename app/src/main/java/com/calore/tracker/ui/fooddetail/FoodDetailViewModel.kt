package com.calore.tracker.ui.fooddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.DiaryRepository
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.parsePortionGrams
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.ui.navigation.Routes
import com.calore.tracker.ui.navigation.dateArg
import com.calore.tracker.ui.navigation.mealArg
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

sealed interface FoodLoad {
    data object Loading : FoodLoad
    data object NotFound : FoodLoad
    data class Loaded(val food: Food) : FoodLoad
}

enum class FoodDetailEvent { LOGGED, DELETED }

data class FoodDetailUiState(
    val load: FoodLoad = FoodLoad.Loading,
    val gramsText: String = "",
    val meal: MealType = MealType.SNACK,
    val isSaving: Boolean = false,
) {
    val grams: Double? get() = parsePortionGrams(gramsText)

    val portion: Nutrients?
        get() {
            val food = (load as? FoodLoad.Loaded)?.food ?: return null
            return grams?.let { food.per100g.forGramsOfPer100g(it) }
        }

    val canAdd: Boolean get() = load is FoodLoad.Loaded && grams != null && !isSaving
}

class FoodDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    clock: Clock,
) : ViewModel() {

    val date: LocalDate = savedStateHandle.dateArg(clock)
    private val foodId: Long = savedStateHandle.get<Long>(Routes.ARG_FOOD_ID) ?: Routes.NEW_FOOD_ID

    /** Null until the user types, so the field follows the food's default portion until then. */
    private val gramsText = MutableStateFlow<String?>(null)
    private val meal = MutableStateFlow(savedStateHandle.mealArg())
    private val isSaving = MutableStateFlow(false)

    private val eventChannel = Channel<FoodDetailEvent>(Channel.BUFFERED)
    val events: Flow<FoodDetailEvent> = eventChannel.receiveAsFlow()

    private val load: Flow<FoodLoad> = foods.observeFood(foodId).map { food ->
        if (food == null) FoodLoad.NotFound else FoodLoad.Loaded(food)
    }

    val uiState: StateFlow<FoodDetailUiState> = combine(load, gramsText, meal, isSaving) { food, grams, selectedMeal, saving ->
        val defaultGrams = (food as? FoodLoad.Loaded)?.food?.servingGrams ?: DEFAULT_GRAMS
        FoodDetailUiState(
            load = food,
            gramsText = grams ?: formatEditable(defaultGrams),
            meal = selectedMeal,
            isSaving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FoodDetailUiState(meal = meal.value))

    fun onGramsChange(text: String) {
        gramsText.value = text
    }

    fun onMealChange(newMeal: MealType) {
        meal.value = newMeal
    }

    fun addToDiary() {
        val state = uiState.value
        val food = (state.load as? FoodLoad.Loaded)?.food ?: return
        val grams = state.grams ?: return
        if (isSaving.value) return
        isSaving.value = true
        viewModelScope.launch {
            try {
                diary.log(food, grams, state.meal, date)
                foods.markUsed(food.id)
                eventChannel.send(FoodDetailEvent.LOGGED)
            } finally {
                isSaving.value = false
            }
        }
    }

    fun deleteFood() {
        viewModelScope.launch {
            foods.deleteFood(foodId)
            eventChannel.send(FoodDetailEvent.DELETED)
        }
    }

    private companion object {
        const val DEFAULT_GRAMS = 100.0
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
