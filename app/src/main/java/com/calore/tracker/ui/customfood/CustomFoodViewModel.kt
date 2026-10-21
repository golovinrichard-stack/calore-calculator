package com.calore.tracker.ui.customfood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.domain.FoodDraft
import com.calore.tracker.domain.FoodDraftResult
import com.calore.tracker.domain.FoodField
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.toDraft
import com.calore.tracker.domain.validate
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.ui.navigation.Routes
import com.calore.tracker.ui.navigation.dateArg
import com.calore.tracker.ui.navigation.mealArg
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class CustomFoodUiState(
    val draft: FoodDraft,
    val isEditing: Boolean,
    val isLoaded: Boolean,
    val isSaving: Boolean = false,
    /** Errors appear only after the first save attempt, then update live as the user fixes them. */
    val attemptedSave: Boolean = false,
) {
    val errors: Set<FoodField>
        get() = if (attemptedSave) (draft.validate() as? FoodDraftResult.Invalid)?.fields.orEmpty() else emptySet()
}

sealed interface CustomFoodEvent {
    data class Created(val foodId: Long) : CustomFoodEvent
    data object Updated : CustomFoodEvent
}

class CustomFoodViewModel(
    savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
    clock: Clock,
) : ViewModel() {

    val date: LocalDate = savedStateHandle.dateArg(clock)
    val meal: MealType = savedStateHandle.mealArg()
    private val editId: Long? = savedStateHandle.get<Long>(Routes.ARG_FOOD_ID)?.takeIf { it > 0 }

    private val state = MutableStateFlow(
        CustomFoodUiState(
            draft = FoodDraft(name = savedStateHandle.get<String>(Routes.ARG_NAME).orEmpty()),
            isEditing = editId != null,
            isLoaded = editId == null,
        ),
    )
    val uiState: StateFlow<CustomFoodUiState> = state.asStateFlow()

    private val eventChannel = Channel<CustomFoodEvent>(Channel.BUFFERED)
    val events: Flow<CustomFoodEvent> = eventChannel.receiveAsFlow()

    init {
        if (editId != null) {
            viewModelScope.launch {
                val food = foods.getFood(editId)
                state.update { current ->
                    current.copy(draft = food?.toDraft(::formatEditable) ?: current.draft, isLoaded = true)
                }
            }
        }
    }

    fun updateDraft(transform: (FoodDraft) -> FoodDraft) {
        state.update { it.copy(draft = transform(it.draft)) }
    }

    fun save() {
        val current = state.value
        if (current.isSaving || !current.isLoaded) return
        when (val result = current.draft.validate()) {
            is FoodDraftResult.Invalid -> state.update { it.copy(attemptedSave = true) }
            is FoodDraftResult.Valid -> {
                state.update { it.copy(isSaving = true) }
                viewModelScope.launch {
                    try {
                        val id = foods.saveCustom(
                            existingId = editId,
                            name = result.name,
                            brand = result.brand,
                            per100g = result.per100g,
                            servingGrams = result.servingGrams,
                        )
                        eventChannel.send(if (editId == null) CustomFoodEvent.Created(id) else CustomFoodEvent.Updated)
                    } finally {
                        state.update { it.copy(isSaving = false) }
                    }
                }
            }
        }
    }
}
