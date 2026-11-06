package com.calore.tracker.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.MealType
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class RecipeEditUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    val form: RecipeForm = RecipeForm(),
    /** Errors show only after the first try to save. */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    /** Something differs from what was loaded, so leaving asks first. */
    val changed: Boolean = false,
    val pickerQuery: String = "",
    val pickerResults: List<Food> = emptyList(),
)

/** The recipe builder (M3.2): a new recipe, or changes to one. */
class RecipeEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val foods: FoodRepository,
    private val locale: () -> Locale = { Locale.getDefault() },
) : ViewModel() {

    private val editId: String? = savedStateHandle.get<String>(Routes.ARG_RECIPE_ID)?.takeIf { it.isNotBlank() }
    private val state = MutableStateFlow(RecipeEditUiState(isNew = editId == null))
    val uiState: StateFlow<RecipeEditUiState> = state.asStateFlow()

    private var initial: RecipeForm = RecipeForm()
    private var takenCodes: Set<String> = emptySet()
    private var pickerJob: Job? = null

    private val savedChannel = Channel<String>(Channel.BUFFERED)

    /** The id of the saved recipe. */
    val saved: Flow<String> = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            takenCodes = recipes.shortCodes()
            val form = if (editId == null) {
                RecipeForm()
            } else {
                val details = recipes.details(editId)
                if (details == null) {
                    state.update { it.copy(loading = false, notFound = true) }
                    return@launch
                }
                takenCodes = takenCodes - details.recipe.shortCode
                RecipeForm.from(details, locale())
            }
            initial = form
            state.update { it.copy(loading = false, form = form) }
            onPickerQueryChange("")
        }
    }

    private fun edit(transform: (RecipeForm) -> RecipeForm) = state.update { current ->
        val form = transform(current.form)
        current.copy(form = form, changed = form != initial)
    }

    fun onNameChange(name: String) = edit { it.withName(name, takenCodes) }
    fun onServingsChange(text: String) = edit { it.copy(servingsText = text) }
    fun onShortCodeChange(code: String) = edit { it.withShortCode(code) }

    // Ingredients

    fun addFood(food: Food) = edit {
        val grams = food.servingGrams?.takeIf { g -> g > 0 } ?: DEFAULT_INGREDIENT_GRAMS
        it.copy(ingredients = it.ingredients + IngredientDraft(id = "", foodUuid = food.uuid, food = food, gramsText = formatEditable(grams)))
    }

    /** A food chosen by the scanner or just created (by its row id). */
    fun addFoodById(foodId: Long) {
        viewModelScope.launch { foods.getFood(foodId)?.let(::addFood) }
    }

    fun onGramsChange(index: Int, text: String) = editIngredient(index) { it.copy(gramsText = text) }
    fun onNoteChange(index: Int, note: String) = editIngredient(index) { it.copy(note = note) }
    fun onOptionalChange(index: Int, optional: Boolean) = editIngredient(index) { it.copy(optional = optional) }
    fun removeIngredient(index: Int) = edit { it.copy(ingredients = it.ingredients.without(index)) }
    fun moveIngredient(index: Int, by: Int) = edit { it.copy(ingredients = it.ingredients.moved(index, by)) }

    private fun editIngredient(index: Int, transform: (IngredientDraft) -> IngredientDraft) = edit {
        it.copy(ingredients = it.ingredients.mapIndexed { i, row -> if (i == index) transform(row) else row })
    }

    // Steps

    fun addStep() = edit { it.copy(steps = it.steps + StepDraft(id = "", text = "")) }
    fun onStepTextChange(index: Int, text: String) = editStep(index) { it.copy(text = text) }
    fun onStepMinutesChange(index: Int, text: String) = editStep(index) { it.copy(minutesText = text) }
    fun onStepPassiveChange(index: Int, passive: Boolean) = editStep(index) { it.copy(passive = passive) }
    fun onStepEquipmentChange(index: Int, equipment: String?) = editStep(index) { it.copy(equipment = equipment) }
    fun removeStep(index: Int) = edit { it.copy(steps = it.steps.without(index)) }
    fun moveStep(index: Int, by: Int) = edit { it.copy(steps = it.steps.moved(index, by)) }

    private fun editStep(index: Int, transform: (StepDraft) -> StepDraft) = edit {
        it.copy(steps = it.steps.mapIndexed { i, step -> if (i == index) transform(step) else step })
    }

    // Storage and details

    fun onFridgeDaysChange(text: String) = edit { it.copy(fridgeDaysText = text) }
    fun onFreezableChange(freezable: Boolean) = edit { it.copy(freezable = freezable) }
    fun onFreezerMonthsChange(text: String) = edit { it.copy(freezerMonthsText = text) }
    fun onReheatNoteChange(text: String) = edit { it.copy(reheatNote = text) }
    fun onMealTypeToggle(meal: MealType) = edit { it.copy(mealTypes = it.mealTypes.toggled(meal)) }
    fun onCuisineChange(cuisine: String?) = edit { it.copy(cuisine = cuisine) }
    fun onTagsChange(text: String) = edit { it.copy(tagsText = text) }
    fun onEquipmentToggle(equipment: Equipment) = edit { it.copy(equipment = it.equipment.toggled(equipment)) }
    fun onMealPrepChange(on: Boolean) = edit { it.copy(mealPrepFriendly = on) }

    // The food picker: recent foods, or a search with basic foods first.

    fun onPickerQueryChange(query: String) {
        state.update { it.copy(pickerQuery = query) }
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            val results = if (query.isBlank()) {
                foods.recentFoods().first()
            } else {
                delay(SEARCH_DEBOUNCE_MS)
                foods.searchLocal(query)
            }
            state.update { it.copy(pickerResults = results) }
        }
    }

    fun save() {
        val current = state.value
        if (current.saving) return
        val toSave = current.form.toRecipe(locale())
        if (toSave == null) {
            state.update { it.copy(showErrors = true) }
            return
        }
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = recipes.save(toSave.recipe, toSave.ingredients, toSave.steps)
            initial = current.form
            state.update { it.copy(saving = false, changed = false) }
            savedChannel.send(id)
        }
    }

    private companion object {
        const val DEFAULT_INGREDIENT_GRAMS = 100.0
        const val SEARCH_DEBOUNCE_MS = 200L

        fun <T> List<T>.without(index: Int): List<T> = filterIndexed { i, _ -> i != index }

        fun <T> List<T>.moved(index: Int, by: Int): List<T> {
            val target = index + by
            if (index !in indices || target !in indices) return this
            return toMutableList().apply { add(target, removeAt(index)) }
        }

        fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
    }
}
