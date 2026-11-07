package com.calore.tracker.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.Recipes
import com.calore.tracker.domain.matchesSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.Collator
import java.util.Locale

/** One line in the recipe list. */
data class RecipeListItem(
    val id: String,
    val name: String,
    val perServing: Nutrients,
    val mealTypes: Set<MealType>,
    /** The user's own allergens this recipe contains (shown only with "Show all"). */
    val myAllergens: Set<Allergen>,
)

data class RecipeFilters(
    val query: String = "",
    val meal: MealType? = null,
    val fitsDietOnly: Boolean = false,
    val showAll: Boolean = false,
)

data class RecipeListUiState(
    val loading: Boolean = true,
    val filters: RecipeFilters = RecipeFilters(),
    val items: List<RecipeListItem> = emptyList(),
    /** Recipes hidden because they contain one of the user's allergens (M3.3). */
    val hiddenForAllergens: Int = 0,
    val diet: Diet = Diet.OMNIVORE,
    /** False before the first recipe exists, so the empty list can say why. */
    val hasAnyRecipe: Boolean = false,
)

/** The recipe list in the Plan tab (M3.3): search, meal and diet filters, allergens hidden by default. */
class RecipeListViewModel(
    recipes: RecipeRepository,
    members: MemberRepository,
    private val locale: () -> Locale = { Locale.getDefault() },
) : ViewModel() {

    private val filters = MutableStateFlow(RecipeFilters())

    val uiState: StateFlow<RecipeListUiState> = combine(recipes.observeAll(), members.me(), filters) { all, me, filters ->
        buildRecipeList(all, me.diet, me.allergens, filters, locale())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecipeListUiState())

    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }

    /** Tapping the selected meal again clears the filter. */
    fun onMealChange(meal: MealType?) = filters.update { it.copy(meal = if (it.meal == meal) null else meal) }

    fun onFitsDietChange(on: Boolean) = filters.update { it.copy(fitsDietOnly = on) }

    fun onShowAllChange(on: Boolean) = filters.update { it.copy(showAll = on) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The list for these filters (M3.3): search in both languages, meal type, the A15 diet check, and
 * recipes with one of [myAllergens] hidden unless "Show all" is on. Sorted by name for [locale].
 */
internal fun buildRecipeList(
    all: List<RecipeDetails>,
    diet: Diet,
    myAllergens: Set<Allergen>,
    filters: RecipeFilters,
    locale: Locale,
): RecipeListUiState {
    val collator = Collator.getInstance(locale)
    val matching = all.filter { details ->
        val recipe = details.recipe
        matchesSearch(filters.query, recipe.displayName(locale), recipe.name, recipe.nameCs, recipe.nameEn, recipe.tags.joinToString(" ")) &&
            (filters.meal == null || filters.meal in recipe.mealTypes) &&
            (!filters.fitsDietOnly || Recipes.fitsDiet(details.animalFlags, diet))
    }
    val (withAllergen, safe) = matching.partition { details -> details.allergens.any { it in myAllergens } }
    val shown = if (filters.showAll) matching else safe
    return RecipeListUiState(
        loading = false,
        filters = filters,
        items = shown
            .map { details ->
                RecipeListItem(
                    id = details.recipe.id,
                    name = details.recipe.displayName(locale),
                    perServing = details.nutrition.perServing,
                    mealTypes = details.recipe.mealTypes,
                    myAllergens = details.allergens intersect myAllergens,
                )
            }
            .sortedWith(compareBy(collator) { it.name }),
        hiddenForAllergens = if (filters.showAll) 0 else withAllergen.size,
        diet = diet,
        hasAnyRecipe = all.isNotEmpty(),
    )
}
