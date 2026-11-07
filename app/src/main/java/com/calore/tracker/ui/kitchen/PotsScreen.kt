package com.calore.tracker.ui.kitchen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.domain.Pot
import com.calore.tracker.domain.parseDecimalIn
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.formatEditable
import com.calore.tracker.ui.formatGrams
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** My pots (M4.2): saved pots and their empty weights. */
class PotsViewModel(private val cooking: CookingRepository) : ViewModel() {
    val pots: StateFlow<List<Pot>> = cooking.pots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: String?, name: String, emptyGrams: Double) {
        viewModelScope.launch { cooking.savePot(id, name, emptyGrams) }
    }

    fun delete(id: String) {
        viewModelScope.launch { cooking.deletePot(id) }
    }
}

/** An empty pot weighs more than nothing and less than 20 kg. */
internal val POT_GRAMS_RANGE = 1.0..20_000.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotsScreen(onBack: () -> Unit, viewModel: PotsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val pots by viewModel.pots.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pots_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.pots_add)) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "hint") {
                Text(
                    stringResource(R.string.pots_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(pots, key = { it.id }) { pot ->
                ListItem(
                    headlineContent = { Text(pot.name) },
                    supportingContent = { Text(stringResource(R.string.pots_empty_weight_value, formatGrams(pot.emptyGrams))) },
                    modifier = Modifier.clickable { editing = pot.id },
                )
            }
        }
    }

    val pot = pots.firstOrNull { it.id == editing }
    if (adding || pot != null) {
        PotDialog(
            pot = pot,
            onSave = { name, grams ->
                viewModel.save(pot?.id, name, grams)
                adding = false
                editing = null
            },
            onDelete = pot?.let {
                {
                    viewModel.delete(it.id)
                    editing = null
                }
            },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun PotDialog(pot: Pot?, onSave: (String, Double) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(pot?.name.orEmpty()) }
    var gramsText by rememberSaveable { mutableStateOf(pot?.emptyGrams?.let { formatEditable(it) }.orEmpty()) }
    val grams = parseDecimalIn(gramsText, POT_GRAMS_RANGE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (pot == null) R.string.pots_add else R.string.pots_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.pots_name)) }, singleLine = true)
                NumberField(
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    label = stringResource(R.string.pots_empty_weight),
                    suffix = stringResource(R.string.unit_g),
                    errorText = if (gramsText.isNotEmpty() && grams == null) stringResource(R.string.error_pot_grams) else null,
                )
                Text(stringResource(R.string.pots_weigh_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { grams?.let { onSave(name, it) } }, enabled = name.isNotBlank() && grams != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
