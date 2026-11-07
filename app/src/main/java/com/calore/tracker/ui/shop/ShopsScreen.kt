package com.calore.tracker.ui.shop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.ShopRepository
import com.calore.tracker.data.repository.Store
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.labelRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shops (M5.6): favourites, and a default shop per section (for example meat at Lidl). */
class ShopsViewModel(private val shops: ShopRepository) : ViewModel() {
    val stores: StateFlow<List<Store>> = shops.stores().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFavorite(store: Store, on: Boolean) {
        viewModelScope.launch { shops.setFavorite(store, on) }
    }

    fun setDefault(section: ShopSection, store: Store?) {
        viewModelScope.launch { shops.setDefault(section, store) }
    }
}

/** A shop's name; "Market / other" is translated. */
@Composable
fun storeName(store: Store?): String = when {
    store == null -> stringResource(R.string.shops_default_none)
    store.key == "cz:other" -> stringResource(R.string.store_other)
    else -> store.name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsScreen(onBack: () -> Unit, viewModel: ShopsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shops_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionHeader(stringResource(R.string.shops_favourites), modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
            items(stores.sortedBy { it.name }, key = { it.id }) { store ->
                ListItem(
                    headlineContent = { Text(storeName(store)) },
                    trailingContent = { Checkbox(checked = store.favorite, onCheckedChange = null) },
                    modifier = Modifier.clickable { viewModel.setFavorite(store, !store.favorite) },
                )
            }
            item { SectionHeader(stringResource(R.string.shops_defaults), modifier = Modifier.padding(start = 16.dp, top = 16.dp)) }
            items(ShopSection.entries.filter { it != ShopSection.OTHER }, key = { it.name }) { section ->
                var open by remember { mutableStateOf(false) }
                val current = stores.firstOrNull { section in it.defaultForSections }
                Box {
                    ListItem(
                        headlineContent = { Text(stringResource(section.labelRes())) },
                        supportingContent = { Text(storeName(current)) },
                        modifier = Modifier.clickable { open = true },
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.shops_default_none)) }, onClick = {
                            open = false
                            viewModel.setDefault(section, null)
                        })
                        stores.sortedWith(compareByDescending<Store> { it.favorite }.thenBy { it.name }).forEach { store ->
                            DropdownMenuItem(text = { Text(storeName(store)) }, onClick = {
                                open = false
                                viewModel.setDefault(section, store)
                            })
                        }
                    }
                }
            }
        }
    }
}
