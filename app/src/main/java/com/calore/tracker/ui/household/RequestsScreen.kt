package com.calore.tracker.ui.household

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.household.RequestStatus
import com.calore.tracker.data.household.RequestType
import com.calore.tracker.data.household.requestStatus
import com.calore.tracker.data.household.requestType
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.household.RequestText
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.DatePickerModal
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.labelRes
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** Requests (M11.7) and the activity feed (M11.8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(onBack: () -> Unit, viewModel: RequestsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var cooking by rememberSaveable { mutableStateOf(false) }
    var buying by rememberSaveable { mutableStateOf(false) }
    var suggesting by remember { mutableStateOf<RequestItem?>(null) }
    val context = LocalContext.current
    val share: (RequestItem) -> Unit = { item ->
        val text = RequestText.asMessage(context, item.request, item.itemName, item.from?.name.orEmpty())
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.requests_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.requests_tab)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.activity_tab)) })
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.loaded) return@Column
                if (tab == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { cooking = true }) { Text(stringResource(R.string.request_cook_for_me)) }
                        OutlinedButton(onClick = { buying = true }) { Text(stringResource(R.string.request_buy_for_me)) }
                    }
                    SectionHeader(stringResource(R.string.requests_for_me))
                    if (state.incoming.isEmpty()) Text(stringResource(R.string.requests_none), style = MaterialTheme.typography.bodyMedium)
                    state.incoming.forEach { item ->
                        RequestCard(item, onShare = { share(item) }) {
                            if (item.request.requestStatus == RequestStatus.OPEN) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when (item.request.requestType) {
                                        RequestType.COOK -> {
                                            Button(onClick = { viewModel.answer(item.request, RequestStatus.ACCEPTED) }) { Text(stringResource(R.string.request_accept)) }
                                            TextButton(onClick = { suggesting = item }) { Text(stringResource(R.string.request_suggest)) }
                                            TextButton(onClick = { viewModel.answer(item.request, RequestStatus.DECLINED) }) { Text(stringResource(R.string.request_decline)) }
                                        }
                                        RequestType.CAN_I_HAVE -> {
                                            Button(onClick = { viewModel.answer(item.request, RequestStatus.ACCEPTED) }) { Text(stringResource(R.string.request_yes)) }
                                            TextButton(onClick = { viewModel.answer(item.request, RequestStatus.DECLINED) }) { Text(stringResource(R.string.request_no)) }
                                        }
                                        RequestType.BUY, RequestType.OTHER ->
                                            Button(onClick = { viewModel.answer(item.request, RequestStatus.DONE) }) { Text(stringResource(R.string.request_done)) }
                                    }
                                }
                            } else if (item.request.requestStatus == RequestStatus.ACCEPTED && item.request.requestType == RequestType.COOK) {
                                TextButton(onClick = { viewModel.answer(item.request, RequestStatus.DONE) }) { Text(stringResource(R.string.request_done)) }
                            }
                        }
                    }
                    SectionHeader(stringResource(R.string.requests_sent))
                    if (state.sent.isEmpty()) Text(stringResource(R.string.requests_none), style = MaterialTheme.typography.bodyMedium)
                    state.sent.forEach { item ->
                        RequestCard(item, onShare = { share(item) }) {
                            if (item.request.requestStatus == RequestStatus.OPEN) {
                                TextButton(onClick = { viewModel.cancel(item.request) }) { Text(stringResource(R.string.request_cancel)) }
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.activity_hint), style = MaterialTheme.typography.bodySmall)
                    if (state.feed.isEmpty()) Text(stringResource(R.string.activity_none), style = MaterialTheme.typography.bodyMedium)
                    state.feed.forEach { (line, who) ->
                        Column {
                            Text(RequestText.activity(context, line, who?.name ?: "?"), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(line.at)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (cooking) {
        CookForMeDialog(
            today = state.today,
            others = state.others,
            recipes = state.recipes,
            onSend = { to, date, meal, recipe, portions, message ->
                cooking = false
                viewModel.cookForMe(to, date, meal, recipe, portions, message)
            },
            onDismiss = { cooking = false },
        )
    }
    if (buying) {
        BuyForMeDialog(
            onSend = { name, message ->
                buying = false
                viewModel.buyForMe(name, message)
            },
            onDismiss = { buying = false },
        )
    }
    suggesting?.let { item ->
        DayAndMealDialog(
            title = stringResource(R.string.request_suggest),
            today = state.today,
            onDone = { date, meal ->
                suggesting = null
                viewModel.suggest(item.request, date, meal)
            },
            onDismiss = { suggesting = null },
        )
    }
}

@Composable
private fun RequestCard(item: RequestItem, onShare: () -> Unit, actions: @Composable () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val who = if (item.mine) {
                item.to?.name?.let { stringResource(R.string.request_to, it) } ?: stringResource(R.string.request_to_anyone)
            } else {
                stringResource(R.string.request_from, item.from?.name ?: "?")
            }
            Text(who, style = MaterialTheme.typography.labelLarge)
            Text(RequestText.what(context, item.request, item.itemName), style = MaterialTheme.typography.bodyLarge)
            item.request.message?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
            Text(RequestText.status(context, item.request), style = MaterialTheme.typography.bodySmall)
            actions()
            TextButton(onClick = onShare) { Text(stringResource(R.string.request_send_as_message)) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CookForMeDialog(
    today: LocalDate,
    others: List<Member>,
    recipes: List<Pair<String, String>>,
    onSend: (to: String?, date: LocalDate, meal: MealType, recipe: Pair<String, String>?, portions: Int, message: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var to by rememberSaveable { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(today.plusDays(1)) }
    var meal by rememberSaveable { mutableStateOf(MealType.DINNER) }
    var recipe by remember { mutableStateOf<Pair<String, String>?>(null) }
    var portions by rememberSaveable { mutableIntStateOf(1) }
    var message by rememberSaveable { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingRecipe by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.request_cook_for_me)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.request_who), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = to == null, onClick = { to = null }, label = { Text(stringResource(R.string.request_anyone)) })
                    others.forEach { m -> FilterChip(selected = to == m.id, onClick = { to = m.id }, label = { Text(m.name) }) }
                }
                OutlinedButton(onClick = { pickingDate = true }) {
                    Text(date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { m -> FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(stringResource(m.labelRes())) }) }
                }
                Box {
                    OutlinedButton(onClick = { pickingRecipe = true }) { Text(recipe?.second ?: stringResource(R.string.request_any_recipe)) }
                    DropdownMenu(expanded = pickingRecipe, onDismissRequest = { pickingRecipe = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.request_any_recipe)) }, onClick = {
                            recipe = null
                            pickingRecipe = false
                        })
                        recipes.forEach { r ->
                            DropdownMenuItem(text = { Text(r.second) }, onClick = {
                                recipe = r
                                pickingRecipe = false
                            })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (portions > 1) portions-- }) { Text("−") }
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.request_portions, portions, portions),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    TextButton(onClick = { if (portions < 20) portions++ }) { Text("+") }
                }
                OutlinedTextField(value = message, onValueChange = { message = it.take(200) }, label = { Text(stringResource(R.string.request_message)) })
            }
        },
        confirmButton = { TextButton(onClick = { onSend(to, date, meal, recipe, portions, message) }) { Text(stringResource(R.string.request_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickingDate) {
        DatePickerModal(initial = date, onDismiss = { pickingDate = false }, onConfirm = {
            date = it
            pickingDate = false
        })
    }
}

@Composable
private fun BuyForMeDialog(onSend: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.request_buy_for_me)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text(stringResource(R.string.request_item)) }, singleLine = true)
                OutlinedTextField(value = message, onValueChange = { message = it.take(200) }, label = { Text(stringResource(R.string.request_message)) })
                Text(stringResource(R.string.request_buy_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSend(name, message) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.request_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DayAndMealDialog(title: String, today: LocalDate, onDone: (LocalDate, MealType) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(today.plusDays(1)) }
    var meal by rememberSaveable { mutableStateOf(MealType.DINNER) }
    var pickingDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingDate = true }) {
                    Text(date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { m -> FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(stringResource(m.labelRes())) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(date, meal) }) { Text(stringResource(R.string.request_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickingDate) {
        DatePickerModal(initial = date, onDismiss = { pickingDate = false }, onConfirm = {
            date = it
            pickingDate = false
        })
    }
}
