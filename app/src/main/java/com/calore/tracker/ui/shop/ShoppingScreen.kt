package com.calore.tracker.ui.shop

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.ProductSource
import com.calore.tracker.data.repository.ShoppingItem
import com.calore.tracker.data.repository.Store
import com.calore.tracker.domain.parseDecimal
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.LocalHideNumbers
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.ui.formatGrams
import com.calore.tracker.ui.formatNumber

/** The Shop tab (M5.7): the list by shop and section, the basket, manual items and product picks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    onScanProduct: () -> Unit,
    onOpenShops: () -> Unit,
    scannedFoodId: Long? = null,
    onScannedFoodUsed: () -> Unit = {},
    viewModel: ShoppingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickingFor by viewModel.pickingFor.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var ticking by remember { mutableStateOf<ShoppingItem?>(null) }
    var details by remember { mutableStateOf<ShoppingItem?>(null) }
    var scanned by remember { mutableStateOf<com.calore.tracker.domain.Food?>(null) }
    val labels = storeLabels(state.stores)
    val resources = LocalResources.current
    val context = LocalContext.current

    LaunchedEffect(scannedFoodId) {
        if (scannedFoodId != null) {
            scanned = viewModel.scannedFood(scannedFoodId)
            onScannedFoodUsed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shop_title)) },
                actions = {
                    IconButton(onClick = viewModel::regenerate) { Icon(Icons.Filled.Refresh, stringResource(R.string.shop_regenerate)) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.shop_share)) }, onClick = {
                                menuOpen = false
                                // M5.10: the unticked items, by shop, through the Android share sheet.
                                val text = shareText(state, labels, resources)
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(send, null))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.shop_clear_ticked)) }, onClick = {
                                menuOpen = false
                                viewModel.clearTicked()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.shops_title)) }, onClick = {
                                menuOpen = false
                                onOpenShops()
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.shop_add_item)) })
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (state.loaded && state.items.isEmpty()) {
                item { Text(stringResource(R.string.shop_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            val byStore = state.open.groupBy { it.storeId }
            val storeOrder = state.stores.sortedWith(compareByDescending<Store> { it.favorite }.thenBy { it.name })
            (storeOrder.map { it.id } + listOf<String?>(null)).forEach { storeId ->
                val lines = byStore[storeId] ?: return@forEach
                val store = state.stores.firstOrNull { it.id == storeId }
                val order = store?.sectionOrder.orEmpty()
                item(key = "store_$storeId") {
                    SectionHeader(if (store == null) stringResource(R.string.shop_no_store) else storeName(store), modifier = Modifier.padding(start = 16.dp, top = 16.dp))
                }
                items(lines.sortedBy { line -> order.indexOf(line.section).let { if (it < 0) Int.MAX_VALUE else it } }, key = { it.id }) { line ->
                    ShoppingLine(line, state, onTick = { ticking = line }, onClick = { details = line }, onPick = { viewModel.startPicking(line) })
                }
            }
            if (state.basket.isNotEmpty()) {
                item(key = "basket") { SectionHeader(stringResource(R.string.shop_basket), modifier = Modifier.padding(start = 16.dp, top = 24.dp)) }
                items(state.basket, key = { it.id }) { line ->
                    ListItem(
                        leadingContent = { Checkbox(checked = true, onCheckedChange = null) },
                        headlineContent = { Text(lineTitle(line, state), textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
            }
        }
    }

    if (adding) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.shop_add_item)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.shop_item_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    adding = false
                    viewModel.addManual(name)
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    ticking?.let { line ->
        PriceDialog(
            onDone = { price ->
                ticking = null
                viewModel.tick(line, price)
            },
            onDismiss = { ticking = null },
        )
    }
    details?.let { line ->
        val recipes = line.foodUuid?.let { state.neededFor[it] }.orEmpty()
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(lineTitle(line, state)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recipes.isNotEmpty()) Text(stringResource(R.string.shop_needed_for, recipes.joinToString(", ")))
                    line.storeProductId?.let { state.products[it] }?.let { product ->
                        product.brand?.let { Text(it) }
                        product.barcode?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text(stringResource(R.string.cancel)) } },
            dismissButton = {
                TextButton(onClick = {
                    details = null
                    viewModel.delete(line)
                }) { Text(stringResource(R.string.recipe_remove), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
    pickingFor?.let { line ->
        val results by viewModel.onlineResults.collectAsStateWithLifecycle()
        PickProductDialog(
            line = line,
            state = state,
            scanned = scanned,
            online = results,
            onSearch = viewModel::searchOnline,
            onScan = onScanProduct,
            onPick = { store, name, brand, barcode, grams, source ->
                scanned = null
                viewModel.pick(line, store, name, brand, barcode, grams, source)
            },
            onDismiss = {
                scanned = null
                viewModel.startPicking(null)
            },
        )
    }
}

@Composable
private fun ShoppingLine(line: ShoppingItem, state: ShoppingUiState, onTick: () -> Unit, onClick: () -> Unit, onPick: () -> Unit) {
    val hideNumbers = LocalHideNumbers.current
    val product = line.storeProductId?.let { state.products[it] }
    ListItem(
        leadingContent = { Checkbox(checked = false, onCheckedChange = { onTick() }) },
        headlineContent = { Text(lineTitle(line, state)) },
        supportingContent = {
            Column {
                when {
                    product != null && line.packs != null -> Text(
                        stringResource(R.string.shop_packs, formatGrams(product.packAmount), unitLabel(product.packUnit), line.packs),
                    )
                    line.amountNeeded != null -> Text(stringResource(R.string.value_g, formatGrams(line.amountNeeded)))
                }
                product?.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                val price = product?.priceHalers
                if (!hideNumbers && price != null && line.packs != null) {
                    Text(stringResource(R.string.shop_price_estimate, formatNumber(price * line.packs / 100.0, decimals = 2)), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        trailingContent = if (!line.manual && line.foodUuid != null && product == null) {
            { TextButton(onClick = onPick) { Text(stringResource(R.string.shop_pick_product)) } }
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun lineTitle(line: ShoppingItem, state: ShoppingUiState): String =
    line.storeProductId?.let { state.products[it]?.name } ?: line.foodUuid?.let { state.foods[it]?.displayName() } ?: line.name

@Composable
private fun unitLabel(unit: String): String = when (unit) {
    "ML" -> stringResource(R.string.unit_ml)
    "PIECE" -> stringResource(R.string.unit_pieces)
    else -> stringResource(R.string.unit_g)
}

@Composable
private fun storeLabels(stores: List<Store>): Map<String, String> = stores.associate { it.id to storeName(it) }

@Composable
private fun PriceDialog(onDone: (Long?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val price = parseDecimal(text)?.takeIf { it >= 0 && it < 100_000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            NumberField(value = text, onValueChange = { text = it }, label = stringResource(R.string.shop_price_paid), suffix = stringResource(R.string.unit_czk))
        },
        confirmButton = {
            TextButton(onClick = { onDone(price?.let { Math.round(it * 100) }) }, enabled = text.isBlank() || price != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(stringResource(R.string.shop_skip)) } },
    )
}

/** Pick a product (M5.6 source 2): type it, search Open Food Facts, or scan it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickProductDialog(
    line: ShoppingItem,
    state: ShoppingUiState,
    scanned: com.calore.tracker.domain.Food?,
    online: List<com.calore.tracker.data.remote.RemoteFood>,
    onSearch: (String) -> Unit,
    onScan: () -> Unit,
    onPick: (Store, String, String?, String?, Double, ProductSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultStore = state.stores.firstOrNull { line.section in it.defaultForSections } ?: state.stores.firstOrNull { it.favorite } ?: state.stores.firstOrNull()
    var storeId by rememberSaveable { mutableStateOf(defaultStore?.id) }
    var name by rememberSaveable(scanned) { mutableStateOf(scanned?.name ?: "") }
    var brand by rememberSaveable(scanned) { mutableStateOf(scanned?.brand ?: "") }
    var barcode by rememberSaveable(scanned) { mutableStateOf(scanned?.barcode) }
    var packText by rememberSaveable(scanned) { mutableStateOf(scanned?.defaultPackGrams?.let { formatEditable(it) }.orEmpty()) }
    var source by rememberSaveable(scanned) { mutableStateOf(if (scanned != null) ProductSource.OFF else ProductSource.USER) }
    var query by rememberSaveable { mutableStateOf(state.foods[line.foodUuid]?.displayName().orEmpty()) }
    val pack = parseDecimal(packText)?.takeIf { it > 0 }
    val store = state.stores.firstOrNull { it.id == storeId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shop_pick_product)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.product_shop), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.stores.sortedWith(compareByDescending<Store> { it.favorite }.thenBy { it.name }).forEach { option ->
                        FilterChip(selected = option.id == storeId, onClick = { storeId = option.id }, label = { Text(storeName(option)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.product_search_off)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onSearch(query) }) { Icon(Icons.Filled.Search, stringResource(R.string.product_search_off)) }
                    IconButton(onClick = onScan) { Icon(painterResource(R.drawable.ic_barcode), stringResource(R.string.scanner_title)) }
                }
                Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    online.forEach { remote ->
                        ListItem(
                            headlineContent = { Text(remote.name) },
                            supportingContent = { Text(listOfNotNull(remote.brand, remote.packGrams?.let { formatGrams(it) + " g" }).joinToString(" · ")) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                name = remote.name
                                brand = remote.brand.orEmpty()
                                barcode = remote.barcode
                                packText = remote.packGrams?.let { formatEditable(it) }.orEmpty()
                                source = ProductSource.OFF
                            },
                        )
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.product_name)) }, singleLine = true)
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text(stringResource(R.string.product_brand)) }, singleLine = true)
                NumberField(value = packText, onValueChange = { packText = it }, label = stringResource(R.string.product_pack), suffix = stringResource(R.string.unit_g))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (store != null && pack != null) onPick(store, name, brand.ifBlank { null }, barcode, pack, source) },
                enabled = store != null && pack != null && name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The list as text for the share sheet (M5.10): unticked items grouped by shop. */
private fun shareText(state: ShoppingUiState, labels: Map<String, String>, resources: android.content.res.Resources): String {
    val groups = state.open.groupBy { it.storeId }.map { (storeId, lines) ->
        val title = storeId?.let { labels[it] } ?: resources.getString(R.string.shop_no_store)
        title to lines.map { line ->
            val product = line.storeProductId?.let { state.products[it] }
            val name = product?.name ?: line.foodUuid?.let { state.foods[it]?.displayName() } ?: line.name
            val amount = when {
                product != null && line.packs != null -> resources.getString(
                    R.string.shop_packs,
                    formatGrams(product.packAmount),
                    resources.getString(if (product.packUnit == "ML") R.string.unit_ml else if (product.packUnit == "PIECE") R.string.unit_pieces else R.string.unit_g),
                    line.packs,
                )
                line.amountNeeded != null -> resources.getString(R.string.value_g, formatGrams(line.amountNeeded))
                else -> null
            }
            listOfNotNull(name, amount).joinToString(" ")
        }
    }
    return formatShareText(resources.getString(R.string.shop_title), groups)
}

/** "Title", then each shop and its lines as "• line". Plain text that reads well in any chat app. */
internal fun formatShareText(title: String, groups: List<Pair<String, List<String>>>): String = buildString {
    append(title)
    groups.forEach { (shop, lines) ->
        append("\n\n").append(shop).append(':')
        lines.forEach { append("\n• ").append(it) }
    }
}
