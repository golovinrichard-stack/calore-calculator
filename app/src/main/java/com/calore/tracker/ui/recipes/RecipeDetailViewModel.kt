package com.calore.tracker.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.DiaryRepository
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Recipes
import com.calore.tracker.domain.mealAt
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

sealed interface RecipeLoad {
    data object Loading : RecipeLoad
    data object NotFound : RecipeLoad
    data class Loaded(val details: RecipeDetails, val myAllergens: Set<Allergen>) : RecipeLoad
}

sealed interface RecipeEvent {
    data object Deleted : RecipeEvent
    data class Logged(val meal: MealType) : RecipeEvent
    data class Copied(val id: String) : RecipeEvent
}

/** One recipe (M3.3): log a portion (M3.5), scale and swap (M3.6), edit and delete. */
class RecipeDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    members: MemberRepository,
    foods: FoodRepository? = null,
    private val diary: DiaryRepository? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    val recipeId: String = savedStateHandle.get<String>(Routes.ARG_RECIPE_ID).orEmpty()

    val uiState: StateFlow<RecipeLoad> = combine(recipes.observe(recipeId), members.me()) { details, me ->
        if (details == null) RecipeLoad.NotFound else RecipeLoad.Loaded(details, me.allergens)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecipeLoad.Loading)

    /** The food picker for swapping an ingredient. */
    val picker: FoodSearch? = foods?.let { FoodSearch(it, viewModelScope) }

    private val eventChannel = Channel<RecipeEvent>(Channel.BUFFERED)
    val events: Flow<RecipeEvent> = eventChannel.receiveAsFlow()

    /** The meal a portion logged now most likely belongs to. */
    fun mealNow(): MealType = mealAt(LocalTime.now(clock))

    fun delete() {
        viewModelScope.launch {
            recipes.delete(recipeId)
            eventChannel.send(RecipeEvent.Deleted)
        }
    }

    /** Logs a portion to today's diary (M3.5). */
    fun logPortion(details: RecipeDetails, servings: Double?, grams: Double?, meal: MealType) {
        val diary = diary ?: return
        viewModelScope.launch {
            diary.logDish(details.recipe, details.nutrition, servings, grams, meal, LocalDate.now(clock))
            eventChannel.send(RecipeEvent.Logged(meal))
        }
    }

    /** Swaps an ingredient in this recipe (M3.6). */
    fun swapInRecipe(details: RecipeDetails, index: Int, food: Food, grams: Double) {
        viewModelScope.launch {
            recipes.save(details.recipe, Recipes.swapped(details.ingredients, index, food.uuid, grams), details.steps)
        }
    }

    /** Saves the swap as a new recipe, keeping this one (M3.6). */
    fun swapAsCopy(details: RecipeDetails, index: Int, food: Food, grams: Double, name: String) {
        viewModelScope.launch {
            val id = recipes.saveCopy(details, Recipes.swapped(details.ingredients, index, food.uuid, grams), name)
            eventChannel.send(RecipeEvent.Copied(id))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
