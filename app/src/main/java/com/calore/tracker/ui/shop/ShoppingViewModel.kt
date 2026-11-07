package com.calore.tracker.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.remote.RemoteFood
import com.calore.tracker.data.remote.RemoteResult
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.ProductSource
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.data.repository.ShopRepository
import com.calore.tracker.data.repository.ShoppingItem
import com.calore.tracker.data.repository.ShoppingRepository
import com.calore.tracker.data.repository.Store
import com.calore.tracker.data.repository.StoreProduct
import com.calore.tracker.domain.Food
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class ShoppingUiState(
    val loaded: Boolean = false,
    val items: List<ShoppingItem> = emptyList(),
    val stores: List<Store> = emptyList(),
    val products: Map<String, StoreProduct> = emptyMap(),
    val foods: Map<String, Food> = emptyMap(),
    /** Food uuid → names of the planned recipes that need it. */
    val neededFor: Map<String, List<String>> = emptyMap(),
    /** "Buy for me" (M11.7): member id → name, for "for Anna". */
    val names: Map<String, String> = emptyMap(),
) {
    val open: List<ShoppingItem> get() = items.filter { it.checkedAt == null }
    val basket: List<ShoppingItem> get() = items.filter { it.checkedAt != null }
}

/** The shopping list (M5.7). It is brought up to date with the plan when it opens. */
class ShoppingViewModel(
    private val shopping: ShoppingRepository,
    private val shops: ShopRepository,
    private val members: MemberRepository,
    private val foods: FoodRepository,
    private val recipes: RecipeRepository,
    private val clock: Clock,
) : ViewModel() {

    private val extra = MutableStateFlow(ShoppingUiState())

    private val names = combine(members.me(), members.managed(), members.household()) { me, managed, household ->
        (listOf(me) + managed + household).associate { it.id to it.name }
    }

    val uiState: StateFlow<ShoppingUiState> = combine(shopping.items(), shops.stores(), shops.products(), extra, names) { items, stores, products, extra, names ->
        extra.copy(loaded = true, items = items, stores = stores, products = products.associateBy { it.id }, names = names)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    /** The line a product is being picked for, kept while the scanner is open. */
    private val picking = MutableStateFlow<ShoppingItem?>(null)
    val pickingFor: StateFlow<ShoppingItem?> = picking.asStateFlow()

    private val online = MutableStateFlow<List<RemoteFood>>(emptyList())
    val onlineResults: StateFlow<List<RemoteFood>> = online.asStateFlow()

    init {
        regenerate()
    }

    fun regenerate() {
        viewModelScope.launch {
            val me = members.me().first()
            val today = LocalDate.now(clock)
            shopping.regenerate(me, today)
            // Which recipes need each food, and the foods' names for lines without a product.
            val cooks = shopping.upcomingCooks(me, today)
            val needed = mutableMapOf<String, MutableList<String>>()
            val foodMap = mutableMapOf<String, Food>()
            for (cook in cooks) {
                val details = recipes.details(cook.recipeId) ?: continue
                details.ingredients.forEach { ingredient ->
                    needed.getOrPut(ingredient.foodUuid) { mutableListOf() }.let { names ->
                        if (details.recipe.displayName() !in names) names += details.recipe.displayName()
                    }
                }
                foodMap += details.foods
            }
            extra.value = extra.value.copy(neededFor = needed, foods = foodMap)
        }
    }

    fun addManual(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { shopping.addManual(name) }
    }

    fun tick(item: ShoppingItem, priceHalers: Long?) {
        viewModelScope.launch { shopping.tick(item, priceHalers) }
    }

    fun clearTicked() {
        viewModelScope.launch { shopping.clearTicked() }
    }

    fun delete(item: ShoppingItem) {
        viewModelScope.launch { shopping.delete(item) }
    }

    fun startPicking(item: ShoppingItem?) {
        picking.value = item
        online.value = emptyList()
    }

    fun searchOnline(query: String) {
        viewModelScope.launch {
            online.value = (foods.searchOnline(query) as? RemoteResult.Success)?.value.orEmpty()
        }
    }

    suspend fun scannedFood(id: Long): Food? = foods.getFood(id)

    fun priceHistory(productId: String) = shops.priceHistory(productId)

    /** A price typed in on the product (M5.8). */
    fun addPrice(productId: String, halers: Long) {
        viewModelScope.launch { shops.addPrice(productId, halers, LocalDate.now(clock)) }
    }

    /** Saves the pick as this shop's product for the food and uses it on the line (M5.6). */
    fun pick(item: ShoppingItem, store: Store, name: String, brand: String?, barcode: String?, packGrams: Double, source: ProductSource) {
        viewModelScope.launch {
            val id = shops.pickProduct(store.id, item.foodUuid, name, brand, barcode, packGrams, source = source)
            shopping.useProduct(item, id)
            picking.value = null
        }
    }
}
