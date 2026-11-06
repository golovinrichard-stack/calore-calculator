package com.calore.tracker.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.domain.Allergen
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RecipeLoad {
    data object Loading : RecipeLoad
    data object NotFound : RecipeLoad
    data class Loaded(val details: RecipeDetails, val myAllergens: Set<Allergen>) : RecipeLoad
}

/** One recipe (M3.3): ingredients, steps, nutrition per serving, allergens and storage. */
class RecipeDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    members: MemberRepository,
) : ViewModel() {

    val recipeId: String = savedStateHandle.get<String>(Routes.ARG_RECIPE_ID).orEmpty()

    val uiState: StateFlow<RecipeLoad> = combine(recipes.observe(recipeId), members.me()) { details, me ->
        if (details == null) RecipeLoad.NotFound else RecipeLoad.Loaded(details, me.allergens)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecipeLoad.Loading)

    private val deletedChannel = Channel<Unit>(Channel.BUFFERED)
    val deleted: Flow<Unit> = deletedChannel.receiveAsFlow()

    fun delete() {
        viewModelScope.launch {
            recipes.delete(recipeId)
            deletedChannel.send(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
